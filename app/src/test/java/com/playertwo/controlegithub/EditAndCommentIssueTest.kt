package com.playertwo.controlegithub

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditAndCommentIssueTest {

    @Test
    fun createIssueCommentEmptyBodyReturnsEmptyBodyWithoutNetworkCall() = fixture(200, "{}").use { fixture ->
        val client = fixture.client()
        val result = createIssueComment(
            client = client,
            accessToken = "test-token",
            repository = "playertwo1/controlegithub",
            issueNumber = 10,
            body = "   "
        )

        assertEquals(CreateIssueCommentResult.EmptyBody, result)
        assertEquals(0, fixture.requestCount)
    }

    @Test
    fun createIssueCommentOfflineReturnsOfflineWithoutNetworkCall() = fixture(200, "{}").use { fixture ->
        val client = fixture.client()
        val result = createIssueComment(
            client = client,
            accessToken = "test-token",
            repository = "playertwo1/controlegithub",
            issueNumber = 10,
            body = "Comentário offline",
            isOffline = true
        )

        assertEquals(CreateIssueCommentResult.Offline, result)
        assertEquals(0, fixture.requestCount)
    }

    @Test
    fun createIssueCommentForbiddenReturnsFailure() = fixture(
        status = 403,
        body = """{"message": "Resource not accessible by integration"}"""
    ).use { fixture ->
        val client = fixture.client()
        val result = createIssueComment(
            client = client,
            accessToken = "test-token",
            repository = "playertwo1/controlegithub",
            issueNumber = 10,
            body = "Comentário sem permissão"
        )

        assertTrue(result is CreateIssueCommentResult.Failure)
        val failure = result as CreateIssueCommentResult.Failure
        assertEquals(GitHubHttpError.FORBIDDEN, failure.error)
        assertEquals(403, failure.statusCode)
        assertEquals("POST", fixture.requestMethod.get())
        assertEquals("/repos/playertwo1/controlegithub/issues/10/comments", fixture.requestPath.get())
    }

    @Test
    fun editIssueEmptyTitleReturnsEmptyTitleWithoutNetworkCall() = fixture(200, "{}").use { fixture ->
        val client = fixture.client()
        val issue = sampleIssue()
        val result = editGitHubIssue(
            client = client,
            accessToken = "test-token",
            currentIssue = issue,
            title = "   ",
            body = "Novo corpo"
        )

        assertEquals(EditIssueResult.EmptyTitle, result)
        assertEquals(0, fixture.requestCount)
    }

    @Test
    fun editIssueOfflineReturnsOfflineWithoutNetworkCall() = fixture(200, "{}").use { fixture ->
        val client = fixture.client()
        val issue = sampleIssue()
        val result = editGitHubIssue(
            client = client,
            accessToken = "test-token",
            currentIssue = issue,
            title = "Título novo",
            body = "Novo corpo",
            isOffline = true
        )

        assertEquals(EditIssueResult.Offline, result)
        assertEquals(0, fixture.requestCount)
    }

    @Test
    fun editIssueForbiddenReturnsFailure() = fixture(
        status = 403,
        body = """{"message": "Resource not accessible by integration"}"""
    ).use { fixture ->
        val client = fixture.client()
        val issue = sampleIssue()
        val result = editGitHubIssue(
            client = client,
            accessToken = "test-token",
            currentIssue = issue,
            title = "Título editado sem permissão",
            body = "Corpo editado"
        )

        assertTrue(result is EditIssueResult.Failure)
        val failure = result as EditIssueResult.Failure
        assertEquals(GitHubHttpError.FORBIDDEN, failure.error)
        assertEquals(403, failure.statusCode)
        assertEquals("PATCH", fixture.requestMethod.get())
        assertEquals("/repos/playertwo1/controlegithub/issues/42", fixture.requestPath.get())
    }

    private fun sampleIssue() = GitHubIssue(
        number = 42,
        title = "Issue original",
        state = GitHubIssueState.OPEN,
        repository = "playertwo1/controlegithub",
        author = "playertwo1",
        assignees = emptyList(),
        labels = emptyList(),
        updatedAt = null
    )

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
