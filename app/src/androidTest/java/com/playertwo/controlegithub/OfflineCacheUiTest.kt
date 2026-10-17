package com.playertwo.controlegithub

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.ServerSocket
import java.net.URI
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineCacheUiTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "ui_test_cache.db"
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
    fun offlineCachedRepositories_showsOfflineBadgeAndCachedItems() {
        val testUser = "alice"
        val cachedPayload = """
            [
              {
                "id": 999,
                "name": "cached-offline-project",
                "full_name": "alice/cached-offline-project",
                "owner": {
                  "login": "alice"
                },
                "private": false,
                "stargazers_count": 88,
                "language": "Kotlin",
                "description": "Repositorio recuperado do cache SQLite"
              }
            ]
        """.trimIndent()

        // Pre-populate cache for alice with current timestamp
        cacheStore.put(testUser, GitHubRepositoryPager.FIRST_PAGE_PATH, cachedPayload, statusCode = 200)

        // Find an unused closed port to ensure immediate network failure
        val closedPortServer = ServerSocket(0)
        val closedPort = closedPortServer.localPort
        closedPortServer.close()

        val offlineClient = GitHubHttpClient(baseUrl = URI.create("http://127.0.0.1:$closedPort/"))
        val session = GitHubSession(
            GitHubUser(testUser, "https://github.com/$testUser"),
            SessionCredentials("valid-token")
        )

        compose.setContent {
            MaterialTheme {
                GitHubRepositoriesScreen(
                    modifier = Modifier,
                    client = offlineClient,
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

        // Wait until cached repo is loaded and rendered
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("alice/cached-offline-project").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("alice/cached-offline-project").assertIsDisplayed()
        compose.onNodeWithText("Repositorio recuperado do cache SQLite").assertIsDisplayed()

        // Verify OfflineCacheBadge is displayed
        compose.onNodeWithTag("cache-offline-indicator").assertIsDisplayed()
    }

    @Test
    fun offlineEmptyCache_showsNoSavedDataMessage() {
        val testUser = "bob"
        // Bob has no cache

        val closedPortServer = ServerSocket(0)
        val closedPort = closedPortServer.localPort
        closedPortServer.close()

        val offlineClient = GitHubHttpClient(baseUrl = URI.create("http://127.0.0.1:$closedPort/"))
        val session = GitHubSession(
            GitHubUser(testUser, "https://github.com/$testUser"),
            SessionCredentials("valid-token")
        )

        compose.setContent {
            MaterialTheme {
                GitHubRepositoriesScreen(
                    modifier = Modifier,
                    client = offlineClient,
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

        // Wait until network failure message appears
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Sem conexão com a rede e nenhum dado salvo para esta consulta.")
                .fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Sem conexão com a rede e nenhum dado salvo para esta consulta.")
            .assertIsDisplayed()

        // No offline badge should be visible because there's no cache
        assertEquals(0, compose.onAllNodesWithTag("cache-offline-indicator").fetchSemanticsNodes().size)
    }

    @Test
    fun accountIsolationInUi_userBCannotSeeUserACache() {
        // Alice has cached items
        cacheStore.put(
            "alice",
            GitHubRepositoryPager.FIRST_PAGE_PATH,
            """[{"id": 1, "name": "secret-alice", "full_name": "alice/secret-alice", "owner": {"login": "alice"}, "private": true, "stargazers_count": 0, "language": "Kotlin"}]"""
        )

        val closedPortServer = ServerSocket(0)
        val closedPort = closedPortServer.localPort
        closedPortServer.close()

        val offlineClient = GitHubHttpClient(baseUrl = URI.create("http://127.0.0.1:$closedPort/"))
        // Bob logs in
        val session = GitHubSession(
            GitHubUser("bob", "https://github.com/bob"),
            SessionCredentials("bob-token")
        )

        compose.setContent {
            MaterialTheme {
                GitHubRepositoriesScreen(
                    modifier = Modifier,
                    client = offlineClient,
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
            compose.onAllNodesWithText("Sem conexão com a rede e nenhum dado salvo para esta consulta.")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Bob must not see Alice's repo
        assertEquals(0, compose.onAllNodesWithText("alice/secret-alice").fetchSemanticsNodes().size)
    }
}
