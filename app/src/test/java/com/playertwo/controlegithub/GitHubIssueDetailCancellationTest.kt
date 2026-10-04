package com.playertwo.controlegithub

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubIssueDetailCancellationTest {
    @Test
    fun cancellingLoaderDisconnectsAnInFlightDetailRequest() = HeldResponse().use { server ->
        val loader = GitHubIssueDetailLoader(
            GitHubHttpClient(
                URI.create("http://127.0.0.1:${server.port}/"),
                connectTimeoutMillis = 1_000,
                readTimeoutMillis = 10_000
            ),
            "fixture-token"
        )
        val result = AtomicReference<GitHubIssueDetailResult>()
        val worker = Thread { result.set(loader.load(issue)) }
        worker.start()

        assertTrue(server.requestReceived.await(2, TimeUnit.SECONDS))
        loader.cancel()
        worker.join(2_000)
        server.releaseResponse.countDown()

        assertFalse("cancelling the detail loader should stop its HTTP worker", worker.isAlive)
        assertTrue(result.get() is GitHubIssueDetailResult.Failed)
        assertTrue((result.get() as GitHubIssueDetailResult.Failed).error == GitHubHttpError.CANCELLED)
    }

    private class HeldResponse : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requestReceived = CountDownLatch(1)
        val releaseResponse = CountDownLatch(1)
        val port: Int get() = server.address.port

        init {
            server.createContext("/") { exchange ->
                requestReceived.countDown()
                releaseResponse.await(5, TimeUnit.SECONDS)
                val body = """{"number":18,"title":"Issue","state":"open","html_url":"https://github.com/owner/repo/issues/18"}"""
                val bytes = body.toByteArray(Charsets.UTF_8)
                runCatching {
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
            }
            server.start()
        }

        override fun close() {
            releaseResponse.countDown()
            server.stop(0)
        }
    }

    private companion object {
        val issue = GitHubIssue(
            number = 18,
            title = "Issue",
            state = GitHubIssueState.OPEN,
            repository = "owner/repo",
            author = null,
            assignees = emptyList(),
            labels = emptyList(),
            updatedAt = null
        )
    }
}
