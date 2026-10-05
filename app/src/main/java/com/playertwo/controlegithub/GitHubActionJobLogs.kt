package com.playertwo.controlegithub

import java.io.ByteArrayOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.Socket
import java.net.UnknownHostException
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import android.net.DnsResolver
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.SSLParameters
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal sealed interface GitHubActionJobLogsResult {
    data class Loaded(val text: String, val truncated: Boolean) : GitHubActionJobLogsResult
    data object Empty : GitHubActionJobLogsResult
    data class Failed(val message: String, val error: GitHubHttpError? = null) : GitHubActionJobLogsResult
}

internal data class ValidatedLogDownloadTarget(val uri: URI, val addresses: List<InetAddress>)

internal class GitHubActionJobLogsLoader(
    private val client: GitHubHttpClient,
    private val accessToken: String,
    private val repository: GitHubRepository,
    private val resolveHost: ((String) -> Array<InetAddress>)? = null,
    private val downloadLog: (URI, List<InetAddress>, AtomicBoolean, AtomicReference<Socket?>) -> GitHubActionJobLogsResult =
        { uri, addresses, cancelled, activeSocket ->
            downloadLogFromPinnedAddresses(uri, addresses, cancelled, activeSocket)
        },
    private val dnsResolver: CancellableLogHostResolver = CancellableLogHostResolver()
) {
    private val cancelled = AtomicBoolean(false)
    private val apiCall = AtomicReference<GitHubHttpCall?>(null)
    private val downloadSocket = AtomicReference<Socket?>(null)

    fun cancel() {
        cancelled.set(true)
        apiCall.getAndSet(null)?.cancel()
        dnsResolver.cancel()
        downloadSocket.getAndSet(null)?.let { runCatching { it.close() } }
    }

    fun load(jobId: Long): GitHubActionJobLogsResult {
        require(jobId > 0)
        if (cancelled.get()) return GitHubActionJobLogsResult.Failed(GitHubHttpError.CANCELLED.userMessage)

        val apiPath = "/repos/${encodePathSegment(repository.owner)}/${encodePathSegment(repository.name)}/actions/jobs/$jobId/logs"
        val call = client.get(apiPath, accessToken)
        if (!registerApiCallIfActive(call, cancelled, apiCall)) {
            return GitHubActionJobLogsResult.Failed(GitHubHttpError.CANCELLED.userMessage)
        }
        val redirect = try {
            when (val response = call.execute()) {
                is GitHubHttpResult.Success -> return GitHubActionJobLogsResult.Failed(INVALID_REDIRECT_MESSAGE)
                is GitHubHttpResult.Failure -> when {
                    cancelled.get() || response.error == GitHubHttpError.CANCELLED ->
                        return GitHubActionJobLogsResult.Failed(GitHubHttpError.CANCELLED.userMessage)
                    response.statusCode == 302 ->
                        response.headers.location ?: return GitHubActionJobLogsResult.Failed(INVALID_REDIRECT_MESSAGE)
                    else -> return GitHubActionJobLogsResult.Failed(response.error.userMessage, response.error)
                }
            }
        } finally {
            apiCall.compareAndSet(call, null)
        }

        val uri = parseLogDownloadUri(redirect)
            ?: return GitHubActionJobLogsResult.Failed(INVALID_REDIRECT_MESSAGE)
        val host = uri.host ?: return GitHubActionJobLogsResult.Failed(INVALID_REDIRECT_MESSAGE)
        val addresses = try {
            resolveHost?.invoke(host) ?: dnsResolver.resolve(host, cancelled)
        } catch (_: Exception) {
            null
        } ?: return if (cancelled.get()) GitHubActionJobLogsResult.Failed(GitHubHttpError.CANCELLED.userMessage)
        else GitHubActionJobLogsResult.Failed(INVALID_REDIRECT_MESSAGE)
        if (cancelled.get()) return GitHubActionJobLogsResult.Failed(GitHubHttpError.CANCELLED.userMessage)
        if (addresses.isEmpty() || addresses.any { !it.isGloballyRoutable() }) {
            return GitHubActionJobLogsResult.Failed(INVALID_REDIRECT_MESSAGE)
        }

        return downloadLog(uri, addresses.toList(), cancelled, downloadSocket)
    }

    private fun encodePathSegment(value: String) =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

}

