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

class CreatePullRequestCommentUiTest {
    @get:Rule
    val compose = createComposeRule()

    private fun fixtureSession() = GitHubSession(
        GitHubUser("fixture-user", "https://github.com/fixture-user"),
        SessionCredentials("fixture-token")
    )

    private fun fixturePullRequest() = GitHubPullRequest(
        number = 27,
        title = "Fixture PR Title",
        state = GitHubPullRequestState.OPEN,
        repository = "owner/project",
        author = "fixture-author",
        labels = listOf("ready"),
        updatedAt = null
    )

    @Test
    fun commentSuccessSubmitsPostAppendsToUiAndClearsInput() {
        PullRequestCommentMockServer().use { server ->
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
                        isOffline = false
                    )
                }
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("pr_comment_input").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("pr_comment_input").performScrollTo().performTextInput("Excelente PR! LGTM")
            compose.onNodeWithTag("pr_comment_submit_button").performScrollTo().assertIsEnabled()
            compose.onNodeWithTag("pr_comment_submit_button").performClick()

            compose.waitUntil(10_000) {
                server.requests.any { it.firstLine.startsWith("POST") } &&
                    compose.onAllNodesWithText("Excelente PR! LGTM").fetchSemanticsNodes().isNotEmpty()
            }

            // Input is cleared
            compose.onNodeWithTag("pr_comment_input")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))

            val postReq = server.requests.first { it.firstLine.startsWith("POST") }
            assertTrue(postReq.firstLine.startsWith("POST /repos/owner/project/issues/27/comments"))
            assertTrue(postReq.body.contains("\"body\":\"Excelente PR! LGTM\""))
            assertEquals("Bearer fixture-token", postReq.authHeader)
        }
    }

    @Test
    fun commentAntiDoubleTapDisablesButtonDuringSubmission() {
        PullRequestCommentMockServer(postDelayMillis = 1_500).use { server ->
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
                        isOffline = false
                    )
                }
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("pr_comment_input").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("pr_comment_input").performScrollTo().performTextInput("Comentário lento PR")
            compose.onNodeWithTag("pr_comment_submit_button").performScrollTo().performClick()

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("pr_comment_progress").fetchSemanticsNodes().isNotEmpty() ||
                    !compose.onNodeWithTag("pr_comment_submit_button").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            }

            compose.waitUntil(10_000) {
                server.requests.any { it.firstLine.startsWith("POST") } &&
                    compose.onAllNodesWithText("Comentário lento PR").fetchSemanticsNodes().isNotEmpty()
            }

            assertEquals(1, server.requests.count { it.firstLine.startsWith("POST") })
        }
    }

    @Test
    fun commentFailurePreservesDraftTextAndDisplaysError() {
        PullRequestCommentMockServer(postStatus = 403).use { server ->
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
                        isOffline = false
                    )
                }
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("pr_comment_input").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("pr_comment_input").performScrollTo().performTextInput("Rascunho que nao pode sumir")
            compose.onNodeWithTag("pr_comment_submit_button").performScrollTo().performClick()

            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Você não tem permissão para comentar neste pull request.").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("pr_comment_input")
                .assertTextContains("Rascunho que nao pode sumir")
        }
    }

    @Test
    fun mutationGuardEmptyValidation() {
        PullRequestCommentMockServer().use { server ->
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
                        isOffline = false
                    )
                }
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("pr_comment_submit_button").fetchSemanticsNodes().isNotEmpty()
            }

            // Click comment with blank text
            compose.onNodeWithTag("pr_comment_submit_button").performScrollTo().performClick()
            compose.onNodeWithText("O comentário não pode ficar em branco.").assertIsDisplayed()
            assertEquals(0, server.requests.count { it.firstLine.startsWith("POST") })
        }
    }

    @Test
    fun mutationGuardOfflineBlocked() {
        PullRequestCommentMockServer().use { server ->
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
                compose.onAllNodesWithTag("pr_comment_offline_banner").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("pr_comment_offline_banner").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Modo offline: Conexão necessária para comentar em pull requests.").assertIsDisplayed()
            compose.onAllNodesWithTag("pr_comment_input").assertCountEquals(0)
            compose.onAllNodesWithTag("pr_comment_submit_button").assertCountEquals(0)
            assertEquals(0, server.requests.count { it.firstLine.startsWith("POST") })
        }
    }

    private data class RecordedRequest(
        val firstLine: String,
        val authHeader: String?,
        val body: String
    )

    private class PullRequestCommentMockServer(
        private val postStatus: Int = 201,
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
                                "user": {"login": "fixture-author"},
                                "labels": [{"name": "ready"}],
                                "head": {"ref": "feature", "sha": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "repo": {"full_name": "owner/project"}},
                                "base": {"ref": "main", "repo": {"full_name": "owner/project"}}
                            }""".trimIndent()
                        }
                        firstLine.startsWith("POST /repos/owner/project/issues/27/comments") -> {
                            if (postDelayMillis > 0) Thread.sleep(postDelayMillis)
                            status = postStatus
                            statusText = if (postStatus == 201) "Created" else "Forbidden"
                            responseBody = if (postStatus == 201) {
                                val extractedBody = if (body.contains("\"body\":\"")) {
                                    body.substringAfter("\"body\":\"").substringBefore("\"")
                                } else "Comentario padrao"
                                """{
                                    "id": 9999,
                                    "body": "$extractedBody",
                                    "user": {"login": "fixture-user"},
                                    "created_at": "2026-10-17T20:30:00Z"
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
