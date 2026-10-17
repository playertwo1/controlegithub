package com.playertwo.controlegithub

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmitPullRequestReviewTest {

    @Test
    fun submitPullRequestReviewRequestChangesEmptyBodyReturnsEmptyChangesBodyWithoutNetworkCall() = fixture(200, "{}").use { fixture ->
        val client = fixture.client()
        val result = submitPullRequestReview(
            client = client,
            accessToken = "test-token",
            repository = "playertwo1/controlegithub",
            pullNumber = 15,
            event = PullRequestReviewEvent.REQUEST_CHANGES,
            body = "   "
        )

        assertEquals(SubmitPullRequestReviewResult.EmptyChangesBody, result)
        assertEquals(0, fixture.requestCount)
    }

    @Test
    fun submitPullRequestReviewOfflineReturnsOfflineWithoutNetworkCall() = fixture(200, "{}").use { fixture ->
        val client = fixture.client()
        val result = submitPullRequestReview(
            client = client,
            accessToken = "test-token",
            repository = "playertwo1/controlegithub",
            pullNumber = 15,
            event = PullRequestReviewEvent.APPROVE,
            body = "Aprovado offline",
            isOffline = true
        )

        assertEquals(SubmitPullRequestReviewResult.Offline, result)
        assertEquals(0, fixture.requestCount)
    }

    @Test
    fun submitPullRequestReviewInvalidRepoOrNumberReturnsFailureWithoutNetworkCall() = fixture(200, "{}").use { fixture ->
        val client = fixture.client()
        val result1 = submitPullRequestReview(
            client = client,
            accessToken = "test-token",
            repository = "invalid",
            pullNumber = 15,
            event = PullRequestReviewEvent.APPROVE,
            body = null
        )
        val result2 = submitPullRequestReview(
            client = client,
            accessToken = "test-token",
            repository = "playertwo1/controlegithub",
            pullNumber = 0,
            event = PullRequestReviewEvent.APPROVE,
            body = null
        )

        assertTrue(result1 is SubmitPullRequestReviewResult.Failure)
        assertTrue(result2 is SubmitPullRequestReviewResult.Failure)
        assertEquals(0, fixture.requestCount)
    }

    @Test
    fun submitPullRequestReviewForbiddenReturnsFailureAndSendsPost() = fixture(
        status = 403,
        body = """{"message": "Resource not accessible by integration"}"""
    ).use { fixture ->
        val client = fixture.client()
        val result = submitPullRequestReview(
            client = client,
            accessToken = "test-token",
            repository = "playertwo1/controlegithub",
            pullNumber = 15,
            event = PullRequestReviewEvent.APPROVE,
            body = "LGTM!",
            commitId = "11223344556677889900aabbccddeeff11223344"
        )

        assertTrue(result is SubmitPullRequestReviewResult.Failure)
        val failure = result as SubmitPullRequestReviewResult.Failure
        assertEquals(GitHubHttpError.FORBIDDEN, failure.error)
        assertEquals(403, failure.statusCode)
        assertEquals(1, fixture.requestCount)
        assertEquals("POST", fixture.requestMethod.get())
        assertEquals("/repos/playertwo1/controlegithub/pulls/15/reviews", fixture.requestPath.get())
        assertEquals(
            """{"event":"APPROVE","body":"LGTM!","commit_id":"11223344556677889900aabbccddeeff11223344"}""",
            fixture.requestBody.get()
        )
    }

    @Test
    fun submitPullRequestReviewSelfReviewReturnsSelfReviewForbidden() = fixture(
        status = 422,
        body = """{"message": "Unprocessable Entity: Can not approve your own pull request."}"""
    ).use { fixture ->
        val client = fixture.client()
        val result = submitPullRequestReview(
            client = client,
            accessToken = "test-token",
            repository = "playertwo1/controlegithub",
            pullNumber = 15,
            event = PullRequestReviewEvent.APPROVE,
            body = null
        )

        assertEquals(SubmitPullRequestReviewResult.SelfReviewForbidden, result)
        assertEquals(1, fixture.requestCount)
        assertEquals("POST", fixture.requestMethod.get())
        assertEquals("/repos/playertwo1/controlegithub/pulls/15/reviews", fixture.requestPath.get())
    }

    private fun fixture(status: Int, body: String): Fixture = Fixture(status, body)

    private class Fixture(
        private val status: Int,
        private val body: String
    ) : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requestPath = AtomicReference("")
        val requestMethod = AtomicReference("")
        val requestBody = AtomicReference("")
        @Volatile var requestCount = 0
            private set

        init {
            server.createContext("/") { exchange ->
                requestCount++
                requestPath.set(exchange.requestURI.toString())
                requestMethod.set(exchange.requestMethod)
                requestBody.set(exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() })
                val bytes = body.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }

        fun client() = GitHubHttpClient(
            baseUrl = URI.create("http://127.0.0.1:${server.address.port}/"),
            connectTimeoutMillis = 1_000,
            readTimeoutMillis = 1_000
        )

        override fun close() = server.stop(0)
    }
}
