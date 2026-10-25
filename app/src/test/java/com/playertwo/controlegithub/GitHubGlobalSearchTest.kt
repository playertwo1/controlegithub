package com.playertwo.controlegithub

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

class GitHubGlobalSearchTest {
    @Test
    fun globalRepositorySearchPathEncodesQueryAndValidatesArguments() {
        assertEquals(
            "/search/repositories?q=jetpack+compose+language%3Akotlin&sort=stars&order=desc&per_page=30&page=1",
            githubGlobalRepositorySearchPath("  jetpack compose language:kotlin  ")
        )
        assertEquals(
            "/search/repositories?q=torvalds%2Flinux&sort=stars&order=desc&per_page=50&page=2",
            githubGlobalRepositorySearchPath("torvalds/linux", page = 2, perPage = 50)
        )

        assertIllegalArgument { githubGlobalRepositorySearchPath("   ") }
        assertIllegalArgument { githubGlobalRepositorySearchPath("compose", page = 0) }
        assertIllegalArgument { githubGlobalRepositorySearchPath("compose", perPage = 0) }
        assertIllegalArgument { githubGlobalRepositorySearchPath("compose", perPage = 101) }
    }

    @Test
    fun parseGitHubRepositorySearchParsesItemsAndHandlesEmptyResults() {
        val json = """
            {
              "total_count": 2,
              "incomplete_results": false,
              "items": [
                {
                  "id": 101,
                  "name": "linux",
                  "full_name": "torvalds/linux",
                  "owner": { "login": "torvalds" },
                  "private": false,
                  "description": "Linux kernel source tree",
                  "language": "C",
                  "stargazers_count": 180000,
                  "pushed_at": "2026-04-10T12:00:00Z",
                  "archived": false,
                  "open_issues_count": 350
                },
                {
                  "id": 202,
                  "name": "compose-samples",
                  "full_name": "android/compose-samples",
                  "owner": { "login": "android" },
                  "private": false,
                  "description": null,
                  "language": "Kotlin",
                  "stargazers_count": 22000,
                  "archived": true
                }
              ]
            }
        """.trimIndent()

        val repositories = parseGitHubRepositorySearch(json)
        assertEquals(2, repositories.size)
        assertEquals(
            GitHubRepository(
                id = 101L,
                name = "linux",
                fullName = "torvalds/linux",
                owner = "torvalds",
                isPrivate = false,
                description = "Linux kernel source tree",
                language = "C",
                stars = 180000,
                pushedAt = Instant.parse("2026-04-10T12:00:00Z"),
                archived = false,
                openIssues = 350
            ),
            repositories[0]
        )
        assertEquals("android/compose-samples", repositories[1].fullName)
        assertTrue(repositories[1].archived)

        assertEquals(emptyList<GitHubRepository>(), parseGitHubRepositorySearch("""{"total_count":0,"items":[]}"""))
        assertEquals(emptyList<GitHubRepository>(), parseGitHubRepositorySearch("""{"total_count":0}"""))

        try {
            parseGitHubRepositorySearch("not-json")
            fail("Expected IOException for invalid JSON")
        } catch (_: IOException) {
        }
    }

    @Test
    fun globalSearchPagerPaginatesAndHandlesRateLimitAndUnauthorized() {
        val page1Path = "/search/repositories?q=compose&sort=stars&order=desc&per_page=2&page=1"
        val page2Path = "/search/repositories?q=compose&sort=stars&order=desc&per_page=2&page=2"
        val page1Body = """
            {
              "total_count": 2,
              "items": [
                {
                  "id": 10,
                  "name": "compose-one",
                  "full_name": "google/compose-one",
                  "owner": { "login": "google" },
                  "private": false,
                  "description": "First public repo",
                  "language": "Kotlin",
                  "stargazers_count": 500
                }
              ]
            }
        """.trimIndent()
        val page2Body = """
            {
              "total_count": 2,
              "items": [
                {
                  "id": 20,
                  "name": "compose-two",
                  "full_name": "jetbrains/compose-two",
                  "owner": { "login": "jetbrains" },
                  "private": false,
                  "description": "Second public repo",
                  "language": "Kotlin",
                  "stargazers_count": 300
                }
              ]
            }
        """.trimIndent()

        Fixture(
            mapOf(
                page1Path to listOf(
                    Reply(200, page1Body, link = "<http://127.0.0.1:PORT$page2Path>; rel=\"next\"")
                ),
                page2Path to listOf(
                    Reply(429, "", retryAfter = "15"),
                    Reply(200, page2Body)
                ),
                "/search/repositories?q=expired&sort=stars&order=desc&per_page=30&page=1" to listOf(
                    Reply(401, "")
                )
            )
        ).use { fixture ->
            fixture.replacePortInLinks()
            var now = 1_790_000_000_000L
            val client = fixture.client { now }
            val pager = GitHubGlobalRepositorySearchPager(
                client = client,
                accessToken = TOKEN,
                query = "compose",
                perPage = 2
            )

            val first = pager.loadNext() as GitHubPageResult.Loaded
            assertTrue(first.hasNext)
            assertEquals(listOf("google/compose-one"), first.items.map(GitHubRepository::fullName))

            val rateLimited = pager.loadNext() as GitHubPageResult.RateLimited
            assertEquals(now + 15_000, rateLimited.retryAtEpochMillis)
            assertEquals(listOf("google/compose-one"), rateLimited.items.map(GitHubRepository::fullName))

            now = rateLimited.retryAtEpochMillis
            val second = pager.loadNext() as GitHubPageResult.Loaded
            assertFalse(second.hasNext)
            assertEquals(
                listOf("google/compose-one", "jetbrains/compose-two"),
                second.items.map(GitHubRepository::fullName)
            )
            assertEquals(listOf(page1Path, page2Path, page2Path), fixture.requests)
            assertTrue(fixture.authorization.all { it == "Bearer $TOKEN" })

            val unauthorizedPager = GitHubGlobalRepositorySearchPager(
                client = client,
                accessToken = TOKEN,
                query = "expired"
            )
            val unauthorized = unauthorizedPager.loadNext() as GitHubPageResult.Failed
            assertEquals(GitHubHttpError.UNAUTHORIZED, unauthorized.error)
        }
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    private data class Reply(
        val status: Int,
        val body: String,
        val link: String? = null,
        val retryAfter: String? = null
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
        const val TOKEN = "global-search-fixture-token"
    }
}
