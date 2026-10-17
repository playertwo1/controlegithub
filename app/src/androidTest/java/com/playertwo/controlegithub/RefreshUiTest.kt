package com.playertwo.controlegithub

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RefreshUiTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "refresh_test_cache.db"
    private lateinit var cacheStore: SQLiteGitHubCacheStore

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        cacheStore = SQLiteGitHubCacheStore(context, dbName = dbName)
    }

    @After
    fun tearDown() {
        cacheStore.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun revalidation304_preservesItemsOnScreen_andNoOfflineBadge() {
        val testUser = "alice"
        val cachedPayload = """
            [
              {
                "id": 101,
                "name": "project-304",
                "full_name": "alice/project-304",
                "owner": { "login": "alice" },
                "private": false,
                "stargazers_count": 12,
                "language": "Kotlin",
                "description": "Repo validado via 304"
              }
            ]
        """.trimIndent()

        cacheStore.put(
            testUser,
            GitHubRepositoryPager.FIRST_PAGE_PATH,
            cachedPayload,
            statusCode = 200,
            etag = "\"etag-304\""
        )

        MockServer { requestHeaders ->
            val ifNoneMatch = requestHeaders["if-none-match"]
            if (ifNoneMatch == "\"etag-304\"") {
                MockResponse(statusCode = 304, headers = mapOf("ETag" to "\"etag-304\""), body = "")
            } else {
                MockResponse(statusCode = 200, headers = mapOf("ETag" to "\"etag-304\""), body = cachedPayload)
            }
        }.use { server ->
            val client = GitHubHttpClient(baseUrl = server.baseUrl)
            val session = GitHubSession(
                GitHubUser(testUser, "https://github.com/$testUser"),
                SessionCredentials("valid-token")
            )

            compose.setContent {
                MaterialTheme {
                    GitHubRepositoriesScreen(
                        modifier = Modifier,
                        client = client,
                        session = session,
                        sessionRestoring = false,
                        sessionStorageError = false,
                        sessionExpired = false,
                        onConnected = { true },
                        onSessionExpired = {},
                        onLogout = {},
                        onAppearance = {},
                        cacheStore = cacheStore
                    )
                }
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("alice/project-304").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithText("alice/project-304").assertIsDisplayed()
            assertEquals(0, compose.onAllNodesWithTag("cache-offline-indicator").fetchSemanticsNodes().size)

            // Click Refresh button
            compose.onNodeWithContentDescription("Atualizar repositórios").performClick()

            compose.waitUntil(10_000) { server.requestCount.get() >= 2 }
            compose.onNodeWithText("alice/project-304").assertIsDisplayed()
            assertEquals(0, compose.onAllNodesWithTag("cache-offline-indicator").fetchSemanticsNodes().size)
        }
    }

    @Test
    fun refreshFailure_preservesExistingItemsOnScreen_andShowsErrorWithRetry() {
        val testUser = "alice"
        val initialPayload = """
            [
              {
                "id": 201,
                "name": "resilient-repo",
                "full_name": "alice/resilient-repo",
                "owner": { "login": "alice" },
                "private": false,
                "stargazers_count": 9,
                "language": "Kotlin"
              }
            ]
        """.trimIndent()

        MockServer { _ ->
            val count = itRequestCount
            if (count == 1) {
                MockResponse(statusCode = 200, headers = mapOf("ETag" to "\"etag-v1\""), body = initialPayload)
            } else {
                MockResponse(statusCode = 500, headers = emptyMap(), body = "Server error")
            }
        }.use { server ->
            val client = GitHubHttpClient(baseUrl = server.baseUrl)
            val session = GitHubSession(
                GitHubUser(testUser, "https://github.com/$testUser"),
                SessionCredentials("valid-token")
            )

            compose.setContent {
                MaterialTheme {
                    GitHubRepositoriesScreen(
                        modifier = Modifier,
                        client = client,
                        session = session,
                        sessionRestoring = false,
                        sessionStorageError = false,
                        sessionExpired = false,
                        onConnected = { true },
                        onSessionExpired = {},
                        onLogout = {},
                        onAppearance = {},
                        cacheStore = cacheStore
                    )
                }
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("alice/resilient-repo").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("alice/resilient-repo").assertIsDisplayed()

            // Trigger Refresh which fails with 500
            compose.onNodeWithContentDescription("Atualizar repositórios").performClick()

            compose.waitUntil(10_000) { server.requestCount.get() >= 2 }

            // Item must STILL be visible on screen
            compose.onNodeWithText("alice/resilient-repo").assertIsDisplayed()

            // Error message and retry button must be displayed
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Tentar novamente").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Tentar novamente").assertIsDisplayed()
        }
    }

    @Test
    fun refresh200_updatesListWithoutDuplication() {
        val testUser = "alice"
        val page1 = """
            [
              {
                "id": 301,
                "name": "initial-repo",
                "full_name": "alice/initial-repo",
                "owner": { "login": "alice" },
                "private": false,
                "stargazers_count": 3,
                "language": "Kotlin"
              }
            ]
        """.trimIndent()
        val page2 = """
            [
              {
                "id": 302,
                "name": "refreshed-repo",
                "full_name": "alice/refreshed-repo",
                "owner": { "login": "alice" },
                "private": false,
                "stargazers_count": 7,
                "language": "Kotlin"
              }
            ]
        """.trimIndent()

        MockServer { _ ->
            val count = itRequestCount
            if (count == 1) {
                MockResponse(statusCode = 200, headers = mapOf("ETag" to "\"etag-1\""), body = page1)
            } else {
                MockResponse(statusCode = 200, headers = mapOf("ETag" to "\"etag-2\""), body = page2)
            }
        }.use { server ->
            val client = GitHubHttpClient(baseUrl = server.baseUrl)
            val session = GitHubSession(
                GitHubUser(testUser, "https://github.com/$testUser"),
                SessionCredentials("valid-token")
            )

            compose.setContent {
                MaterialTheme {
                    GitHubRepositoriesScreen(
                        modifier = Modifier,
                        client = client,
                        session = session,
                        sessionRestoring = false,
                        sessionStorageError = false,
                        sessionExpired = false,
                        onConnected = { true },
                        onSessionExpired = {},
                        onLogout = {},
                        onAppearance = {},
                        cacheStore = cacheStore
                    )
                }
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("alice/initial-repo").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("alice/initial-repo").assertIsDisplayed()

            // Refresh to get page2
            compose.onNodeWithContentDescription("Atualizar repositórios").performClick()

            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("alice/refreshed-repo").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("alice/refreshed-repo").assertIsDisplayed()

            // initial-repo should have been replaced, not duplicated
            assertEquals(0, compose.onAllNodesWithText("alice/initial-repo").fetchSemanticsNodes().size)
        }
    }

    private data class MockResponse(
        val statusCode: Int,
        val headers: Map<String, String> = emptyMap(),
        val body: String = ""
    )

    private class MockServer(
        private val handler: MockServer.(headers: Map<String, String>) -> MockResponse
    ) : AutoCloseable {
        private val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val baseUrl = URI.create("http://127.0.0.1:${server.localPort}/")
        val requestCount = AtomicInteger(0)
        val itRequestCount get() = requestCount.get()

        private val acceptThread = Thread {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                        val headers = mutableMapOf<String, String>()
                        var line = reader.readLine()
                        while (!line.isNullOrEmpty()) {
                            val colon = line.indexOf(':')
                            if (colon > 0) {
                                val key = line.substring(0, colon).trim().lowercase()
                                val value = line.substring(colon + 1).trim()
                                headers[key] = value
                            }
                            line = reader.readLine()
                        }
                        requestCount.incrementAndGet()

                        val response = handler(headers)
                        val bodyBytes = response.body.toByteArray(StandardCharsets.UTF_8)
                        val statusLine = when (response.statusCode) {
                            200 -> "HTTP/1.1 200 OK"
                            304 -> "HTTP/1.1 304 Not Modified"
                            500 -> "HTTP/1.1 500 Internal Server Error"
                            else -> "HTTP/1.1 ${response.statusCode} Custom"
                        }
                        val headerBuilder = StringBuilder()
                        headerBuilder.append("$statusLine\r\n")
                        headerBuilder.append("Content-Type: application/json\r\n")
                        headerBuilder.append("Content-Length: ${bodyBytes.size}\r\n")
                        headerBuilder.append("Connection: close\r\n")
                        response.headers.forEach { (k, v) ->
                            headerBuilder.append("$k: $v\r\n")
                        }
                        headerBuilder.append("\r\n")

                        socket.getOutputStream().apply {
                            write(headerBuilder.toString().toByteArray(StandardCharsets.UTF_8))
                            if (bodyBytes.isNotEmpty()) write(bodyBytes)
                            flush()
                        }
                    }
                } catch (_: java.net.SocketException) {
                } catch (_: Exception) {
                }
            }
        }.apply {
            name = "mock-refresh-server"
            isDaemon = true
            start()
        }

        override fun close() {
            server.close()
            acceptThread.join(1_000)
        }
    }
}
