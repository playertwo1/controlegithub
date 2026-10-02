package com.playertwo.controlegithub

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubHttpClientTest {
    @Test
    fun successSendsHeadersAndKeepsTokenOutOfUrl() = fixture(200, "{\"login\":\"octocat\"}").use { fixture ->
        val result = fixture.client().get("/user?per_page=10", TOKEN).execute()

        assertEquals(GitHubHttpResult.Success(200, "{\"login\":\"octocat\"}"), result)
        assertEquals("Bearer $TOKEN", fixture.request.get()?.getFirst("Authorization"))
        assertEquals("application/vnd.github+json", fixture.request.get()?.getFirst("Accept"))
        assertEquals("2026-03-10", fixture.request.get()?.getFirst("X-GitHub-Api-Version"))
        assertEquals("ControleGitHub/0.1.0 (Android)", fixture.request.get()?.getFirst("User-Agent"))
        assertFalse(fixture.requestPath.get().contains(TOKEN))
    }

    @Test
    fun preservesOnlyHeadersNeededForPaginationAndRateLimit() = fixture(
        200,
        "[]",
        headers = mapOf(
            "Link" to "<https://api.github.com/user/repos?page=2>; rel=next",
            "Retry-After" to "12",
            "X-RateLimit-Remaining" to "0",
            "X-RateLimit-Reset" to "1790967600"
        )
    ).use { fixture ->
        val result = fixture.client().get("/user/repos").execute()

        assertEquals(
            GitHubResponseHeaders(
                link = "<https://api.github.com/user/repos?page=2>; rel=next",
                retryAfter = "12",
                rateLimitRemaining = "0",
                rateLimitReset = "1790967600"
            ),
            (result as GitHubHttpResult.Success).headers
        )
    }

    @Test
    fun httpFailuresHaveDistinctSafeMessages() {
        val expected = mapOf(
            401 to GitHubHttpError.UNAUTHORIZED,
            403 to GitHubHttpError.FORBIDDEN,
            404 to GitHubHttpError.NOT_FOUND,
            429 to GitHubHttpError.RATE_LIMITED,
            500 to GitHubHttpError.SERVER,
            503 to GitHubHttpError.SERVER
        )

        expected.forEach { (status, error) ->
            fixture(status, "error body containing $TOKEN").use { fixture ->
                val result = fixture.client().get("/user", TOKEN).execute()
                val failure = result as GitHubHttpResult.Failure
                assertEquals(error, failure.error)
                assertEquals(status, failure.statusCode)
                assertEquals(status == 429, failure.retryAtEpochMillis != null)
                assertFalse(error.userMessage.contains(TOKEN))
                assertFalse(error.userMessage.contains("error body"))
            }
        }
    }

    @Test
    fun readTimeoutIsReportedWithoutExceptionDetails() = fixture(200, "ok", delayMillis = 250).use { fixture ->
        val result = fixture.client(readTimeoutMillis = 50).get("/user", TOKEN).execute()
        assertEquals(GitHubHttpResult.Failure(GitHubHttpError.TIMEOUT), result)
    }

    @Test
    fun connectionFailureIsReportedWithoutExceptionDetails() {
        val fixture = fixture(200, "ok")
        val client = fixture.client()
        fixture.close()

        assertEquals(
            GitHubHttpResult.Failure(GitHubHttpError.NETWORK),
            client.get("/user", TOKEN).execute()
        )
    }

    @Test
    fun cancelDisconnectsInFlightRequest() = fixture(200, "ok", responseGate = CountDownLatch(1)).use { fixture ->
        val call = fixture.client(readTimeoutMillis = 5_000).get("/user", TOKEN)
        val result = AtomicReference<GitHubHttpResult>()
        val worker = Thread { result.set(call.execute()) }
        worker.start()

        assertTrue(fixture.requestReceived.await(2, TimeUnit.SECONDS))
        call.cancel()
        worker.join(2_000)
        fixture.responseGate?.countDown()

        assertFalse("cancel should end the request", worker.isAlive)
        assertEquals(GitHubHttpResult.Failure(GitHubHttpError.CANCELLED), result.get())
        assertEquals(1, fixture.requestCount)
    }

    @Test
    fun rejectsUntrustedOriginsBeforeSendingCredentials() {
        val invalidBase = URI.create("http://example.com/")
        assertThrows(IllegalArgumentException::class.java) { GitHubHttpClient(invalidBase) }

        val client = GitHubHttpClient()
        assertThrows(IllegalArgumentException::class.java) { client.get("//example.com/user", TOKEN) }
    }

    @Test
    fun doesNotFollowRedirectsWithAuthorizationHeader() = fixture(
        302,
        "",
        headers = mapOf("Location" to "https://example.com/collect")
    ).use { fixture ->
        assertEquals(
            GitHubHttpResult.Failure(GitHubHttpError.UNEXPECTED, 302),
            fixture.client().get("/user", TOKEN).execute()
        )
        assertEquals(1, fixture.requestCount)
    }

    private fun fixture(
        status: Int,
        body: String,
        delayMillis: Long = 0,
        responseGate: CountDownLatch? = null,
        headers: Map<String, String> = emptyMap()
    ): Fixture = Fixture(status, body, delayMillis, responseGate, headers)

    private class Fixture(
        private val status: Int,
        private val body: String,
        private val delayMillis: Long,
        val responseGate: CountDownLatch?,
        private val headers: Map<String, String>
    ) : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val request = AtomicReference<com.sun.net.httpserver.Headers>()
        val requestPath = AtomicReference("")
        val requestReceived = CountDownLatch(1)
        @Volatile var requestCount = 0
            private set

        init {
            server.createContext("/") { exchange ->
                requestCount++
                request.set(exchange.requestHeaders)
                requestPath.set(exchange.requestURI.toString())
                requestReceived.countDown()
                if (delayMillis > 0) Thread.sleep(delayMillis)
                responseGate?.await(3, TimeUnit.SECONDS)
                val bytes = body.toByteArray(Charsets.UTF_8)
                headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }

        fun client(readTimeoutMillis: Int = 1_000) = GitHubHttpClient(
            baseUrl = URI.create("http://127.0.0.1:${server.address.port}/"),
            connectTimeoutMillis = 1_000,
            readTimeoutMillis = readTimeoutMillis
        )

        override fun close() = server.stop(0)
    }

    private companion object {
        const val TOKEN = "fixture-secret-token"
    }
}
