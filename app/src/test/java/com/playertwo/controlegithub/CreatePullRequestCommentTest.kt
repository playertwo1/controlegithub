package com.playertwo.controlegithub

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatePullRequestCommentTest {

    @Test
    fun createPullRequestCommentEmptyBodyReturnsEmptyBodyWithoutNetworkCall() = fixture(201, "{}").use { fixture ->
        val client = fixture.client()
        val result = createPullRequestComment(
            client = client,
            accessToken = "test-token",
            repository = "playertwo1/controlegithub",
            pullNumber = 12,
            body = "   "
        )

        assertEquals(CreatePullRequestCommentResult.EmptyBody, result)
        assertEquals(0, fixture.requestCount)
    }

    @Test
    fun createPullRequestCommentOfflineReturnsOfflineWithoutNetworkCall() = fixture(201, "{}").use { fixture ->
        val client = fixture.client()
        val result = createPullRequestComment(
            client = client,
            accessToken = "test-token",
            repository = "playertwo1/controlegithub",
            pullNumber = 12,
            body = "Comentário offline",
            isOffline = true
        )

        assertEquals(CreatePullRequestCommentResult.Offline, result)
        assertEquals(0, fixture.requestCount)
    }

    @Test
    fun createPullRequestCommentInvalidRepoOrNumberReturnsFailureWithoutNetworkCall() = fixture(201, "{}").use { fixture ->
        val client = fixture.client()
        val result1 = createPullRequestComment(
            client = client,
            accessToken = "test-token",
            repository = "invalid",
            pullNumber = 12,
            body = "Comentário válido"
        )
        val result2 = createPullRequestComment(
            client = client,
            accessToken = "test-token",
            repository = "playertwo1/controlegithub",
            pullNumber = 0,
            body = "Comentário válido"
        )

        assertTrue(result1 is CreatePullRequestCommentResult.Failure)
        assertTrue(result2 is CreatePullRequestCommentResult.Failure)
        assertEquals(0, fixture.requestCount)
    }

    @Test
    fun createPullRequestCommentForbiddenReturnsFailureAndSendsPost() = fixture(
        status = 403,
        body = """{"message": "Resource not accessible by integration"}"""
    ).use { fixture ->
        val client = fixture.client()
        val result = createPullRequestComment(
            client = client,
            accessToken = "test-token",
            repository = "playertwo1/controlegithub",
            pullNumber = 12,
            body = "Comentário sem permissão"
        )

        assertTrue(result is CreatePullRequestCommentResult.Failure)
        val failure = result as CreatePullRequestCommentResult.Failure
        assertEquals(GitHubHttpError.FORBIDDEN, failure.error)
        assertEquals(403, failure.statusCode)
        assertEquals(1, fixture.requestCount)
        assertEquals("POST", fixture.requestMethod.get())
        assertEquals("/repos/playertwo1/controlegithub/issues/12/comments", fixture.requestPath.get())
        assertEquals("""{"body":"Comentário sem permissão"}""", fixture.requestBody.get())
    }

    @Test
    fun createPullRequestCommentNotFoundReturnsFailure() = fixture(
        status = 404,
        body = """{"message": "Not Found"}"""
    ).use { fixture ->
        val client = fixture.client()
        val result = createPullRequestComment(
            client = client,
            accessToken = "test-token",
            repository = "playertwo1/controlegithub",
            pullNumber = 999,
            body = "Comentário em PR inexistente"
        )

        assertTrue(result is CreatePullRequestCommentResult.Failure)
        val failure = result as CreatePullRequestCommentResult.Failure
        assertEquals(GitHubHttpError.NOT_FOUND, failure.error)
        assertEquals(404, failure.statusCode)
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
