package com.playertwo.controlegithub

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.BufferedReader
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GitHubNotificationsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun forbiddenOAuthIsExplicitAndNeverRendersEmptyState() {
        NotificationsScreenApi(initialStatus = 403, notificationsJson = "{}").use { api ->
            var sessionExpired = false
            showNotifications(api, onSessionExpired = { sessionExpired = true })

            compose.waitUntil(10_000) {
                api.requests.isNotEmpty() &&
                    compose.onAllNodesWithText("O GitHub não aceitou o acesso OAuth às notificações.")
                        .fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithText("Nenhuma notificação.").assertDoesNotExist()
            assertEquals(1, api.requests.size)
            assertEquals(false, sessionExpired)
        }
    }

    @Test
    fun emptyStateAppearsOnlyAfterAValidSuccessfulResponse() {
        NotificationsScreenApi(notificationsJson = "[]").use { api ->
            showNotifications(api)
            compose.waitUntil(10_000) {
                api.requests.isNotEmpty() &&
                    compose.onAllNodesWithText("Nenhuma notificação.").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(api.requests.single().startsWith("GET /notifications?all=true&per_page=50"))
        }
    }

    @Test
    fun opensOriginAndBackReturnsToNotificationAndPreservesFilters() {
        NotificationsScreenApi(notificationsJson = NOTIFICATION).use { api ->
            showNotifications(api)
            compose.waitUntil(10_000) {
                api.requests.isNotEmpty() &&
                    compose.onAllNodesWithText("Fixture notification").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Não lidas").performClick()
            compose.onNodeWithText("Issue").performClick()
            compose.onNodeWithText("Não lidas").assertIsSelected()
            compose.onNodeWithText("Issue").assertIsSelected()
            compose.onNodeWithText("Fixture notification").performClick()
            compose.onNodeWithText("Abrir item de origem").performClick()

            compose.waitUntil(10_000) {
                api.requests.any { it.startsWith("GET /repos/fixture-owner/fixture-repo/issues/18 ") } &&
                    compose.onAllNodesWithText("Fixture actual issue").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Voltar às issues").performClick()
            compose.onNodeWithText("Abrir item de origem").assertIsDisplayed()
            compose.onNodeWithContentDescription("Voltar aos avisos").performClick()

            compose.onNodeWithText("Fixture notification").assertIsDisplayed()
            compose.onNodeWithText("Não lidas").assertIsSelected()
            compose.onNodeWithText("Issue").assertIsSelected()
            assertTrue(api.requests.any { it.startsWith("GET /repos/fixture-owner/fixture-repo/issues/18/comments") })
        }
    }

    @Test
    fun markingNotificationAsReadSendsPatchAndUpdatesLocalStateToRead() {
        NotificationsScreenApi(notificationsJson = NOTIFICATION).use { api ->
            showNotifications(api)
            compose.waitUntil(10_000) {
                api.requests.isNotEmpty() &&
                    compose.onAllNodesWithText("Fixture notification").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Não lidas").performClick()
            compose.onNodeWithText("Fixture notification").performClick()

            compose.onNodeWithText("Não lida · Atribuída a você").assertIsDisplayed()
            compose.onNodeWithText("Marcar como lida").assertIsDisplayed()
            compose.onNodeWithText("Marcar como lida").performClick()

            compose.waitUntil(10_000) {
                api.requests.any { it.startsWith("PATCH /notifications/threads/thread-1") } &&
                    compose.onAllNodesWithText("Lida · Atribuída a você").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithText("Marcar como lida").assertDoesNotExist()
            compose.onNodeWithContentDescription("Voltar aos avisos").performClick()
            compose.onNodeWithText("Nenhuma notificação encontrada com estes filtros.").assertIsDisplayed()
        }
    }

    @Test
    fun markingNotificationAsReadFailurePreservesUnreadAndDisplaysErrorMessage() {
        NotificationsScreenApi(notificationsJson = NOTIFICATION, patchStatus = 500).use { api ->
            showNotifications(api)
            compose.waitUntil(10_000) {
                api.requests.isNotEmpty() &&
                    compose.onAllNodesWithText("Fixture notification").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Fixture notification").performClick()
            compose.onNodeWithText("Não lida · Atribuída a você").assertIsDisplayed()
            compose.onNodeWithText("Marcar como lida").performClick()

            compose.waitUntil(10_000) {
                api.requests.any { it.startsWith("PATCH /notifications/threads/thread-1") } &&
                    compose.onAllNodesWithText(GitHubHttpError.SERVER.userMessage).fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithText("Não lida · Atribuída a você").assertIsDisplayed()
            compose.onNodeWithText("Marcar como lida").assertIsDisplayed()
        }
    }

    @Test
    fun markingNotificationAsReadWith304UpdatesLocalStateToRead() {
        NotificationsScreenApi(notificationsJson = NOTIFICATION, patchStatus = 304).use { api ->
            showNotifications(api)
            compose.waitUntil(10_000) {
                api.requests.isNotEmpty() &&
                    compose.onAllNodesWithText("Fixture notification").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Fixture notification").performClick()
            compose.onNodeWithText("Marcar como lida").performClick()

            compose.waitUntil(10_000) {
                api.requests.any { it.startsWith("PATCH /notifications/threads/thread-1") } &&
                    compose.onAllNodesWithText("Lida · Atribuída a você").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Marcar como lida").assertDoesNotExist()
        }
    }

    @Test
    fun markingNotificationAsReadWith401PreservesUnreadAndNotifiesSessionExpiry() {
        NotificationsScreenApi(notificationsJson = NOTIFICATION, patchStatus = 401).use { api ->
            var sessionExpired = false
            showNotifications(api, onSessionExpired = { sessionExpired = true })
            compose.waitUntil(10_000) {
                api.requests.isNotEmpty() &&
                    compose.onAllNodesWithText("Fixture notification").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Fixture notification").performClick()
            compose.onNodeWithText("Marcar como lida").performClick()

            compose.waitUntil(10_000) {
                api.requests.any { it.startsWith("PATCH /notifications/threads/thread-1") } &&
                    compose.onAllNodesWithText(GitHubHttpError.UNAUTHORIZED.userMessage).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Não lida · Atribuída a você").assertIsDisplayed()
            assertTrue(sessionExpired)
        }
    }

    @Test
    fun releaseNotificationOpensSelectedRepositoryAndPreservesInbox() {
        NotificationsScreenApi(
            notificationsJson = releaseNotification(),
            originResponses = otherRepoResponses()
        ).use { api ->
            showNotifications(api)
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Fixture release notice").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(hasText("Outro") and isSelectable()).performScrollTo().performClick()
            compose.onNode(hasText("Outro") and isSelectable()).assertIsSelected()
            compose.onNodeWithText("Fixture release notice").performClick()
            compose.onNodeWithText("Abrir item de origem").performClick()

            compose.waitUntil(10_000) {
                api.requests.any { it.startsWith("GET /repos/other-owner/other-repo ") } &&
                    compose.onAllNodesWithText("Fixture readme", useUnmergedTree = true)
                        .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("other-owner/other-repo").assertIsDisplayed()
            compose.onNodeWithContentDescription("Voltar à notificação").assertIsDisplayed()
            compose.onNodeWithContentDescription("Voltar aos repositórios").assertDoesNotExist()
            assertTrue(api.requests.none { it.contains("captured/name") })

            compose.onNodeWithTag("code_browser_button").performClick()
            compose.waitUntil(10_000) {
                api.requests.any { it.startsWith("GET /repos/other-owner/other-repo/contents?ref=main") } &&
                    compose.onAllNodesWithText("Esta pasta está vazia.").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Voltar").performClick()

            compose.onNodeWithText("GitHub Actions", useUnmergedTree = true).performScrollTo().performClick()
            compose.waitUntil(10_000) {
                api.requests.any { it.startsWith("GET /repos/other-owner/other-repo/actions/workflows?per_page=50") } &&
                    compose.onAllNodesWithText("Este repositório não tem workflows do GitHub Actions.")
                        .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Voltar").performClick()

            compose.onNodeWithText("Nova issue", useUnmergedTree = true).performScrollTo().performClick()
            compose.onNodeWithText("Criar issue").assertIsDisplayed()
            compose.onNodeWithContentDescription("Cancelar criação de issue").performClick()

            compose.onNodeWithText("Releases e APKs", useUnmergedTree = true).performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("nenhum APK", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(api.requests.any { it.contains("/repos/other-owner/other-repo/releases?") })
            compose.onNodeWithText("nenhum APK", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("other-owner/other-repo").assertIsDisplayed()
            compose.onNodeWithContentDescription("Voltar").performClick()
            compose.onNodeWithContentDescription("Voltar à notificação").performClick()
            compose.onNodeWithContentDescription("Voltar aos avisos").performClick()

            compose.onNodeWithText("Fixture release notice").assertIsDisplayed()
            compose.onNode(hasText("Outro") and isSelectable()).assertIsSelected()
            assertEquals(1, api.requests.count { it.startsWith("GET /notifications") })
        }
    }

    @Test
    fun inaccessibleRepositoryStaysExplicit() {
        NotificationsScreenApi(notificationsJson = releaseNotification(), repositoryStatus = 403).use { api ->
            showNotifications(api)
            openReleaseOrigin()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(GitHubHttpError.FORBIDDEN.userMessage).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Nenhuma notificação.").assertDoesNotExist()
            compose.onNodeWithText("Sem descrição").assertDoesNotExist()
            compose.onNodeWithText("Público").assertDoesNotExist()
        }
    }

    @Test
    fun networkFailureStaysExplicit() {
        NotificationsScreenApi(notificationsJson = releaseNotification(), dropRepository = true).use { api ->
            showNotifications(api)
            openReleaseOrigin()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(GitHubHttpError.NETWORK.userMessage).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Nenhuma notificação.").assertDoesNotExist()
            compose.onNodeWithText("Sem descrição").assertDoesNotExist()
        }
    }

    @Test
    fun loadedPagesAndScrollSurviveReturnFromRepository() {
        NotificationsScreenApi(
            notificationsJson = notificationPage(1, 3),
            secondPageJson = notificationPage(4, 18)
        ).use { api ->
        showNotifications(api)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Aviso 01").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("Outro") and isSelectable()).performScrollTo().performClick()
        compose.onNodeWithText("Carregar mais notificações").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Aviso 04").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Aviso 18"))
        compose.onNodeWithText("Aviso 18").performClick()
            compose.onNodeWithText("Abrir item de origem").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("Voltar à notificação").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Voltar à notificação").performClick()
            compose.onNodeWithContentDescription("Voltar aos avisos").performClick()

            compose.onNodeWithText("Aviso 18").assertIsDisplayed()
            compose.onNodeWithText("Aviso 01").assertDoesNotExist()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Outro") and isSelectable())
            compose.onNode(hasText("Outro") and isSelectable()).assertIsSelected()
            assertEquals(2, api.requests.count { it.startsWith("GET /notifications") })
        }
    }

    @Test
    fun releaseFromNotificationReachesManualApkInstall() {
        val assetId = 250251L
        val workName = releaseAssetWorkName(42, assetId)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(workName).result.get()
        try {
            NotificationsScreenApi(
                notificationsJson = releaseNotification(),
                originResponses = otherRepoResponses(assetId)
            ).use { api ->
                showNotifications(api)
                openReleaseOrigin()
                compose.onNodeWithText("Releases e APKs", useUnmergedTree = true).performScrollTo().performClick()
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText("1 APK disponível").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("Com APK").performClick()
                compose.onNodeWithText("app-release.apk").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Baixar").assertIsDisplayed()
                compose.onNodeWithText("Instalar").assertDoesNotExist()
                compose.onNodeWithText("Permitir instalação manual").assertDoesNotExist()

                val preparing = OneTimeWorkRequestBuilder<CompletedReleaseDownloadWorkerForUiTest>()
                    .setInitialDelay(10, TimeUnit.MINUTES)
                    .build()
                manager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, preparing).result.get()
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText("Preparando download…").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("Cancelar").assertIsDisplayed()
                compose.onNodeWithText("Instalar").assertDoesNotExist()
                manager.cancelUniqueWork(workName).result.get()

                val done = OneTimeWorkRequestBuilder<CompletedReleaseDownloadWorkerForUiTest>().setInputData(
                    workDataOf(
                        ReleaseDownloadKeys.OUTPUT_URI to "content://media/external/downloads/1",
                        ReleaseDownloadKeys.HASH_STATUS to "verified",
                        ReleaseDownloadKeys.HASH_SOURCE to "GitHub"
                    )
                ).build()
                manager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, done).result.get()
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText("Instalar").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("SHA-256 confere (GitHub)", substring = true).assertIsDisplayed()
                assertTrue(
                    "O teste requer permissão de instalação manual negada no AVD",
                    !context.packageManager.canRequestPackageInstalls()
                )
                compose.onNodeWithText("Instalar").performClick()
                compose.onNodeWithText("Permitir instalação manual").assertIsDisplayed()
                compose.onNodeWithText("Cancelar").performClick()
                compose.onNodeWithText("Permitir instalação manual").assertDoesNotExist()
                compose.onNodeWithText("Instalar").assertIsDisplayed()
            }
        } finally {
            manager.cancelUniqueWork(workName)
        }
    }

    @Test
    fun notificationCardDirectActionsOpenRepositoryAndReleasesForAnyNotice() {
        NotificationsScreenApi(
            notificationsJson = """
                [{"id":"thread-issue","unread":true,"reason":"assign","updated_at":"2026-10-01T12:00:00Z",
                  "subject":{"title":"Issue notice with repo","type":"Issue","url":"https://api.github.com/repos/other-owner/other-repo/issues/18"},
                  "repository":{"id":42,"full_name":"other-owner/other-repo"}}]
            """,
            originResponses = otherRepoResponses()
        ).use { api ->
            showNotifications(api)
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Issue notice with repo").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("notification-card-open-repo-thread-issue").performClick()
            compose.waitUntil(10_000) {
                api.requests.any { it.startsWith("GET /repos/other-owner/other-repo ") } &&
                    compose.onAllNodesWithText("Fixture readme", useUnmergedTree = true)
                        .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Voltar à notificação").performClick()
            compose.onNodeWithText("Issue notice with repo").assertIsDisplayed()

            compose.onNodeWithTag("notification-card-open-releases-thread-issue").performClick()
            compose.waitUntil(10_000) {
                api.requests.any { it.contains("/repos/other-owner/other-repo/releases?") } &&
                    compose.onAllNodesWithText("1 APK disponível").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Voltar").performClick()
            compose.onNodeWithContentDescription("Voltar à notificação").performClick()
            compose.onNodeWithText("Issue notice with repo").assertIsDisplayed()
        }
    }

    private fun openReleaseOrigin() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Fixture release notice").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Fixture release notice").performClick()
        compose.onNodeWithText("Abrir item de origem").performClick()
    }

    private fun showNotifications(
        api: NotificationsScreenApi,
        session: GitHubSession = notificationFixtureSession(),
        onSessionExpired: (GitHubSession) -> Unit = {}
    ) {
        compose.setContent {
            ControleTheme(AppThemeMode.LIGHT) {
                GitHubNotificationsScreen(
                    modifier = Modifier,
                    client = GitHubHttpClient(api.baseUri),
                    session = session,
                    sessionRestoring = false,
                    onSessionExpired = onSessionExpired,
                    onAppearance = {}
                )
            }
        }
    }

    private companion object {
        const val NOTIFICATION = """
            [{"id":"thread-1","unread":true,"reason":"assign","updated_at":"2026-10-01T12:00:00Z",
              "subject":{"title":"Fixture notification","type":"Issue","url":"https://api.github.com/repos/fixture-owner/fixture-repo/issues/18"},
              "repository":{"id":1,"full_name":"fixture-owner/fixture-repo"}}]
        """
    }
}

private fun notificationFixtureSession(login: String = "fixture-user") = GitHubSession(
    GitHubUser(login, "https://github.com/$login"),
    SessionCredentials("notification-fixture-token")
)

private fun releaseNotification() = """
    [{"id":"thread-release","unread":true,"reason":"ci_activity","updated_at":"2026-10-01T12:00:00Z",
      "subject":{"title":"Fixture release notice","type":"Release","url":"https://api.github.com/repos/captured/name/releases/1"},
      "repository":{"id":42,"full_name":"other-owner/other-repo"}}]
"""

private fun otherRepoResponses(apkAssetId: Long = 250251): Map<String, String> {
    val readme = java.util.Base64.getEncoder().encodeToString("Fixture readme".toByteArray())
    val root = "/repos/other-owner/other-repo"
    val detail = """{"id":42,"name":"other-repo","full_name":"other-owner/other-repo","owner":{"login":"other-owner"},"private":false,"description":"Fixture detail","stargazers_count":0,"forks_count":0,"open_issues_count":0,"default_branch":"main","visibility":"public"}"""
    val releases = """[{"id":8,"tag_name":"v0","name":"Sem pacote","draft":false,"prerelease":false,"published_at":"2026-10-01T00:00:00Z","assets":[{"id":3,"name":"notes.txt","size":12,"download_count":0,"content_type":"text/plain"}]},{"id":9,"tag_name":"v1","name":"Com APK","draft":false,"prerelease":false,"published_at":"2026-10-18T00:00:00Z","assets":[{"id":$apkAssetId,"name":"app-release.apk","size":128,"download_count":1,"content_type":"application/vnd.android.package-archive","browser_download_url":"https://github.com/other-owner/other-repo/releases/download/v1/app-release.apk"}]}]"""
    return mapOf(
        root to detail,
        "$root/readme?ref=main" to """{"encoding":"base64","content":"$readme"}""",
        "$root/contents?ref=main" to "[]",
        "$root/branches?per_page=100" to "[]",
        "$root/tags?per_page=100" to "[]",
        "$root/actions/workflows?per_page=50" to """{"workflows":[]}""",
        "$root/releases?per_page=30" to releases
    )
}

private fun notificationPage(start: Int, end: Int) = (start..end).joinToString(prefix = "[", postfix = "]") { number ->
    val label = "%02d".format(number)
    """{"id":"aviso-$label","unread":true,"reason":"ci_activity","updated_at":"2026-10-01T12:00:00Z","subject":{"title":"Aviso $label","type":"Release","url":null},"repository":{"id":1,"full_name":"fixture-owner/fixture-repo"}}"""
}

private class NotificationsScreenApi(
    private val initialStatus: Int = 200,
    private val notificationsJson: String,
    private val originResponses: Map<String, String> = emptyMap(),
    private val patchStatus: Int = 205,
    private val repositoryStatus: Int = 200,
    private val dropRepository: Boolean = false,
    private val secondPageJson: String? = null
) : AutoCloseable {
    private val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    val baseUri = java.net.URI.create("http://127.0.0.1:${server.localPort}/")
    val requests = CopyOnWriteArrayList<String>()
    val authorizations = CopyOnWriteArrayList<String?>()
    private val worker = Thread {
        while (!server.isClosed) runCatching {
            server.accept().use { socket ->
                val input = BufferedReader(socket.getInputStream().reader(StandardCharsets.US_ASCII))
                val request = input.readLine().orEmpty()
                requests += request
                val headers = buildList {
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        add(line)
                    }
                }
                authorizations += headers.firstOrNull { it.startsWith("Authorization:", ignoreCase = true) }
                    ?.substringAfter(':')?.trim()
                if (request.startsWith("PATCH /notifications/threads/")) {
                    val status = patchStatus
                    val statusText = when (status) {
                        205 -> "Reset Content"
                        304 -> "Not Modified"
                        401 -> "Unauthorized"
                        403 -> "Forbidden"
                        404 -> "Not Found"
                        429 -> "Too Many Requests"
                        else -> "Server Error"
                    }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 $status $statusText\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                        flush()
                    }
                    return@use
                }
                val path = request.substringAfter("GET ").substringBefore(" HTTP")
                val exactRepository = path.matches(Regex("/repos/[^/]+/[^/]+"))
                if (dropRepository && exactRepository) {
                    socket.close()
                    return@use
                }
                val status = when {
                    path.startsWith("/notifications") -> initialStatus
                    exactRepository && repositoryStatus != 200 -> repositoryStatus
                    else -> 200
                }
                val body = when {
                    secondPageJson != null && path.contains("page=2") -> secondPageJson
                    path.startsWith("/notifications") -> notificationsJson
                    else -> originResponses[path] ?: when {
                        path.matches(Regex("/repos/[^/]+/[^/]+/issues/[0-9]+")) -> ISSUE_DETAIL
                        path.contains("/comments?") -> "[]"
                        path.startsWith("/repos/") -> REPOSITORY_DETAIL
                        else -> "{}"
                    }
                }
                val statusText = when (status) {
                    200 -> "OK"
                    401 -> "Unauthorized"
                    403 -> "Forbidden"
                    429 -> "Too Many Requests"
                    else -> "Server Error"
                }
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                val link = if (secondPageJson != null && path.startsWith("/notifications") && !path.contains("&page=") && !path.contains("?page=")) {
                    "Link: <http://127.0.0.1:${server.localPort}/notifications?all=true&per_page=50&page=2>; rel=next\r\n"
                } else ""
                socket.getOutputStream().apply {
                    write("HTTP/1.1 $status $statusText\r\n${link}Content-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                    write(bytes)
                    flush()
                }
            }
        }
    }.apply { isDaemon = true; start() }

    override fun close() {
        server.close()
        worker.join(1_000)
    }

    private companion object {
        const val ISSUE_DETAIL = """{"number":18,"title":"Fixture actual issue","state":"open","html_url":"https://github.com/fixture-owner/fixture-repo/issues/18","body":"Fixture body","repository":{"full_name":"fixture-owner/fixture-repo"}}"""
        const val REPOSITORY_DETAIL = """{"id":1,"name":"fixture-repo","full_name":"fixture-owner/fixture-repo","owner":{"login":"fixture-owner"},"private":false,"stargazers_count":0,"forks_count":0,"open_issues_count":0,"default_branch":"main","visibility":"public"}"""
    }
}
