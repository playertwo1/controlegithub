package com.playertwo.controlegithub

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

internal sealed interface GitHubHttpResult {
    data class Success(
        val statusCode: Int,
        val body: String,
        val headers: GitHubResponseHeaders = GitHubResponseHeaders()
    ) : GitHubHttpResult

    data class Failure(
        val error: GitHubHttpError,
        val statusCode: Int? = null,
        val headers: GitHubResponseHeaders = GitHubResponseHeaders(),
        val retryAtEpochMillis: Long? = null
    ) : GitHubHttpResult
}

internal data class GitHubResponseHeaders(
    val link: String? = null,
    val retryAfter: String? = null,
    val rateLimitRemaining: String? = null,
    val rateLimitReset: String? = null,
    val location: String? = null
)

internal class GitHubRateLimitGate(private val nowMillis: () -> Long) {
    @Volatile
    private var blockedUntil: Long? = null

    @Synchronized
    fun retryAtEpochMillis(): Long? {
        val deadline = blockedUntil ?: return null
        if (nowMillis() >= deadline) blockedUntil = null
        return blockedUntil
    }

    @Synchronized
    fun block(headers: GitHubResponseHeaders): Long {
        val now = nowMillis()
        val retryAfter = headers.retryAfter?.toLongOrNull()?.takeIf { it >= 0 }
            ?.let { addSeconds(now, it) }
        val reset = headers.rateLimitReset?.toLongOrNull()?.takeIf { it >= 0 }
            ?.takeIf { headers.rateLimitRemaining == "0" }
            ?.let(::epochSecondsToMillis)
        val fallback = if (retryAfter == null && reset == null) addMillis(now, FALLBACK_DELAY_MILLIS) else now
        val deadline = maxOf(addMillis(now, MINIMUM_DELAY_MILLIS), retryAfter ?: now, reset ?: now, fallback)
        return maxOf(blockedUntil?.takeIf { it > now } ?: now, deadline).also { blockedUntil = it }
    }

    private fun addSeconds(now: Long, seconds: Long): Long =
        if (seconds > (Long.MAX_VALUE - now) / 1_000) Long.MAX_VALUE else now + seconds * 1_000

    private fun epochSecondsToMillis(seconds: Long): Long =
        if (seconds > Long.MAX_VALUE / 1_000) Long.MAX_VALUE else seconds * 1_000

    private fun addMillis(now: Long, millis: Long): Long =
        if (now > Long.MAX_VALUE - millis) Long.MAX_VALUE else now + millis

    private companion object {
        const val MINIMUM_DELAY_MILLIS = 1_000L
        const val FALLBACK_DELAY_MILLIS = 60_000L
    }
}

internal enum class GitHubHttpError(val userMessage: String) {
    UNAUTHORIZED("Sua sessão expirou. Conecte-se novamente."),
    FORBIDDEN("Sua conta não tem permissão para acessar este recurso."),
    NOT_FOUND("Este recurso não está disponível para sua conta."),
    RATE_LIMITED("O GitHub limitou temporariamente as consultas."),
    SERVER("O GitHub está com instabilidade. Tente novamente mais tarde."),
    TIMEOUT("A conexão demorou demais. Verifique sua rede e tente novamente."),
    NETWORK("Não foi possível conectar ao GitHub. Verifique sua rede."),
    CANCELLED("A solicitação foi cancelada."),
    UNEXPECTED("Não foi possível concluir a solicitação.")
}

internal class GitHubHttpClient(
    private val baseUrl: URI = URI.create("https://api.github.com/"),
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
    nowMillis: () -> Long = System::currentTimeMillis
) {
    private val rateLimitGate = GitHubRateLimitGate(nowMillis)

    init {
        require(connectTimeoutMillis > 0 && readTimeoutMillis > 0)
        require(isAllowedBase(baseUrl)) { "GitHub API URL must use HTTPS on api.github.com" }
    }

    fun get(path: String, accessToken: String? = null): GitHubHttpCall {
        require(path.startsWith('/') && !path.startsWith("//") && '#' !in path)
        require(accessToken == null || (accessToken.isNotBlank() && accessToken.none(Char::isWhitespace)))

        val target = baseUrl.resolve(path.removePrefix("/"))
        require(sameOrigin(baseUrl, target))
        return GitHubHttpCall(target, accessToken, connectTimeoutMillis, readTimeoutMillis, rateLimitGate)
    }

    internal fun pathFromLink(linkUrl: String): String {
        val target = baseUrl.resolve(URI.create(linkUrl))
        require(target.userInfo == null && target.fragment == null && sameOrigin(baseUrl, target))
        val path = target.rawPath?.takeIf(String::isNotEmpty) ?: throw IllegalArgumentException()
        return path + (target.rawQuery?.let { "?$it" } ?: "")
    }

    private fun isAllowedBase(uri: URI): Boolean {
        if (uri.userInfo != null || uri.query != null || uri.fragment != null || uri.path != "/") return false
        return (uri.scheme == "https" && uri.host == "api.github.com" && uri.port == -1) ||
            (uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port in 1..65535)
    }

    private fun sameOrigin(base: URI, target: URI): Boolean =
        base.scheme == target.scheme && base.host == target.host && base.port == target.port
}

