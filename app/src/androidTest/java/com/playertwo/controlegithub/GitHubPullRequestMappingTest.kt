package com.playertwo.controlegithub

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubPullRequestMappingTest {
    @Test fun searchKeepsOnlyValidPullRequestsAndDeduplicatesRepositoryNumber() {
        val result = parseGitHubPullRequestSearch(
            """{"total_count":3,"incomplete_results":true,"items":[
                {"number":7,"title":"First","state":"open","repository_url":"https://api.github.com/repos/owner/repo","user":{"login":"author"},"pull_request":{}},
                {"number":7,"title":"Duplicate","state":"open","repository_url":"https://api.github.com/repos/owner/repo","pull_request":{}},
                {"number":8,"title":"Issue","state":"open","repository_url":"https://api.github.com/repos/owner/repo"},
                {"number":9,"title":"Untrusted host","state":"open","repository_url":"https://evil.example/repos/owner/repo","pull_request":{}}
            ]}""",
            GitHubPullRequestFilter.OPEN
        )

        assertEquals(3, result.totalCount)
        assertTrue(result.incomplete)
        assertEquals(1, result.items.size)
        assertEquals("owner/repo", result.items.single().repository)
        assertEquals(7, result.items.single().number)
        assertEquals(GitHubPullRequestState.OPEN, result.items.single().state)
    }

    @Test fun detailDistinguishesClosedAndMergedAndPreservesForkBranches() {
        val headSha = "a".repeat(40)
        val merged = parseGitHubPullRequestDetail(
            """{"number":12,"title":"Merged","state":"closed","merged_at":"2026-10-01T10:00:00Z","body":"body","user":{"login":"author"},"labels":[{"name":"ready"}],"head":{"ref":"feature","sha":"$headSha","repo":{"full_name":"contributor/repo"}},"base":{"ref":"main","repo":{"full_name":"owner/repo"}}}""",
            "owner/repo",
            12
        )
        val closed = parseGitHubPullRequestDetail(
            """{"number":13,"title":"Closed","state":"closed","merged_at":null,"head":{"ref":"work","sha":"$headSha","repo":{"full_name":"owner/repo"}},"base":{"ref":"main","repo":{"full_name":"owner/repo"}}}""",
            "owner/repo",
            13
        )

        assertEquals(GitHubPullRequestState.MERGED, merged.state)
        assertEquals("contributor/repo", merged.headRepository)
        assertEquals("feature", merged.headBranch)
        assertEquals(headSha, merged.headSha)
        assertEquals("owner/repo", merged.baseRepository)
        assertEquals("main", merged.baseBranch)
        assertEquals(listOf("ready"), merged.labels)
        assertEquals(GitHubPullRequestState.CLOSED, closed.state)
    }

    @Test fun filesChecksAndStatusesMapDiffAndCommitResults() {
        val sha = "b".repeat(40)
        val files = parseGitHubPullRequestFiles(
            """[{"filename":"src/A.kt","status":"modified","additions":4,"deletions":2,"changes":6,"patch":"@@ -1 +1 @@\n-old\n+new"},{"filename":"logo.png","status":"added","additions":0,"deletions":0,"changes":0},{"filename":"empty.txt","status":"modified","additions":0,"deletions":0,"changes":0,"patch":""}]"""
        )
        val checks = parseGitHubPullRequestChecks(
            """{"total_count":2,"check_runs":[{"id":1,"name":"build","status":"completed","conclusion":"success","head_sha":"$sha"},{"id":2,"name":"tests","status":"in_progress","head_sha":"$sha"}]}""",
            sha
        )
        val statuses = parseGitHubCommitStatuses(
            """{"state":"failure","sha":"$sha","statuses":[{"id":3,"context":"lint","state":"failure","description":"Failed","updated_at":"2026-10-01T12:00:00Z"}]}""",
            sha
        )

        assertEquals(3, files.size)
        assertEquals(4, files.first().additions)
        assertTrue(files.first().patch!!.contains("+new"))
        assertEquals(null, files[1].patch)
        assertEquals("", files[2].patch)
        val longPatch = parseGitHubPullRequestFiles(
            """[{"filename":"large.txt","status":"modified","additions":1,"deletions":0,"changes":1,"patch":"${"x".repeat(20_001)}"}]"""
        ).single().patch
        assertEquals(20_001, longPatch?.length)
        assertEquals(listOf("success", "in_progress"), checks.map { it.conclusion ?: it.status })
        assertEquals("failure", statuses.state)
        assertEquals("lint", statuses.statuses.single().context)
        assertTrue(runCatching {
            parseGitHubPullRequestChecks("""{"total_count":0,"check_runs":[],"head_sha":"${"c".repeat(40)}"}""", sha)
        }.isFailure)
        val unknownConclusion = parseGitHubPullRequestChecks(
            """{"total_count":1,"check_runs":[{"id":4,"name":"future","status":"completed","conclusion":"future_state","head_sha":"$sha"}]}""",
            sha
        ).single()
        assertEquals("future_state", unknownConclusion.conclusion)
    }

    @Test fun paginationCapsAreReportedEvenWhenTheServerOmitsNextLinkAtTheBoundary() {
        val file = GitHubPullRequestFile("src/A.kt", "modified", 0, 0, 0, null)
        val filesPage = limitedFilesPage(listOf(file), hasNext = false, pageNumber = 30, maxPages = 30)
        val checksPage = GitHubPageResult.Loaded(listOf("check"), hasNext = false).limitPage(pageNumber = 100, maxPages = 100)

        assertTrue(filesPage.limited)
        assertTrue(checksPage is GitHubLimitedPageResult.Loaded && checksPage.page.limited)
    }

    @Test fun checkRunPagerEnforcesTheHundredPageHttpCap() {
        PaginatedArtifactsApi("check-runs").use { api ->
            val pager = GitHubPullRequestCheckPager(api.client, "fixture-token", fixturePullRequest(), SHA)
            var result: GitHubLimitedPageResult<GitHubPullRequestCheck>? = null
            repeat(100) { result = pager.loadNext() }

            val page = result as GitHubLimitedPageResult.Loaded
            assertEquals(100, api.requests.size)
            assertEquals(100, page.page.items.size)
            assertTrue(page.page.limited)
            assertTrue(!page.page.hasNext)
            assertEquals(page, pager.loadNext())
            assertEquals(100, api.requests.size)
        }
    }

    @Test fun filesPagerEnforcesTheThirtyPageAndThreeThousandFileHttpCap() {
        PaginatedArtifactsApi("files").use { api ->
            val pager = GitHubPullRequestFilesPager(api.client, "fixture-token", fixturePullRequest())
            var result: GitHubPullRequestFilesPageResult? = null
            repeat(30) { result = pager.loadNext() }

            val page = (result as GitHubPullRequestFilesPageResult.Loaded).page
            assertEquals(30, api.requests.size)
            assertEquals(3_000, page.files.size)
            assertTrue(page.limited)
            assertTrue(!page.hasNext)
            assertEquals(page, (pager.loadNext() as GitHubPullRequestFilesPageResult.Loaded).page)
            assertEquals(30, api.requests.size)
        }
    }

    @Test fun commitStatusPagerEnforcesTheHundredPageHttpCap() {
        PaginatedArtifactsApi("status").use { api ->
            val pager = GitHubCommitStatusPager(api.client, "fixture-token", fixturePullRequest(), SHA)
            var result: GitHubLimitedPageResult<GitHubCommitStatus>? = null
            repeat(100) { result = pager.loadNext() }

            val page = result as GitHubLimitedPageResult.Loaded
            assertEquals(100, api.requests.size)
            assertEquals(100, page.page.items.size)
            assertTrue(page.page.limited)
            assertTrue(!page.page.hasNext)
            assertEquals(page, pager.loadNext())
            assertEquals(100, api.requests.size)
        }
    }

    private fun fixturePullRequest() = GitHubPullRequest(
        number = 27,
        title = "Fixture",
        state = GitHubPullRequestState.OPEN,
        repository = "owner/project",
        author = null,
        labels = emptyList(),
        updatedAt = Instant.EPOCH
    )

    private class PaginatedArtifactsApi(private val endpoint: String) : AutoCloseable {
        private val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
        val client = GitHubHttpClient(URI.create("http://127.0.0.1:${server.localPort}/"))
        val requests = CopyOnWriteArrayList<String>()
        private val worker = Thread {
            while (!server.isClosed) runCatching {
                server.accept().use { socket ->
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                    val request = reader.readLine()
                    requests += request
                    while (reader.readLine()?.isNotEmpty() == true) Unit
                    val path = request.substringAfter(' ').substringBefore(" HTTP/")
                    val query = path.substringAfter('?', "")
                    val page = Regex("(?:^|&)page=(\\d+)").find(query)?.groupValues?.get(1)?.toInt() ?: 1
                    val body = when (endpoint) {
                        "check-runs" -> """{"total_count":1,"check_runs":[{"id":$page,"name":"check-$page","status":"completed","conclusion":"success","head_sha":"$SHA"}]}"""
                        "files" -> (0 until 100).joinToString(prefix = "[", postfix = "]") { index ->
                            """{"filename":"src/$page-$index.kt","status":"modified","additions":1,"deletions":0,"changes":1}"""
                        }
                        else -> """{"state":"failure","sha":"$SHA","statuses":[{"id":$page,"context":"status-$page","state":"failure"}]}"""
                    }
                    val nextHeader = if (page <= 100) {
                        val baseQuery = query.replace(Regex("(?:^|&)page=\\d+"), "")
                        val next = "http://127.0.0.1:${server.localPort}${path.substringBefore('?')}?$baseQuery&page=${page + 1}"
                        "Link: <$next>; rel=\"next\"\r\n"
                    } else ""
                    val bytes = body.toByteArray(StandardCharsets.UTF_8)
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n${nextHeader}Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                        write(bytes)
                        flush()
                    }
                }
            }
        }.apply { isDaemon = true; start() }

        override fun close() {
            server.close()
            worker.join(1_000)
        }
    }

    private companion object {
        const val SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
