package com.playertwo.controlegithub

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryHealthTest {
    private val now = Instant.parse("2026-10-20T12:00:00Z")

    private fun daysAgo(days: Long) = now.minus(Duration.ofDays(days))

    @Test
    fun classifiesByDaysWithoutPush() {
        assertEquals(RepositoryHealthStatus.ACTIVE, repositoryHealth(daysAgo(0), false, now).status)
        assertEquals(RepositoryHealthStatus.ACTIVE, repositoryHealth(daysAgo(30), false, now).status)
        assertEquals(RepositoryHealthStatus.COOLING, repositoryHealth(daysAgo(31), false, now).status)
        assertEquals(RepositoryHealthStatus.STALE, repositoryHealth(daysAgo(200), false, now).status)
        assertEquals(RepositoryHealthStatus.ABANDONED, repositoryHealth(daysAgo(400), false, now).status)
    }

    @Test
    fun archivedAndUnknownAreExplicit() {
        assertEquals(RepositoryHealthStatus.ARCHIVED, repositoryHealth(daysAgo(1), true, now).status)
        val unknown = repositoryHealth(null, false, now)
        assertEquals(RepositoryHealthStatus.UNKNOWN, unknown.status)
        assertNull(unknown.daysWithoutPush)
        assertEquals("Sem push registrado", unknown.summary)
    }

    @Test
    fun summaryShowsDays() {
        assertEquals("Ativo · push hoje", repositoryHealth(now, false, now).summary)
        assertEquals("Ativo · 1 dia sem push", repositoryHealth(daysAgo(1), false, now).summary)
        assertEquals("Parado · 120 dias sem push", repositoryHealth(daysAgo(120), false, now).summary)
    }

    @Test
    fun futurePushIsClampedToZero() {
        assertEquals(0L, repositoryHealth(now.plusSeconds(3600), false, now).daysWithoutPush)
    }

    @Test
    fun repositoryListParsesHealthFieldsWithoutMarkingEmptyBeforeConfirmation() {
        val repo = parseGitHubRepositories(
            """[{"id":1,"name":"app","full_name":"me/app","owner":{"login":"me"},"private":false,
            "stargazers_count":2,"pushed_at":"2026-10-01T00:00:00Z","archived":false,"open_issues_count":4,"size":0}]"""
        ).single()
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), repo.pushedAt)
        assertEquals(false, repo.archived)
        assertEquals(4, repo.openIssues)
        assertEquals(0L, repo.sizeKb)
        assertTrue(repo.isEmptyCandidate)
        assertFalse(repo.confirmedEmpty)
        assertFalse(repo.health.status == RepositoryHealthStatus.EMPTY)
    }

    @Test
    fun emptyStatusRequiresConfirmationAndArchivedAlwaysWins() {
        val confirmed = repositoryHealth(now, archived = false, now = now, confirmedEmpty = true)
        assertEquals(RepositoryHealthStatus.EMPTY, confirmed.status)
        assertEquals("Vazio · sem arquivos", confirmed.summary)
        assertEquals("Vazio", confirmed.compactSummary)

        val unconfirmedRecent = repositoryHealth(daysAgo(5), archived = false, now = now, confirmedEmpty = false)
        assertEquals(RepositoryHealthStatus.ACTIVE, unconfirmedRecent.status)

        val unconfirmedNoPush = repositoryHealth(null, archived = false, now = now, confirmedEmpty = false)
        assertEquals(RepositoryHealthStatus.UNKNOWN, unconfirmedNoPush.status)

        val archivedEmpty = repositoryHealth(null, archived = true, now = now, confirmedEmpty = true)
        assertEquals(RepositoryHealthStatus.ARCHIVED, archivedEmpty.status)

        assertTrue(isEmptyRepositoryCandidate(archived = false, sizeKb = 0L))
        assertFalse(isEmptyRepositoryCandidate(archived = true, sizeKb = 0L))
        assertFalse(isEmptyRepositoryCandidate(archived = false, sizeKb = 1L))
        assertFalse(isEmptyRepositoryCandidate(archived = false, sizeKb = null))
    }

    @Test
    fun severityOrderPutsEmptyFirstAndArchivedAndUnknownLast() {
        val ordered = RepositoryHealthStatus.entries.sortedBy { it.severityRank }
        assertEquals(
            listOf(
                RepositoryHealthStatus.EMPTY,
                RepositoryHealthStatus.ABANDONED,
                RepositoryHealthStatus.STALE,
                RepositoryHealthStatus.COOLING,
                RepositoryHealthStatus.ACTIVE,
                RepositoryHealthStatus.ARCHIVED,
                RepositoryHealthStatus.UNKNOWN
            ),
            ordered
        )
        assertEquals(ordered, RepositoryHealthStatus.entries)
    }

    @Test
    fun confirmRepositoryEmptyTreatsOnly409AsEmpty() {
        StubCommitsApi(
            mapOf(
                "/repos/me/empty-repo/commits?per_page=1" to (409 to """{"message":"Git Repository is empty."}"""),
                "/repos/me/non-empty-zero-kb/commits?per_page=1" to (200 to """[{"sha":"abc123"}]"""),
                "/repos/me/forbidden/commits?per_page=1" to (403 to """{"message":"Forbidden"}"""),
                "/repos/me/missing/commits?per_page=1" to (404 to """{"message":"Not Found"}"""),
                "/repos/me/rate-limited/commits?per_page=1" to (429 to """{"message":"Rate limit"}""")
            )
        ).use { api ->
            val client = GitHubHttpClient(baseUrl = api.baseUrl)

            val emptyResult = confirmRepositoryEmpty(client, "token", "me", "empty-repo")
            assertEquals(RepositoryEmptyStatus.EMPTY, emptyResult.status)
            assertFalse(emptyResult.stopBatch)

            val notEmptyResult = confirmRepositoryEmpty(client, "token", "me", "non-empty-zero-kb")
            assertEquals(RepositoryEmptyStatus.NOT_EMPTY, notEmptyResult.status)
            assertFalse(notEmptyResult.stopBatch)

            val forbiddenResult = confirmRepositoryEmpty(client, "token", "me", "forbidden")
            assertEquals(RepositoryEmptyStatus.UNCONFIRMED, forbiddenResult.status)
            assertFalse(forbiddenResult.stopBatch)

            val notFoundResult = confirmRepositoryEmpty(client, "token", "me", "missing")
            assertEquals(RepositoryEmptyStatus.UNCONFIRMED, notFoundResult.status)
            assertFalse(notFoundResult.stopBatch)

            val rateLimitedResult = confirmRepositoryEmpty(client, "token", "me", "rate-limited")
            assertEquals(RepositoryEmptyStatus.UNCONFIRMED, rateLimitedResult.status)
            assertTrue(rateLimitedResult.stopBatch)
        }

        val closedSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val deadBaseUrl = URI.create("http://127.0.0.1:${closedSocket.localPort}/")
        closedSocket.close()
        val networkClient = GitHubHttpClient(baseUrl = deadBaseUrl, connectTimeoutMillis = 500, readTimeoutMillis = 500)
        val networkResult = confirmRepositoryEmpty(networkClient, "token", "me", "offline-repo")
        assertEquals(RepositoryEmptyStatus.UNCONFIRMED, networkResult.status)
        assertTrue(networkResult.stopBatch)
    }

    @Test
    fun checkEmptyRepositoryCandidatesOnlyProbesUncachedCandidatesUpToLimitAndStopsOnNetworkError() {
        fun repo(id: Long, sizeKb: Long?, archived: Boolean = false) = GitHubRepository(
            id = id, name = "r$id", fullName = "me/r$id", owner = "me",
            isPrivate = false, description = null, language = null, stars = 0,
            pushedAt = daysAgo(10), archived = archived, sizeKb = sizeKb
        )

        val repos = listOf(
            repo(1, sizeKb = 12L), // non-candidate
            repo(2, sizeKb = 0L, archived = true), // archived -> never candidate
            repo(3, sizeKb = 0L), // already cached
            repo(4, sizeKb = 0L), // candidate 1 -> 409 EMPTY
            repo(5, sizeKb = 0L), // candidate 2 -> 200 NOT_EMPTY
            repo(6, sizeKb = 0L), // candidate 3 -> 403 UNCONFIRMED (continue)
            repo(7, sizeKb = 0L), // candidate 4 -> beyond maxChecks=3
            repo(8, sizeKb = null) // unknown size -> non-candidate
        )
        val probed = mutableListOf<Long>()
        val results = checkEmptyRepositoryCandidates(
            repositories = repos,
            existingResults = mapOf(3L to RepositoryEmptyStatus.NOT_EMPTY),
            maxChecks = 3
        ) { candidate ->
            probed += candidate.id
            when (candidate.id) {
                4L -> RepositoryEmptyProbeResult(RepositoryEmptyStatus.EMPTY)
                5L -> RepositoryEmptyProbeResult(RepositoryEmptyStatus.NOT_EMPTY)
                else -> RepositoryEmptyProbeResult(RepositoryEmptyStatus.UNCONFIRMED)
            }
        }

        assertEquals(listOf(4L, 5L, 6L), probed)
        assertEquals(RepositoryEmptyStatus.NOT_EMPTY, results[3L])
        assertEquals(RepositoryEmptyStatus.EMPTY, results[4L])
        assertEquals(RepositoryEmptyStatus.NOT_EMPTY, results[5L])
        assertEquals(RepositoryEmptyStatus.UNCONFIRMED, results[6L])
        assertNull(results[7L])

        val stoppedProbed = mutableListOf<Long>()
        val stoppedResults = checkEmptyRepositoryCandidates(
            repositories = listOf(repo(10, 0L), repo(11, 0L)),
            existingResults = emptyMap(),
            maxChecks = 5
        ) { candidate ->
            stoppedProbed += candidate.id
            RepositoryEmptyProbeResult(RepositoryEmptyStatus.UNCONFIRMED, stopBatch = true)
        }
        assertEquals(listOf(10L), stoppedProbed)
        assertTrue(stoppedResults.isEmpty())
    }

    @Test
    fun detailLoaderConfirmsEmptyOnlyOn409AndParserToleratesInvalidSize() {
        val listRepos = parseGitHubRepositories(
            """[
                {"id":1,"name":"empty","full_name":"me/empty","owner":{"login":"me"},"private":false,"stargazers_count":0,"size":0},
                {"id":2,"name":"no-size","full_name":"me/no-size","owner":{"login":"me"},"private":false,"stargazers_count":0},
                {"id":3,"name":"neg-size","full_name":"me/neg-size","owner":{"login":"me"},"private":false,"stargazers_count":0,"size":-5},
                {"id":4,"name":"float-size","full_name":"me/float-size","owner":{"login":"me"},"private":false,"stargazers_count":0,"size":1.5}
            ]"""
        )
        assertEquals(0L, listRepos[0].sizeKb)
        assertTrue(listRepos[0].isEmptyCandidate)
        assertEquals(RepositoryHealthStatus.UNKNOWN, listRepos[0].health.status)
        assertNull(listRepos[1].sizeKb)
        assertNull(listRepos[2].sizeKb)
        assertNull(listRepos[3].sizeKb)

        val negativeSizeDetail = parseGitHubRepositoryDetail(
            """{"id":1,"name":"neg","full_name":"me/neg","owner":{"login":"me"},"private":false,"size":-5}"""
        )
        assertNull(negativeSizeDetail.sizeKb)
        assertFalse(negativeSizeDetail.confirmedEmpty)

        val floatSizeDetail = parseGitHubRepositoryDetail(
            """{"id":2,"name":"flt","full_name":"me/flt","owner":{"login":"me"},"private":false,"size":2.5}"""
        )
        assertNull(floatSizeDetail.sizeKb)

        val stringSizeDetail = parseGitHubRepositoryDetail(
            """{"id":3,"name":"str","full_name":"me/str","owner":{"login":"me"},"private":false,"size":"invalid"}"""
        )
        assertNull(stringSizeDetail.sizeKb)

        StubCommitsApi(
            mapOf(
                "/repos/me/empty" to (200 to """{"id":1,"name":"empty","full_name":"me/empty","owner":{"login":"me"},"private":false,"size":0}"""),
                "/repos/me/empty/commits?per_page=1" to (409 to """{"message":"Git Repository is empty."}"""),
                "/repos/me/empty/readme" to (404 to """{"message":"Not Found"}"""),
                "/repos/me/falsezero" to (200 to """{"id":2,"name":"falsezero","full_name":"me/falsezero","owner":{"login":"me"},"private":false,"size":0,"pushed_at":"2026-10-19T12:00:00Z"}"""),
                "/repos/me/falsezero/commits?per_page=1" to (200 to """[{"sha":"abc"}]"""),
                "/repos/me/falsezero/readme" to (404 to """{"message":"Not Found"}""")
            )
        ).use { api ->
            val loader = GitHubRepositoryDetailLoader(GitHubHttpClient(baseUrl = api.baseUrl), "token")

            val emptyLoaded = loader.load(
                GitHubRepository(1, "empty", "me/empty", "me", false, null, null, 0, sizeKb = 0L)
            ) as GitHubRepositoryDetailResult.Loaded
            assertTrue(emptyLoaded.detail.confirmedEmpty)
            assertEquals(
                RepositoryHealthStatus.EMPTY,
                repositoryHealth(emptyLoaded.detail.pushedAt, emptyLoaded.detail.archived, now, emptyLoaded.detail.confirmedEmpty).status
            )

            val falseZeroLoaded = loader.load(
                GitHubRepository(2, "falsezero", "me/falsezero", "me", false, null, null, 0, sizeKb = 0L)
            ) as GitHubRepositoryDetailResult.Loaded
            assertFalse(falseZeroLoaded.detail.confirmedEmpty)
            assertEquals(
                RepositoryHealthStatus.ACTIVE,
                repositoryHealth(falseZeroLoaded.detail.pushedAt, falseZeroLoaded.detail.archived, now, falseZeroLoaded.detail.confirmedEmpty).status
            )
        }
    }

    private class StubCommitsApi(
        private val responses: Map<String, Pair<Int, String>>
    ) : AutoCloseable {
        private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val baseUrl: URI = URI.create("http://127.0.0.1:${server.localPort}/")

        private val thread = Thread {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                        val requestLine = reader.readLine().orEmpty()
                        var line = reader.readLine()
                        while (!line.isNullOrEmpty()) line = reader.readLine()
                        val path = requestLine.split(' ').getOrNull(1).orEmpty()
                        val (status, bodyText) = responses[path] ?: (404 to "{}")
                        val body = bodyText.toByteArray(StandardCharsets.UTF_8)
                        val headers = (
                            "HTTP/1.1 $status Status\r\n" +
                                "Content-Type: application/json\r\n" +
                                "Content-Length: ${body.size}\r\n" +
                                "Connection: close\r\n\r\n"
                            ).toByteArray(StandardCharsets.UTF_8)
                        socket.getOutputStream().apply {
                            write(headers)
                            write(body)
                            flush()
                        }
                    }
                } catch (_: java.net.SocketException) {
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        override fun close() {
            server.close()
            thread.join(1_000)
        }
    }
}
