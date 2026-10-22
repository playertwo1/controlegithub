package com.playertwo.controlegithub

import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

internal data class DownloadedReleaseAsset(val bytes: Long, val sha256Hex: String)

internal class ReleaseAssetHttpException(val statusCode: Int) : IOException("GitHub asset request failed")

internal class GitHubReleaseAssetClient(
    private val apiBaseUrl: URI = URI("https://api.github.com/"),
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000
) {
    private val activeConnection = AtomicReference<HttpURLConnection?>(null)
    private val allowLoopbackHttp = apiBaseUrl.scheme == "http" && apiBaseUrl.host == "127.0.0.1"

    init {
        require(connectTimeoutMillis > 0 && readTimeoutMillis > 0)
        require(apiBaseUrl.userInfo == null && apiBaseUrl.query == null && apiBaseUrl.fragment == null)
        require(apiBaseUrl.path == "/")
        require((apiBaseUrl.scheme == "https" && apiBaseUrl.host == "api.github.com" && apiBaseUrl.port == -1) ||
            (allowLoopbackHttp && apiBaseUrl.port in 1..65535))
    }

    fun cancel() {
        activeConnection.getAndSet(null)?.disconnect()
    }

    fun download(
        repository: GitHubRepository,
        assetId: Long,
        accessToken: String,
        output: OutputStream,
        isCancelled: () -> Boolean = { false },
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> }
    ): DownloadedReleaseAsset {
        require(assetId > 0 && accessToken.isNotBlank() && accessToken.none(Char::isWhitespace))
        val apiUri = apiBaseUrl.resolve(
            "repos/${encodePathSegment(repository.owner)}/${encodePathSegment(repository.name)}/releases/assets/$assetId"
        )
        val api = open(apiUri, accessToken)
        try {
            when (val status = api.responseCode) {
                HttpURLConnection.HTTP_OK -> return copyBody(api, output, isCancelled, onProgress)
                HttpURLConnection.HTTP_MOVED_TEMP -> {
                    if (isCancelled()) throw java.util.concurrent.CancellationException()
                    val location = api.getHeaderField("Location") ?: throw IOException("GitHub asset redirect missing")
                    if (location.length > MAX_REDIRECT_LENGTH) throw IOException("GitHub asset redirect invalid")
                    val redirect = validateReleaseAssetRedirect(apiUri.resolve(location).toString(), allowLoopbackHttp)
                        ?: throw IOException("GitHub asset redirect invalid")
                    api.disconnect()
                    activeConnection.compareAndSet(api, null)
                    val redirected = open(redirect, accessToken = null)
                    try {
                        if (redirected.responseCode != HttpURLConnection.HTTP_OK) {
                            throw ReleaseAssetHttpException(redirected.responseCode)
                        }
                        return copyBody(redirected, output, isCancelled, onProgress)
                    } finally {
                        redirected.disconnect()
                        activeConnection.compareAndSet(redirected, null)
                    }
                }
                else -> throw ReleaseAssetHttpException(status)
            }
        } finally {
            api.disconnect()
            activeConnection.compareAndSet(api, null)
        }
    }

    fun downloadText(
        repository: GitHubRepository,
        assetId: Long,
        accessToken: String,
        maxBytes: Int = MAX_CHECKSUM_BYTES,
        isCancelled: () -> Boolean = { false }
    ): String {
        require(maxBytes in 1..MAX_CHECKSUM_BYTES)
        val output = LimitedByteArrayOutputStream(maxBytes)
        download(repository, assetId, accessToken, output, isCancelled)
        return output.toString(Charsets.UTF_8.name())
    }

    private fun open(uri: URI, accessToken: String?): HttpURLConnection {
        val connection = uri.toURL().openConnection() as HttpURLConnection
        activeConnection.set(connection)
        connection.apply {
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", USER_AGENT)
            accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        return connection
    }

    private fun copyBody(
        connection: HttpURLConnection,
        output: OutputStream,
        isCancelled: () -> Boolean,
        onProgress: (Long, Long?) -> Unit
    ): DownloadedReleaseAsset {
        val digest = MessageDigest.getInstance("SHA-256")
        val total = connection.contentLengthLong.takeIf { it >= 0 }
        var downloaded = 0L
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        connection.inputStream.use { input ->
            while (true) {
                if (isCancelled()) throw java.util.concurrent.CancellationException()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                downloaded += count
                onProgress(downloaded, total)
            }
        }
        output.flush()
        if (downloaded == 0L) throw IOException("GitHub asset is empty")
        return DownloadedReleaseAsset(downloaded, digest.digest().toHex())
    }

    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private class LimitedByteArrayOutputStream(private val maxBytes: Int) : java.io.ByteArrayOutputStream() {
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (count + length > maxBytes) throw IOException("Checksum asset is too large")
            super.write(bytes, offset, length)
        }

        override fun write(value: Int) {
            if (count + 1 > maxBytes) throw IOException("Checksum asset is too large")
            super.write(value)
        }
    }

    private companion object {
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val MAX_CHECKSUM_BYTES = 256 * 1024
        const val MAX_REDIRECT_LENGTH = 8 * 1024
        const val USER_AGENT = "ControleGitHub/0.1.0 (Android)"
    }
}

internal fun validateReleaseAssetRedirect(raw: String, allowLoopbackHttp: Boolean = false): URI? {
    val uri = try {
        URI(raw)
    } catch (_: Exception) {
        return null
    }
    return uri.takeIf { isAllowedReleaseAssetRedirect(it, allowLoopbackHttp) }
}

internal fun isAllowedReleaseAssetRedirect(uri: URI, allowLoopbackHttp: Boolean): Boolean =
    uri.userInfo == null && uri.fragment == null && !uri.host.isNullOrBlank() &&
        ((uri.scheme == "https" && uri.port in listOf(-1, 443)) ||
            (allowLoopbackHttp && uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port in 1..65535))

internal fun parseSha256Sums(text: String, assetName: String): String? {
    for (line in text.lineSequence()) {
        val separator = line.indexOfFirst(Char::isWhitespace)
        if (separator <= 0) continue
        val hash = line.substring(0, separator)
        if (!SHA256_HEX.matches(hash)) continue
        val fileName = line.substring(separator).trim().removePrefix("*").trim()
        if (fileName == assetName) return hash.lowercase(Locale.ROOT)
    }
    return null
}

internal fun parseGitHubReleaseDigest(value: String): String? {
    if (!value.startsWith("sha256:", ignoreCase = true)) return null
    return value.substringAfter(':').takeIf(SHA256_HEX::matches)?.lowercase(Locale.ROOT)
}

internal fun isSha256Hex(value: String): Boolean = SHA256_HEX.matches(value)

internal fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

private val SHA256_HEX = Regex("[a-fA-F0-9]{64}")
private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(Locale.ROOT, it) }
