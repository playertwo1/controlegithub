package com.playertwo.controlegithub

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueMetadataAndStateTest {

    private fun fixtureIssue(state: GitHubIssueState = GitHubIssueState.OPEN) = GitHubIssue(
        number = 42,
        title = "Issue de teste",
        state = state,
        repository = "acme/app",
        author = "author",
        assignees = listOf("dev1"),
        labels = listOf("bug"),
        updatedAt = null
    )

    @Test
    fun updateIssueStateOpenToClosedSendsPatchStateClosed() = fixture(200, "{}").use { f ->
        val client = f.client()
        val result = updateIssueState(
            client = client,
            accessToken = "test-token",
            currentIssue = fixtureIssue(GitHubIssueState.OPEN),
            newState = GitHubIssueState.CLOSED,
            isOffline = false
        )

        assertEquals(1, f.requestCount)
        assertEquals("PATCH", f.lastMethod.get())
        assertEquals("/repos/acme/app/issues/42", f.lastPath.get())
        assertEquals("Bearer test-token", f.lastAuth.get())
        assertEquals("{\"state\":\"closed\"}", f.lastBody.get())
    }

    @Test
    fun updateIssueStateClosedToOpenSendsPatchStateOpen() = fixture(200, "{}").use { f ->
        val client = f.client()
        val result = updateIssueState(
            client = client,
            accessToken = "test-token",
            currentIssue = fixtureIssue(GitHubIssueState.CLOSED),
            newState = GitHubIssueState.OPEN,
            isOffline = false
        )

        assertEquals(1, f.requestCount)
        assertEquals("PATCH", f.lastMethod.get())
        assertEquals("/repos/acme/app/issues/42", f.lastPath.get())
        assertEquals("{\"state\":\"open\"}", f.lastBody.get())
    }

    @Test
    fun updateIssueStateOfflineReturnsOfflineWithoutNetwork() = fixture(200, "{}").use { f ->
        val client = f.client()
        val result = updateIssueState(
            client = client,
            accessToken = "test-token",
            currentIssue = fixtureIssue(GitHubIssueState.OPEN),
            newState = GitHubIssueState.CLOSED,
            isOffline = true
        )

        assertEquals(MutateIssueStateResult.Offline, result)
        assertEquals(0, f.requestCount)
    }

    @Test
    fun updateIssueStateForbiddenReturnsFailure() = fixture(
        status = 403,
        body = """{"message": "Resource not accessible by integration"}"""
    ).use { f ->
        val client = f.client()
        val result = updateIssueState(
            client = client,
            accessToken = "test-token",
            currentIssue = fixtureIssue(GitHubIssueState.OPEN),
            newState = GitHubIssueState.CLOSED
        )

        assertTrue(result is MutateIssueStateResult.Failure)
        val failure = result as MutateIssueStateResult.Failure
        assertEquals(GitHubHttpError.FORBIDDEN, failure.error)
        assertEquals(403, failure.statusCode)
    }

    @Test
    fun editGitHubIssueWithLabelsAndAssigneesSerializesJsonArrays() = fixture(200, "{}").use { f ->
        val client = f.client()
        val result = editGitHubIssue(
            client = client,
            accessToken = "test-token",
            currentIssue = fixtureIssue(),
            title = "Titulo Novo",
            body = "Corpo Novo",
            labels = listOf("bug", "urgente"),
            assignees = listOf("dev1", "dev2"),
            isOffline = false
        )

        assertEquals(1, f.requestCount)
        assertEquals("PATCH", f.lastMethod.get())
        assertEquals("/repos/acme/app/issues/42", f.lastPath.get())
        val body = f.lastBody.get()
        assertTrue(body.contains("\"title\":\"Titulo Novo\""))
        assertTrue(body.contains("\"body\":\"Corpo Novo\""))
        assertTrue(body.contains("\"labels\":[\"bug\",\"urgente\"]"))
        assertTrue(body.contains("\"assignees\":[\"dev1\",\"dev2\"]"))
    }

    @Test
    fun editGitHubIssueEmptyTitleReturnsEmptyTitleWithoutNetwork() = fixture(200, "{}").use { f ->
        val client = f.client()
        val result = editGitHubIssue(
            client = client,
            accessToken = "test-token",
            currentIssue = fixtureIssue(),
            title = "   ",
            body = "Corpo",
            isOffline = false
        )

        assertEquals(EditIssueResult.EmptyTitle, result)
        assertEquals(0, f.requestCount)
    }

    private fun fixture(status: Int, body: String): ServerFixture {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val lastMethod = AtomicReference<String>()
        val lastPath = AtomicReference<String>()
        val lastAuth = AtomicReference<String?>()
        val lastBody = AtomicReference<String>()
        var count = 0

        server.createContext("/") { exchange ->
            count++
            lastMethod.set(exchange.requestMethod)
            lastPath.set(exchange.requestURI.rawPath)
            lastAuth.set(exchange.requestHeaders.getFirst("Authorization"))
            lastBody.set(exchange.requestBody.bufferedReader().readText())

            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return ServerFixture(server, URI.create("http://127.0.0.1:${server.address.port}/"), lastMethod, lastPath, lastAuth, lastBody) { count }
    }

    private class ServerFixture(
        private val server: HttpServer,
        val baseUri: URI,
        val lastMethod: AtomicReference<String>,
        val lastPath: AtomicReference<String>,
        val lastAuth: AtomicReference<String?>,
        val lastBody: AtomicReference<String>,
        private val countProvider: () -> Int
    ) : AutoCloseable {
        val requestCount: Int get() = countProvider()

        fun client(): GitHubHttpClient = GitHubHttpClient(baseUrl = baseUri)

        override fun close() {
            server.stop(0)
        }
    }
}
