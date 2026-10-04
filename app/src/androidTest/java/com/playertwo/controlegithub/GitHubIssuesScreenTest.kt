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

    private fun fixtureSession() = GitHubSession(
        GitHubUser("fixture-user", "https://github.com/fixture-user"),
        SessionCredentials("fixture-token")
    )
}

private class IssuesApi(initialStatus: Int = 200) : AutoCloseable {
    private val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    val baseUri = java.net.URI("http://127.0.0.1:${server.localPort}/")
    val requests = CopyOnWriteArrayList<String>()
    @Volatile var responseStatus = initialStatus
    @Volatile var dropNextResponse = false
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
                val body = when {
                    responseStatus != 200 -> "{}"
                    request.contains("state=closed") -> "[]"
                    request.contains("page=2") -> """[$issueOne,$issueTwo]"""
                    else -> """[$issueOne,$pullRequest]"""
                }
                val link = if (responseStatus == 200 && "page=2" !in request && "state=closed" !in request) {
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

    private companion object {
        val issueOne = """{"number":18,"title":"Fixture issue one","state":"open","html_url":"https://github.com/fixture-owner/fixture-repo/issues/18","repository":{"full_name":"fixture-owner/fixture-repo"},"user":{"login":"fixture-author"},"assignees":[{"login":"fixture-user"}],"labels":[{"name":"bug"}],"updated_at":"2026-10-04T12:00:00Z"}"""
        val issueTwo = """{"number":19,"title":"Fixture issue two","state":"open","html_url":"https://github.com/fixture-owner/fixture-repo/issues/19","repository":{"full_name":"fixture-owner/fixture-repo"}}"""
        val pullRequest = """{"number":20,"title":"Fixture pull request","state":"open","repository":{"full_name":"fixture-owner/fixture-repo"},"pull_request":{"url":"https://api.github.com/repos/fixture-owner/fixture-repo/pulls/20"}}"""
    }
}
