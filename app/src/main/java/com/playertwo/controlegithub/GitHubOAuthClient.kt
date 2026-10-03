package com.playertwo.controlegithub

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine

internal data class DeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Long,
    val intervalSeconds: Long
) {
    override fun toString() = "DeviceAuthorization(expiresInSeconds=$expiresInSeconds, intervalSeconds=$intervalSeconds)"
}

internal data class GitHubUser(val login: String, val profileUrl: String) {
    companion object {
        fun parse(json: String): GitHubUser {
            val profile = JSONObject(json)
            val login = (profile.opt("login") as? String)?.takeIf { it.matches(Regex("[A-Za-z0-9-]{1,39}")) }
                ?: throw IOException("Perfil inválido")
            val profileUrl = (profile.opt("html_url") as? String) ?: throw IOException("Perfil inválido")
            val uri = runCatching { URI.create(profileUrl) }.getOrNull()
            if (uri?.scheme != "https" || uri.host != "github.com" || uri.port != -1 || uri.userInfo != null ||
                uri.path != "/$login" || uri.query != null || uri.fragment != null
            ) throw IOException("Perfil inválido")
            return GitHubUser(login, profileUrl)
        }
    }
}

internal sealed interface DevicePoll {
    data class Pending(val intervalSeconds: Long) : DevicePoll
    data class Authorized(val accessToken: String) : DevicePoll {
        override fun toString() = "Authorized"
    }
    data object Denied : DevicePoll
    data object Expired : DevicePoll
    data object Failed : DevicePoll
}

internal suspend fun <T> OAuthHttpCall<T>.await(): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    Dispatchers.IO.dispatch(continuation.context) {
        runCatching(::execute).fold(
            onSuccess = { if (continuation.isActive) continuation.resume(it) },
            onFailure = { if (continuation.isActive) continuation.resumeWithException(it) }
        )
    }
}

internal suspend fun GitHubHttpCall.await(): GitHubHttpResult = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    Dispatchers.IO.dispatch(continuation.context) {
        runCatching(::execute).fold(
            onSuccess = { if (continuation.isActive) continuation.resume(it) },
            onFailure = { if (continuation.isActive) continuation.resumeWithException(it) }
        )
    }
}

internal class OAuthHttpCall<T> internal constructor(
    private val endpoint: URI,
    private val fields: Map<String, String>,
    private val connectTimeoutMillis: Int,
    private val readTimeoutMillis: Int,
    private val parse: (String) -> T
) {
    private val cancelled = AtomicBoolean(false)
    @Volatile private var connection: HttpURLConnection? = null

    fun cancel() {
        cancelled.set(true)
        connection?.disconnect()
    }

    fun execute(): T {
        if (cancelled.get()) throw IOException("Cancelled")
        var active: HttpURLConnection? = null
        return try {
            active = endpoint.toURL().openConnection() as HttpURLConnection
            connection = active
            if (cancelled.get()) throw IOException("Cancelled")
            active.apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                doOutput = true
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            }
            val body = fields.entries.joinToString("&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }
            active.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            if (cancelled.get()) throw IOException("Cancelled")
            if (active.responseCode != HttpURLConnection.HTTP_OK) throw IOException("OAuth request failed")
            parse(active.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() })
        } catch (error: Exception) {
            if (cancelled.get()) throw IOException("Cancelled")
            if (error is IOException) throw error
            throw IOException("OAuth response invalid")
        } finally {
            active?.disconnect()
            connection = null
        }
    }

    @Suppress("DEPRECATION")
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}

internal class GitHubOAuthClient(
    private val clientId: String,
    private val authBaseUrl: URI = URI.create("https://github.com/login/"),
    private val apiClient: GitHubHttpClient = GitHubHttpClient(),
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 15_000
) {
    init {
        require(connectTimeoutMillis > 0 && readTimeoutMillis > 0)
        require(authBaseUrl.userInfo == null && authBaseUrl.query == null && authBaseUrl.fragment == null)
        require((authBaseUrl.scheme == "https" && authBaseUrl.host == "github.com" && authBaseUrl.port == -1) ||
            (authBaseUrl.scheme == "http" && authBaseUrl.host == "127.0.0.1" && authBaseUrl.port in 1..65535))
    }

    fun requestDeviceCode(): OAuthHttpCall<DeviceAuthorization> = OAuthHttpCall(
        authBaseUrl.resolve("device/code"),
        mapOf("client_id" to clientId, "scope" to SCOPES),
        connectTimeoutMillis,
        readTimeoutMillis,
        ::parseDeviceAuthorization
    )

    fun poll(deviceCode: String, intervalSeconds: Long): OAuthHttpCall<DevicePoll> = OAuthHttpCall(
        authBaseUrl.resolve("oauth/access_token"),
        mapOf(
            "client_id" to clientId,
            "device_code" to deviceCode,
            "grant_type" to DEVICE_GRANT
        ),
        connectTimeoutMillis,
        readTimeoutMillis
    ) { response ->
        val values = parseForm(response)
        when (values["error"]) {
            "authorization_pending" -> DevicePoll.Pending(intervalSeconds)
            "slow_down" -> DevicePoll.Pending(intervalSeconds + SLOW_DOWN_SECONDS)
            "expired_token", "token_expired" -> DevicePoll.Expired
            "access_denied" -> DevicePoll.Denied
            null -> values["access_token"]?.takeIf(String::isNotBlank)?.let(DevicePoll::Authorized)
                ?: DevicePoll.Failed
            else -> DevicePoll.Failed
        }
    }

    fun user(accessToken: String): GitHubHttpCall = apiClient.get("/user", accessToken)

    private fun parseDeviceAuthorization(response: String): DeviceAuthorization {
        val values = parseForm(response)
        val deviceCode = values["device_code"]?.takeIf(String::isNotBlank) ?: throw IOException("Invalid device code")
        val userCode = values["user_code"]?.takeIf(String::isNotBlank) ?: throw IOException("Invalid user code")
        val verificationUri = values["verification_uri"]
        if (verificationUri != "https://github.com/login/device") throw IOException("Invalid verification URL")
        val expires = values["expires_in"]?.toLongOrNull()?.takeIf { it in 1..900 } ?: throw IOException("Invalid expiry")
        val interval = values["interval"]?.toLongOrNull()?.takeIf { it in 1..expires } ?: throw IOException("Invalid interval")
        return DeviceAuthorization(deviceCode, userCode, verificationUri, expires, interval)
    }

    @Suppress("DEPRECATION")
    private fun parseForm(body: String): Map<String, String> = body.split('&').associate { field ->
        val separator = field.indexOf('=')
        if (separator < 0) URLDecoder.decode(field, "UTF-8") to ""
        else URLDecoder.decode(field.substring(0, separator), "UTF-8") to
            URLDecoder.decode(field.substring(separator + 1), "UTF-8")
    }

    private companion object {
        const val SCOPES = "read:user repo notifications"
        const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
        const val SLOW_DOWN_SECONDS = 5L
    }
}