internal class GitHubHttpCall internal constructor(
    private val url: URI,
    private val accessToken: String?,
    private val connectTimeoutMillis: Int,
    private val readTimeoutMillis: Int,
    private val rateLimitGate: GitHubRateLimitGate
) {
    private val cancelled = AtomicBoolean(false)

    @Volatile
    private var connection: HttpURLConnection? = null

    fun cancel() {
        cancelled.set(true)
        connection?.disconnect()
    }

    fun execute(): GitHubHttpResult {
        if (cancelled.get()) return GitHubHttpResult.Failure(GitHubHttpError.CANCELLED)
        rateLimitGate.retryAtEpochMillis()?.let {
            return GitHubHttpResult.Failure(GitHubHttpError.RATE_LIMITED, retryAtEpochMillis = it)
        }

        var activeConnection: HttpURLConnection? = null
        return try {
            activeConnection = url.toURL().openConnection() as HttpURLConnection
            connection = activeConnection
            if (cancelled.get()) return GitHubHttpResult.Failure(GitHubHttpError.CANCELLED)

            activeConnection.apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", API_VERSION)
                setRequestProperty("User-Agent", USER_AGENT)
                accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            }

            val statusCode = activeConnection.responseCode
            val headers = GitHubResponseHeaders(
                link = activeConnection.getHeaderField("Link"),
                retryAfter = activeConnection.getHeaderField("Retry-After"),
                rateLimitRemaining = activeConnection.getHeaderField("X-RateLimit-Remaining"),
                rateLimitReset = activeConnection.getHeaderField("X-RateLimit-Reset"),
                location = activeConnection.getHeaderField("Location")
            )
            if (cancelled.get()) {
                GitHubHttpResult.Failure(GitHubHttpError.CANCELLED)
            } else if (statusCode in 200..299) {
                val body = activeConnection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                if (cancelled.get()) GitHubHttpResult.Failure(GitHubHttpError.CANCELLED)
                else GitHubHttpResult.Success(statusCode, body, headers)
            } else {
                val rateLimited = statusCode == 429 ||
                    (statusCode == 403 &&
                        (headers.retryAfter != null || headers.rateLimitRemaining == "0"))
                val error = if (rateLimited) GitHubHttpError.RATE_LIMITED else errorFor(statusCode)
                val retryAt = if (rateLimited) rateLimitGate.block(headers) else null
                GitHubHttpResult.Failure(error, statusCode, headers, retryAt)
            }
        } catch (_: SocketTimeoutException) {
            failureUnlessCancelled(GitHubHttpError.TIMEOUT)
        } catch (_: IOException) {
            failureUnlessCancelled(GitHubHttpError.NETWORK)
        } finally {
            activeConnection?.disconnect()
            connection = null
        }
    }

    private fun failureUnlessCancelled(error: GitHubHttpError) =
        GitHubHttpResult.Failure(if (cancelled.get()) GitHubHttpError.CANCELLED else error)

    private fun errorFor(statusCode: Int): GitHubHttpError = when (statusCode) {
        401 -> GitHubHttpError.UNAUTHORIZED
        403 -> GitHubHttpError.FORBIDDEN
        404 -> GitHubHttpError.NOT_FOUND
        429 -> GitHubHttpError.RATE_LIMITED
        in 500..599 -> GitHubHttpError.SERVER
        else -> GitHubHttpError.UNEXPECTED
    }

    private companion object {
        const val API_VERSION = "2026-03-10"
        const val USER_AGENT = "ControleGitHub/0.1.0 (Android)"
    }
}
