package com.playertwo.controlegithub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GitHubIssuesScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun filtersIssuesPaginatesAndExcludesPullRequestsAndDuplicates() {
        IssuesApi().use { api ->
            compose.setContent {
                ControleTheme(AppThemeMode.LIGHT) {
                    GitHubIssuesScreen(
                        androidx.compose.ui.Modifier,
                        GitHubHttpClient(api.baseUri),
                        fixtureSession(),
                        sessionRestoring = false,
                        sessionStorageError = false,
                        onConnected = { true },
                        onSessionExpired = {},
                        onAppearance = {}
                    )
                }
            }
            compose.waitUntil(10_000) {
                api.requests.size == 1 &&
                    compose.onAllNodesWithText("Fixture issue one").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("fixture-owner/fixture-repo · #18").assertIsDisplayed()
            compose.onNodeWithText("Fixture pull request").assertDoesNotExist()
            assertEquals("GET /issues?filter=assigned&state=open&pulls=false&per_page=50 HTTP/1.1", api.requests[0])
            if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("maestroPreview") == "true") {
                Thread.sleep(60_000)
            }

            compose.onNodeWithText("Carregar mais issues").performClick()
            compose.waitUntil(10_000) {
                api.requests.size == 2 &&
                    compose.onAllNodesWithText("Fixture issue two").fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
            compose.onNodeWithText("Fixture issue one").assertExists()
            assertEquals(1, compose.onAllNodesWithText("Fixture issue one").fetchSemanticsNodes().size)
        }
    }

    @Test fun selectedParticipationAndStateRestartQueryAndShowEmptyState() {
        IssuesApi().use { api ->
            showScreen(api)
            compose.waitUntil(10_000) {
                api.requests.size == 1 &&
                    compose.onAllNodesWithText("Fixture issue one").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Criadas por mim").performClick()
            compose.waitUntil(10_000) {
                api.requests.size == 2 &&
                    api.requests.last().startsWith("GET /issues?filter=created&state=open&pulls=false&per_page=50") &&
                    compose.onAllNodesWithText("Fixture issue one").fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
            compose.onNodeWithText("Fechadas").performClick()
            compose.waitUntil(10_000) {
                api.requests.size == 3 &&
                    compose.onAllNodesWithText("Nenhuma issue encontrada com estes filtros.").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("GET /issues?filter=created&state=closed&pulls=false&per_page=50 HTTP/1.1", api.requests[2])
        }
    }

    @Test fun opensSelectedIssueAndLoadsPagedCommentsAsInertText() {
        IssuesApi().use { api ->
            showScreen(api)
            compose.waitUntil(10_000) {
                api.requests.size == 1 &&
                    compose.onAllNodesWithText("Fixture issue one").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Fixture issue one").performClick()
            compose.waitUntil(30_000) {
                api.requests.any { it.startsWith("GET /repos/fixture-owner/fixture-repo/issues/18 HTTP") } &&
                    api.requests.any { it.startsWith("GET /repos/fixture-owner/fixture-repo/issues/18/comments") } &&
                    compose.onAllNodesWithText("**fixture description**").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Fixture detail title").assertIsDisplayed()
            compose.onNodeWithText("@fixture-comment-one").assertIsDisplayed()
            compose.onNodeWithText("<script>inert()</script>").assertIsDisplayed()
            assertTrue(api.requests.any { it.contains("/comments?per_page=50") })
            if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("maestroPreview") == "true") {
                Thread.sleep(60_000)
            }

            compose.onNodeWithText("Carregar mais comentários").performClick()
            compose.waitUntil(10_000) {
                api.requests.any { it.contains("/comments?per_page=50&page=2") } &&
                    compose.onAllNodesWithText("fixture-comment-two").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(1, compose.onAllNodesWithText("@fixture-comment-one").fetchSemanticsNodes().size)
        }
    }

    @Test fun backFromDetailPreservesFiltersAndLoadedIssues() {
        IssuesApi(largeIssueList = true).use { api ->
            showScreen(api)
            compose.waitUntil(10_000) {
                api.requests.size == 1 &&
                    compose.onAllNodesWithText("Fixture issue one").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Criadas por mim").performClick()
            compose.waitUntil(10_000) {
                api.requests.size == 2 &&
                    api.requests.last().startsWith("GET /issues?filter=created&state=open&pulls=false&per_page=50") &&
                    compose.onAllNodesWithText("Fixture issue one").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Criadas por mim").assertIsSelected()

            repeat(5) {
                compose.onRoot().performTouchInput { swipeUp() }
                compose.waitForIdle()
            }
            compose.onNodeWithText("fixture-owner/fixture-repo · #37").assertIsDisplayed()
            compose.onNodeWithText("Fixture long issue 37").performClick()
            compose.waitUntil(10_000) {
                api.requests.any { it.startsWith("GET /repos/fixture-owner/fixture-repo/issues/37 HTTP") } &&
                    compose.onAllNodesWithText("Fixture detail 37").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Voltar às issues").performClick()

            compose.onNodeWithText("Fixture long issue 37").assertIsDisplayed()
            assertEquals(2, api.requests.count { it.startsWith("GET /issues?") })
            compose.onRoot().performTouchInput { swipeDown() }
            compose.onNodeWithText("Criadas por mim").assertIsSelected()
        }
    }

    @Test fun removedIssueHasOwnState() {
        IssuesApi(initialStatus = 410).use { api ->
            showIssueDetail(api)
            compose.waitUntil(10_000) {
                api.requests.any { it.startsWith("GET /repos/fixture-owner/fixture-repo/issues/18 HTTP") } &&
                    compose.onAllNodesWithText("Esta issue foi removida do GitHub.").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(api.requests.none { it.contains("/comments?") })
        }
    }

    @Test fun notFoundIssueStaysPrivateSafe() {
        IssuesApi(initialStatus = 404).use { api ->
            showIssueDetail(api)
            compose.waitUntil(10_000) {
                api.requests.any { it.startsWith("GET /repos/fixture-owner/fixture-repo/issues/18 HTTP") } &&
                    compose.onAllNodesWithText("Esta issue não está disponível para sua conta.").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Esta issue foi removida do GitHub.").assertDoesNotExist()
            assertTrue(api.requests.none { it.contains("/comments?") })
        }
    }

    @Test fun failedNextCommentPagePreservesLoadedCommentsAndCanRetry() {
        IssuesApi().use { api ->
            showScreen(api)
            compose.waitUntil(10_000) { api.requests.size == 1 && compose.onAllNodesWithText("Fixture issue one").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Fixture issue one").performClick()
            compose.waitUntil(10_000) {
                api.requests.any { it.contains("/comments?per_page=50") } &&
                    compose.onAllNodesWithText("<script>inert()</script>").fetchSemanticsNodes().isNotEmpty()
            }
            api.responseStatus = 503
            compose.onNodeWithText("Carregar mais comentários").performClick()
            compose.waitUntil(10_000) {
                api.requests.any { it.contains("/comments?per_page=50&page=2") } &&
                    compose.onAllNodesWithText("O GitHub está com instabilidade. Tente novamente mais tarde.").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("<script>inert()</script>").assertIsDisplayed()
            api.responseStatus = 200
            compose.onNodeWithTag("issue-comments-retry").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                api.requests.count { it.contains("/comments?per_page=50&page=2") } == 2 &&
                    compose.onAllNodesWithText("fixture-comment-two").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test fun missingSessionOffersConnectionWithoutDemoIssues() {
        compose.setContent {
            ControleTheme(AppThemeMode.LIGHT) {
                GitHubIssuesScreen(
                    androidx.compose.ui.Modifier,
                    GitHubHttpClient(),
                    session = null,
                    sessionRestoring = false,
                    sessionStorageError = false,
                    onConnected = { true },
                    onSessionExpired = {},
                    onAppearance = {}
                )
            }
        }
        compose.onNodeWithText("Conecte sua conta GitHub para consultar as issues que ela pode acessar.").assertIsDisplayed()
        compose.onNodeWithText("Preparar primeira versão Android").assertDoesNotExist()
        compose.onNodeWithText("ISSUE #18").assertDoesNotExist()
    }

    @Test fun unauthorizedExpiresSessionOnce() {
        IssuesApi(initialStatus = 401).use { api ->
            val expired = AtomicInteger()
            showScreen(api, onSessionExpired = { expired.incrementAndGet() })
            compose.waitUntil(10_000) { api.requests.size == 1 && expired.get() == 1 }
            compose.runOnIdle { assertEquals(1, expired.get()) }
            compose.onNodeWithText("Sua sessão expirou. Conecte-se novamente.").assertIsDisplayed()
        }
    }

    @Test fun forbiddenCanRetry() {
        IssuesApi(initialStatus = 403).use { api ->
            showScreen(api)
            compose.waitUntil(10_000) {
                api.requests.size == 1 &&
                    compose.onAllNodesWithText("Sua conta não tem permissão para acessar este recurso.").fetchSemanticsNodes().isNotEmpty()
            }
            api.responseStatus = 200
            compose.onNodeWithText("Tentar novamente").performClick()
            compose.waitUntil(10_000) {
                api.requests.size == 2 &&
                    compose.onAllNodesWithText("Fixture issue one").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test fun serverFailureCanRetry() {
        IssuesApi(initialStatus = 503).use { api ->
            showScreen(api)
            compose.waitUntil(10_000) {
                api.requests.size == 1 &&
                    compose.onAllNodesWithText("O GitHub está com instabilidade. Tente novamente mais tarde.").fetchSemanticsNodes().isNotEmpty()
            }
            api.responseStatus = 200
            compose.onNodeWithText("Tentar novamente").performClick()
            compose.waitUntil(10_000) {
                api.requests.size == 2 &&
                    compose.onAllNodesWithText("Fixture issue one").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test fun networkFailureCanRetry() {
        IssuesApi().use { api ->
            api.dropNextResponse = true
            showScreen(api)
            compose.waitUntil(10_000) {
                api.requests.size == 1 &&
                    compose.onAllNodesWithText("Não foi possível conectar ao GitHub. Verifique sua rede.").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Tentar novamente").performClick()
            compose.waitUntil(10_000) {
                api.requests.size == 2 &&
                    compose.onAllNodesWithText("Fixture issue one").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test fun rateLimitShowsExplicitRetryAndRecovers() {
        IssuesApi(initialStatus = 429).use { api ->
            showScreen(api)
            compose.waitUntil(10_000) {
                api.requests.size == 1 &&
                    compose.onAllNodesWithText("O GitHub limitou temporariamente as consultas.").fetchSemanticsNodes().isNotEmpty()
            }
            api.responseStatus = 200
            Thread.sleep(1_100)
            compose.onNodeWithText("Tentar novamente").performClick()
            compose.waitUntil(10_000) {
                api.requests.size == 2 &&
                    compose.onAllNodesWithText("Fixture issue one").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun showScreen(api: IssuesApi, onSessionExpired: (GitHubSession) -> Unit = {}) {
        compose.setContent {
            ControleTheme(AppThemeMode.LIGHT) {
                GitHubIssuesScreen(
                    androidx.compose.ui.Modifier,
                    GitHubHttpClient(api.baseUri),
                    fixtureSession(),
                    sessionRestoring = false,
                    sessionStorageError = false,
                    onConnected = { true },
                    onSessionExpired = onSessionExpired,
                    onAppearance = {}
                )
            }
        }
    }

    private fun showIssueDetail(api: IssuesApi) {
        compose.setContent {
            ControleTheme(AppThemeMode.LIGHT) {
                GitHubIssueDetailScreen(
                    modifier = androidx.compose.ui.Modifier,
                    client = GitHubHttpClient(api.baseUri),
                    session = fixtureSession(),
                    issue = GitHubIssue(
                        number = 18,
                        title = "Fixture issue one",
                        state = GitHubIssueState.OPEN,
                        repository = "fixture-owner/fixture-repo",
                        author = "fixture-author",
                        assignees = emptyList(),
                        labels = emptyList(),
                        updatedAt = null
                    ),
                    onBack = {},
                    onSessionExpired = {},
                    onAppearance = {}
                )
            }
        }
    }

    private fun fixtureSession() = GitHubSession(
        GitHubUser("fixture-user", "https://github.com/fixture-user"),
        SessionCredentials("fixture-token")
    )
}

private class IssuesApi(initialStatus: Int = 200, largeIssueList: Boolean = false) : AutoCloseable {
    private val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    val baseUri = java.net.URI("http://127.0.0.1:${server.localPort}/")
    val requests = CopyOnWriteArrayList<String>()
    @Volatile var responseStatus = initialStatus
    @Volatile var dropNextResponse = false
    private val largeIssues = (18..37).joinToString(prefix = "[", postfix = "]") { number ->
        val title = if (number == 18) "Fixture issue one" else "Fixture long issue $number"
        """{"number":$number,"title":"$title","state":"open","html_url":"https://github.com/fixture-owner/fixture-repo/issues/$number","repository":{"full_name":"fixture-owner/fixture-repo"},"user":{"login":"fixture-author"}}"""
    }
    private val worker = Thread {
        while (!server.isClosed) runCatching {
            server.accept().use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                val request = reader.readLine()
                requests += request
                while (reader.readLine()?.isNotEmpty() == true) Unit
                if (dropNextResponse) {
                    dropNextResponse = false
                    return@use
                }
                val requestPath = request.substringAfter("GET ").substringBefore(" HTTP")
                val body = when {
                    responseStatus != 200 -> "{}"
                    "/comments" in requestPath && "page=2" in requestPath -> """[{"id":2,"body":"fixture-comment-two","user":{"login":"fixture-author"},"created_at":"2026-10-04T13:00:00Z"}]"""
                    "/comments" in requestPath -> """[{"id":1,"body":"<script>inert()</script>","user":{"login":"fixture-comment-one"},"created_at":"2026-10-04T12:00:00Z"}]"""
                    requestPath.startsWith("/repos/fixture-owner/fixture-repo/issues/") -> detailBody(requestPath)
                    request.contains("state=closed") -> "[]"
                    request.contains("page=2") -> """[$issueOne,$issueTwo]"""
                    largeIssueList && request.startsWith("GET /issues") -> largeIssues
                    else -> """[$issueOne,$pullRequest]"""
                }
                val link = if (responseStatus == 200 && "/comments" in requestPath && "page=2" !in requestPath) {
                    "Link: <http://127.0.0.1:${server.localPort}${requestPath.substringBefore('?')}?per_page=50&page=2>; rel=\"next\"\r\n"
                } else if (responseStatus == 200 && request.startsWith("GET /issues") && "page=2" !in request && "state=closed" !in request) {
                    "Link: <http://127.0.0.1:${server.localPort}/issues?filter=assigned&state=open&pulls=false&per_page=50&page=2>; rel=\"next\"\r\n"
                } else ""
                val retryAfter = if (responseStatus == 429) "Retry-After: 0\r\n" else ""
                val reason = when (responseStatus) {
                    200 -> "OK"
                    401 -> "Unauthorized"
                    403 -> "Forbidden"
                    429 -> "Too Many Requests"
                    else -> "Server Error"
                }
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                socket.getOutputStream().apply {
                    write("HTTP/1.1 $responseStatus $reason\r\nContent-Type: application/json\r\n${link}${retryAfter}Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
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

    private fun detailBody(requestPath: String): String {
        val number = requestPath.substringAfterLast('/').toInt()
        val title = if (number == 18) "Fixture detail title" else "Fixture detail $number"
        return """{"number":$number,"title":"$title","state":"open","html_url":"https://github.com/fixture-owner/fixture-repo/issues/$number","body":"**fixture description**","user":{"login":"fixture-author"},"labels":[{"name":"bug"}]}"""
    }

    private companion object {
        val issueOne = """{"number":18,"title":"Fixture issue one","state":"open","html_url":"https://github.com/fixture-owner/fixture-repo/issues/18","repository":{"full_name":"fixture-owner/fixture-repo"},"user":{"login":"fixture-author"},"assignees":[{"login":"fixture-user"}],"labels":[{"name":"bug"}],"updated_at":"2026-10-04T12:00:00Z"}"""
        val issueTwo = """{"number":19,"title":"Fixture issue two","state":"open","html_url":"https://github.com/fixture-owner/fixture-repo/issues/19","repository":{"full_name":"fixture-owner/fixture-repo"}}"""
        val pullRequest = """{"number":20,"title":"Fixture pull request","state":"open","repository":{"full_name":"fixture-owner/fixture-repo"},"pull_request":{"url":"https://api.github.com/repos/fixture-owner/fixture-repo/pulls/20"}}"""
    }
}