internal class CancellableLogHostResolver(
    private val startQuery: (String, LogDnsCancellation, (Array<InetAddress>?, Throwable?) -> Unit) -> Unit =
        ::startPlatformLogDnsQuery,
    private val timeoutMillis: Long = DNS_RESOLUTION_TIMEOUT_MILLIS
) {
    private val activeSignal = AtomicReference<LogDnsCancellation?>(null)
    private val activeWaiter = AtomicReference<CountDownLatch?>(null)

    init { require(timeoutMillis > 0) }

    fun resolve(host: String, cancelled: AtomicBoolean): Array<InetAddress>? {
        if (cancelled.get()) return null
        val signal = LogDnsCancellation()
        val waiter = CountDownLatch(1)
        val answers = AtomicReference<Array<InetAddress>?>(null)
        activeSignal.set(signal)
        activeWaiter.set(waiter)
        if (cancelled.get()) {
            cancel()
            return null
        }
        try {
            if (signal.isCancelled) return null
            startQuery(host, signal) { result, error ->
                if (!signal.isCancelled && error == null) answers.set(result)
                waiter.countDown()
            }
            if (!waiter.await(timeoutMillis, TimeUnit.MILLISECONDS)) signal.cancel()
            return answers.get().takeUnless { cancelled.get() || signal.isCancelled }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            signal.cancel()
            return null
        } catch (_: Exception) {
            signal.cancel()
            return null
        } finally {
            activeSignal.compareAndSet(signal, null)
            activeWaiter.compareAndSet(waiter, null)
        }
    }

    fun cancel() {
    activeSignal.getAndSet(null)?.cancel()
        activeWaiter.getAndSet(null)?.countDown()
    }
}

internal class LogDnsCancellation {
    private val cancelled = AtomicBoolean(false)
    private val cancelOperation = AtomicReference<(() -> Unit)?>(null)
    val isCancelled: Boolean get() = cancelled.get()

    fun setCancelOperation(operation: () -> Unit) {
        cancelOperation.set(operation)
        if (cancelled.get()) cancelOperation.getAndSet(null)?.invoke()
    }

    fun cancel() {
        cancelled.set(true)
        cancelOperation.getAndSet(null)?.invoke()
    }
}

private const val DNS_RESOLUTION_TIMEOUT_MILLIS = 10_000L

