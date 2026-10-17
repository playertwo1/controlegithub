package com.playertwo.controlegithub

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.io.BufferedReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateIssueUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun createIssueSuccessSubmitsPostAndInvokesCallback() {
        CreateIssueMockServer(
            responseStatus = 201,
            responseBody = """
                {
                    "id": 101,
                    "number": 42,
                    "title": "Bug critico no app",
                    "state": "open",
                    "html_url": "https://github.com/acme/app/issues/42",
                    "user": {"login": "testuser"},
                    "body": "Passos para reproduzir o bug",
                    "created_at": "2026-10-17T19:00:00Z"
                }
            """.trimIndent()
        ).use { server ->
            val client = server.client()
            var createdIssue: GitHubIssue? = null

            compose.setContent {
                GitHubCreateIssueScreen(
                    modifier = Modifier,
                    client = client,
                    accessToken = "test-token",
                    repositoryFullName = "acme/app",
                    isOffline = false,
                    onIssueCreated = { createdIssue = it },
                    onCancel = {},
                    onSessionExpired = {}
                )
            }

            compose.onNodeWithTag("create_issue_title_input").performTextInput("Bug critico no app")
            compose.onNodeWithTag("create_issue_body_input").performTextInput("Passos para reproduzir o bug")

            compose.onNodeWithTag("create_issue_submit_button").assertIsEnabled()
            compose.onNodeWithTag("create_issue_submit_button").performClick()

            compose.waitUntil(10_000) {
                server.requests.isNotEmpty() && createdIssue != null
            }

            assertEquals(42, createdIssue?.number)
            assertEquals("Bug critico no app", createdIssue?.title)
            assertEquals("acme/app", createdIssue?.repository)

            val request = server.requests.single()
            assertTrue(request.firstLine.startsWith("POST /repos/acme/app/issues"))
            assertTrue(request.body.contains("\"title\":\"Bug critico no app\""))
            assertTrue(request.body.contains("\"body\":\"Passos para reproduzir o bug\""))
            assertEquals("Bearer test-token", request.authHeader)
        }
    }

    @Test
    fun createIssueAntiDoubleTapDisablesButtonDuringSubmission() {
        CreateIssueMockServer(
            responseStatus = 201,
            responseBody = """{"id":1,"number":1,"title":"T","state":"open","html_url":"https://github.com/acme/app/issues/1"}""",
            delayMillis = 1_500
        ).use { server ->
            val client = server.client()
            var createdIssue: GitHubIssue? = null

            compose.setContent {
                GitHubCreateIssueScreen(
                    modifier = Modifier,
                    client = client,
                    accessToken = "test-token",
                    repositoryFullName = "acme/app",
                    isOffline = false,
                    onIssueCreated = { createdIssue = it },
                    onCancel = {},
                    onSessionExpired = {}
                )
            }

            compose.onNodeWithTag("create_issue_title_input").performTextInput("Teste anti-duplo clique")
            compose.onNodeWithTag("create_issue_submit_button").performClick()

            // Button should immediately show progress indicator and become disabled
            compose.onNodeWithTag("create_issue_progress").assertIsDisplayed()
            compose.onNodeWithTag("create_issue_submit_button").assertIsNotEnabled()

            compose.waitUntil(10_000) {
                createdIssue != null
            }
            assertEquals(1, server.requests.size)
        }
    }

    @Test
    fun createIssueFailureDisplaysErrorAndPreservesDraft() {
        CreateIssueMockServer(
            responseStatus = 403,
            responseBody = """{"message":"Resource not accessible by integration"}"""
        ).use { server ->
            val client = server.client()

            compose.setContent {
                GitHubCreateIssueScreen(
                    modifier = Modifier,
                    client = client,
                    accessToken = "test-token",
                    repositoryFullName = "acme/app",
                    isOffline = false,
                    onIssueCreated = {},
                    onCancel = {},
                    onSessionExpired = {}
                )
            }

            compose.onNodeWithTag("create_issue_title_input").performTextInput("Título importante que não pode sumir")
            compose.onNodeWithTag("create_issue_body_input").performTextInput("Corpo detalhado do rascunho")

            compose.onNodeWithTag("create_issue_submit_button").performClick()

            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Você não tem permissão para abrir issues neste repositório.")
                    .fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithText("Você não tem permissão para abrir issues neste repositório.")
                .assertIsDisplayed()

            // Draft must be preserved intact!
            compose.onNodeWithTag("create_issue_title_input")
                .assertTextContains("Título importante que não pode sumir")
            compose.onNodeWithTag("create_issue_body_input")
                .assertTextContains("Corpo detalhado do rascunho")

            // User can try again without losing work
            compose.onNodeWithTag("create_issue_submit_button").assertIsEnabled()
        }
    }

    @Test
    fun createIssueEmptyTitleShowsValidationErrorWithoutNetworkCall() {
        CreateIssueMockServer(responseStatus = 200, responseBody = "{}").use { server ->
            val client = server.client()

            compose.setContent {
                GitHubCreateIssueScreen(
                    modifier = Modifier,
                    client = client,
                    accessToken = "test-token",
                    repositoryFullName = "acme/app",
                    isOffline = false,
                    onIssueCreated = {},
                    onCancel = {},
                    onSessionExpired = {}
                )
            }

            compose.onNodeWithTag("create_issue_submit_button").performClick()

            compose.onNodeWithText("O título da issue não pode ficar em branco.").assertIsDisplayed()
            assertEquals(0, server.requests.size)
        }
    }

    @Test
    fun createIssueOfflineModeDisablesSubmissionAndShowsBanner() {
        CreateIssueMockServer(responseStatus = 200, responseBody = "{}").use { server ->
            val client = server.client()

            compose.setContent {
                GitHubCreateIssueScreen(
                    modifier = Modifier,
                    client = client,
                    accessToken = "test-token",
                    repositoryFullName = "acme/app",
                    isOffline = true,
                    onIssueCreated = {},
                    onCancel = {},
                    onSessionExpired = {}
                )
            }

            compose.onNodeWithText("Modo offline: Conexão necessária para abrir issues. A submissão foi desabilitada.")
                .assertIsDisplayed()
            compose.onNodeWithTag("create_issue_submit_button").assertIsNotEnabled()
            compose.onNodeWithTag("create_issue_title_input").assertIsNotEnabled()
            compose.onNodeWithTag("create_issue_body_input").assertIsNotEnabled()
            assertEquals(0, server.requests.size)
        }
    }

    private data class RecordedRequest(
        val firstLine: String,
        val authHeader: String?,
        val body: String
    )

    private class CreateIssueMockServer(
        private val responseStatus: Int,
        private val responseBody: String,
        private val delayMillis: Long = 0
    ) : AutoCloseable {
        private val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
        val requests = CopyOnWriteArrayList<RecordedRequest>()

        private val worker = Thread {
            while (!server.isClosed) runCatching {
                server.accept().use { socket ->
                    if (delayMillis > 0) Thread.sleep(delayMillis)

                    val input = socket.getInputStream()
                    val firstLine = readAsciiLine(input)
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

                    val statusText = when (responseStatus) {
                        201 -> "Created"
                        403 -> "Forbidden"
                        404 -> "Not Found"
                        else -> "OK"
                    }
                    val bytes = responseBody.toByteArray(StandardCharsets.UTF_8)
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 $responseStatus $statusText\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
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

private fun readAsciiLine(input: java.io.InputStream): String {
    val out = java.io.ByteArrayOutputStream()
    while (true) {
        val b = input.read()
        if (b == -1 || b == '\n'.code) break
        if (b != '\r'.code) out.write(b)
    }
    return out.toString(StandardCharsets.US_ASCII.name())
}

