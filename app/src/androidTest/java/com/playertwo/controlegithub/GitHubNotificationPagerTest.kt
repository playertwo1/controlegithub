package com.playertwo.controlegithub

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubNotificationPagerTest {
    @Test fun pagerRequestsAllThreadsAndDeduplicatesAcrossPages() {
        NotificationApi(
            pages = listOf(
                fixtureThreads(fixtureNotificationJson(id = "1")),
                fixtureThreads(fixtureNotificationJson(id = "1"))
            ),
            nextLink = "<http://127.0.0.1:PORT/page-2>; rel=\"next\""
        ).use { api ->
            val pager = GitHubNotificationPager(GitHubHttpClient(api.baseUri), "fixture-token")

            val first = pager.loadNext() as GitHubPageResult.Loaded
            val second = pager.loadNext() as GitHubPageResult.Loaded

            assertTrue(first.hasNext)
            assertEquals(1, first.items.size)
            assertEquals(1, second.items.size)
            assertEquals(2, api.requests.size)
            assertTrue(api.requests.first().startsWith("GET /notifications?all=true&per_page=50"))
            assertEquals(listOf("Bearer fixture-token", "Bearer fixture-token"), api.authorizations)
        }
    }

    @Test fun distinguishesUnauthorizedForbiddenAndRateLimitResponses() {
        assertEquals(GitHubHttpError.UNAUTHORIZED, (loadPage(401) as GitHubPageResult.Failed).error)
        assertEquals(GitHubHttpError.FORBIDDEN, (loadPage(403) as GitHubPageResult.Failed).error)
        assertTrue(loadPage(429) is GitHubPageResult.RateLimited)
        assertEquals(GitHubHttpError.SERVER, (loadPage(503) as GitHubPageResult.Failed).error)
    }

    @Test fun malformedJsonFailsInsteadOfReturningAnEmptyPage() {
        NotificationApi(pages = listOf("not-json")).use { api ->
            val result = GitHubNotificationPager(GitHubHttpClient(api.baseUri), "fixture-token").loadNext()

            assertTrue(result is GitHubPageResult.Failed)
            assertTrue(result.items.isEmpty())
            assertEquals("Não foi possível interpretar a resposta do GitHub.", (result as GitHubPageResult.Failed).message)
        }
    }

    private fun loadPage(status: Int): GitHubPageResult<GitHubNotification> =
        NotificationApi(initialStatus = status, pages = listOf("{}")).use { api ->
            GitHubNotificationPager(GitHubHttpClient(api.baseUri), "fixture-token").loadNext()
        }
}

private class NotificationApi(
    private val initialStatus: Int = 200,
    private val pages: List<String>,
    private val nextLink: String? = null
) : AutoCloseable {
    private val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    val baseUri = URI.create("http://127.0.0.1:${server.localPort}/")
    val requests = CopyOnWriteArrayList<String>()
    val authorizations = CopyOnWriteArrayList<String?>()
    private val page = AtomicInteger()
    private val worker = Thread {
        while (!server.isClosed) runCatching {
            server.accept().use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                val requestLine = reader.readLine().orEmpty()
                requests += requestLine
                var authorization: String? = null
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Authorization:", ignoreCase = true)) authorization = line.substringAfter(':').trim()
                }
                authorizations += authorization
                val body = pages.getOrElse(page.getAndIncrement()) { pages.lastOrNull().orEmpty() }
                val link = nextLink?.replace("PORT", server.localPort.toString())
                    ?.takeIf { requestLine.contains("/notifications?") }
                val status = initialStatus
                val output = socket.getOutputStream()
                output.write("HTTP/1.1 $status ${if (status == 200) "OK" else "Error"}\r\n".toByteArray(StandardCharsets.US_ASCII))
                output.write("Content-Type: application/json\r\n".toByteArray(StandardCharsets.US_ASCII))
                output.write("Content-Length: ${body.toByteArray(StandardCharsets.UTF_8).size}\r\n".toByteArray(StandardCharsets.US_ASCII))
                link?.let { output.write("Link: $it\r\n".toByteArray(StandardCharsets.US_ASCII)) }
                output.write("Connection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                output.write(body.toByteArray(StandardCharsets.UTF_8))
                output.flush()
            }
        }
    }.apply { start() }

    override fun close() {
        server.close()
        worker.join(1_000)
    }
}
