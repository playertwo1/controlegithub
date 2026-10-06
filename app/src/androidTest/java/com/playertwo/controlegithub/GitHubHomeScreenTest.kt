package com.playertwo.controlegithub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GitHubHomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun authenticatedHomeLoadsOnlyAuthenticatedUserAndRefreshesWithoutDemoValues() {
        ProfileApi().use { api ->
            val session = GitHubSession(
                GitHubUser("fixture-user", "https://github.com/fixture-user"),
                SessionCredentials("fixture-token")
            )
            compose.setContent {
                ControleTheme(AppThemeMode.LIGHT) {
                    ControleApp(
                        themeMode = AppThemeMode.LIGHT,
                        preferenceError = false,
                        favoritesStore = DataStoreRepositoryFavoritesStore(
                            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
                        ),
                        favoriteOperations = kotlinx.coroutines.sync.Mutex(),
                        favoriteCleanupError = false,
                        apiClient = GitHubHttpClient(api.baseUri),
                        session = session,
                        sessionRestoring = false,
                        sessionRetry = false,
                        sessionStorageError = false,
                        onRetrySession = {},
                        onRetrySessionCleanup = {},
                        onConnected = { true },
                        onSessionExpired = {},
                        onLogout = {},
                        onThemeModeChange = {}
                    )
                }
            }
            compose.onNodeWithText("Abrir painel").performClick()
            compose.waitUntil(10_000) {
                api.requests.size == 1 &&
                    compose.onAllNodesWithText("Fixture Developer").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Fixture Developer").assertIsDisplayed()
            compose.onNodeWithText("0").assertIsDisplayed()
            compose.onNodeWithText("7").assertIsDisplayed()
            compose.onNodeWithText("12").assertIsDisplayed()
            compose.onNodeWithText("Origem: Perfil do GitHub").assertIsDisplayed()
            compose.onNodeWithText("Abrir perfil no GitHub").assertIsDisplayed()
            compose.onNodeWithText("Revisar navegação Android").assertDoesNotExist()
            compose.onNodeWithText("Menos abas.", substring = true).assertDoesNotExist()

            compose.onNodeWithText("Trabalho").performClick()
            compose.waitUntil(10_000) {
                api.requests.size == 2 &&
                    compose.onAllNodesWithText("Issues do GitHub").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Nenhuma issue encontrada com estes filtros.").assertIsDisplayed()
            compose.onNodeWithText("ISSUE #18").assertDoesNotExist()
            compose.onNodeWithText("Avisos").performClick()
            compose.waitUntil(10_000) {
                api.requests.size == 3 &&
                    compose.onAllNodesWithText("Nenhuma notificação.").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Nenhuma notificação.").assertIsDisplayed()
            compose.onNodeWithText("Você foi mencionado em uma issue").assertDoesNotExist()

            compose.onNodeWithText("Início").performClick()
            compose.waitUntil(10_000) {
                api.requests.size == 4 &&
                    compose.onAllNodesWithText("Fixture Developer").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Atualizar perfil").performClick()
            compose.waitUntil(10_000) {
                api.requests.size == 5 &&
                    compose.onAllNodesWithText("Fixture Developer").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(
                listOf("GET /user HTTP/1.1", "GET /issues?filter=assigned&state=open&pulls=false&per_page=50 HTTP/1.1", "GET /notifications?all=true&per_page=50 HTTP/1.1") +
                    List(2) { "GET /user HTTP/1.1" },
                api.requests
            )
            if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("maestroPreview") == "true") {
                Thread.sleep(60_000)
            }
            compose.onNodeWithText("Seus repositórios").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                api.requests.size == 5 &&
                    compose.onAllNodesWithText("Nenhum repositório acessível").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(api.requests.last().startsWith("GET /user/repos?"))
        }
    }

    @Test fun authenticatedWorkAndNoticesShowUnintegratedState() {
        compose.setContent {
            ControleTheme(AppThemeMode.LIGHT) {
                NotIntegratedScreen("Seu trabalho", "Issues e pull requests")
            }
        }
        compose.onNodeWithText("Ainda não integrado").assertIsDisplayed()
        compose.onNodeWithText("ISSUE #18").assertDoesNotExist()
        compose.onNodeWithText("Issues e pull requests aparecerá aqui quando essa integração estiver disponível.").assertIsDisplayed()
    }

    @Test fun demonstrationDataIsLabeledAcrossHomeWorkAndNotices() {
        compose.setContent {
            ControleTheme(AppThemeMode.LIGHT) {
                ControleApp(
                    themeMode = AppThemeMode.LIGHT,
                    preferenceError = false,
                    favoritesStore = DataStoreRepositoryFavoritesStore(
                        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
                    ),
                    favoriteOperations = kotlinx.coroutines.sync.Mutex(),
                    favoriteCleanupError = false,
                    apiClient = GitHubHttpClient(),
                    session = null,
                    sessionRestoring = false,
                    sessionRetry = false,
                    sessionStorageError = false,
                    onRetrySession = {},
                    onRetrySessionCleanup = {},
                    onConnected = { true },
                    onSessionExpired = {},
                    onLogout = {},
                    onThemeModeChange = {}
                )
            }
        }
        compose.onNodeWithText("Explorar demonstração").performClick()
        compose.onNodeWithText("Modo demonstração · dados fictícios").assertIsDisplayed()
        compose.onNodeWithText("Trabalho").performClick()
        compose.onNodeWithText("Conecte sua conta GitHub para consultar as issues que ela pode acessar.").assertIsDisplayed()
        compose.onNodeWithText("Preparar primeira versão Android").assertDoesNotExist()
        compose.onNodeWithText("Avisos").performClick()
        compose.onNodeWithText("Modo demonstração · dados fictícios").assertIsDisplayed()
        compose.onNodeWithText("Você foi mencionado em uma issue").assertIsDisplayed()
    }

    @Test fun forbiddenProfileKeepsSessionIdentitySeparateAndOffersRetry() {
        ProfileApi(initialStatus = 403).use { api ->
            val session = GitHubSession(
                GitHubUser("fixture-user", "https://github.com/fixture-user"),
                SessionCredentials("fixture-token")
            )
            compose.setContent {
                ControleTheme(AppThemeMode.LIGHT) {
                    GitHubHomeScreen(
                        androidx.compose.ui.Modifier,
                        GitHubHttpClient(api.baseUri),
                        session,
                        sessionRestoring = false,
                        onOpenRepositories = {},
                        onSessionExpired = {},
                        onLogout = {},
                        onAppearance = {}
                    )
                }
            }
            compose.waitUntil(10_000) {
                api.requests.size == 1 &&
                    compose.onAllNodesWithText("Sua conta não tem permissão para acessar este recurso.")
                        .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Sessão: @fixture-user").assertIsDisplayed()
            compose.onNodeWithText("Sua conta não tem permissão para acessar este recurso.").assertIsDisplayed()
            compose.onNodeWithText("Fixture Developer").assertDoesNotExist()

            api.responseStatus = 200
            compose.onNodeWithText("Tentar novamente").performClick()
            compose.waitUntil(10_000) {
                api.requests.size == 2 &&
                    compose.onAllNodesWithText("Fixture Developer").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(listOf("GET /user HTTP/1.1", "GET /user HTTP/1.1"), api.requests)
        }
    }

    @Test fun unauthorizedProfileExpiresSessionOnceAndDoesNotShowLoadedProfile() {
        ProfileApi(initialStatus = 401).use { api ->
            val expired = java.util.concurrent.atomic.AtomicInteger()
            showProfileHome(api) { expired.incrementAndGet() }
            compose.waitUntil(10_000) { expired.get() == 1 }
            compose.onNodeWithText("Sessão: @fixture-user").assertIsDisplayed()
            compose.onNodeWithText("Fixture Developer").assertDoesNotExist()
            compose.runOnIdle { assertEquals(1, expired.get()) }
            assertEquals(listOf("GET /user HTTP/1.1"), api.requests)
        }
    }

    @Test fun rateLimitedProfileShowsExplicitRetry() {
        ProfileApi(initialStatus = 429).use { api ->
            showProfileHome(api)
            compose.waitUntil(10_000) {
                api.requests.size == 1 &&
                    compose.onAllNodesWithText("O GitHub limitou temporariamente as consultas.")
                        .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Sessão: @fixture-user").assertIsDisplayed()
            compose.onNodeWithText("Tentar novamente").assertIsDisplayed()
            compose.onNodeWithText("Fixture Developer").assertDoesNotExist()
        }
    }

    @Test fun serverFailureCanRecoverThroughRetry() {
        ProfileApi(initialStatus = 503).use { api ->
            showProfileHome(api)
            compose.waitUntil(10_000) {
                api.requests.size == 1 &&
                    compose.onAllNodesWithText("O GitHub está com instabilidade. Tente novamente mais tarde.")
                        .fetchSemanticsNodes().isNotEmpty()
            }
            api.responseStatus = 200
            compose.onNodeWithText("Tentar novamente").performClick()
            compose.waitUntil(10_000) {
                api.requests.size == 2 &&
                    compose.onAllNodesWithText("Fixture Developer").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test fun networkFailureCanRecoverThroughRetry() {
        ProfileApi().use { api ->
            api.dropNextResponse = true
            showProfileHome(api)
            compose.waitUntil(10_000) {
                api.requests.size == 1 &&
                    compose.onAllNodesWithText("Não foi possível conectar ao GitHub. Verifique sua rede.")
                        .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Tentar novamente").performClick()
            compose.waitUntil(10_000) {
                api.requests.size == 2 &&
                    compose.onAllNodesWithText("Fixture Developer").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun showProfileHome(api: ProfileApi, onSessionExpired: (GitHubSession) -> Unit = {}) {
        val session = GitHubSession(
            GitHubUser("fixture-user", "https://github.com/fixture-user"),
            SessionCredentials("fixture-token")
        )
        compose.setContent {
            ControleTheme(AppThemeMode.LIGHT) {
                GitHubHomeScreen(
                    androidx.compose.ui.Modifier,
                    GitHubHttpClient(api.baseUri),
                    session,
                    sessionRestoring = false,
                    onOpenRepositories = {},
                    onSessionExpired = onSessionExpired,
                    onLogout = {},
                    onAppearance = {}
                )
            }
        }
    }
}

private class ProfileApi(initialStatus: Int = 200) : AutoCloseable {
    private val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    val baseUri = java.net.URI("http://127.0.0.1:${server.localPort}/")
    val requests = CopyOnWriteArrayList<String>()
    @Volatile var responseStatus = initialStatus
    @Volatile var dropNextResponse = false
    private val worker = Thread {
        while (!server.isClosed) {
            runCatching {
                server.accept().use { socket ->
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                    requests += reader.readLine()
                    while (reader.readLine()?.isNotEmpty() == true) Unit
                    if (dropNextResponse) {
                        dropNextResponse = false
                        return@use
                    }
                    val body = if (responseStatus != 200) "{}" else if (requests.last().startsWith("GET /user/repos?") || requests.last().startsWith("GET /issues?") || requests.last().startsWith("GET /notifications?")) "[]" else
                        """{"login":"fixture-user","html_url":"https://github.com/fixture-user","name":"Fixture Developer","public_repos":0,"owned_private_repos":7,"followers":12}"""
                    val bytes = body.toByteArray(StandardCharsets.UTF_8)
                    socket.getOutputStream().apply {
                        val reason = when (responseStatus) {
                            200 -> "OK"
                            401 -> "Unauthorized"
                            403 -> "Forbidden"
                            429 -> "Too Many Requests"
                            else -> "Server Error"
                        }
                        write("HTTP/1.1 $responseStatus $reason\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                        write(bytes)
                        flush()
                    }
                }
            }
        }
    }.apply { isDaemon = true; start() }

    override fun close() {
        server.close()
        worker.join(1_000)
    }
}
