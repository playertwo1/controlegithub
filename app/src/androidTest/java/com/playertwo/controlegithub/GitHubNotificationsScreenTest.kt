package com.playertwo.controlegithub

import androidx.compose.runtime.mutableStateOf
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

class GitHubNotificationsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun validEmptyResponseShowsEmptyState() {
        NotificationsScreenApi(notificationsJson = "[]").use { api ->
            showNotifications(api)

            compose.waitUntil(10_000) { api.requests.size == 1 && compose.onAllNodesWithText("Nenhuma notificação.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Nenhuma notificação.").assertIsDisplayed()
            compose.onNodeWithText("O GitHub não aceitou o acesso OAuth às notificações.").assertDoesNotExist()
        }
    }

    @Test fun forbiddenOAuthIsExplicitAndNeverRendersEmptyState() {
        NotificationsScreenApi(initialStatus = 403, notificationsJson = "[]").use { api ->
            showNotifications(api)

            compose.waitUntil(10_000) {
                api.requests.size == 1 &&
                    compose.onAllNodesWithText("O GitHub não aceitou o acesso OAuth às notificações.").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("O GitHub não aceitou o acesso OAuth às notificações.").assertIsDisplayed()
            compose.onNodeWithText("Nenhuma notificação.").assertDoesNotExist()
        }
    }

    @Test fun filtersCombineStateAndUnknownType() {
        val fixtures = fixtureThreads(
            fixtureNotificationJson(id = "1", type = "Issue", title = "Fixture issue", unread = true),
            fixtureNotificationJson(id = "2", type = "PullRequest", url = "https://api.github.com/repos/acme/app/pulls/13", title = "Fixture pull request", unread = false),
            fixtureNotificationJson(id = "3", type = "FutureType", title = "Fixture unknown", unread = true)
        )
        NotificationsScreenApi(notificationsJson = fixtures).use { api ->
            showNotifications(api)
            compose.waitUntil(10_000) { api.requests.size == 1 && compose.onAllNodesWithText("Fixture issue").fetchSemanticsNodes().isNotEmpty() }

            compose.onNodeWithText("Não lidas").performClick()
            compose.onNodeWithText("Fixture pull request").assertDoesNotExist()
            compose.onNodeWithText("Fixture issue").assertIsDisplayed()

            compose.onNodeWithText("Outro").performClick()
            compose.onNodeWithText("Fixture unknown").assertIsDisplayed()
            compose.onNodeWithText("Fixture issue").assertDoesNotExist()
            compose.onNodeWithText("Lidas").performClick()
            compose.onNodeWithText("Nenhuma notificação corresponde a estes filtros.").assertIsDisplayed()
        }
    }

    @Test fun opensIssueAndReturnsToFilteredListWithoutReloading() {
        val notifications = fixtureThreads(
            fixtureNotificationJson(id = "1", type = "Issue", title = "Fixture source issue", unread = true),
            fixtureNotificationJson(id = "2", type = "PullRequest", url = "https://api.github.com/repos/acme/app/pulls/13", title = "Fixture other", unread = false)
        )
        val issueResponse = """{"number":12,"title":"Issue from GitHub fixture","state":"open","html_url":"https://github.com/acme/app/issues/12","labels":[],"updated_at":"2026-10-01T12:00:00Z","body":"Fixture body","user":{"login":"fixture-author"}}"""
        NotificationsScreenApi(
            notificationsJson = notifications,
            originResponses = mapOf(
                "/repos/acme/app/issues/12" to issueResponse,
                "/repos/acme/app/issues/12/comments?per_page=50" to "[]"
            )
        ).use { api ->
            showNotifications(api)
            compose.waitUntil(10_000) { api.requests.size == 1 && compose.onAllNodesWithText("Fixture source issue").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Não lidas").performClick()
            compose.onNodeWithText("Issue").performClick()
            compose.onNodeWithText("Fixture source issue").performClick()
            compose.onNodeWithText("Abrir origem").performClick()

            compose.waitUntil(10_000) {
                api.requests.any { it.startsWith("GET /repos/acme/app/issues/12 HTTP") } &&
                    compose.onAllNodesWithText("Issue from GitHub fixture").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(1, api.requests.count { it.startsWith("GET /notifications?") })

            compose.onNodeWithContentDescription("Voltar às issues").performClick()
            compose.onNodeWithText("Detalhe da notificação").assertIsDisplayed()
            compose.onNodeWithContentDescription("Voltar às notificações").performClick()

            compose.onNodeWithText("Fixture source issue").assertIsDisplayed()
            compose.onNodeWithText("Não lidas").assertIsSelected()
            compose.onNodeWithText("Issue").assertIsSelected()
            assertEquals(1, api.requests.count { it.startsWith("GET /notifications?") })
        }
    }

    @Test fun unauthorizedCallsSharedSessionExpiryHandler() {
        NotificationsScreenApi(initialStatus = 401, notificationsJson = "[]").use { api ->
            val expiredCalls = AtomicInteger()
            showNotifications(api, onSessionExpired = { expiredCalls.incrementAndGet() })

            compose.waitUntil(10_000) { api.requests.size == 1 && expiredCalls.get() == 1 }
            compose.onNodeWithText("Sua sessão expirou. Conecte-se novamente.").assertIsDisplayed()
            compose.onNodeWithText("Nenhuma notificação.").assertDoesNotExist()
        }
    }

    @Test fun serverFailureCanRetry() {
        NotificationsScreenApi(initialStatus = 503, notificationsJson = fixtureThreads(fixtureNotificationJson(title = "Fixture after retry"))).use { api ->
            showNotifications(api)
            compose.waitUntil(10_000) {
                api.requests.size == 1 && compose.onAllNodesWithText("O GitHub está com instabilidade. Tente novamente mais tarde.").fetchSemanticsNodes().isNotEmpty()
            }

            api.responseStatus = 200
            compose.onNodeWithText("Tentar novamente").performClick()
            compose.waitUntil(10_000) { api.requests.size == 2 && compose.onAllNodesWithText("Fixture after retry").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test fun logoutAndAccountSwitchDiscardPriorAccountRows() {
        NotificationsScreenApi(notificationsJson = fixtureThreads(fixtureNotificationJson(title = "Fixture first account"))).use { api ->
            val session = mutableStateOf<GitHubSession?>(fixtureSession("first-account"))
            compose.setContent {
                ControleTheme(AppThemeMode.LIGHT) {
                    GitHubNotificationsScreen(
                        modifier = androidx.compose.ui.Modifier,
                        client = GitHubHttpClient(api.baseUri),
                        session = session.value,
                        sessionRestoring = false,
                        onSessionExpired = {},
                        onAppearance = {}
                    )
                }
            }
            compose.waitUntil(10_000) { api.requests.size == 1 && compose.onAllNodesWithText("Fixture first account").fetchSemanticsNodes().isNotEmpty() }

            api.notificationsJson = fixtureThreads(fixtureNotificationJson(id = "2", title = "Fixture second account"))
            compose.runOnIdle { session.value = fixtureSession("second-account") }
            compose.waitUntil(10_000) { api.requests.size == 2 }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Fixture second account").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Fixture first account").assertDoesNotExist()

            compose.runOnIdle { session.value = null }
            compose.onNodeWithText("Conecte sua conta GitHub para consultar suas notificações.").assertIsDisplayed()
            compose.onNodeWithText("Fixture second account").assertDoesNotExist()
        }
    }

    private fun showNotifications(
        api: NotificationsScreenApi,
        session: GitHubSession = fixtureSession(),
        onSessionExpired: (GitHubSession) -> Unit = {}
    ) {
        compose.setContent {
            ControleTheme(AppThemeMode.LIGHT) {
                GitHubNotificationsScreen(
                    modifier = androidx.compose.ui.Modifier,
                    client = GitHubHttpClient(api.baseUri),
                    session = session,
                    sessionRestoring = false,
                    onSessionExpired = onSessionExpired,
                    onAppearance = {}
                )
            }
        }
    }
}

private fun fixtureSession(login: String = "fixture-user") = GitHubSession(
    GitHubUser(login, "https://github.com/$login"),
    SessionCredentials("fixture-token-$login")
)

internal class NotificationsScreenApi(
    initialStatus: Int = 200,
    notificationsJson: String,
    private val originResponses: Map<String, String> = emptyMap()
) : AutoCloseable {
    private val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    val baseUri: URI = URI.create("http://127.0.0.1:${server.localPort}/")
    val requests = CopyOnWriteArrayList<String>()
    @Volatile var responseStatus = initialStatus
    @Volatile var notificationsJson = notificationsJson
    private val worker = Thread {
        while (!server.isClosed) runCatching {
            server.accept().use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                val requestLine = reader.readLine().orEmpty()
                requests += requestLine
                while (reader.readLine()?.isNotEmpty() == true) Unit
                val path = requestLine.substringAfter("GET ").substringBefore(" HTTP")
                val isNotifications = path.startsWith("/notifications?")
                val body = if (isNotifications) this@NotificationsScreenApi.notificationsJson else originResponses[path] ?: "{}"
                val status = if (isNotifications) responseStatus else if (path in originResponses) 200 else 404
                val reason = if (status == 200) "OK" else "Error"
                val output = socket.getOutputStream()
                output.write("HTTP/1.1 $status $reason\r\n".toByteArray(StandardCharsets.US_ASCII))
                output.write("Content-Type: application/json\r\n".toByteArray(StandardCharsets.US_ASCII))
                output.write("Content-Length: ${body.toByteArray(StandardCharsets.UTF_8).size}\r\n".toByteArray(StandardCharsets.US_ASCII))
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
