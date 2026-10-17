package com.playertwo.controlegithub

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
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

class EditAndCommentIssueUiTest {
    @get:Rule
    val compose = createComposeRule()

    private fun fixtureSession() = GitHubSession(
        GitHubUser("fixture-user", "https://github.com/fixture-user"),
        SessionCredentials("fixture-token")
    )

    private fun fixtureInitialIssue() = GitHubIssue(
        number = 18,
        title = "Fixture issue one",
        state = GitHubIssueState.OPEN,
        repository = "fixture-owner/fixture-repo",
        author = "fixture-author",
        assignees = emptyList(),
        labels = listOf("bug"),
        updatedAt = null
    )

    @Test
    fun commentSuccessSubmitsPostAppendsToUiAndClearsInput() {
        EditCommentMockServer().use { server ->
            val client = server.client()

            compose.setContent {
                GitHubIssueDetailScreen(
                    modifier = Modifier,
                    client = client,
                    session = fixtureSession(),
                    issue = fixtureInitialIssue(),
                    onBack = {},
                    onSessionExpired = {},
                    onAppearance = {},
                    isOffline = false
                )
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Fixture issue one").fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithTag("issue_comment_input").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("issue_comment_input").performTextInput("Comentario teste 123")
            compose.onNodeWithTag("issue_comment_submit_button").assertIsEnabled()
            compose.onNodeWithTag("issue_comment_submit_button").performClick()

            compose.waitUntil(10_000) {
                server.requests.any { it.firstLine.startsWith("POST") } &&
                    compose.onAllNodesWithText("Comentario teste 123").fetchSemanticsNodes().isNotEmpty()
            }

            // Input is cleared
            compose.onNodeWithTag("issue_comment_input")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))

            val postReq = server.requests.first { it.firstLine.startsWith("POST") }
            assertTrue(postReq.firstLine.startsWith("POST /repos/fixture-owner/fixture-repo/issues/18/comments"))
            assertTrue(postReq.body.contains("\"body\":\"Comentario teste 123\""))
            assertEquals("Bearer fixture-token", postReq.authHeader)
        }
    }

    @Test
    fun commentAntiDoubleTapDisablesButtonDuringSubmission() {
        EditCommentMockServer(postDelayMillis = 1_500).use { server ->
            val client = server.client()

            compose.setContent {
                GitHubIssueDetailScreen(
                    modifier = Modifier,
                    client = client,
                    session = fixtureSession(),
                    issue = fixtureInitialIssue(),
                    onBack = {},
                    onSessionExpired = {},
                    onAppearance = {},
                    isOffline = false
                )
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("issue_comment_input").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("issue_comment_input").performTextInput("Comentario lento")
            compose.onNodeWithTag("issue_comment_submit_button").performClick()

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("issue_comment_progress").fetchSemanticsNodes().isNotEmpty() ||
                    !compose.onNodeWithTag("issue_comment_submit_button").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            }

            compose.waitUntil(10_000) {
                server.requests.any { it.firstLine.startsWith("POST") } &&
                    compose.onAllNodesWithText("Comentario lento").fetchSemanticsNodes().isNotEmpty()
            }

            assertEquals(1, server.requests.count { it.firstLine.startsWith("POST") })
        }
    }

    @Test
    fun commentFailurePreservesDraftTextAndDisplaysError() {
        EditCommentMockServer(postStatus = 403).use { server ->
            val client = server.client()

            compose.setContent {
                GitHubIssueDetailScreen(
                    modifier = Modifier,
                    client = client,
                    session = fixtureSession(),
                    issue = fixtureInitialIssue(),
                    onBack = {},
                    onSessionExpired = {},
                    onAppearance = {},
                    isOffline = false
                )
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("issue_comment_input").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("issue_comment_input").performTextInput("Rascunho importante que nao pode perder")
            compose.onNodeWithTag("issue_comment_submit_button").performClick()

            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Você não tem permissão para comentar nesta issue.").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("issue_comment_input")
                .assertTextContains("Rascunho importante que nao pode perder")
        }
    }

    @Test
    fun editIssueSuccessSubmitsPatchAndUpdatesHeader() {
        EditCommentMockServer().use { server ->
            val client = server.client()

            compose.setContent {
                GitHubIssueDetailScreen(
                    modifier = Modifier,
                    client = client,
                    session = fixtureSession(),
                    issue = fixtureInitialIssue(),
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
                compose.onAllNodesWithTag("issue_edit_title_input").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("issue_edit_title_input").performTextClearance()
            compose.onNodeWithTag("issue_edit_title_input").performTextInput("Titulo Atualizado 42")
            compose.onNodeWithTag("issue_edit_body_input").performTextClearance()
            compose.onNodeWithTag("issue_edit_body_input").performTextInput("Descricao Atualizada 42")

            compose.onNodeWithTag("issue_edit_save_button").performClick()

            compose.waitUntil(10_000) {
                server.requests.any { it.firstLine.startsWith("PATCH") } &&
                    compose.onAllNodesWithText("Titulo Atualizado 42").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onAllNodesWithTag("issue_edit_title_input").assertCountEquals(0)

            val patchReq = server.requests.first { it.firstLine.startsWith("PATCH") }
            assertTrue(patchReq.firstLine.startsWith("PATCH /repos/fixture-owner/fixture-repo/issues/18"))
            assertTrue(patchReq.body.contains("\"title\":\"Titulo Atualizado 42\""))
            assertTrue(patchReq.body.contains("\"body\":\"Descricao Atualizada 42\""))
            assertEquals("Bearer fixture-token", patchReq.authHeader)
        }
    }

    @Test
    fun editIssueFailurePreservesDraftAndDisplaysError() {
        EditCommentMockServer(patchStatus = 403).use { server ->
            val client = server.client()

            compose.setContent {
                GitHubIssueDetailScreen(
                    modifier = Modifier,
                    client = client,
                    session = fixtureSession(),
                    issue = fixtureInitialIssue(),
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
                compose.onAllNodesWithTag("issue_edit_title_input").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("issue_edit_title_input").performTextClearance()
            compose.onNodeWithTag("issue_edit_title_input").performTextInput("Titulo Falha 403")

            compose.onNodeWithTag("issue_edit_save_button").performClick()

            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Você não tem permissão para editar esta issue.").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("issue_edit_title_input")
                .assertTextContains("Titulo Falha 403")
        }
    }

    @Test
    fun mutationGuardEmptyValidation() {
        EditCommentMockServer().use { server ->
            val client = server.client()

            compose.setContent {
                GitHubIssueDetailScreen(
                    modifier = Modifier,
                    client = client,
                    session = fixtureSession(),
                    issue = fixtureInitialIssue(),
                    onBack = {},
                    onSessionExpired = {},
                    onAppearance = {},
                    isOffline = false
                )
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("issue_comment_submit_button").fetchSemanticsNodes().isNotEmpty()
            }

            // Click comment with blank text
            compose.onNodeWithTag("issue_comment_submit_button").performClick()
            compose.onNodeWithText("O comentário não pode ficar em branco.").assertIsDisplayed()
            assertEquals(0, server.requests.count { it.firstLine.startsWith("POST") })

            // Open edit and click save with empty title
            compose.onNodeWithTag("issue_edit_button").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("issue_edit_title_input").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("issue_edit_title_input").performTextClearance()
            compose.onNodeWithTag("issue_edit_save_button").performClick()
            compose.onNodeWithText("O título da issue não pode ficar em branco.").assertIsDisplayed()
            assertEquals(0, server.requests.count { it.firstLine.startsWith("PATCH") })
        }
    }

    @Test
    fun mutationGuardOfflineBlocked() {
        EditCommentMockServer().use { server ->
            val client = server.client()

            compose.setContent {
                GitHubIssueDetailScreen(
                    modifier = Modifier,
                    client = client,
                    session = fixtureSession(),
                    issue = fixtureInitialIssue(),
                    onBack = {},
                    onSessionExpired = {},
                    onAppearance = {},
                    isOffline = true
                )
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Modo offline: Conexão necessária para comentar ou editar issues.").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onAllNodesWithTag("issue_edit_button").assertCountEquals(0)
            compose.onAllNodesWithTag("issue_comment_input").assertCountEquals(0)
            compose.onAllNodesWithTag("issue_comment_submit_button").assertCountEquals(0)
            assertEquals(0, server.requests.count { it.firstLine.startsWith("POST") || it.firstLine.startsWith("PATCH") })
        }
    }

    private data class RecordedRequest(
        val firstLine: String,
        val authHeader: String?,
        val body: String
    )

    private class EditCommentMockServer(
        private val postStatus: Int = 201,
        private val patchStatus: Int = 200,
        private val postDelayMillis: Long = 0
    ) : AutoCloseable {
        private val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
        val requests = CopyOnWriteArrayList<RecordedRequest>()

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
                            responseBody = """{
                                "number": 18,
                                "title": "Fixture issue one",
                                "state": "open",
                                "html_url": "https://github.com/fixture-owner/fixture-repo/issues/18",
                                "body": "Descricao inicial da issue",
                                "user": {"login": "fixture-author"},
                                "labels": [{"name": "bug"}],
                                "repository": {"full_name": "fixture-owner/fixture-repo"}
                            }""".trimIndent()
                        }
                        firstLine.startsWith("POST /repos/fixture-owner/fixture-repo/issues/18/comments") -> {
                            if (postDelayMillis > 0) Thread.sleep(postDelayMillis)
                            status = postStatus
                            statusText = if (postStatus == 201) "Created" else "Forbidden"
                            responseBody = if (postStatus == 201) {
                                val extractedBody = if (body.contains("\"body\":\"")) {
                                    body.substringAfter("\"body\":\"").substringBefore("\"")
                                } else "Comentario padrao"
                                """{
                                    "id": 999,
                                    "body": "$extractedBody",
                                    "user": {"login": "fixture-user"},
                                    "created_at": "2026-10-17T19:30:00Z"
                                }""".trimIndent()
                            } else {
                                """{"message": "Forbidden"}"""
                            }
                        }
                        firstLine.startsWith("PATCH /repos/fixture-owner/fixture-repo/issues/18") -> {
                            status = patchStatus
                            statusText = if (patchStatus == 200) "OK" else "Forbidden"
                            responseBody = if (patchStatus == 200) {
                                val extractedTitle = if (body.contains("\"title\":\"")) {
                                    body.substringAfter("\"title\":\"").substringBefore("\"")
                                } else "Titulo Atualizado"
                                val extractedIssueBody = if (body.contains("\"body\":\"")) {
                                    body.substringAfter("\"body\":\"").substringBefore("\"")
                                } else "Descricao Atualizada"
                                """{
                                    "number": 18,
                                    "title": "$extractedTitle",
                                    "body": "$extractedIssueBody",
                                    "state": "open",
                                    "html_url": "https://github.com/fixture-owner/fixture-repo/issues/18",
                                    "user": {"login": "fixture-author"},
                                    "labels": [{"name": "bug"}],
                                    "updated_at": "2026-10-17T19:40:00Z"
                                }""".trimIndent()
                            } else {
                                """{"message": "Forbidden"}"""
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
