package com.playertwo.controlegithub

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
import org.junit.Rule
import org.junit.Test

class GitHubPullRequestsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun filtersGlobalPullRequestsByOpenClosedAndMergedStates() {
        PullRequestApi().use { api ->
            compose.setContent {
                ControleTheme(AppThemeMode.LIGHT) {
                    GitHubPullRequestsScreen(
                        modifier = Modifier,
                        client = GitHubHttpClient(api.baseUri),
                        session = fixtureSession(),
                        sessionRestoring = false,
                        sessionStorageError = false,
                        onConnected = { true },
                        onSessionExpired = {},
                        onAppearance = {},
                        onShowIssues = {}
                    )
                }
            }

            compose.waitUntil(10_000) {
                api.requests.size == 1 &&
                    compose.onAllNodesWithText("Fixture pull request").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Fixture pull request").assertIsDisplayed()
            compose.onNodeWithText("Fixture issue").assertDoesNotExist()
            assertTrue(api.requests[0].startsWith("GET /search/issues?q=is%3Apr+involves%3A%40me+state%3Aopen"))

            if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("maestroPreview") == "true") {
                Thread.sleep(60_000)
            }

            compose.onNodeWithText("Fechadas").performClick()
            compose.waitUntil(10_000) {
                api.requests.any { it.contains("state%3Aclosed+-is%3Amerged") }
            }
            compose.onNodeWithText("Mescladas").performClick()
            compose.waitUntil(10_000) {
                api.requests.any { it.contains("is%3Apr+involves%3A%40me+is%3Amerged") }
            }
        }
    }

    @Test fun opensPullRequestDetailWithCommentsAndReviews() {
        PullRequestApi().use { api ->
            compose.setContent {
                ControleTheme(AppThemeMode.LIGHT) {
                    GitHubPullRequestsScreen(
                        modifier = Modifier,
                        client = GitHubHttpClient(api.baseUri),
                        session = fixtureSession(),
                        sessionRestoring = false,
                        sessionStorageError = false,
                        onConnected = { true },
                        onSessionExpired = {},
                        onAppearance = {},
                        onShowIssues = {}
                    )
                }
            }

            compose.onNodeWithText("Fixture pull request").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Fixture PR description").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("owner/project:feature-branch").assertIsDisplayed()
            compose.onNodeWithText("owner/project:main").assertIsDisplayed()
            compose.waitUntil(10_000) { api.requests.any { it.startsWith("GET /repos/owner/project/pulls/27/files") } }
            compose.onNodeWithText("src/Foo.kt").performClick()
            compose.onNodeWithText("+new line", substring = true).assertIsDisplayed()
            compose.waitUntil(10_000) {
                api.requests.any { it.startsWith("GET /repos/owner/project/commits/${"a".repeat(40)}/check-runs") } &&
                    api.requests.any { it.startsWith("GET /repos/owner/project/commits/${"a".repeat(40)}/status") } &&
                    compose.onAllNodesWithText("lint-fixture").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("build-fixture").assertIsDisplayed()
            compose.onNodeWithText("Sucesso").assertIsDisplayed()
            compose.onNodeWithText("lint-fixture").assertExists()
            compose.onNodeWithText("Resultado combinado: Falha").assertExists()
            compose.onNodeWithText("Fixture PR comment").assertExists()
            compose.onNodeWithText("Aprovou · @reviewer").assertExists()
            assertTrue(api.requests.any { it.startsWith("GET /repos/owner/project/pulls/27 ") })
            assertTrue(api.requests.any { it.startsWith("GET /repos/owner/project/issues/27/comments") })
            assertTrue(api.requests.any { it.startsWith("GET /repos/owner/project/pulls/27/reviews") })
            assertTrue(api.requests.any { it.startsWith("GET /repos/owner/project/pulls/27/files") })
            assertTrue(api.requests.any { it.startsWith("GET /repos/owner/project/commits/${"a".repeat(40)}/check-runs") })
            assertTrue(api.requests.any { it.startsWith("GET /repos/owner/project/commits/${"a".repeat(40)}/status") })
            assertTrue(api.requests.all { it.startsWith("GET ") })
            if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("maestroPreview") == "true") {
                Thread.sleep(60_000)
            }
        }
    }

    @Test fun reportsSearchLimitWhenTotalCountIsExactlyOneThousand() {
        PullRequestApi(totalCount = 1_000).use { api ->
            compose.setContent {
                ControleTheme(AppThemeMode.LIGHT) {
                    GitHubPullRequestsScreen(
                        modifier = Modifier,
                        client = GitHubHttpClient(api.baseUri),
                        session = fixtureSession(),
                        sessionRestoring = false,
                        sessionStorageError = false,
                        onConnected = { true },
                        onSessionExpired = {},
                        onAppearance = {},
                        onShowIssues = {}
                    )
                }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("A busca do GitHub retornou resultados parciais ou atingiu o limite de 1.000 itens.")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("A busca do GitHub retornou resultados parciais ou atingiu o limite de 1.000 itens.")
                .assertIsDisplayed()
        }
    }

    @Test fun doesNotRevealWhetherMissingOrGonePullRequestWasAccessible() {
        for (statusCode in listOf(404, 410)) {
            PullRequestApi(detailStatusCode = statusCode).use { api ->
                val result = GitHubPullRequestLoader(
                    GitHubHttpClient(api.baseUri),
                    "fixture-token"
                ).load(fixturePullRequest())
                assertTrue("HTTP $statusCode should be indistinguishable", result is GitHubPullRequestDetailResult.Unavailable)
            }
        }
    }

    @Test fun emptyCheckAndStatusListsDoNotClaimCombinedSuccess() {
        PullRequestApi(emptyArtifacts = true).use { api ->
            compose.setContent {
                ControleTheme(AppThemeMode.LIGHT) {
                    GitHubPullRequestsScreen(
                        modifier = Modifier,
                        client = GitHubHttpClient(api.baseUri),
                        session = fixtureSession(),
                        sessionRestoring = false,
                        sessionStorageError = false,
                        onConnected = { true },
                        onSessionExpired = {},
                        onAppearance = {},
                        onShowIssues = {}
                    )
                }
            }
            compose.waitUntil(30_000) {
                api.requests.size == 1 &&
                    compose.onAllNodesWithText("Fixture pull request").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Fixture pull request").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Nenhum check informado neste repositório e commit.").fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText("Nenhum status informado neste repositório e commit.").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Resultado combinado: Sucesso").assertDoesNotExist()
        }
    }

    @Test fun expandingFileWithoutPatchExplainsWhyDiffIsUnavailable() {
        PullRequestApi(missingFilePatch = true).use { api ->
            showPullRequestDetail(api)
            compose.onNodeWithText("src/Foo.kt").performClick()
            compose.onNodeWithText("Diff não fornecido pelo GitHub (arquivo binário ou trecho indisponível/truncado).").assertExists()
        }
    }

    @Test fun expandingLongFilePatchExplainsLocalTruncation() {
        PullRequestApi(longFilePatchLength = 20_001).use { api ->
            showPullRequestDetail(api)
            compose.onNodeWithText("src/Foo.kt").performClick()
            compose.onNodeWithText("Trecho cortado localmente após 20.000 caracteres.").assertExists()
        }
    }

    private fun showPullRequestDetail(api: PullRequestApi) {
        compose.setContent {
            ControleTheme(AppThemeMode.LIGHT) {
                GitHubPullRequestsScreen(
                    modifier = Modifier,
                    client = GitHubHttpClient(api.baseUri),
                    session = fixtureSession(),
                    sessionRestoring = false,
                    sessionStorageError = false,
                    onConnected = { true },
                    onSessionExpired = {},
                    onAppearance = {},
                    onShowIssues = {}
                )
            }
        }
        compose.waitUntil(30_000) {
            api.requests.size == 1 &&
                compose.onAllNodesWithText("Fixture pull request").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Fixture pull request").performClick()
        compose.waitUntil(30_000) {
            api.requests.any { it.startsWith("GET /repos/owner/project/pulls/27/files") }
        }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("src/Foo.kt").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun filesPaginationFollowsNextAndDeduplicatesPaths() {
        PullRequestApi(paginatedFiles = true).use { api ->
            compose.setContent {
                ControleTheme(AppThemeMode.LIGHT) {
                    GitHubPullRequestsScreen(
                        modifier = Modifier,
                        client = GitHubHttpClient(api.baseUri),
                        session = fixtureSession(),
                        sessionRestoring = false,
                        sessionStorageError = false,
                        onConnected = { true },
                        onSessionExpired = {},
                        onAppearance = {},
                        onShowIssues = {}
                    )
                }
            }
            compose.onNodeWithText("Fixture pull request").performClick()
            compose.waitUntil(10_000) { api.requests.any { it.startsWith("GET /repos/owner/project/pulls/27/files") } }
            compose.onNodeWithText("Carregar mais arquivos").performClick()
            compose.waitUntil(10_000) {
                api.requests.any { it.startsWith("GET /repos/owner/project/pulls/27/files?per_page=100&page=2") } &&
                    compose.onAllNodesWithText("src/Bar.kt").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(1, compose.onAllNodesWithText("src/Foo.kt").fetchSemanticsNodes().size)
            assertEquals(1, compose.onAllNodesWithText("src/Bar.kt").fetchSemanticsNodes().size)
        }
    }

    @Test fun statusFailureKeepsChecksAndRetryLoadsOnlyFailedSection() {
        PullRequestApi(failFirstStatus = true).use { api ->
            compose.setContent {
                ControleTheme(AppThemeMode.LIGHT) {
                    GitHubPullRequestsScreen(
                        modifier = Modifier,
                        client = GitHubHttpClient(api.baseUri),
                        session = fixtureSession(),
                        sessionRestoring = false,
                        sessionStorageError = false,
                        onConnected = { true },
                        onSessionExpired = {},
                        onAppearance = {},
                        onShowIssues = {}
                    )
                }
            }
            compose.onNodeWithText("Fixture pull request").performClick()
            compose.waitUntil(10_000) {
                api.statusRequests.get() == 1 &&
                    compose.onAllNodesWithText("build-fixture").fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText("src/Foo.kt").fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText("Fixture PR comment").fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText("Aprovou · @reviewer").fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText("O GitHub está com instabilidade. Tente novamente mais tarde.").fetchSemanticsNodes().isNotEmpty()
            }
            val requestsBeforeRetry = api.requests.filterNot { it.contains("/commits/${"a".repeat(40)}/status") }
            compose.onNodeWithTag("pull-request-statuses-retry").performScrollTo().performClick()
            compose.waitUntil(10_000) { api.statusRequests.get() == 2 }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Resultado combinado: Falha").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(
                requestsBeforeRetry,
                api.requests.filterNot { it.contains("/commits/${"a".repeat(40)}/status") }
            )
            compose.onNodeWithText("build-fixture").assertExists()
        }
    }

    private fun fixtureSession() = GitHubSession(
        GitHubUser("fixture-user", "https://github.com/fixture-user"),
        SessionCredentials("fixture-token")
    )
}

private class PullRequestApi(
    private val totalCount: Int = 1,
    private val detailStatusCode: Int? = null,
    private val emptyArtifacts: Boolean = false,
    private val paginatedFiles: Boolean = false,
    private val failFirstStatus: Boolean = false,
    private val missingFilePatch: Boolean = false,
    private val longFilePatchLength: Int? = null
) : AutoCloseable {
    private val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    val baseUri: URI = URI.create("http://127.0.0.1:${server.localPort}/")
    val requests = CopyOnWriteArrayList<String>()
    val statusRequests = AtomicInteger()
    private val worker = Thread {
        while (!server.isClosed) runCatching {
            server.accept().use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                val request = reader.readLine()
                requests += request
                while (reader.readLine()?.isNotEmpty() == true) Unit
                val isStatusRequest = request.startsWith("GET /repos/owner/project/commits/" + "a".repeat(40) + "/status")
                val statusAttempt = if (isStatusRequest) statusRequests.incrementAndGet() else 0
                val statusCode = when {
                    request.startsWith("GET /repos/owner/project/pulls/27 ") -> detailStatusCode ?: 200
                    isStatusRequest && failFirstStatus && statusAttempt == 1 -> 500
                    else -> 200
                }
                val body = when {
                    statusCode != 200 -> ""
                    request.startsWith("GET /search/issues") -> """{"total_count":$totalCount,"incomplete_results":false,"items":[{"number":27,"title":"Fixture pull request","state":"open","repository_url":"https://api.github.com/repos/owner/project","user":{"login":"fixture-author"},"pull_request":{},"labels":[{"name":"ready"}]}]}"""
                    request.startsWith("GET /repos/owner/project/pulls/27/reviews") -> """[{"id":77,"state":"APPROVED","submitted_at":"2026-10-01T10:00:00Z","user":{"login":"reviewer"}}]"""
                    request.startsWith("GET /repos/owner/project/issues/27/comments") -> """[{"id":66,"body":"Fixture PR comment","created_at":"2026-10-01T09:00:00Z","user":{"login":"commenter"}}]"""
                    request.startsWith("GET /repos/owner/project/pulls/27/files") -> when {
                        emptyArtifacts -> "[]"
                        paginatedFiles && request.contains("page=2") -> """[{"filename":"src/Foo.kt","status":"modified","additions":1,"deletions":1,"changes":2,"patch":"@@ -1 +1 @@\n-old line\n+new line"},{"filename":"src/Bar.kt","status":"added","additions":1,"deletions":0,"changes":1,"patch":"+new"}]"""
                        paginatedFiles -> """[{"filename":"src/Foo.kt","status":"modified","additions":1,"deletions":1,"changes":2,"patch":"@@ -1 +1 @@\n-old line\n+new line"}]"""
                        missingFilePatch -> """[{"filename":"src/Foo.kt","status":"modified","additions":1,"deletions":1,"changes":2}]"""
                        longFilePatchLength != null -> """[{"filename":"src/Foo.kt","status":"modified","additions":1,"deletions":1,"changes":2,"patch":"${"x".repeat(longFilePatchLength)}"}]"""
                        else -> """[{"filename":"src/Foo.kt","status":"modified","additions":1,"deletions":1,"changes":2,"patch":"@@ -1 +1 @@\n-old line\n+new line"}]"""
                    }
                    request.startsWith("GET /repos/owner/project/commits/" + "a".repeat(40) + "/check-runs") -> if (emptyArtifacts) """{"total_count":0,"check_runs":[]}""" else """{"total_count":1,"check_runs":[{"id":1,"name":"build-fixture","status":"completed","conclusion":"success","head_sha":"${"a".repeat(40)}"}]}"""
                    request.startsWith("GET /repos/owner/project/commits/" + "a".repeat(40) + "/status") -> if (emptyArtifacts) """{"state":"success","sha":"${"a".repeat(40)}","statuses":[]}""" else """{"state":"failure","sha":"${"a".repeat(40)}","statuses":[{"id":2,"context":"lint-fixture","state":"failure","description":"Fixture failure"}]}"""
                    request.startsWith("GET /repos/owner/project/pulls/27") -> """{"number":27,"title":"Fixture pull request","state":"open","merged_at":null,"body":"Fixture PR description","user":{"login":"fixture-author"},"labels":[{"name":"ready"}],"head":{"ref":"feature-branch","sha":"${"a".repeat(40)}","repo":{"full_name":"owner/project"}},"base":{"ref":"main","repo":{"full_name":"owner/project"}}}"""
                    else -> "{}"
                }
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                socket.getOutputStream().apply {
                    val reason = if (statusCode == 200) "OK" else "Error"
                    val nextLink = if (paginatedFiles && request.startsWith("GET /repos/owner/project/pulls/27/files?per_page=100 ") && !request.contains("page=2")) {
                        "Link: <${baseUri}repos/owner/project/pulls/27/files?per_page=100&page=2>; rel=\"next\"\r\n"
                    } else ""
                    write("HTTP/1.1 $statusCode $reason\r\nContent-Type: application/json\r\n${nextLink}Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
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

private fun fixturePullRequest() = GitHubPullRequest(
    number = 27,
    title = "Fixture pull request",
    state = GitHubPullRequestState.OPEN,
    repository = "owner/project",
    author = "fixture-author",
    labels = listOf("ready"),
    updatedAt = null
)
