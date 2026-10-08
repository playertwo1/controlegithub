package com.playertwo.controlegithub

import java.io.BufferedReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubNotificationPagerTest {
    @Test
    fun requestsAllNotificationsAndDeduplicatesAcrossPages() = NotificationApi(
        listOf(
            Reply(200, fixtureThreads(fixtureNotification()), nextPath = "/notifications?page=2"),
            Reply(200, fixtureThreads(fixtureNotification()))
        )
    ).use { api ->
        val pager = GitHubNotificationPager(GitHubHttpClient(api.baseUri), TOKEN)

        val first = pager.loadNext() as GitHubPageResult.Loaded
        val second = pager.loadNext() as GitHubPageResult.Loaded

        assertTrue(first.hasNext)
        assertEquals(1, second.items.size)
        assertEquals(listOf("/notifications?all=true&per_page=50", "/notifications?page=2"), api.requests)
        assertTrue(api.authorization.all { it == "Bearer $TOKEN" })
    }

    @Test
    fun preservesUnauthorizedForbiddenRateLimitAndMalformedResponseStates() {
        NotificationApi(listOf(Reply(401, ""))).use { api ->
            val result = GitHubNotificationPager(GitHubHttpClient(api.baseUri), TOKEN).loadNext()
            assertEquals(GitHubHttpError.UNAUTHORIZED, (result as GitHubPageResult.Failed).error)
        }
        NotificationApi(listOf(Reply(403, ""))).use { api ->
            val result = GitHubNotificationPager(GitHubHttpClient(api.baseUri), TOKEN).loadNext()
            assertEquals(GitHubHttpError.FORBIDDEN, (result as GitHubPageResult.Failed).error)
        }
        NotificationApi(listOf(Reply(429, "", retryAfter = "1"))).use { api ->
            val result = GitHubNotificationPager(GitHubHttpClient(api.baseUri), TOKEN).loadNext()
            assertTrue(result is GitHubPageResult.RateLimited)
        }
        NotificationApi(listOf(Reply(200, "not-json"))).use { api ->
            val result = GitHubNotificationPager(GitHubHttpClient(api.baseUri), TOKEN).loadNext()
            assertTrue(result is GitHubPageResult.Failed)
            assertTrue((result as GitHubPageResult.Failed).items.isEmpty())
        }
    }

    private data class Reply(
        val status: Int,
        val body: String,
        val nextPath: String? = null,
        val retryAfter: String? = null
    )

    private class NotificationApi(private val replies: List<Reply>) : AutoCloseable {
        private val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
        val baseUri = URI.create("http://127.0.0.1:${server.localPort}/")
        val requests = CopyOnWriteArrayList<String>()
        val authorization = CopyOnWriteArrayList<String?>()
        private val worker = Thread {
            replies.forEach { reply ->
                server.accept().use { socket ->
                    val input = BufferedReader(socket.getInputStream().reader(Charsets.US_ASCII))
                    val requestLine = input.readLine()
                    requests += requestLine.orEmpty().split(' ').getOrNull(1).orEmpty()
                    val headers = buildList {
                        while (true) {
                            val line = input.readLine() ?: break
                            if (line.isEmpty()) break
                            add(line)
                        }
                    }
                    authorization += headers.firstOrNull { it.startsWith("Authorization:", ignoreCase = true) }
                        ?.substringAfter(':')?.trim()

                    val body = reply.body.toByteArray(Charsets.UTF_8)
                    val responseHeaders = buildString {
                        append("HTTP/1.1 ${reply.status} ${statusText(reply.status)}\r\n")
                        append("Content-Type: application/json\r\n")
                        append("Content-Length: ${body.size}\r\n")
                        append("Connection: close\r\n")
                        reply.retryAfter?.let { append("Retry-After: $it\r\n") }
                        reply.nextPath?.let { append("Link: <${baseUri.resolve(it)}>; rel=\"next\"\r\n") }
                        append("\r\n")
                    }.toByteArray(Charsets.US_ASCII)
                    socket.getOutputStream().apply {
                        write(responseHeaders)
                        write(body)
                        flush()
                    }
                }
            }
        }.apply { isDaemon = true; start() }

        private fun statusText(status: Int) = when (status) {
            200 -> "OK"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            429 -> "Too Many Requests"
            else -> "Error"
        }

        override fun close() {
            server.close()
            worker.join(1_000)
        }
    }

    private companion object {
        const val TOKEN = "notification-fixture-token"
    }
}
