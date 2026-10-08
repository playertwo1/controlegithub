package com.playertwo.controlegithub

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.io.BufferedReader
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GitHubNotificationsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun forbiddenOAuthIsExplicitAndNeverRendersEmptyState() {
        NotificationsScreenApi(initialStatus = 403, notificationsJson = "{}").use { api ->
            var sessionExpired = false
            showNotifications(api, onSessionExpired = { sessionExpired = true })

            compose.waitUntil(10_000) {
                api.requests.isNotEmpty() &&
                    compose.onAllNodesWithText("O GitHub não aceitou o acesso OAuth às notificações.")
                        .fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithText("Nenhuma notificação.").assertDoesNotExist()
            assertEquals(1, api.requests.size)
            assertEquals(false, sessionExpired)
        }
    }

    @Test
    fun emptyStateAppearsOnlyAfterAValidSuccessfulResponse() {
        NotificationsScreenApi(notificationsJson = "[]").use { api ->
            showNotifications(api)
            compose.waitUntil(10_000) {
                api.requests.isNotEmpty() &&
                    compose.onAllNodesWithText("Nenhuma notificação.").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(api.requests.single().startsWith("GET /notifications?all=true&per_page=50"))
        }
    }

    @Test
    fun opensOriginAndBackReturnsToNotificationAndPreservesFilters() {
        NotificationsScreenApi(notificationsJson = NOTIFICATION).use { api ->
            showNotifications(api)
            compose.waitUntil(10_000) {
                api.requests.isNotEmpty() &&
                    compose.onAllNodesWithText("Fixture notification").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Não lidas").performClick()
            compose.onNodeWithText("Issue").performClick()
            compose.onNodeWithText("Não lidas").assertIsSelected()
            compose.onNodeWithText("Issue").assertIsSelected()
            compose.onNodeWithText("Fixture notification").performClick()
            compose.onNodeWithText("Abrir item de origem").performClick()

            compose.waitUntil(10_000) {
                api.requests.any { it.startsWith("GET /repos/fixture-owner/fixture-repo/issues/18 ") } &&
                    compose.onAllNodesWithText("Fixture actual issue").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Voltar às issues").performClick()
            compose.onNodeWithText("Abrir item de origem").assertIsDisplayed()
            compose.onNodeWithContentDescription("Voltar aos avisos").performClick()

            compose.onNodeWithText("Fixture notification").assertIsDisplayed()
            compose.onNodeWithText("Não lidas").assertIsSelected()
            compose.onNodeWithText("Issue").assertIsSelected()
            assertTrue(api.requests.any { it.startsWith("GET /repos/fixture-owner/fixture-repo/issues/18/comments") })
        }
    }

    private fun showNotifications(
        api: NotificationsScreenApi,
        session: GitHubSession = notificationFixtureSession(),
        onSessionExpired: (GitHubSession) -> Unit = {}
    ) {
        compose.setContent {
            ControleTheme(AppThemeMode.LIGHT) {
                GitHubNotificationsScreen(
                    modifier = Modifier,
                    client = GitHubHttpClient(api.baseUri),
                    session = session,
                    sessionRestoring = false,
                    onSessionExpired = onSessionExpired,
                    onAppearance = {}
                )
            }
        }
    }

    private companion object {
        const val NOTIFICATION = """
            [{"id":"thread-1","unread":true,"reason":"assign","updated_at":"2026-10-01T12:00:00Z",
              "subject":{"title":"Fixture notification","type":"Issue","url":"https://api.github.com/repos/fixture-owner/fixture-repo/issues/18"},
              "repository":{"id":1,"full_name":"fixture-owner/fixture-repo"}}]
        """
    }
}

private fun notificationFixtureSession(login: String = "fixture-user") = GitHubSession(
    GitHubUser(login, "https://github.com/$login"),
    SessionCredentials("notification-fixture-token")
)

private class NotificationsScreenApi(
    private val initialStatus: Int = 200,
    private val notificationsJson: String,
    private val originResponses: Map<String, String> = emptyMap()
) : AutoCloseable {
    private val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    val baseUri = java.net.URI.create("http://127.0.0.1:${server.localPort}/")
    val requests = CopyOnWriteArrayList<String>()
    val authorizations = CopyOnWriteArrayList<String?>()
    private val worker = Thread {
        while (!server.isClosed) runCatching {
            server.accept().use { socket ->
                val input = BufferedReader(socket.getInputStream().reader(StandardCharsets.US_ASCII))
                val request = input.readLine().orEmpty()
                requests += request
                val headers = buildList {
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        add(line)
                    }
                }
                authorizations += headers.firstOrNull { it.startsWith("Authorization:", ignoreCase = true) }
                    ?.substringAfter(':')?.trim()
                val path = request.substringAfter("GET ").substringBefore(" HTTP")
                val status = if (path.startsWith("/notifications")) initialStatus else 200
                val body = if (path.startsWith("/notifications")) notificationsJson else
                    originResponses[path] ?: when {
                        path.matches(Regex("/repos/[^/]+/[^/]+/issues/[0-9]+")) -> ISSUE_DETAIL
                        path.contains("/comments?") -> "[]"
                        path.startsWith("/repos/") -> REPOSITORY_DETAIL
                        else -> "{}"
                    }
                val statusText = when (status) {
                    200 -> "OK"
                    401 -> "Unauthorized"
                    403 -> "Forbidden"
                    429 -> "Too Many Requests"
                    else -> "Server Error"
                }
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                socket.getOutputStream().apply {
                    write("HTTP/1.1 $status $statusText\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
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
        const val ISSUE_DETAIL = """{"number":18,"title":"Fixture actual issue","state":"open","html_url":"https://github.com/fixture-owner/fixture-repo/issues/18","body":"Fixture body","repository":{"full_name":"fixture-owner/fixture-repo"}}"""
        const val REPOSITORY_DETAIL = """{"id":1,"name":"fixture-repo","full_name":"fixture-owner/fixture-repo","owner":{"login":"fixture-owner"},"private":false,"stargazers_count":0,"forks_count":0,"open_issues_count":0,"default_branch":"main","visibility":"public"}"""
    }
}
