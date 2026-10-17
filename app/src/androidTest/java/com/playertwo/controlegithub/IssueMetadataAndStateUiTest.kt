package com.playertwo.controlegithub

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class IssueMetadataAndStateUiTest {
    @get:Rule
    val compose = createComposeRule()

    private fun fixtureSession() = GitHubSession(
        GitHubUser("fixture-user", "https://github.com/fixture-user"),
        SessionCredentials("fixture-token")
    )

    private fun fixtureIssue(state: GitHubIssueState = GitHubIssueState.OPEN) = GitHubIssue(
        number = 18,
        title = "Fixture issue one",
        state = state,
        repository = "fixture-owner/fixture-repo",
        author = "fixture-author",
        assignees = listOf("dev1"),
        labels = listOf("bug"),
        updatedAt = null
    )

    @Test
    fun closeIssueSuccessSendsPatchAndUpdatesUiToClosed() {
        StateMockServer().use { server ->
            val client = server.client()

            compose.setContent {
                GitHubIssueDetailScreen(
                    modifier = Modifier,
                    client = client,
                    session = fixtureSession(),
                    issue = fixtureIssue(GitHubIssueState.OPEN),
                    onBack = {},
                    onSessionExpired = {},
                    onAppearance = {},
                    isOffline = false
                )
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("issue_close_button").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("issue_close_button").performClick()

            compose.waitUntil(10_000) {
                server.requests.any { it.firstLine.startsWith("PATCH") } &&
                    compose.onAllNodesWithTag("issue_reopen_button").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithText("fixture-owner/fixture-repo · #18 · Fechada").assertIsDisplayed()

            val patch = server.requests.first { it.firstLine.startsWith("PATCH") }
            assertTrue(patch.body.contains("\"state\":\"closed\""))
            assertEquals("Bearer fixture-token", patch.authHeader)
        }
    }

    @Test
    fun reopenIssueSuccessSendsPatchAndUpdatesUiToOpen() {
        StateMockServer(initialState = GitHubIssueState.CLOSED).use { server ->
            val client = server.client()

            compose.setContent {
                GitHubIssueDetailScreen(
                    modifier = Modifier,
                    client = client,
                    session = fixtureSession(),
                    issue = fixtureIssue(GitHubIssueState.CLOSED),
                    onBack = {},
                    onSessionExpired = {},
                    onAppearance = {},
                    isOffline = false
                )
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("issue_reopen_button").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("issue_reopen_button").performClick()

            compose.waitUntil(10_000) {
                server.requests.any { it.firstLine.startsWith("PATCH") } &&
                    compose.onAllNodesWithTag("issue_close_button").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithText("fixture-owner/fixture-repo · #18 · Aberta").assertIsDisplayed()

            val patch = server.requests.first { it.firstLine.startsWith("PATCH") }
            assertTrue(patch.body.contains("\"state\":\"open\""))
        }
    }

    @Test
    fun editIssueMetadataUpdatesLabelsAndAssignees() {
        StateMockServer().use { server ->
            val client = server.client()

            compose.setContent {
                GitHubIssueDetailScreen(
                    modifier = Modifier,
                    client = client,
                    session = fixtureSession(),
                    issue = fixtureIssue(),
                    onBack = {},
                    onSessionExpired = {},
                    onAppearance = {},
                    isOffline = false
                )
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("issue_edit_button").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("issue_edit_button").performClick()

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("issue_edit_labels_input").fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithTag("issue_edit_assignees_input").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("issue_edit_labels_input").performTextClearance()
            compose.onNodeWithTag("issue_edit_labels_input").performTextInput("enhancement, ui")
            compose.onNodeWithTag("issue_edit_assignees_input").performTextClearance()
            compose.onNodeWithTag("issue_edit_assignees_input").performTextInput("alice, bob")

            compose.onNodeWithTag("issue_edit_save_button").performClick()

            compose.waitUntil(10_000) {
                server.requests.any { it.firstLine.startsWith("PATCH") } &&
                    compose.onAllNodesWithTag("issue_assignees_text").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("issue_assignees_text").assertTextContains("Responsáveis: @alice, @bob")
            compose.onNodeWithTag("issue_labels_text").assertTextContains("Rótulos: enhancement · ui")

            val patch = server.requests.first { it.firstLine.startsWith("PATCH") }
            assertTrue(patch.body.contains("\"labels\":[\"enhancement\",\"ui\"]"))
            assertTrue(patch.body.contains("\"assignees\":[\"alice\",\"bob\"]"))
        }
    }

    @Test
    fun permissionForbidden403DisplaysErrorAndPreservesState() {
        StateMockServer(patchStatus = 403).use { server ->
            val client = server.client()

            compose.setContent {
                GitHubIssueDetailScreen(
                    modifier = Modifier,
                    client = client,
                    session = fixtureSession(),
                    issue = fixtureIssue(GitHubIssueState.OPEN),
                    onBack = {},
                    onSessionExpired = {},
                    onAppearance = {},
                    isOffline = false
                )
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("issue_close_button").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("issue_close_button").performClick()

            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Você não tem permissão para alterar o estado desta issue.").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("issue_close_button").assertIsDisplayed()
            compose.onNodeWithText("fixture-owner/fixture-repo · #18 · Aberta").assertIsDisplayed()
        }
    }

    @Test
    fun mutationGuardAntiDoubleTapDisablesButtonDuringStateChange() {
        StateMockServer(patchDelayMillis = 1_500).use { server ->
            val client = server.client()

            compose.setContent {
                GitHubIssueDetailScreen(
                    modifier = Modifier,
                    client = client,
                    session = fixtureSession(),
                    issue = fixtureIssue(GitHubIssueState.OPEN),
                    onBack = {},
                    onSessionExpired = {},
                    onAppearance = {},
                    isOffline = false
                )
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("issue_close_button").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("issue_close_button").performClick()

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("issue_state_progress").fetchSemanticsNodes().isNotEmpty() ||
                    !compose.onNodeWithTag("issue_close_button").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            }

            compose.waitUntil(10_000) {
                server.requests.any { it.firstLine.startsWith("PATCH") } &&
                    compose.onAllNodesWithTag("issue_reopen_button").fetchSemanticsNodes().isNotEmpty()
            }

            assertEquals(1, server.requests.count { it.firstLine.startsWith("PATCH") })
        }
    }

    @Test
    fun mutationGuardOfflineHidesMutationButtons() {
        StateMockServer().use { server ->
            val client = server.client()

            compose.setContent {
                GitHubIssueDetailScreen(
                    modifier = Modifier,
                    client = client,
                    session = fixtureSession(),
                    issue = fixtureIssue(GitHubIssueState.OPEN),
                    onBack = {},
                    onSessionExpired = {},
                    onAppearance = {},
                    isOffline = true
                )
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Modo offline: Conexão necessária para comentar ou editar issues.").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onAllNodesWithTag("issue_close_button").assertCountEquals(0)
            compose.onAllNodesWithTag("issue_reopen_button").assertCountEquals(0)
            compose.onAllNodesWithTag("issue_edit_button").assertCountEquals(0)
            assertEquals(0, server.requests.count { it.firstLine.startsWith("PATCH") })
        }
    }

    private data class RecordedRequest(
        val firstLine: String,
        val authHeader: String?,
        val body: String
    )

    private class StateMockServer(
        private val patchStatus: Int = 200,
        private val patchDelayMillis: Long = 0,
        private val initialState: GitHubIssueState = GitHubIssueState.OPEN
    ) : AutoCloseable {
        private val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        private var currentState = initialState
        private var currentLabels = listOf("bug")
        private var currentAssignees = listOf("dev1")
        private var currentTitle = "Fixture issue one"
        private var currentBody = "Descricao inicial da issue"

        private val worker = Thread {
            while (!server.isClosed) runCatching {
                server.accept().use { socket ->
                    val input = socket.getInputStream()
                    val firstLine = readAsciiLine(input)
                    if (firstLine.isEmpty()) return@use

                    var authHeader: String? = null
                    var contentLength = 0

                    while (true) {
                        val header = readAsciiLine(input)
                        if (header.isEmpty()) break
                        if (header.startsWith("Authorization:", ignoreCase = true)) {
                            authHeader = header.substringAfter(':').trim()
                        } else if (header.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = header.substringAfter(':').trim().toIntOrNull() ?: 0
                        }
                    }

                    val bodyBytes = ByteArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val count = input.read(bodyBytes, read, contentLength - read)
                        if (count == -1) break
                        read += count
                    }
                    val body = String(bodyBytes, 0, read, StandardCharsets.UTF_8)

                    requests += RecordedRequest(firstLine, authHeader, body)

                    val responseBody: String
                    val status: Int
                    val statusText: String

                    when {
                        firstLine.startsWith("GET /repos/fixture-owner/fixture-repo/issues/18/comments") -> {
                            status = 200
                            statusText = "OK"
                            responseBody = "[]"
                        }
                        firstLine.startsWith("GET /repos/fixture-owner/fixture-repo/issues/18") -> {
                            status = 200
                            statusText = "OK"
                            responseBody = buildIssueDetailJson()
                        }
                        firstLine.startsWith("PATCH /repos/fixture-owner/fixture-repo/issues/18") -> {
                            if (patchDelayMillis > 0) Thread.sleep(patchDelayMillis)
                            status = patchStatus
                            statusText = if (patchStatus == 200) "OK" else "Forbidden"
                            if (patchStatus == 200) {
                                if (body.contains("\"state\":\"closed\"")) currentState = GitHubIssueState.CLOSED
                                if (body.contains("\"state\":\"open\"")) currentState = GitHubIssueState.OPEN
                                if (body.contains("\"labels\":[")) {
                                    val labelsJson = body.substringAfter("\"labels\":[").substringBefore("]")
                                    currentLabels = labelsJson.split(',')
                                        .map { it.trim().removeSurrounding("\"") }
                                        .filter(String::isNotBlank)
                                }
                                if (body.contains("\"assignees\":[")) {
                                    val assigneesJson = body.substringAfter("\"assignees\":[").substringBefore("]")
                                    currentAssignees = assigneesJson.split(',')
                                        .map { it.trim().removeSurrounding("\"") }
                                        .filter(String::isNotBlank)
                                }
                                responseBody = buildIssueDetailJson()
                            } else {
                                responseBody = """{"message":"Forbidden"}"""
                            }
                        }
                        else -> {
                            status = 404
                            statusText = "Not Found"
                            responseBody = "{}"
                        }
                    }

                    val bytes = responseBody.toByteArray(StandardCharsets.UTF_8)
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 $status $statusText\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                        write(bytes)
                        flush()
                    }
                }
            }
        }.apply { isDaemon = true; start() }

        private fun buildIssueDetailJson(): String {
            val stateStr = if (currentState == GitHubIssueState.OPEN) "open" else "closed"
            val labelsJson = currentLabels.joinToString(",") { """{"name":"$it"}""" }
            val assigneesJson = currentAssignees.joinToString(",") { """{"login":"$it"}""" }
            return """{
                "number": 18,
                "title": "$currentTitle",
                "state": "$stateStr",
                "html_url": "https://github.com/fixture-owner/fixture-repo/issues/18",
                "body": "$currentBody",
                "user": {"login": "fixture-author"},
                "assignees": [$assigneesJson],
                "labels": [$labelsJson],
                "updated_at": "2026-10-17T20:00:00Z",
                "repository": {"full_name": "fixture-owner/fixture-repo"}
            }""".trimIndent()
        }

        fun client() = GitHubHttpClient(
            baseUrl = URI.create("http://127.0.0.1:${server.localPort}/"),
            connectTimeoutMillis = 5_000,
            readTimeoutMillis = 5_000
        )

        override fun close() {
            server.close()
            worker.join(1_000)
        }
    }
}

private fun readAsciiLine(input: InputStream): String {
    val out = ByteArrayOutputStream()
    while (true) {
        val b = input.read()
        if (b == -1 || b == '\n'.code) break
        if (b != '\r'.code) out.write(b)
    }
    return out.toString(StandardCharsets.US_ASCII.name())
}
