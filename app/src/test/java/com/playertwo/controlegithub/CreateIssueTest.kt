package com.playertwo.controlegithub

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateIssueTest {

    @Test
    fun createIssueEmptyTitleReturnsEmptyTitleWithoutNetworkCall() = fixture(200, "{}").use { fixture ->
        val client = fixture.client()
        val result = createGitHubIssue(
            client = client,
            accessToken = "test-token",
            repository = "playertwo1/controlegithub",
            title = "   ",
            body = "Alguma descrição"
        )

        assertEquals(CreateIssueResult.EmptyTitle, result)
        assertEquals(0, fixture.requestCount)
    }

    @Test
    fun createIssueOfflineReturnsOfflineWithoutNetworkCall() = fixture(200, "{}").use { fixture ->
        val client = fixture.client()
        val result = createGitHubIssue(
            client = client,
            accessToken = "test-token",
            repository = "playertwo1/controlegithub",
            title = "Issue offline",
            body = "Descrição",
            isOffline = true
        )

        assertEquals(CreateIssueResult.Offline, result)
        assertEquals(0, fixture.requestCount)
    }

    @Test
    fun createIssueForbiddenReturnsFailure() = fixture(
        status = 403,
        body = """{"message": "Resource not accessible by integration"}"""
    ).use { fixture ->
        val client = fixture.client()
        val result = createGitHubIssue(
            client = client,
            accessToken = "test-token",
            repository = "playertwo1/controlegithub",
            title = "Issue sem permissão",
            body = null
        )

        assertTrue(result is CreateIssueResult.Failure)
        val failure = result as CreateIssueResult.Failure
        assertEquals(GitHubHttpError.FORBIDDEN, failure.error)
        assertEquals(403, failure.statusCode)
        assertEquals("POST", fixture.requestMethod.get())
        assertEquals("/repos/playertwo1/controlegithub/issues", fixture.requestPath.get())
    }

    @Test
    fun createIssueNotFoundReturnsFailure() = fixture(
        status = 404,
        body = """{"message": "Not Found"}"""
    ).use { fixture ->
        val client = fixture.client()
        val result = createGitHubIssue(
            client = client,
            accessToken = "test-token",
            repository = "playertwo1/inexistente",
            title = "Issue em repo inexistente",
            body = null
        )

        assertTrue(result is CreateIssueResult.Failure)
        val failure = result as CreateIssueResult.Failure
        assertEquals(GitHubHttpError.NOT_FOUND, failure.error)
        assertEquals(404, failure.statusCode)
    }

    @Test
    fun escapeJsonEscapesQuotesBackslashesAndNewlines() {
        assertEquals("""Hello \"World\"""", escapeJson("""Hello "World""""))
        assertEquals("""Path\\to\\file""", escapeJson("""Path\to\file"""))
        assertEquals("""Line 1\nLine 2\r\nLine 3\tTab""", escapeJson("Line 1\nLine 2\r\nLine 3\tTab"))
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