internal class BoundedLegacyLogDnsQueries(
    private val maxWorkers: Int = 2,
    queueCapacity: Int = 8,
    private val lookup: (String) -> Array<InetAddress> = InetAddress::getAllByName
) {
    private val executor = ThreadPoolExecutor(
        maxWorkers,
        maxWorkers,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(queueCapacity),
        { runnable -> Thread(runnable, "controlegithub-log-dns").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )

    val activeCount: Int get() = executor.activeCount
    val queuedCount: Int get() = executor.queue.size

    fun submit(
        host: String,
        signal: LogDnsCancellation,
        callback: (Array<InetAddress>?, Throwable?) -> Unit
    ) {
        val task = FutureTask<Unit> {
            if (!signal.isCancelled) {
                try {
                    callback(lookup(host), null)
                } catch (error: Exception) {
                    callback(null, error)
                }
            }
            Unit
        }
        signal.setCancelOperation {
            task.cancel(true)
            executor.remove(task)
        }
        if (!signal.isCancelled) executor.execute(task)
    }
}

private object LegacyLogDnsQueries {
    private val queries = BoundedLegacyLogDnsQueries()
    fun submit(host: String, signal: LogDnsCancellation, callback: (Array<InetAddress>?, Throwable?) -> Unit) =
        queries.submit(host, signal, callback)
}

private fun startPlatformLogDnsQuery(
    host: String,
    signal: LogDnsCancellation,
    callback: (Array<InetAddress>?, Throwable?) -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startAndroidLogDnsQuery(host, signal, callback)
    } else {
        LegacyLogDnsQueries.submit(host, signal, callback)
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun startAndroidLogDnsQuery(
    host: String,
    cancellation: LogDnsCancellation,
    callback: (Array<InetAddress>?, Throwable?) -> Unit
) {
    val signal = CancellationSignal()
    cancellation.setCancelOperation { signal.cancel() }
    if (cancellation.isCancelled) return
    DnsResolver.getInstance().query(
        null,
        host,
        DnsResolver.FLAG_EMPTY,
        Executor { command -> command.run() },
        signal,
        object : DnsResolver.Callback<List<InetAddress>> {
            override fun onAnswer(answer: List<InetAddress>, rcode: Int) {
                if (rcode == 0) callback(answer.toTypedArray(), null)
                else callback(null, UnknownHostException("DNS query failed with response code $rcode"))
            }

            override fun onError(error: DnsResolver.DnsException) = callback(null, error)
        }
    )
}

internal fun registerApiCallIfActive(
    call: GitHubHttpCall,
    cancelled: AtomicBoolean,
    activeCall: AtomicReference<GitHubHttpCall?>
): Boolean {
    activeCall.set(call)
    if (!cancelled.get()) return true
    if (activeCall.compareAndSet(call, null)) call.cancel()
    return false
}

internal fun registerLogSocketIfActive(
    socket: Socket,
    cancelled: AtomicBoolean,
    activeSocket: AtomicReference<Socket?>
): Boolean {
    activeSocket.set(socket)
    if (!cancelled.get()) return true
    if (activeSocket.compareAndSet(socket, null)) runCatching { socket.close() }
    return false
}

internal fun parseJobLogPayload(
    bytes: ByteArray,
    contentType: String?,
    contentEncoding: String?,
    maxBytes: Int = 1_048_576
): GitHubActionJobLogsResult {
    require(maxBytes > 0)
    val fields = contentType.orEmpty().split(';').map(String::trim)
    val mime = fields.firstOrNull()
    if (!mime.equals("text/plain", ignoreCase = true) &&
        !mime.equals("application/octet-stream", ignoreCase = true)
    ) return GitHubActionJobLogsResult.Failed(INVALID_CONTENT_MESSAGE)
    val charset = fields.drop(1).firstOrNull { it.startsWith("charset=", ignoreCase = true) }
        ?.substringAfter('=')?.trim()?.trim('"')
    if (charset != null && !charset.equals("utf-8", ignoreCase = true)) {
        return GitHubActionJobLogsResult.Failed(INVALID_CONTENT_MESSAGE)
    }
    if (!contentEncoding.isNullOrBlank() && !contentEncoding.equals("identity", ignoreCase = true)) {
        return GitHubActionJobLogsResult.Failed(INVALID_CONTENT_MESSAGE)
    }

    val truncated = bytes.size > maxBytes
    if (bytes.isEmpty()) return GitHubActionJobLogsResult.Empty
    val raw = if (truncated) bytes.copyOf(maxBytes) else bytes
    val incompleteSuffix = if (truncated) incompleteUtf8SuffixLength(raw) else 0
    if (incompleteSuffix > 0 && !validLookaheadCompletesUtf8(bytes, maxBytes, incompleteSuffix)) {
        return GitHubActionJobLogsResult.Failed(INVALID_CONTENT_MESSAGE)
    }
    val text = decodeUtf8(raw, truncated) ?: return GitHubActionJobLogsResult.Failed(INVALID_CONTENT_MESSAGE)
    return GitHubActionJobLogsResult.Loaded(text, truncated)
}

private fun validLookaheadCompletesUtf8(bytes: ByteArray, limit: Int, incompleteSuffix: Int): Boolean {
    val start = limit - incompleteSuffix
    val lead = bytes[start].toInt() and 0xff
    val expectedLength = when (lead) {
        in 0xc2..0xdf -> 2
        in 0xe0..0xef -> 3
        in 0xf0..0xf4 -> 4
        else -> return false
    }
    val end = start + expectedLength
    if (bytes.size < end) return false
    return decodeUtf8(bytes.copyOfRange(start, end), truncated = false) != null
}

private fun decodeUtf8(bytes: ByteArray, truncated: Boolean): String? {
    val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
    val usableBytes = if (truncated) bytes.size - incompleteUtf8SuffixLength(bytes) else bytes.size
    return try {
        decoder.decode(ByteBuffer.wrap(bytes, 0, usableBytes)).toString()
    } catch (_: CharacterCodingException) {
        null
    }
}

private fun incompleteUtf8SuffixLength(bytes: ByteArray): Int {
    var sequenceStart = bytes.lastIndex
    var continuationCount = 0
    while (sequenceStart >= 0 && continuationCount < 3 &&
        (bytes[sequenceStart].toInt() and 0xc0) == 0x80
    ) {
        sequenceStart--
        continuationCount++
    }
    if (sequenceStart < 0) return 0

    val lead = bytes[sequenceStart].toInt() and 0xff
    val expectedLength = when (lead) {
        in 0xc2..0xdf -> 2
        in 0xe0..0xef -> 3
        in 0xf0..0xf4 -> 4
        else -> return 0
    }
    val actualLength = continuationCount + 1
    if (actualLength >= expectedLength || bytes.size - sequenceStart != actualLength) return 0

    if (actualLength > 1) {
        val second = bytes[sequenceStart + 1].toInt() and 0xff
        if ((second and 0xc0) != 0x80) return 0
        if (lead == 0xe0 && second < 0xa0 || lead == 0xed && second >= 0xa0 ||
            lead == 0xf0 && second < 0x90 || lead == 0xf4 && second > 0x8f
        ) return 0
    }
    return actualLength
}

internal fun splitJobLogPreview(text: String, maxChunkChars: Int = 4_096): List<String> {
    require(maxChunkChars > 1)
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = minOf(start + maxChunkChars, text.length)
        if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
        chunks += text.substring(start, end)
        start = end
    }
    return chunks
}

private const val INVALID_CONTENT_MESSAGE = "O GitHub retornou conteúdo de logs inválido."

internal fun validateLogDownloadUri(
    location: String,
    resolveHost: (String) -> Array<InetAddress> = InetAddress::getAllByName
): ValidatedLogDownloadTarget? {
    val uri = parseLogDownloadUri(location) ?: return null
    val host = uri.host ?: return null
    val addresses = try { resolveHost(host) } catch (_: Exception) { return null }
    if (addresses.isEmpty() || addresses.any { !it.isGloballyRoutable() }) return null
    return ValidatedLogDownloadTarget(uri, addresses.toList())
}

internal fun parseLogDownloadUri(location: String): URI? {
    val uri = try { URI(location) } catch (_: Exception) { return null }
    if (!uri.isAbsolute || !uri.scheme.equals("https", true) || uri.host.isNullOrBlank() ||
        uri.userInfo != null || uri.fragment != null || uri.port !in setOf(-1, 443)
    ) return null

    val host = uri.host.lowercase()
    if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") ||
        host.endsWith(".internal") || host.endsWith(".test")
    ) return null
    return uri
}

