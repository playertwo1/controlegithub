package com.playertwo.controlegithub

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.io.IOException
import java.net.Socket
import java.nio.file.Files
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.ExtendedSSLSession
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GitHubActionJobLogsTest {
    @Test fun downloadsTextThroughTemporaryRedirectWithoutForwardingToken() {
        val apiServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val apiHeaders = AtomicReference<com.sun.net.httpserver.Headers>()
        val apiPath = AtomicReference<String>()
        apiServer.createContext("/") { exchange ->
            apiHeaders.set(exchange.requestHeaders)
            apiPath.set(exchange.requestURI.toString())
            exchange.responseHeaders.add("Location", "https://logs.example.net/signed?sig=temporary")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        apiServer.start()

        try {
            val loader = GitHubActionJobLogsLoader(
                client = GitHubHttpClient(URI.create("http://127.0.0.1:${apiServer.address.port}/")),
                accessToken = "test-token",
                repository = repository,
                resolveHost = { arrayOf(InetAddress.getByName("8.8.8.8")) },
                downloadLog = { downloadUri, validatedAddresses, cancelled, _ ->
                    assertEquals("https://logs.example.net/signed?sig=temporary", downloadUri.toString())
                    assertEquals(listOf("8.8.8.8"), validatedAddresses.map { it.hostAddress })
                    assertFalse(cancelled.get())
                    GitHubActionJobLogsResult.Loaded("Gradle build failed", truncated = false)
                }
            )

            assertEquals(
                GitHubActionJobLogsResult.Loaded("Gradle build failed", truncated = false),
                loader.load(700)
            )
            assertEquals("Bearer test-token", apiHeaders.get()?.getFirst("Authorization"))
            assertEquals("/repos/acme/Mobile/actions/jobs/700/logs", apiPath.get())
            assertFalse("Downloader receives no token parameter", apiPath.get().contains("test-token"))
        } finally {
            apiServer.stop(0)
        }
    }

    @Test fun rejectsPrivateOrLocalRedirectAddressesBeforeOpeningConnection() {
        var connectionOpened = false
        val privateAddresses = listOf(
            "127.0.0.1", "10.0.0.1", "169.254.2.3", "192.0.2.1", "::1", "fc00::1",
            "2001:db8::1", "2001:2::1", "2001:10::1", "2001:20::1", "2002::1", "3fff::1"
        )
        for (address in privateAddresses) {
            val result = validateLogDownloadUri("https://logs.example.net/file", { arrayOf(InetAddress.getByName(address)) })
            assertNull("$address must not be accepted", result)
        }

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange -> exchange.sendResponseHeaders(302, -1); exchange.close() }
        server.start()
        try {
            val loader = GitHubActionJobLogsLoader(
                client = GitHubHttpClient(URI.create("http://127.0.0.1:${server.address.port}/")),
                accessToken = "test-token",
                repository = repository,
                resolveHost = { arrayOf(InetAddress.getByName("127.0.0.1")) },
                downloadLog = { _, _, _, _ -> connectionOpened = true; error("must not connect") }
            )
            val result = loader.load(700)
            assertTrue(result is GitHubActionJobLogsResult.Failed)
            assertFalse(connectionOpened)
        } finally {
            server.stop(0)
        }
    }

    @Test fun validatesRedirectUriShapeAndPort() {
        val publicResolver: (String) -> Array<InetAddress> = { arrayOf(InetAddress.getByName("8.8.8.8")) }
        val validated = validateLogDownloadUri("https://logs.example.net:443/file?signature=x", publicResolver)
        assertEquals(URI("https://logs.example.net:443/file?signature=x"), validated?.uri)
        assertEquals(listOf("8.8.8.8"), validated?.addresses?.map { it.hostAddress })
        assertNull(validateLogDownloadUri("http://logs.example.net/file", publicResolver))
        assertNull(validateLogDownloadUri("https://user@logs.example.net/file", publicResolver))
        assertNull(validateLogDownloadUri("https://logs.example.net:8443/file", publicResolver))
        assertNull(validateLogDownloadUri("https://logs.example.net/file#fragment", publicResolver))
        assertNull(validateLogDownloadUri("https://localhost/file", publicResolver))
        assertNull(validateLogDownloadUri("https://logs.example.net/file", { emptyArray() }))
    }

    @Test fun opensTcpOnlyForAnAddressFromTheValidatedDnsSet() {
        val attemptedAddresses = mutableListOf<String>()
        val activeSocket = AtomicReference<Socket?>(null)
        val target = URI("https://logs.example.net/signed")
        try {
            openPinnedLogSocket(
                target,
                listOf(InetAddress.getByName("8.8.8.8")),
                AtomicBoolean(false),
                activeSocket,
                connectRaw = { _, address, port, _ ->
                    attemptedAddresses.add(address.hostAddress!!)
                    assertEquals(443, port)
                    throw IOException("Stop before test network I/O")
                }
            )
        } catch (_: IOException) { }
        assertEquals(listOf("8.8.8.8"), attemptedAddresses)
    }

    @Test fun readsBoundedHttpBodiesAndRejectsUnsupportedRedirectResponses() {
        val contentLengthBody = "HTTP test body".toByteArray()
        assertEquals(
            contentLengthBody.toList(),
            readHttpBody(
                BufferedInputStream(ByteArrayInputStream(contentLengthBody)),
                mapOf("content-length" to contentLengthBody.size.toString()),
                64
            ).toList()
        )
        val chunked = "4\r\ntest\r\n0\r\n\r\n".toByteArray()
        assertEquals(
            "test",
            readHttpBody(BufferedInputStream(ByteArrayInputStream(chunked)), mapOf("transfer-encoding" to "chunked"), 64)
                .toString(Charsets.UTF_8)
        )
        val bounded = "8\r\n12345678\r\n0\r\n\r\n".toByteArray()
        assertEquals(
            "1234",
            readHttpBody(BufferedInputStream(ByteArrayInputStream(bounded)), mapOf("transfer-encoding" to "chunked"), 4)
                .toString(Charsets.UTF_8)
        )
        for (status in listOf(300, 302, 307, 308)) {
            assertTrue(logDownloadFailure(status).message.contains("redirecionamento"))
        }
        assertTrue(logDownloadFailure(403).message.contains("negou o acesso"))
        assertTrue(logDownloadFailure(404).message.contains("não estão disponíveis"))
        assertTrue(logDownloadFailure(410).message.contains("não estão disponíveis"))
        assertEquals(GitHubHttpError.RATE_LIMITED.userMessage, logDownloadFailure(429).message)
        assertEquals(GitHubHttpError.SERVER.userMessage, logDownloadFailure(503).message)
    }

    @Test fun successfulHttpDownloadUsesSignedPathWithoutAuthorizationAndParsesTheLog() {
        val socket = RecordingSocket(
            "HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Length: 7\r\n\r\njob log"
        )

        assertEquals(
            GitHubActionJobLogsResult.Loaded("job log", truncated = false),
            exchangeLogHttpResponse(socket, URI("https://logs.example.net/signed?sig=temporary"), AtomicBoolean(false))
        )
        val request = socket.writtenBytes().toString(Charsets.US_ASCII)
        assertTrue(request.startsWith("GET /signed?sig=temporary HTTP/1.1\r\n"))
        assertTrue(request.contains("Host: logs.example.net\r\n"))
        assertTrue(request.contains("Accept-Encoding: identity\r\n"))
        assertFalse(request.contains("Authorization:"))
    }

    @Test fun tlsParametersRequireHttpsHostnameVerificationAndDnsSni() {
        val parameters = logTlsParameters("logs.example.net", SSLParameters())

        assertEquals("HTTPS", parameters.endpointIdentificationAlgorithm)
        assertEquals(listOf(SNIHostName("logs.example.net")), parameters.serverNames)
    }

    @Test fun pinnedDownloaderCompletesVerifiedTlsAndParsesTheHttpLogResponse() {
        val temp = Files.createTempDirectory("controlegithub-test-tls-")
        val keyStoreFile = temp.resolve("logs.p12")
        val keytool = java.nio.file.Paths.get(
            System.getProperty("java.home"), "bin", if (System.getProperty("os.name").orEmpty().startsWith("Windows")) "keytool.exe" else "keytool"
        ).toString()
        val generated = ProcessBuilder(
            keytool, "-genkeypair", "-alias", "logs", "-keyalg", "RSA", "-keysize", "2048",
            "-validity", "1", "-storetype", "PKCS12", "-keystore", keyStoreFile.toString(),
            "-storepass", "test-only", "-keypass", "test-only", "-dname", "CN=logs.example.net",
            "-ext", "SAN=dns:logs.example.net", "-noprompt"
        ).redirectErrorStream(true).start()
        assertTrue(generated.waitFor(20, TimeUnit.SECONDS))
        assertEquals(generated.inputStream.bufferedReader().readText(), 0, generated.exitValue())

        val keyStore = KeyStore.getInstance("PKCS12").apply {
            Files.newInputStream(keyStoreFile).use { load(it, "test-only".toCharArray()) }
        }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, "test-only".toCharArray())
        }
        val serverContext = SSLContext.getInstance("TLS").apply { init(keyManagers.keyManagers, null, null) }
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("logs", keyStore.getCertificate("logs") as X509Certificate)
        }
        val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(trustStore)
        }
        val clientContext = SSLContext.getInstance("TLS").apply { init(null, trustManagers.trustManagers, null) }
        val server = serverContext.serverSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress()) as SSLServerSocket
        val requestHeaders = AtomicReference<List<String>>(emptyList())
        val sniReceived = AtomicBoolean(false)
        val serverThread = Thread {
            server.accept().use { connected ->
                val tls = connected as SSLSocket
                tls.startHandshake()
                sniReceived.set((tls.session as ExtendedSSLSession).requestedServerNames.any {
                    it is SNIHostName && it.asciiName == "logs.example.net"
                })
                val reader = tls.inputStream.bufferedReader(Charsets.US_ASCII)
                val lines = mutableListOf<String>()
                while (true) {
                    val line = reader.readLine() ?: error("Incomplete HTTP request")
                    if (line.isEmpty()) break
                    lines += line
                }
                requestHeaders.set(lines)
                val response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=UTF-8\r\n" +
                    "Content-Length: 10\r\nConnection: close\r\n\r\nsecure log"
                tls.outputStream.write(response.toByteArray(Charsets.US_ASCII))
                tls.outputStream.flush()
            }
        }.apply { start() }

        try {
            val result = downloadLogFromPinnedAddresses(
                URI("https://logs.example.net/signed?sig=temporary"),
                listOf(InetAddress.getByName("8.8.8.8")),
                AtomicBoolean(false),
                AtomicReference(null),
                connectRaw = { raw, _, _, timeout ->
                    raw.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), server.localPort), timeout)
                },
                sslSocketFactory = clientContext.socketFactory,
                peerAddressMatches = { _, _, _ -> true }
            )
            serverThread.join(5_000)
            assertFalse(serverThread.isAlive)
            assertEquals(GitHubActionJobLogsResult.Loaded("secure log", truncated = false), result)
            assertTrue(sniReceived.get())
            assertTrue(requestHeaders.get().any { it == "GET /signed?sig=temporary HTTP/1.1" })
            assertFalse(requestHeaders.get().any { it.startsWith("Authorization:", ignoreCase = true) })
        } finally {
            server.close()
            serverThread.join(5_000)
            Files.walk(temp).use { paths ->
                paths.sorted(java.util.Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    @Test fun rejectsChunkedBodyMissingTheEndOfTrailerSection() {
        try {
            readHttpBody(
                BufferedInputStream(ByteArrayInputStream("0\r\n".toByteArray())),
                mapOf("transfer-encoding" to "chunked"),
                64
            )
            fail("An incomplete chunked trailer section must not be treated as an empty body")
        } catch (_: IOException) {
            // Expected: the terminating empty trailer line is missing.
        }
    }

    @Test fun registrationAfterCancellationCancelsTheApiCallInsteadOfStartingIt() {
        val call = GitHubHttpClient(URI.create("http://127.0.0.1:1/")).get("/logs", "test-token")
        val cancelled = AtomicBoolean(true)
        val activeCall = AtomicReference<GitHubHttpCall?>(null)

        assertFalse(registerApiCallIfActive(call, cancelled, activeCall))
        assertNull(activeCall.get())
        assertEquals(GitHubHttpError.CANCELLED, (call.execute() as GitHubHttpResult.Failure).error)
    }

    @Test fun socketPublishedAfterCancellationIsClosedImmediately() {
        val socket = Socket()
        val cancelled = AtomicBoolean(true)
        val activeSocket = AtomicReference<Socket?>(null)

        assertFalse(registerLogSocketIfActive(socket, cancelled, activeSocket))
        assertTrue(socket.isClosed)
        assertNull(activeSocket.get())
    }

    @Test fun splitsRenderedLogIntoBoundedChunksWithoutBreakingSurrogatePairs() {
        val text = "a".repeat(4_095) + "🙂" + "b".repeat(4_100)
        val chunks = splitJobLogPreview(text)
        assertEquals(text, chunks.joinToString(separator = ""))
        assertTrue(chunks.all { it.length <= 4_096 })
        assertFalse(chunks.any { it.isNotEmpty() && (it.first().isLowSurrogate() || it.last().isHighSurrogate()) })
    }

    @Test fun cancellationDuringTemporaryDownloadDiscardsTheLateResult() {
        val apiServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        apiServer.createContext("/") { exchange ->
            exchange.responseHeaders.add("Location", "https://logs.example.net/signed?sig=temporary")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        apiServer.start()
        val enteredDownloader = CountDownLatch(1)
        val finishDownloader = CountDownLatch(1)
        val output = AtomicReference<GitHubActionJobLogsResult?>()
        val loader = GitHubActionJobLogsLoader(
            client = GitHubHttpClient(URI.create("http://127.0.0.1:${apiServer.address.port}/")),
            accessToken = "test-token",
            repository = repository,
            resolveHost = { arrayOf(InetAddress.getByName("8.8.8.8")) },
            downloadLog = { _, _, cancelled, _ ->
                enteredDownloader.countDown()
                finishDownloader.await(5, TimeUnit.SECONDS)
                if (cancelled.get()) GitHubActionJobLogsResult.Failed(GitHubHttpError.CANCELLED.userMessage)
                else GitHubActionJobLogsResult.Loaded("stale", truncated = false)
            }
        )
        try {
            val worker = Thread { output.set(loader.load(700)) }
            worker.start()
            assertTrue(enteredDownloader.await(5, TimeUnit.SECONDS))
            loader.cancel()
            finishDownloader.countDown()
            worker.join(5_000)
            assertFalse(worker.isAlive)
            assertEquals(
                GitHubActionJobLogsResult.Failed(GitHubHttpError.CANCELLED.userMessage),
                output.get()
            )
        } finally {
            apiServer.stop(0)
        }
    }

    @Test fun cancellationDuringDnsResolutionCancelsTheQueryAndReturnsPromptly() {
        val queryStarted = CountDownLatch(1)
        val activeSignal = AtomicReference<LogDnsCancellation?>()
        val dnsResolver = CancellableLogHostResolver(startQuery = { _, signal, _ ->
            activeSignal.set(signal)
            queryStarted.countDown()
        })
        val cancelled = AtomicBoolean(false)
        val output = AtomicReference<Array<InetAddress>?>(null)
        try {
            val worker = Thread { output.set(dnsResolver.resolve("logs.example.net", cancelled)) }
            worker.start()
            assertTrue(queryStarted.await(5, TimeUnit.SECONDS))
            cancelled.set(true)
            dnsResolver.cancel()
            worker.join(1_000)
            assertFalse("DNS cancellation must release the waiting load", worker.isAlive)
            assertTrue(activeSignal.get()?.isCancelled == true)
            assertNull(output.get())
        } finally {
            cancelled.set(true)
            dnsResolver.cancel()
        }
    }

    @Test fun acceptsOnlyPlainUtf8AndReportsEmptyOrTruncatedBodiesExplicitly() {
        assertEquals(
            GitHubActionJobLogsResult.Loaded("ok", truncated = false),
            parseJobLogPayload("ok".toByteArray(), "text/plain; charset=UTF-8", "identity", 4)
        )
        assertEquals(
            GitHubActionJobLogsResult.Empty,
            parseJobLogPayload(byteArrayOf(), "application/octet-stream", null, 4)
        )
        assertEquals(
            GitHubActionJobLogsResult.Loaded("abcd", truncated = true),
            parseJobLogPayload("abcde".toByteArray(), "text/plain", null, 4)
        )
        assertEquals(
            GitHubActionJobLogsResult.Loaded("ab", truncated = true),
            parseJobLogPayload("ab🙂".toByteArray(), "text/plain", null, 4)
        )
        assertTrue(parseJobLogPayload("<p>unsafe</p>".toByteArray(), "text/html", null) is GitHubActionJobLogsResult.Failed)
        assertTrue(parseJobLogPayload("text".toByteArray(), "text/plain; charset=iso-8859-1", null) is GitHubActionJobLogsResult.Failed)
        assertTrue(parseJobLogPayload("text".toByteArray(), "text/plain", "gzip") is GitHubActionJobLogsResult.Failed)
        assertTrue(parseJobLogPayload(byteArrayOf(0xc3.toByte(), 0x28), "text/plain", null) is GitHubActionJobLogsResult.Failed)
    }

    @Test fun truncationTrimsOnlyAValidIncompleteUtf8CodePoint() {
        val malformedBoundary = byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 0xff.toByte(), 'x'.code.toByte())
        assertTrue(parseJobLogPayload(malformedBoundary, "text/plain", null, maxBytes = 4) is GitHubActionJobLogsResult.Failed)
        val invalidLookahead = byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 0xe2.toByte(), 'A'.code.toByte())
        assertTrue(parseJobLogPayload(invalidLookahead, "text/plain", null, maxBytes = 4) is GitHubActionJobLogsResult.Failed)
        val completeInvalidLookahead = byteArrayOf(
            'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 0xe2.toByte(), 'A'.code.toByte(), 'B'.code.toByte()
        )
        assertTrue(parseJobLogPayload(completeInvalidLookahead, "text/plain", null, maxBytes = 4) is GitHubActionJobLogsResult.Failed)
        assertEquals(
            GitHubActionJobLogsResult.Loaded("abc", truncated = true),
            parseJobLogPayload(
                byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 0xe2.toByte(), 0x82.toByte(), 0xac.toByte()),
                "text/plain",
                null,
                maxBytes = 4
            )
        )
    }

    @Test fun legacyDnsFallbackKeepsBlockedSystemLookupsBounded() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val queries = BoundedLegacyLogDnsQueries(maxWorkers = 1, queueCapacity = 1) {
            started.countDown()
            while (true) {
                try {
                    release.await()
                    break
                } catch (_: InterruptedException) {
                    // Model a system resolver that does not stop when its worker is interrupted.
                }
            }
            arrayOf(InetAddress.getLoopbackAddress())
        }
        val signals = List(12) { LogDnsCancellation() }
        try {
            signals.forEachIndexed { index, signal ->
                try {
                    queries.submit("host-$index.invalid", signal) { _, _ -> }
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    // A saturated legacy pool rejects excess work instead of creating more workers.
                }
                if (index == 0) assertTrue(started.await(2, TimeUnit.SECONDS))
            }
            assertEquals(1, queries.activeCount)
            assertEquals(1, queries.queuedCount)
            signals.forEach(LogDnsCancellation::cancel)
            assertEquals("Cancelled queued DNS work should be removed", 0, queries.queuedCount)
        } finally {
            release.countDown()
            signals.forEach(LogDnsCancellation::cancel)
        }
    }

    private companion object {
        val repository = GitHubRepository(1, "Mobile", "acme/Mobile", "acme", true, null, "Kotlin", 0)
    }

    private class RecordingSocket(response: String) : Socket() {
        private val input = ByteArrayInputStream(response.toByteArray(Charsets.US_ASCII))
        private val output = java.io.ByteArrayOutputStream()
        override fun getInputStream() = input
        override fun getOutputStream() = output
        fun writtenBytes() = output.toByteArray()
    }
}
