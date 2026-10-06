package com.playertwo.controlegithub

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GitHubRepositoriesFilterScreenTest {
    @get:Rule val compose = createComposeRule()
    private val favorites = MemoryRepositoryFavoritesStore()

    @Test fun loadedRepositoryListOffersFavoritesFilter() {
        RepositoryFixtureApi().use { api ->
            showRepositories(api)
            awaitRepositories(api)
            compose.waitUntil(5_000) {
                compose.onAllNodesWithContentDescription("Adicionar acme/Mobile aos favoritos").fetchSemanticsNodes().isNotEmpty()
            }
            if (pauseForMaestroPreviewIfRequested()) return

            compose.onNodeWithText("Favoritos").assertIsDisplayed()
            assertEquals(1, api.requestCount.get())
        }
    }

    @Test fun favoriteToggleFiltersLoadedRepositoriesWithoutRemoteRequest() {
        RepositoryFixtureApi().use { api ->
            showRepositories(api)
            awaitRepositories(api)

            compose.onNodeWithContentDescription("Adicionar acme/Mobile aos favoritos").performClick()
            compose.onNodeWithContentDescription("Remover acme/Mobile dos favoritos").assertIsDisplayed()
            compose.onNodeWithText("Favoritos").performClick()

            compose.onNodeWithText("acme/Mobile").assertIsDisplayed()
            compose.onNodeWithText("acme/docs").assertDoesNotExist()
            compose.onNodeWithText("acme/tools").assertDoesNotExist()
            assertEquals(1, api.requestCount.get())
        }
    }

    @Test fun favoritesStayIsolatedWhenTheConnectedAccountChanges() {
        RepositoryFixtureApi().use { api ->
            val accountLogin = mutableStateOf("fixture-user")
            showRepositories(api, accountLogin)
            awaitRepositories(api)
            compose.onNodeWithContentDescription("Adicionar acme/Mobile aos favoritos").performClick()
            compose.onNodeWithContentDescription("Remover acme/Mobile dos favoritos").assertIsDisplayed()

            accountLogin.value = "second-user"
            compose.waitUntil(10_000) {
                api.requestCount.get() == 2 &&
                    compose.onAllNodesWithContentDescription("Adicionar acme/Mobile aos favoritos").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Adicionar acme/Mobile aos favoritos").assertIsDisplayed()

            accountLogin.value = "fixture-user"
            compose.waitUntil(10_000) {
                api.requestCount.get() == 3 &&
                    compose.onAllNodesWithContentDescription("Remover acme/Mobile dos favoritos").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Remover acme/Mobile dos favoritos").assertIsDisplayed()
        }
    }

    @Test fun favoriteReadFailureDisablesControlsAndRetryRestoresThem() {
        favorites.failLoad = true
        RepositoryFixtureApi().use { api ->
            showRepositories(api)
            awaitRepositories(api)
            compose.onNodeWithText("Não foi possível carregar favoritos").assertIsDisplayed()
            compose.onNodeWithText("Favoritos").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Adicionar acme/Mobile aos favoritos").assertIsNotEnabled()

            favorites.failLoad = false
            compose.onNodeWithText("Tentar novamente").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Não foi possível carregar favoritos").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithContentDescription("Adicionar acme/Mobile aos favoritos").assertIsEnabled()
        }
    }

    @Test fun failedFavoriteWriteKeepsThePersistedStarState() {
        favorites.failWrite = true
        RepositoryFixtureApi().use { api ->
            showRepositories(api)
            awaitRepositories(api)
            compose.onNodeWithContentDescription("Adicionar acme/Mobile aos favoritos").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Não foi possível salvar o favorito").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Adicionar acme/Mobile aos favoritos").assertIsDisplayed()
            compose.onNodeWithContentDescription("Remover acme/Mobile dos favoritos").assertDoesNotExist()
        }
    }

    @Test fun logoutIsDisabledWhileFavoriteIsBeingSaved() {
        favorites.writeGate = CompletableDeferred()
        RepositoryFixtureApi().use { api ->
            showRepositories(api)
            awaitRepositories(api)
            compose.onNodeWithContentDescription("Adicionar acme/Mobile aos favoritos").performClick()
            compose.onNodeWithText("Sair").assertIsNotEnabled()
            favorites.writeGate?.complete(Unit)
            compose.waitUntil(5_000) {
                runCatching {
                    compose.onNodeWithContentDescription("Remover acme/Mobile dos favoritos").assertIsDisplayed()
                }.isSuccess
            }
            compose.onNodeWithText("Sair").assertIsEnabled()
        }
    }

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

    private fun showRepositories(
        api: RepositoryFixtureApi,
        accountLogin: MutableState<String> = mutableStateOf("fixture-user")
    ) {
        compose.setContent {
            val currentLogin = accountLogin.value
            ControleTheme(AppThemeMode.LIGHT) {
                GitHubRepositoriesScreen(
                    modifier = Modifier,
                    client = GitHubHttpClient(baseUrl = api.baseUrl),
                    session = GitHubSession(
                        GitHubUser(currentLogin, "https://github.com/$currentLogin"),
                        SessionCredentials("instrumentation-fixture-token")
                    ),
                    sessionRestoring = false,
                    sessionStorageError = false,
                    sessionExpired = false,
                    onConnected = { true },
                    onSessionExpired = {},
                    onLogout = {},
                    onAppearance = {},
                    favoritesStore = favorites
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
        Thread.sleep(180_000)
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

    private class MemoryRepositoryFavoritesStore : RepositoryFavoritesStore {
        private val byAccount = mutableMapOf<String, Set<Long>>()
        var failLoad = false
        var failWrite = false
        var failClear = false
        var writeGate: CompletableDeferred<Unit>? = null

        override suspend fun load(accountLogin: String): Set<Long> {
            if (failLoad) throw IOException("fixture read failure")
            return byAccount[accountLogin.lowercase(Locale.ROOT)].orEmpty()
        }

        override suspend fun setFavorite(accountLogin: String, repositoryId: Long, favorite: Boolean): Set<Long> {
            if (failWrite) throw IOException("fixture write failure")
            writeGate?.await()
            val key = accountLogin.lowercase(Locale.ROOT)
            val updated = byAccount[key].orEmpty().toMutableSet().apply {
                if (favorite) add(repositoryId) else remove(repositoryId)
            }.toSet()
            byAccount[key] = updated
            return updated
        }

        override suspend fun clear(accountLogin: String) {
            if (failClear) throw IOException("fixture clear failure")
            byAccount.remove(accountLogin.lowercase(Locale.ROOT))
        }
    }
}
