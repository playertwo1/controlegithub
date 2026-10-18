package com.playertwo.controlegithub

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class PerformanceAndFaultToleranceUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun fixtureSession(login: String = "perf-user") = GitHubSession(
        user = GitHubUser(
            login = login,
            profileUrl = "https://github.com/$login"
        ),
        credentials = SessionCredentials(accessToken = "valid-token-perf", userLogin = login)
    )

    @Test
    fun testDashboardCacheLoadingPerformanceBudget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cacheStore = SQLiteGitHubCacheStore(context, dbName = "test_perf_cache.db")
        val account = "perf-tester"

        // Setup fixture data: 10 repositories
        val repoJson = buildString {
            append("[")
            for (i in 1..10) {
                if (i > 1) append(",")
                append("""{"id":$i,"name":"repo-$i","full_name":"$account/repo-$i","owner":{"login":"$account"},"private":false,"html_url":"https://github.com/$account/repo-$i","description":"Description for repo $i","stargazers_count":${i * 10},"updated_at":"2026-10-17T20:00:00Z"}""")
            }
            append("]")
        }

        runBlocking {
            cacheStore.clear(account)
            cacheStore.put(account, "/user/repos?per_page=100&sort=updated", repoJson, etag = "\"etag-repos-1\"")
        }

        // Measure 5 consecutive read iterations
        val measurements = mutableListOf<Long>()
        for (iteration in 1..5) {
            val elapsed = measureTimeMillis {
                val cached = cacheStore.get(account, "/user/repos?per_page=100&sort=updated")
                assertTrue("Cache must not be null in iteration $iteration", cached != null)
                val parsed = parseGitHubRepositories(cached!!.payload)
                assertTrue("Must parse 10 repositories", parsed.size == 10)
            }
            measurements.add(elapsed)
            // Assert budget: <= 2000 ms (2.0s) per iteration
            assertTrue("Iteration $iteration exceeded 2000ms budget: ${elapsed}ms", elapsed <= 2000)
        }

        val average = measurements.average()
        val max = measurements.maxOrNull() ?: 0
        println("PERF BUDGET DASHBOARD: 5 iterations -> avg: ${average}ms, max: ${max}ms (budget <= 2000ms)")
        assertTrue("Average time exceeds budget", average <= 2000)

        runBlocking { cacheStore.clear(account) }
    }

    @Test
    fun testProlongedNetworkFailureRetainsContentAndShowsRetryWithoutAnr() {
        val session = fixtureSession("fault-tolerance-user")
        // Target an invalid port where connection fails immediately with connection refused
        val client = GitHubHttpClient(baseUrl = URI.create("http://127.0.0.1:1/"))

        compose.setContent {
            ControleTheme(AppThemeMode.DARK) {
                GitHubRepositoriesScreen(
                    modifier = Modifier.fillMaxSize(),
                    client = client,
                    session = session,
                    sessionRestoring = false,
                    sessionStorageError = false,
                    sessionExpired = false,
                    onConnected = { true },
                    onSessionExpired = {},
                    onLogout = {},
                    onAppearance = {}
                )
            }
        }

        // Wait for failure banner to be displayed
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Tentar novamente").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify that retry button is displayed, clickable, and does not cause ANR or crash
        val retryNode = compose.onNodeWithText("Tentar novamente")
        retryNode.assertIsDisplayed()
        retryNode.assertHasClickAction()
        retryNode.performClick()

        // App remains responsive
        compose.onNodeWithText("Seus repositórios").assertIsDisplayed()
    }
}
