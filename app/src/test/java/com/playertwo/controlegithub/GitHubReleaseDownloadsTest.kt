package com.playertwo.controlegithub

import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseDownloadsTest {
    @Test fun parsesChecksumForExactAssetName() {
        val expected = "a".repeat(64)
        assertEquals(expected, parseSha256Sums("$expected  app-release.apk\n${"b".repeat(64)} *other.apk", "app-release.apk"))
        assertNull(parseSha256Sums("$expected  other.apk", "app-release.apk"))
        assertNull(parseSha256Sums("invalid app-release.apk", "app-release.apk"))
    }

    @Test fun parsesGithubDigestAndSanitizesSuggestedFileName() {
        assertEquals("a".repeat(64), parseGitHubReleaseDigest("sha256:${"a".repeat(64)}"))
        assertNull(parseGitHubReleaseDigest("md5:${"a".repeat(64)}"))
        assertNull(parseGitHubReleaseDigest("sha256:bad"))
        assertEquals("release.apk", releaseAssetFileName("../../"))
        assertEquals("app-release.apk", releaseAssetFileName("folder/app-release.apk"))
        assertEquals("app-release.apk", releaseAssetFileName("app-release.apk"))
    }

    @Test fun acceptsOnlyHttpsRedirectsWithoutUserInfoFragmentOrNonstandardPort() {
        assertEquals(URI("https://assets.example.net/file?sig=temporary"), validateReleaseAssetRedirect("https://assets.example.net/file?sig=temporary"))
        assertNull(validateReleaseAssetRedirect("http://assets.example.net/file"))
        assertNull(validateReleaseAssetRedirect("https://user@assets.example.net/file"))
        assertNull(validateReleaseAssetRedirect("https://assets.example.net:8443/file"))
        assertNull(validateReleaseAssetRedirect("https://assets.example.net/file#fragment"))
        assertNull(validateReleaseAssetRedirect("not a uri"))
    }

    @Test fun streamsDirect200BinaryBodyAndChecksApiHeaders() {
        val bytes = byteArrayOf(0, 1, 2, 0xff.toByte())
        val headers = AtomicReference<com.sun.net.httpserver.Headers>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            headers.set(exchange.requestHeaders)
            assertEquals("/repos/acme/Mobile/releases/assets/42", exchange.requestURI.path)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val output = ByteArrayOutputStream()
            val result = GitHubReleaseAssetClient(URI("http://127.0.0.1:${server.address.port}/"))
                .download(repository, 42, "test-token", output)
            assertArrayEquals(bytes, output.toByteArray())
            assertEquals(sha256(bytes), result.sha256Hex)
            assertEquals("Bearer test-token", headers.get()?.getFirst("Authorization"))
            assertEquals("application/octet-stream", headers.get()?.getFirst("Accept"))
        } finally {
            server.stop(0)
        }
    }

    @Test fun follows302WithoutForwardingAuthorization() {
        val bytes = "apk-data".toByteArray()
        val apiAuth = AtomicReference<String>()
        val assetAuth = AtomicReference<String>()
        val assetServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        assetServer.createContext("/signed") { exchange ->
            assetAuth.set(exchange.requestHeaders.getFirst("Authorization"))
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        assetServer.start()
        val apiServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        apiServer.createContext("/") { exchange ->
            apiAuth.set(exchange.requestHeaders.getFirst("Authorization"))
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${assetServer.address.port}/signed")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        apiServer.start()
        try {
            val output = ByteArrayOutputStream()
            GitHubReleaseAssetClient(URI("http://127.0.0.1:${apiServer.address.port}/"))
                .download(repository, 42, "test-token", output)
            assertArrayEquals(bytes, output.toByteArray())
            assertEquals("Bearer test-token", apiAuth.get())
            assertNull("redirect host must never receive the bearer", assetAuth.get())
        } finally {
            apiServer.stop(0)
            assetServer.stop(0)
        }
    }

    @Test fun cancelDisconnectsAnActiveAssetStream() {
        val responseStarted = CountDownLatch(1)
        val releaseServer = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.flush()
            responseStarted.countDown()
            releaseServer.await(5, TimeUnit.SECONDS)
            runCatching { exchange.close() }
        }
        server.start()
        val client = GitHubReleaseAssetClient(URI("http://127.0.0.1:${server.address.port}/"))
        val transfer = Thread {
            runCatching {
                client.download(repository, 42, "test-token", ByteArrayOutputStream())
            }
        }.apply { start() }
        try {
            assertTrue(responseStarted.await(2, TimeUnit.SECONDS))
            client.cancel()
            transfer.join(2_000)
            assertFalse("cancel() should unblock a pending read", transfer.isAlive)
        } finally {
            releaseServer.countDown()
            client.cancel()
            server.stop(0)
        }
    }

    @Test fun doesNotAcceptRedirectForProductionHttpApi() {
        assertFalse(isAllowedReleaseAssetRedirect(URI("http://127.0.0.1:8080/file"), allowLoopbackHttp = false))
        assertTrue(isAllowedReleaseAssetRedirect(URI("http://127.0.0.1:8080/file"), allowLoopbackHttp = true))
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        val repository = GitHubRepository(
            id = 1,
            name = "Mobile",
            fullName = "acme/Mobile",
            owner = "acme",
            isPrivate = true,
            description = null,
            language = null,
            stars = 0
        )
    }
}
