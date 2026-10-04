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
                api.requests.size == 2 && api.requests[1].contains("state%3Aclosed+-is%3Amerged")
            }
            compose.onNodeWithText("Mescladas").performClick()
            compose.waitUntil(10_000) {
                api.requests.size == 3 && api.requests[2].contains("is%3Apr+involves%3A%40me+is%3Amerged")
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
            compose.onNodeWithText("Fixture PR comment").assertIsDisplayed()
            compose.onNodeWithText("Aprovou · @reviewer").assertIsDisplayed()
            assertTrue(api.requests.any { it.startsWith("GET /repos/owner/project/pulls/27 ") })
            assertTrue(api.requests.any { it.startsWith("GET /repos/owner/project/issues/27/comments") })
            assertTrue(api.requests.any { it.startsWith("GET /repos/owner/project/pulls/27/reviews") })
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

    private fun fixtureSession() = GitHubSession(
        GitHubUser("fixture-user", "https://github.com/fixture-user"),
        SessionCredentials("fixture-token")
    )
}

private class PullRequestApi(
    private val totalCount: Int = 1,
    private val detailStatusCode: Int? = null
) : AutoCloseable {
    private val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    val baseUri: URI = URI.create("http://127.0.0.1:${server.localPort}/")
    val requests = CopyOnWriteArrayList<String>()
    private val worker = Thread {
        while (!server.isClosed) runCatching {
            server.accept().use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                val request = reader.readLine()
                requests += request
                while (reader.readLine()?.isNotEmpty() == true) Unit
                val statusCode = if (request.startsWith("GET /repos/owner/project/pulls/27 ")) detailStatusCode ?: 200 else 200
                val body = when {
                    statusCode != 200 -> ""
                    request.startsWith("GET /search/issues") -> """{"total_count":$totalCount,"incomplete_results":false,"items":[{"number":27,"title":"Fixture pull request","state":"open","repository_url":"https://api.github.com/repos/owner/project","user":{"login":"fixture-author"},"pull_request":{},"labels":[{"name":"ready"}]}]}"""
                    request.startsWith("GET /repos/owner/project/pulls/27/reviews") -> """[{"id":77,"state":"APPROVED","submitted_at":"2026-10-01T10:00:00Z","user":{"login":"reviewer"}}]"""
                    request.startsWith("GET /repos/owner/project/issues/27/comments") -> """[{"id":66,"body":"Fixture PR comment","created_at":"2026-10-01T09:00:00Z","user":{"login":"commenter"}}]"""
                    request.startsWith("GET /repos/owner/project/pulls/27") -> """{"number":27,"title":"Fixture pull request","state":"open","merged_at":null,"body":"Fixture PR description","user":{"login":"fixture-author"},"labels":[{"name":"ready"}],"head":{"ref":"feature-branch","repo":{"full_name":"owner/project"}},"base":{"ref":"main","repo":{"full_name":"owner/project"}}}"""
                    else -> "{}"
                }
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                socket.getOutputStream().apply {
                    val reason = if (statusCode == 200) "OK" else "Error"
                    write("HTTP/1.1 $statusCode $reason\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
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
