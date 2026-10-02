package com.playertwo.controlegithub

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubPaginationTest {
    @Test
    fun followsLinkAndDeduplicatesItemsInFirstSeenOrder() = fixture(
        "/repos?page=1" to listOf(
            Reply(200, "1:first,2:first", link = "<http://127.0.0.1:PORT/repos?page=2>; rel=\"next\"")
        ),
        "/repos?page=2" to listOf(Reply(200, "2:later,3:last"))
    ).use { fixture ->
        fixture.replacePortInLinks()
        val pager = fixture.pager("/repos?page=1")

        val first = pager.loadNext() as GitHubPageResult.Loaded
        val second = pager.loadNext() as GitHubPageResult.Loaded

        assertTrue(first.hasNext)
        assertFalse(second.hasNext)
        assertEquals(listOf(Item("1", "first"), Item("2", "first"), Item("3", "last")), second.items)
        assertEquals(listOf("/repos?page=1", "/repos?page=2"), fixture.requests)
        assertTrue(fixture.authorization.all { it == "Bearer $TOKEN" })
    }

    @Test
    fun repeatedCursorStopsWithoutAnotherRequest() = fixture(
        "/items?page=1" to listOf(
            Reply(200, "1:one", link = "<http://127.0.0.1:PORT/items?page=2>; rel=\"next\"")
        ),
        "/items?page=2" to listOf(
            Reply(200, "2:two", link = "<http://127.0.0.1:PORT/items?page=1>; rel=\"next\"")
        )
    ).use { fixture ->
        fixture.replacePortInLinks()
        val pager = fixture.pager("/items?page=1")

        assertTrue((pager.loadNext() as GitHubPageResult.Loaded).hasNext)
        val cycle = pager.loadNext() as GitHubPageResult.Failed
        val finished = pager.loadNext() as GitHubPageResult.Loaded

        assertTrue(cycle.message.contains("repetiu"))
        assertEquals(listOf(Item("1", "one"), Item("2", "two")), cycle.items)
        assertFalse(finished.hasNext)
        assertEquals(2, fixture.requests.size)
    }

    @Test
    fun rateLimitBlocksRequestsUntilExplicitRetryDeadline() = fixture(
        "/repos" to listOf(
            Reply(429, "", retryAfter = "10"),
            Reply(200, "1:one")
        )
    ).use { fixture ->
        var now = 1_790_000_000_000L
        val pager = fixture.pager("/repos") { now }

        val limited = pager.loadNext() as GitHubPageResult.RateLimited
        val repeated = pager.loadNext() as GitHubPageResult.RateLimited
        assertEquals(now + 10_000, limited.retryAtEpochMillis)
        assertEquals(limited, repeated)
        assertEquals(1, fixture.requests.size)

        now = limited.retryAtEpochMillis
        val retry = pager.loadNext() as GitHubPageResult.Loaded
        assertEquals(listOf(Item("1", "one")), retry.items)
        assertEquals(2, fixture.requests.size)
    }

    @Test
    fun rateLimitBlocksOtherPagersSharingTheClient() = fixture(
        "/repos" to listOf(Reply(429, "", retryAfter = "10"), Reply(200, "1:repo")),
        "/user" to listOf(Reply(200, "1:user"))
    ).use { fixture ->
        var now = 1_790_000_000_000L
        val client = fixture.client { now }
        val repositories = fixture.pager("/repos", client)
        val user = fixture.pager("/user", client)

        val limited = repositories.loadNext() as GitHubPageResult.RateLimited
        val blocked = user.loadNext() as GitHubPageResult.RateLimited
        assertEquals(limited.retryAtEpochMillis, blocked.retryAtEpochMillis)
        assertEquals(listOf("/repos"), fixture.requests)

        now = limited.retryAtEpochMillis
        assertEquals(listOf(Item("1", "user")), (user.loadNext() as GitHubPageResult.Loaded).items)
        assertEquals(listOf("/repos", "/user"), fixture.requests)
    }

    @Test
    fun forbiddenWithRateLimitResetAndMissingHeadersUseSafeDeadlines() = fixture(
        "/reset" to listOf(Reply(403, "", remaining = "0", reset = "1790000045")),
        "/fallback" to listOf(Reply(429, ""))
    ).use { fixture ->
        val now = 1_790_000_000_000L
        val reset = fixture.pager("/reset") { now }.loadNext() as GitHubPageResult.RateLimited
        val fallback = fixture.pager("/fallback") { now }.loadNext() as GitHubPageResult.RateLimited

        assertEquals(now + 45_000, reset.retryAtEpochMillis)
        assertEquals(now + 60_000, fallback.retryAtEpochMillis)
    }

    @Test
    fun unsafeNextOriginIsRejectedAndPartialItemsAreKept() = fixture(
        "/items" to listOf(
            Reply(200, "1:kept", link = "<https://example.com/steal?page=2>; rel=\"next\"")
        )
    ).use { fixture ->
        val pager = fixture.pager("/items")
        val result = pager.loadNext() as GitHubPageResult.Failed

        assertTrue(result.message.contains("inválido"))
        assertEquals(listOf(Item("1", "kept")), result.items)
        assertEquals(1, fixture.requests.size)
        assertTrue(fixture.authorization.all { it == "Bearer $TOKEN" })
    }

    @Test
    fun malformedLinkAndHttpFailureKeepLoadedItems() = fixture(
        "/items?page=1" to listOf(Reply(200, "1:kept", link = "not-a-link")),
        "/issues?page=1" to listOf(
            Reply(200, "1:kept", link = "<http://127.0.0.1:PORT/issues?page=2>; rel=\"next\"")
        ),
        "/issues?page=2" to listOf(Reply(500, "private error body"))
    ).use { fixture ->
        fixture.replacePortInLinks()

        val malformed = fixture.pager("/items?page=1").loadNext() as GitHubPageResult.Failed
        val pager = fixture.pager("/issues?page=1")
        pager.loadNext()
        val failed = pager.loadNext() as GitHubPageResult.Failed

        assertTrue(malformed.message.contains("cabeçalho"))
        assertEquals(listOf(Item("1", "kept")), failed.items)
        assertFalse(failed.message.contains("private error body"))
    }

    private fun fixture(vararg routes: Pair<String, List<Reply>>) = Fixture(routes.toMap())

    private data class Item(val id: String, val value: String)

    private data class Reply(
        val status: Int,
        val body: String,
        val link: String? = null,
        val retryAfter: String? = null,
        val remaining: String? = null,
        val reset: String? = null
    )

    private class Fixture(routes: Map<String, List<Reply>>) : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private val replies = ConcurrentHashMap<String, ConcurrentLinkedQueue<Reply>>().apply {
            routes.forEach { (path, responses) -> put(path, ConcurrentLinkedQueue(responses)) }
        }
        val requests = CopyOnWriteArrayList<String>()
        val authorization = CopyOnWriteArrayList<String?>()

        init {
            server.createContext("/") { exchange ->
                val path = exchange.requestURI.toString()
                requests += path
                authorization += exchange.requestHeaders.getFirst("Authorization")
                val reply = replies[path]?.poll() ?: Reply(404, "missing fixture")
                reply.link?.let { exchange.responseHeaders.add("Link", it) }
                reply.retryAfter?.let { exchange.responseHeaders.add("Retry-After", it) }
                reply.remaining?.let { exchange.responseHeaders.add("X-RateLimit-Remaining", it) }
                reply.reset?.let { exchange.responseHeaders.add("X-RateLimit-Reset", it) }
                val bytes = reply.body.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(reply.status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }

        fun client(nowMillis: () -> Long = System::currentTimeMillis) = GitHubHttpClient(
                baseUrl = URI.create("http://127.0.0.1:${server.address.port}/"),
                connectTimeoutMillis = 1_000,
                readTimeoutMillis = 1_000,
                nowMillis = nowMillis
            )

        fun pager(path: String, nowMillis: () -> Long = System::currentTimeMillis) = pager(path, client(nowMillis))

        fun pager(path: String, client: GitHubHttpClient) = GitHubPaginator(
            client = client,
            firstPath = path,
            accessToken = TOKEN,
            decode = { body ->
                if (body.isEmpty()) emptyList() else body.split(',').map { value ->
                    Item(value.substringBefore(':'), value.substringAfter(':'))
                }
            },
            itemKey = Item::id
        )

        fun replacePortInLinks() {
            val port = server.address.port
            replies.values.forEach { queue ->
                val updated = queue.map { reply ->
                    reply.copy(link = reply.link?.replace("PORT", port.toString()))
                }
                queue.clear()
                queue.addAll(updated)
            }
        }

        override fun close() = server.stop(0)
    }

    private companion object {
        const val TOKEN = "pagination-fixture-token"
    }
}
