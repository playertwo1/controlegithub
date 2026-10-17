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

class SubmitPullRequestReviewUiTest {
    @get:Rule
    val compose = createComposeRule()

    private fun fixtureSession(login: String = "reviewer-user") = GitHubSession(
        GitHubUser(login, "https://github.com/$login"),
        SessionCredentials("fixture-token")
    )

    private fun fixturePullRequest(author: String = "pr-author") = GitHubPullRequest(
        number = 27,
        title = "Fixture PR Title",
        state = GitHubPullRequestState.OPEN,
        repository = "owner/project",
        author = author,
        labels = listOf("ready"),
        updatedAt = null
    )

    @Test
    fun reviewApproveSuccessSubmitsPostAppendsToUiAndClearsInput() {
        ReviewMockServer(reviewState = "APPROVED").use { server ->
            val client = server.client()

            compose.setContent {
                ControleTheme(AppThemeMode.LIGHT) {
                    GitHubPullRequestDetailScreen(
                        modifier = Modifier,
                        client = client,
                        session = fixtureSession("reviewer-user"),
                        pullRequest = fixturePullRequest("other-author"),
                        onBack = {},
                        onSessionExpired = {},
                        onAppearance = {},
                        isOffline = false
                    )
                }
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("pr_review_event_approve").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("pr_review_event_approve").performScrollTo().performClick()
            compose.onNodeWithTag("pr_review_input").performScrollTo().performTextInput("Código aprovado com louvor!")
            compose.onNodeWithTag("pr_review_submit_button").performScrollTo().assertIsEnabled()
            compose.onNodeWithTag("pr_review_submit_button").performClick()

            compose.waitUntil(10_000) {
                server.requests.any { it.firstLine.startsWith("POST") } &&
                    compose.onAllNodesWithText("Aprovou · @reviewer-user").fetchSemanticsNodes().isNotEmpty()
            }

            // Input is cleared
            compose.onNodeWithTag("pr_review_input")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))

            val postReq = server.requests.first { it.firstLine.startsWith("POST") }
            assertTrue(postReq.firstLine.startsWith("POST /repos/owner/project/pulls/27/reviews"))
            assertTrue(postReq.body.contains("\"event\":\"APPROVE\""))
            assertTrue(postReq.body.contains("\"body\":\"Código aprovado com louvor!\""))
            assertTrue(postReq.body.contains("\"commit_id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\""))
            assertEquals("Bearer fixture-token", postReq.authHeader)
        }
    }

    @Test
    fun reviewRequestChangesRequiresJustificationAndSubmitsWhenFilled() {
        ReviewMockServer(reviewState = "CHANGES_REQUESTED").use { server ->
            val client = server.client()

            compose.setContent {
                ControleTheme(AppThemeMode.LIGHT) {
                    GitHubPullRequestDetailScreen(
                        modifier = Modifier,
                        client = client,
                        session = fixtureSession("reviewer-user"),
                        pullRequest = fixturePullRequest("other-author"),
                        onBack = {},
                        onSessionExpired = {},
                        onAppearance = {},
                        isOffline = false
                    )
                }
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("pr_review_event_request_changes").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("pr_review_event_request_changes").performScrollTo().performClick()

            // Try submit with empty body
            compose.onNodeWithTag("pr_review_submit_button").performScrollTo().performClick()
            compose.onNodeWithText("É obrigatório justificar as alterações solicitadas.").assertIsDisplayed()
            assertEquals(0, server.requests.count { it.firstLine.startsWith("POST") })

            // Fill justification and submit
            compose.onNodeWithTag("pr_review_input").performScrollTo().performTextInput("Ajustar documentação do método")
            compose.onNodeWithTag("pr_review_submit_button").performScrollTo().performClick()

            compose.waitUntil(10_000) {
                server.requests.any { it.firstLine.startsWith("POST") } &&
                    compose.onAllNodesWithText("Solicitou alterações · @reviewer-user").fetchSemanticsNodes().isNotEmpty()
            }

            val postReq = server.requests.first { it.firstLine.startsWith("POST") }
            assertTrue(postReq.body.contains("\"event\":\"REQUEST_CHANGES\""))
            assertTrue(postReq.body.contains("\"body\":\"Ajustar documentação do método\""))
        }
    }

    @Test
    fun reviewSelfReviewForbiddenDisplaysExplanatoryMessage() {
        ReviewMockServer(postStatus = 422).use { server ->
            val client = server.client()

            compose.setContent {
                ControleTheme(AppThemeMode.LIGHT) {
                    GitHubPullRequestDetailScreen(
                        modifier = Modifier,
                        client = client,
                        session = fixtureSession("pr-author"),
                        pullRequest = fixturePullRequest("pr-author"),
                        onBack = {},
                        onSessionExpired = {},
                        onAppearance = {},
                        isOffline = false
                    )
                }
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("pr_review_author_warning").fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithTag("pr_review_input").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("pr_review_input").performScrollTo().performTextInput("Tentando aprovar meu proprio PR")
            compose.onNodeWithTag("pr_review_submit_button").performScrollTo().performClick()

            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Você não pode aprovar ou solicitar alterações no seu próprio pull request.").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("pr_review_input")
                .assertTextContains("Tentando aprovar meu proprio PR")
        }
    }

    @Test
    fun reviewAntiDoubleTapDisablesButtonDuringSubmission() {
        ReviewMockServer(postDelayMillis = 1_500).use { server ->
            val client = server.client()

            compose.setContent {
                ControleTheme(AppThemeMode.LIGHT) {
                    GitHubPullRequestDetailScreen(
                        modifier = Modifier,
                        client = client,
                        session = fixtureSession("reviewer-user"),
                        pullRequest = fixturePullRequest("other-author"),
                        onBack = {},
                        onSessionExpired = {},
                        onAppearance = {},
                        isOffline = false
                    )
                }
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("pr_review_input").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("pr_review_input").performScrollTo().performTextInput("Revisão com delay")
            compose.onNodeWithTag("pr_review_submit_button").performScrollTo().performClick()

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("pr_review_progress").fetchSemanticsNodes().isNotEmpty() ||
                    !compose.onNodeWithTag("pr_review_submit_button").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            }

            compose.waitUntil(10_000) {
                server.requests.any { it.firstLine.startsWith("POST") } &&
                    compose.onAllNodesWithText("Aprovou · @reviewer-user").fetchSemanticsNodes().isNotEmpty()
            }

            assertEquals(1, server.requests.count { it.firstLine.startsWith("POST") })
        }
    }

    @Test
    fun mutationGuardOfflineBlocked() {
        ReviewMockServer().use { server ->
            val client = server.client()

            compose.setContent {
                ControleTheme(AppThemeMode.LIGHT) {
                    GitHubPullRequestDetailScreen(
                        modifier = Modifier,
                        client = client,
                        session = fixtureSession(),
                        pullRequest = fixturePullRequest(),
                        onBack = {},
                        onSessionExpired = {},
                        onAppearance = {},
                        isOffline = true
                    )
                }
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("pr_review_offline_banner").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("pr_review_offline_banner").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Modo offline: Conexão necessária para enviar revisões de pull request.").assertIsDisplayed()
            compose.onAllNodesWithTag("pr_review_input").assertCountEquals(0)
            compose.onAllNodesWithTag("pr_review_submit_button").assertCountEquals(0)
            assertEquals(0, server.requests.count { it.firstLine.startsWith("POST") })
        }
    }

    private data class RecordedRequest(
        val firstLine: String,
        val authHeader: String?,
        val body: String
    )

    private class ReviewMockServer(
        private val postStatus: Int = 200,
        private val reviewState: String = "APPROVED",
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
                        firstLine.startsWith("GET /repos/owner/project/pulls/27/reviews") -> {
                            status = 200
                            statusText = "OK"
                            responseBody = "[]"
                        }
                        firstLine.startsWith("GET /repos/owner/project/issues/27/comments") -> {
                            status = 200
                            statusText = "OK"
                            responseBody = "[]"
                        }
                        firstLine.startsWith("GET /repos/owner/project/pulls/27/files") -> {
                            status = 200
                            statusText = "OK"
                            responseBody = "[]"
                        }
                        firstLine.startsWith("GET /repos/owner/project/commits/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/check-runs") -> {
                            status = 200
                            statusText = "OK"
                            responseBody = """{"total_count":0,"check_runs":[]}"""
                        }
                        firstLine.startsWith("GET /repos/owner/project/commits/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/status") -> {
                            status = 200
                            statusText = "OK"
                            responseBody = """{"state":"success","sha":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","statuses":[]}"""
                        }
                        firstLine.startsWith("GET /repos/owner/project/pulls/27") -> {
                            status = 200
                            statusText = "OK"
                            responseBody = """{
                                "number": 27,
                                "title": "Fixture PR Title",
                                "state": "open",
                                "merged_at": null,
                                "body": "Fixture PR Description",
                                "user": {"login": "pr-author"},
                                "labels": [{"name": "ready"}],
                                "head": {"ref": "feature", "sha": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "repo": {"full_name": "owner/project"}},
                                "base": {"ref": "main", "repo": {"full_name": "owner/project"}}
                            }""".trimIndent()
                        }
                        firstLine.startsWith("POST /repos/owner/project/pulls/27/reviews") -> {
                            if (postDelayMillis > 0) Thread.sleep(postDelayMillis)
                            status = postStatus
                            statusText = if (postStatus == 200) "OK" else "Unprocessable Entity"
                            responseBody = if (postStatus == 200) {
                                val extractedBody = if (body.contains("\"body\":\"")) {
                                    body.substringAfter("\"body\":\"").substringBefore("\"")
                                } else "Parecer padrao"
                                """{
                                    "id": 7766,
                                    "state": "$reviewState",
                                    "body": "$extractedBody",
                                    "user": {"login": "reviewer-user"},
                                    "submitted_at": "2026-10-17T20:42:00Z"
                                }""".trimIndent()
                            } else {
                                """{"message": "Unprocessable Entity: Can not approve your own pull request."}"""
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