private const val MAX_LOG_BYTES = 1_048_576
private const val UTF8_MAX_LOOKAHEAD_BYTES = 4
private const val MAX_HTTP_HEADER_BYTES = 65_536
private const val CONNECT_TIMEOUT_MILLIS = 15_000
private const val READ_TIMEOUT_MILLIS = 30_000
private const val LOG_USER_AGENT = "ControleGitHub/0.1.0 (Android)"
private const val INVALID_REDIRECT_MESSAGE = "O GitHub retornou um link de logs inválido."
private const val ACCESS_DENIED_MESSAGE = "O GitHub negou o acesso aos logs deste job."
private const val TEMPORARY_LINK_MESSAGE = "O link temporário expirou ou os logs não estão disponíveis. Atualize para tentar novamente."

internal fun downloadLogFromPinnedAddresses(
    uri: URI,
    addresses: List<InetAddress>,
    cancelled: AtomicBoolean,
    activeSocket: AtomicReference<Socket?>,
    connectRaw: (Socket, InetAddress, Int, Int) -> Unit = { raw, address, port, timeout ->
        raw.connect(InetSocketAddress(address, port), timeout)
    },
    sslSocketFactory: SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory,
    peerAddressMatches: (Socket, InetAddress, Int) -> Boolean = { raw, address, port ->
        val remote = raw.remoteSocketAddress as? InetSocketAddress
        raw.isConnected && remote?.port == port && remote.address?.address?.contentEquals(address.address) == true
    }
): GitHubActionJobLogsResult {
    var socket: SSLSocket? = null
    return try {
        socket = openPinnedLogSocket(uri, addresses, cancelled, activeSocket, connectRaw, sslSocketFactory, peerAddressMatches)
            ?: return GitHubActionJobLogsResult.Failed(GitHubHttpError.CANCELLED.userMessage)
        exchangeLogHttpResponse(socket, uri, cancelled)
    } catch (_: java.net.SocketTimeoutException) {
        GitHubActionJobLogsResult.Failed(GitHubHttpError.TIMEOUT.userMessage)
    } catch (_: IOException) {
        if (cancelled.get()) GitHubActionJobLogsResult.Failed(GitHubHttpError.CANCELLED.userMessage)
        else GitHubActionJobLogsResult.Failed(GitHubHttpError.NETWORK.userMessage)
    } catch (_: IllegalArgumentException) {
        GitHubActionJobLogsResult.Failed(INVALID_CONTENT_MESSAGE)
    } finally {
        try { socket?.close() } catch (_: IOException) { }
        activeSocket.compareAndSet(socket, null)
    }
}

