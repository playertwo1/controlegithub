package com.playertwo.controlegithub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

class KanbanUiTest {
    @get:Rule val compose = createComposeRule()

    private class TestMemoryKanbanStore : KanbanStore {
        val storage = mutableMapOf<String, MutableMap<String, KanbanColumn>>()

        override suspend fun load(accountLogin: String): Map<String, KanbanColumn> {
            return storage[accountLogin.lowercase()]?.toMap().orEmpty()
        }

        override suspend fun setColumn(
            accountLogin: String,
            issueIdentity: String,
            column: KanbanColumn
        ): Map<String, KanbanColumn> {
            val userMap = storage.getOrPut(accountLogin.lowercase()) { mutableMapOf() }
            userMap[issueIdentity.lowercase()] = column
            return userMap.toMap()
        }

        override suspend fun clear(accountLogin: String) {
            storage.remove(accountLogin.lowercase())
        }
    }

    private fun fixtureSession(login: String = "fixture-user") = GitHubSession(
        GitHubUser(login, "https://github.com/$login"),
        SessionCredentials("fixture-token")
    )

    @Test
    fun kanbanViewDisplaysFourColumnsAndLocalBanner() {
        MockIssuesServer().use { server ->
            val store = TestMemoryKanbanStore()
            compose.setContent {
                ControleTheme(AppThemeMode.LIGHT) {
                    GitHubIssuesScreen(
                        modifier = androidx.compose.ui.Modifier,
                        client = GitHubHttpClient(server.baseUri),
                        session = fixtureSession("kanban-view-user"),
                        sessionRestoring = false,
                        sessionStorageError = false,
                        onConnected = { true },
                        onSessionExpired = {},
                        onAppearance = {},
                        kanbanStore = store
                    )
                }
            }

            // Wait for issues to load
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Fixture issue one").fetchSemanticsNodes().isNotEmpty()
            }

            // Click "Quadro" chip
            compose.onNodeWithTag("filter_chip_kanban").performClick()

            // Verify local banner is displayed
            compose.onNodeWithTag("kanban_local_banner").assertIsDisplayed()
            compose.onNodeWithText("Organização local: mover os cartões organiza seu trabalho neste aparelho e não altera o estado da issue no GitHub.")
                .assertIsDisplayed()

            // Verify the 4 column titles with performScrollTo for horizontal scroll
            compose.onNodeWithText("A fazer").assertIsDisplayed()
            compose.onNodeWithText("Em andamento").assertIsDisplayed()
            compose.onNodeWithText("Em revisão").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Concluído").performScrollTo().assertIsDisplayed()

            // Issue one is open, so it defaults to "A fazer"
            compose.onNodeWithText("A fazer").performScrollTo()
            compose.onNodeWithText("Fixture issue one").assertIsDisplayed()
        }
    }

    @Test
    fun kanbanMoveTransfersCardBetweenColumnsWithoutNetworkMutation() {
        MockIssuesServer().use { server ->
            val store = TestMemoryKanbanStore()
            compose.setContent {
                ControleTheme(AppThemeMode.LIGHT) {
                    GitHubIssuesScreen(
                        modifier = androidx.compose.ui.Modifier,
                        client = GitHubHttpClient(server.baseUri),
                        session = fixtureSession("kanban-move-user"),
                        sessionRestoring = false,
                        sessionStorageError = false,
                        onConnected = { true },
                        onSessionExpired = {},
                        onAppearance = {},
                        kanbanStore = store,
                        initialKanban = true
                    )
                }
            }

            // Wait for issue in kanban
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Fixture issue one").fetchSemanticsNodes().isNotEmpty()
            }

            val initialRequestCount = server.requests.size

            // Click "Mover" on issue #18
            compose.onNodeWithTag("kanban_move_card_18").performClick()

            // Assert dialog appears
            compose.onNodeWithText("Mover cartão").assertIsDisplayed()

            // Target "Em andamento"
            compose.onNodeWithTag("kanban_move_target_in_progress").performClick()

            // Dialog dismissed
            compose.onNodeWithText("Mover cartão").assertDoesNotExist()

            // Verify store updated locally
            runBlocking {
                val mapping = store.load("kanban-move-user")
                assertEquals(KanbanColumn.IN_PROGRESS, mapping["fixture-owner/fixture-repo/18"])
            }

            // Verify zero write requests were made to server (mutation guard)
            val writeRequests = server.requests.drop(initialRequestCount).filter {
                it.startsWith("POST") || it.startsWith("PATCH") || it.startsWith("PUT") || it.startsWith("DELETE")
            }
            assertTrue("Nenhuma chamada de mutação deve ser feita ao mover cartão", writeRequests.isEmpty())
        }
    }

    @Test
    fun kanbanPersistenceRestoresPosition() {
        MockIssuesServer().use { server ->
            val store = TestMemoryKanbanStore()
            // Pre-seed issue #18 in "Em revisão"
            runBlocking {
                store.setColumn("kanban-persist-user", "fixture-owner/fixture-repo/18", KanbanColumn.IN_REVIEW)
            }

            compose.setContent {
                ControleTheme(AppThemeMode.LIGHT) {
                    GitHubIssuesScreen(
                        modifier = androidx.compose.ui.Modifier,
                        client = GitHubHttpClient(server.baseUri),
                        session = fixtureSession("kanban-persist-user"),
                        sessionRestoring = false,
                        sessionStorageError = false,
                        onConnected = { true },
                        onSessionExpired = {},
                        onAppearance = {},
                        kanbanStore = store,
                        initialKanban = true
                    )
                }
            }

            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Fixture issue one").fetchSemanticsNodes().isNotEmpty()
            }

            // Scroll column "Em revisão" into view
            compose.onNodeWithTag("kanban_column_in_review").performScrollTo()
            compose.onNodeWithText("Fixture issue one").assertIsDisplayed()

            // Move dialog should show "Em revisão (atual)" as disabled
            compose.onNodeWithTag("kanban_move_card_18").performClick()
            compose.onNodeWithText("Em revisão (atual)").assertIsNotEnabled()
            compose.onNodeWithText("Cancelar").performClick()
        }
    }

    @Test
    fun kanbanAccountIsolationAndClear() {
        val store = TestMemoryKanbanStore()
        runBlocking {
            store.setColumn("user_a", "repo/1", KanbanColumn.IN_PROGRESS)
            store.setColumn("user_b", "repo/1", KanbanColumn.DONE)

            assertEquals(KanbanColumn.IN_PROGRESS, store.load("user_a")["repo/1"])
            assertEquals(KanbanColumn.DONE, store.load("user_b")["repo/1"])

            // Clear user_a
            clearKanbanBeforeLogout("user_a", store)

            assertTrue(store.load("user_a").isEmpty())
            assertEquals(KanbanColumn.DONE, store.load("user_b")["repo/1"])
        }
    }

    @Test
    fun kanbanMissingItemHandledGracefully() {
        MockIssuesServer().use { server ->
            val store = TestMemoryKanbanStore()
            // Pre-seed with missing/deleted issue
            runBlocking {
                store.setColumn("kanban-missing-user", "nonexistent/repo/999", KanbanColumn.IN_PROGRESS)
            }

            compose.setContent {
                ControleTheme(AppThemeMode.LIGHT) {
                    GitHubIssuesScreen(
                        modifier = androidx.compose.ui.Modifier,
                        client = GitHubHttpClient(server.baseUri),
                        session = fixtureSession("kanban-missing-user"),
                        sessionRestoring = false,
                        sessionStorageError = false,
                        onConnected = { true },
                        onSessionExpired = {},
                        onAppearance = {},
                        kanbanStore = store,
                        initialKanban = true
                    )
                }
            }

            // Wait for issues to load
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Fixture issue one").fetchSemanticsNodes().isNotEmpty()
            }

            // Normal issue is visible, missing issue does not crash
            compose.onNodeWithText("Fixture issue one").assertIsDisplayed()
            compose.onNodeWithText("#999").assertDoesNotExist()
        }
    }
}

private class MockIssuesServer : AutoCloseable {
    private val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    val baseUri = java.net.URI("http://127.0.0.1:${server.localPort}/")
    val requests = CopyOnWriteArrayList<String>()

    private val issueOne = """{"number":18,"title":"Fixture issue one","state":"open","html_url":"https://github.com/fixture-owner/fixture-repo/issues/18","repository":{"full_name":"fixture-owner/fixture-repo"},"user":{"login":"fixture-author"}}"""

    private val worker = Thread {
        while (!server.isClosed) runCatching {
            server.accept().use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                val request = reader.readLine() ?: return@use
                requests += request
                while (reader.readLine()?.isNotEmpty() == true) Unit

                val body = "[$issueOne]"
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                    write(bytes)
                    flush()
                }
            }
        }
    }.apply { isDaemon = true; start() }

    override fun close() {
        runCatching { server.close() }
        runCatching { worker.interrupt() }
    }
}
