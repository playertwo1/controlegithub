package com.playertwo.controlegithub

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GitHubRepositoriesFilterScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchAndCombinedFiltersStayLocalAndCanBeCleared() {
        RepositoryFixtureApi().use { api ->
            showRepositories(api)
            awaitRepositories(api)

            compose.onNode(hasSetTextAction()).performTextInput("  MOBILE  ")
            compose.onNodeWithText("acme/Mobile").assertIsDisplayed()
            compose.onNodeWithText("acme/docs").assertDoesNotExist()
            assertEquals(1, api.requestCount.get())

            compose.onNodeWithContentDescription("Limpar busca").performClick()
            compose.onNodeWithText("Públicos").performClick()
            compose.onNodeWithText("Markdown").performClick()
            compose.onNodeWithText("acme/docs").assertIsDisplayed()
            compose.onNodeWithText("acme/Mobile").assertDoesNotExist()
            compose.onNodeWithText("acme/tools").assertDoesNotExist()
            assertEquals(1, api.requestCount.get())

            compose.onNodeWithText("Todos").performClick()
            compose.onNodeWithText("acme/docs").assertIsDisplayed()
            compose.onNodeWithText("acme/tools").assertDoesNotExist()
            compose.onNodeWithText("acme/Mobile").assertDoesNotExist()

            compose.onNodeWithText("Todas as linguagens").performClick()
            compose.onNodeWithText("acme/docs").assertIsDisplayed()
            compose.onNodeWithText("acme/tools").assertIsDisplayed()
            compose.onNodeWithText("acme/Mobile").assertIsDisplayed()
            assertEquals(1, api.requestCount.get())

            compose.onNodeWithText("Públicos").performClick()
            compose.onNodeWithText("acme/Mobile").assertDoesNotExist()
            compose.onNodeWithText("Limpar filtros").performClick()
            compose.onNodeWithText("acme/Mobile").assertIsDisplayed()
            compose.onNodeWithText("acme/docs").assertIsDisplayed()
            compose.onNodeWithText("acme/tools").assertIsDisplayed()
            assertEquals(1, api.requestCount.get())
        }
    }

    @Test fun searchByLanguageAppliesToNextPageAndRefreshKeepsCriteria() {
        RepositoryFixtureApi().use { api ->
            showRepositories(api)
            awaitRepositories(api)
            if (pauseForMaestroPreviewIfRequested()) return
            compose.onNode(hasSetTextAction()).performTextInput("kOtLiN")
            compose.onNodeWithText("acme/Mobile").assertIsDisplayed()
            compose.onNodeWithText("acme/docs").assertDoesNotExist()
            assertEquals(1, api.requestCount.get())

            compose.onNodeWithText("Carregar mais").performClick()
            compose.waitUntil(10_000) {
                api.requestCount.get() == 2 &&
                    compose.onAllNodesWithText("acme/KotlinCLI").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("acme/KotlinCLI").assertIsDisplayed()
            compose.onNodeWithText("acme/docs").assertDoesNotExist()

            compose.onNodeWithContentDescription("Atualizar repositórios").performClick()
            compose.waitUntil(10_000) {
                api.requestCount.get() == 3 &&
                    compose.onAllNodesWithText("acme/Mobile").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("acme/Mobile").assertIsDisplayed()
            compose.onNodeWithText("acme/KotlinCLI").assertDoesNotExist()
            compose.onNodeWithText("acme/docs").assertDoesNotExist()
        }
    }

    @Test fun filteredEmptyStateIsDistinctAndCanBeCleared() {
        RepositoryFixtureApi().use { api ->
            showRepositories(api)
            awaitRepositories(api)
            compose.onNode(hasSetTextAction()).performTextInput("not-found")
            compose.onNodeWithText("Nenhum repositório corresponde aos critérios").assertIsDisplayed()
            compose.onNodeWithText("Os critérios consideram apenas os repositórios carregados.").assertIsDisplayed()
            compose.onNodeWithText("Nenhum repositório acessível").assertDoesNotExist()
            assertEquals(1, api.requestCount.get())

            compose.onNodeWithText("Limpar filtros").performClick()
            compose.onNodeWithText("acme/Mobile").assertIsDisplayed()
            assertEquals(1, api.requestCount.get())
        }
    }

    private fun showRepositories(api: RepositoryFixtureApi) {
        val session = GitHubSession(
            GitHubUser("fixture-user", "https://github.com/fixture-user"),
            SessionCredentials("instrumentation-fixture-token")
        )
        compose.setContent {
            ControleTheme(AppThemeMode.LIGHT) {
                GitHubRepositoriesScreen(
                    modifier = Modifier,
                    client = GitHubHttpClient(baseUrl = api.baseUrl),
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
    }

    private fun awaitRepositories(api: RepositoryFixtureApi) {
        compose.waitUntil(10_000) {
            api.requestCount.get() == 1 &&
                compose.onAllNodesWithText("acme/Mobile").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pauseForMaestroPreviewIfRequested(): Boolean {
        if (InstrumentationRegistry.getArguments().getString("maestroPreview") != "true") return false
        Thread.sleep(60_000)
        return true
    }

    private class RepositoryFixtureApi : AutoCloseable {
        private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val baseUrl = URI.create("http://127.0.0.1:${server.localPort}/")
        val requestCount = AtomicInteger()
        private val firstPageBody = """[
          {"id":1,"name":"Mobile","full_name":"acme/Mobile","owner":{"login":"acme"},"private":true,"description":null,"language":"Kotlin","stargazers_count":0},
          {"id":2,"name":"docs","full_name":"acme/docs","owner":{"login":"acme"},"private":false,"description":null,"language":"Markdown","stargazers_count":0},
          {"id":3,"name":"tools","full_name":"acme/tools","owner":{"login":"acme"},"private":false,"description":null,"language":null,"stargazers_count":0}
        ]""".toByteArray(StandardCharsets.UTF_8)
        private val nextPageBody = """[
          {"id":4,"name":"KotlinCLI","full_name":"acme/KotlinCLI","owner":{"login":"acme"},"private":false,"description":null,"language":"Java","stargazers_count":0}
        ]""".toByteArray(StandardCharsets.UTF_8)

        private val acceptThread = Thread {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                        val requestLine = reader.readLine().orEmpty()
                        var line = reader.readLine()
                        while (!line.isNullOrEmpty()) line = reader.readLine()
                        requestCount.incrementAndGet()
                        val isNextPage = requestLine.contains("page=2")
                        val body = if (isNextPage) nextPageBody else firstPageBody
                        val linkHeader = if (isNextPage) "" else "Link: </user/repos?page=2>; rel=\"next\"\r\n"
                        val headers = (
                            "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/json\r\n" +
                                linkHeader +
                                "Content-Length: ${body.size}\r\n" +
                                "Connection: close\r\n\r\n"
                            ).toByteArray(StandardCharsets.UTF_8)
                        socket.getOutputStream().apply {
                            write(headers)
                            write(body)
                            flush()
                        }
                    }
                } catch (_: java.net.SocketException) { }
            }
        }.apply {
            name = "repository-filter-fixture"
            isDaemon = true
            start()
        }

        override fun close() {
            server.close()
            acceptThread.join(1_000)
        }
    }
}