internal fun exchangeLogHttpResponse(
    socket: Socket,
    uri: URI,
    cancelled: AtomicBoolean
): GitHubActionJobLogsResult {
    val requestTarget = (uri.rawPath?.takeIf(String::isNotEmpty) ?: "/") +
        (uri.rawQuery?.let { "?$it" } ?: "")
    require(requestTarget.none { it.code < 0x21 || it.code == 0x7f })
    val host = uri.host ?: return GitHubActionJobLogsResult.Failed(INVALID_REDIRECT_MESSAGE)
    val hostForHeader = if (':' in host) "[$host]" else host
    val output = BufferedOutputStream(socket.outputStream)
    output.write(
        ("GET $requestTarget HTTP/1.1\r\n" +
            "Host: $hostForHeader\r\n" +
            "Accept: text/plain, application/octet-stream\r\n" +
            "Accept-Encoding: identity\r\n" +
            "User-Agent: $LOG_USER_AGENT\r\n" +
            "Connection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII)
    )
    output.flush()
    val input = BufferedInputStream(socket.inputStream)
    val statusLine = readHttpLine(input, MAX_HTTP_HEADER_BYTES)
    if (!(statusLine.startsWith("HTTP/1.0 ") || statusLine.startsWith("HTTP/1.1 "))) {
        return GitHubActionJobLogsResult.Failed(INVALID_CONTENT_MESSAGE)
    }
    val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull()
        ?: return GitHubActionJobLogsResult.Failed(INVALID_CONTENT_MESSAGE)
    val headers = readHttpHeaders(input)
    if (cancelled.get()) return GitHubActionJobLogsResult.Failed(GitHubHttpError.CANCELLED.userMessage)
    if (status != 200) return logDownloadFailure(status)
    val bytes = readHttpBody(input, headers, MAX_LOG_BYTES + UTF8_MAX_LOOKAHEAD_BYTES)
    return parseJobLogPayload(bytes, headers["content-type"], headers["content-encoding"], MAX_LOG_BYTES)
}

internal fun openPinnedLogSocket(
    uri: URI,
    addresses: List<InetAddress>,
    cancelled: AtomicBoolean,
    activeSocket: AtomicReference<Socket?>,
    connectRaw: (Socket, InetAddress, Int, Int) -> Unit = { raw, address, port, timeout ->
        raw.connect(InetSocketAddress(address, port), timeout)
    },
    sslSocketFactory: SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory,
    peerAddressMatches: (Socket, InetAddress, Int) -> Boolean = { raw, address, port ->
        val remote = raw.remoteSocketAddress as? InetSocketAddress
        raw.isConnected && remote?.port == port && remote.address?.address?.contentEquals(address.address) == true
    }
): SSLSocket? {
    require(uri.isAbsolute && uri.scheme.equals("https", true) && uri.port in setOf(-1, 443))
    require(uri.userInfo == null && uri.fragment == null)
    val host = uri.host ?: throw IOException()
    require(addresses.isNotEmpty() && addresses.all { it.isGloballyRoutable() })
    var lastError: IOException? = null
    for (address in addresses) {
        if (cancelled.get()) return null
        val raw = Socket()
        if (!registerLogSocketIfActive(raw, cancelled, activeSocket)) return null
        try {
            connectRaw(raw, address, 443, CONNECT_TIMEOUT_MILLIS)
            if (!peerAddressMatches(raw, address, 443)) {
                throw IOException("Connection did not use the selected log address")
            }
            if (cancelled.get()) return null
            val tls = sslSocketFactory.createSocket(raw, host, 443, true) as SSLSocket
            if (!registerLogSocketIfActive(tls, cancelled, activeSocket)) return null
            tls.soTimeout = READ_TIMEOUT_MILLIS
            tls.sslParameters = logTlsParameters(host, tls.sslParameters)
            tls.startHandshake()
            if (cancelled.get()) return null
            return tls
        } catch (error: IOException) {
            lastError = error
            try { raw.close() } catch (_: IOException) { }
            activeSocket.compareAndSet(raw, null)
        }
    }
    throw lastError ?: IOException("Unable to connect to validated log address")
}

internal fun logTlsParameters(host: String, parameters: SSLParameters): SSLParameters {
    parameters.endpointIdentificationAlgorithm = "HTTPS"
    if (':' !in host && host.any(Char::isLetter)) parameters.serverNames = listOf(SNIHostName(host))
    return parameters
}

internal fun logDownloadFailure(status: Int): GitHubActionJobLogsResult.Failed = when (status) {
    403 -> GitHubActionJobLogsResult.Failed(ACCESS_DENIED_MESSAGE)
    401, 404, 410 -> GitHubActionJobLogsResult.Failed(TEMPORARY_LINK_MESSAGE)
    429 -> GitHubActionJobLogsResult.Failed(GitHubHttpError.RATE_LIMITED.userMessage)
    in 500..599 -> GitHubActionJobLogsResult.Failed(GitHubHttpError.SERVER.userMessage)
    in 300..399 -> GitHubActionJobLogsResult.Failed("O link temporário retornou outro redirecionamento, que foi bloqueado.")
    else -> GitHubActionJobLogsResult.Failed(GitHubHttpError.UNEXPECTED.userMessage)
}

private fun readHttpHeaders(input: BufferedInputStream): Map<String, String> {
    val headers = mutableMapOf<String, String>()
    var total = 0
    while (true) {
        val line = readHttpLine(input, MAX_HTTP_HEADER_BYTES - total)
        total += line.length + 2
        if (total > MAX_HTTP_HEADER_BYTES) throw IOException("HTTP headers too large")
        if (line.isEmpty()) return headers
        val colon = line.indexOf(':')
        if (colon <= 0) throw IOException("Invalid HTTP header")
        val name = line.substring(0, colon).trim().lowercase()
        val value = line.substring(colon + 1).trim()
        if (name in headers) throw IOException("Duplicate HTTP header")
        headers[name] = value
    }
}

private fun readHttpLine(input: BufferedInputStream, maxBytes: Int): String {
    if (maxBytes <= 0) throw IOException("HTTP line too large")
    val bytes = ByteArrayOutputStream()
    while (bytes.size() <= maxBytes) {
        val next = input.read()
        if (next < 0) throw IOException("Unexpected end of HTTP response")
        if (next == '\n'.code) {
            val value = bytes.toByteArray()
            val length = if (value.lastOrNull() == '\r'.code.toByte()) value.size - 1 else value.size
            return String(value, 0, length, StandardCharsets.ISO_8859_1)
        }
        bytes.write(next)
    }
    throw IOException("HTTP line too large")
}

internal fun readHttpBody(input: BufferedInputStream, headers: Map<String, String>, byteLimit: Int): ByteArray {
    val output = ByteArrayOutputStream(byteLimit)
    val transferEncoding = headers["transfer-encoding"]
    if (transferEncoding != null) {
        if (!transferEncoding.equals("chunked", true) || headers.containsKey("content-length")) throw IOException("Unsupported transfer encoding")
        while (output.size() < byteLimit) {
            val sizeToken = readHttpLine(input, MAX_HTTP_HEADER_BYTES).substringBefore(';').trim()
            val chunkSize = sizeToken.toLongOrNull(16)?.takeIf { it >= 0 } ?: throw IOException("Invalid chunk size")
            if (chunkSize == 0L) {
                readHttpHeaders(input)
                return output.toByteArray()
            }
            val remaining = byteLimit - output.size()
            val toRead = minOf(chunkSize, remaining.toLong()).toInt()
            copyExactly(input, output, toRead)
            if (chunkSize > toRead) return output.toByteArray()
            if (input.read() != '\r'.code || input.read() != '\n'.code) throw IOException("Invalid chunk boundary")
        }
        return output.toByteArray()
    }

    val contentLength = headers["content-length"]?.toLongOrNull()
    if (headers.containsKey("content-length") && (contentLength == null || contentLength < 0)) throw IOException("Invalid content length")
    if (contentLength != null) {
        copyExactly(input, output, minOf(contentLength, byteLimit.toLong()).toInt())
        return output.toByteArray()
    }

    val buffer = ByteArray(8192)
    while (output.size() < byteLimit) {
        val count = input.read(buffer, 0, minOf(buffer.size, byteLimit - output.size()))
        if (count < 0) break
        if (count > 0) output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun copyExactly(input: BufferedInputStream, output: ByteArrayOutputStream, byteCount: Int) {
    var remaining = byteCount
    val buffer = ByteArray(8192)
    while (remaining > 0) {
        val count = input.read(buffer, 0, minOf(buffer.size, remaining))
        if (count < 0) throw IOException("Incomplete log response")
        if (count > 0) {
            output.write(buffer, 0, count)
            remaining -= count
        }
    }
}

private fun InetAddress.isGloballyRoutable(): Boolean {
    if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress) return false
    val bytes = address
    return when (this) {
        is Inet4Address -> {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            val third = bytes[2].toInt() and 0xff
            when {
                first == 0 || first == 10 || first == 127 || first >= 224 -> false
                first == 100 && second in 64..127 -> false
                first == 169 && second == 254 -> false
                first == 172 && second in 16..31 -> false
                first == 192 && (second == 0 || second == 168) -> false
                first == 192 && second == 88 && third == 99 -> false
                first == 198 && (second == 18 || second == 19) -> false
                first == 198 && second == 51 && third == 100 -> false
                first == 203 && second == 0 && third == 113 -> false
                else -> true
            }
        }
        is Inet6Address -> {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            val third = bytes[2].toInt() and 0xff
            val inGlobalUnicastSpace = first and 0xe0 == 0x20
            val specialPurpose = first == 0x20 && second == 0x01 && third < 0x02
            val secondSegment = (third shl 8) or (bytes[3].toInt() and 0xff)
            val orchid = first == 0x20 && second == 0x01 && secondSegment in 0x10..0x2f
            val sixToFour = first == 0x20 && second == 0x02
            val documentation = first == 0x20 && second == 0x01 && third == 0x0d &&
                (bytes[3].toInt() and 0xff) == 0xb8
            val documentationPrefix = first == 0x3f && second and 0xf0 == 0xf0
            val uniqueLocal = first and 0xfe == 0xfc
            inGlobalUnicastSpace && !specialPurpose && !orchid && !sixToFour && !documentation &&
                !documentationPrefix && !uniqueLocal
        }
        else -> false
    }
}
