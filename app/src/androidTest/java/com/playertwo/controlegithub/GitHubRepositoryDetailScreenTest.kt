package com.playertwo.controlegithub

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GitHubRepositoryDetailScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun opensRealSelectionShowsInertReadmeRefreshesAndReturnsWithSearch() {
        DetailApi().use { api ->
            showRepositories(api)
            compose.waitUntil(10_000) {
                api.requestCount.get() == 1 &&
                    compose.onAllNodesWithText("acme/Mobile").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(hasSetTextAction()).performTextInput("mobile")
            compose.onNodeWithText("acme/Mobile").performClick()

            compose.waitUntil(10_000) {
                api.requestCount.get() == 3 &&
                    compose.onAllNodesWithText("Branch padrão").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("acme/Mobile").assertIsDisplayed()
            compose.onNodeWithText("Interno").assertIsDisplayed()
            compose.onNodeWithText("feature/mobile").assertIsDisplayed()
            compose.onNodeWithText("Estrelas").assertIsDisplayed()
            compose.onNodeWithText("0").assertIsDisplayed()
            compose.onNodeWithText("Forks").assertIsDisplayed()
            compose.onNodeWithText("Indisponível").assertIsDisplayed()
            compose.onNodeWithText("Issues + PRs abertas").assertIsDisplayed()
            compose.onNodeWithText("4").assertIsDisplayed()
            compose.onNodeWithText("Último push").assertIsDisplayed()

            val readme = "# Intro\n<script>literal</script>\n[link](https://example.test)\n```sh\necho safe\n```"
            compose.onNodeWithText(readme).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(readme).assertHasNoClickAction()
            assertTrue(api.paths.any { it.contains("/repos/acme/Mobile/readme?ref=feature%2Fmobile") })
            if (InstrumentationRegistry.getArguments().getString("maestroPreview") == "true") {
                Thread.sleep(60_000)
            }

            api.description = "Descrição atualizada no GitHub"
            compose.onNodeWithContentDescription("Atualizar detalhe").performClick()
            compose.waitUntil(10_000) {
                api.requestCount.get() >= 5 &&
                    compose.onAllNodesWithText("Descrição atualizada no GitHub").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Descrição atualizada no GitHub").assertIsDisplayed()
            compose.onNodeWithText("# Intro", substring = true).assertIsDisplayed()

            compose.onNodeWithContentDescription("Voltar aos repositórios").performClick()
            compose.onNodeWithText("Buscar repositórios").assertIsDisplayed()
            compose.onNodeWithText("mobile").assertIsDisplayed()
            compose.onNodeWithText("acme/Mobile").assertIsDisplayed()
            compose.onNodeWithText("acme/docs").assertDoesNotExist()
            assertTrue("Returning to the list triggered an unnecessary request", api.requestCount.get() == 5)
        }
    }

    @Test fun missingReadmeKeepsMetadataVisible() {
        DetailApi(readmeStatus = 404).use { api ->
            showRepositories(api)
            openDetail(api, expectedText = "README não encontrado para este repositório.")
            compose.onNodeWithText("README não encontrado para este repositório.").assertIsDisplayed()
            compose.onNodeWithText("Branch padrão").assertIsDisplayed()
            compose.onNodeWithText("Interno").assertIsDisplayed()
        }
    }

    @Test fun repositoryDetailOpensWorkflowRunAndJobStatusesForSelectedRepository() {
        DetailApi().use { api ->
            showRepositories(api)
            openDetail(api)

            compose.onNodeWithText("Ver GitHub Actions").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                api.responsesSent.any { it.startsWith("GET /repos/acme/Mobile/actions/workflows?") }
            }
            assertEquals(listOf("CI Android", "Deploy"), parseGitHubWorkflows(api.actionsResponses.single()).map { it.name })
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText("CI Android", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("CI Android", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Desativado manualmente", substring = true).assertIsDisplayed()
            compose.onNodeWithText("CI Android", substring = true).performClick()
            compose.waitUntil(10_000) {
                api.responsesSent.any { it.startsWith("GET /repos/acme/Mobile/actions/workflows/42/runs?") }
            }
            assertTrue("Workflow run endpoint was not called: ${api.paths}", api.paths.any { it.contains("/actions/workflows/42/runs?") })
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText("#17", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Falhou", substring = true).assertIsDisplayed()
            compose.onNodeWithText("#17", substring = true).performClick()
            compose.waitUntil(10_000) {
                api.responsesSent.any { it.startsWith("GET /repos/acme/Mobile/actions/runs/9001/jobs?") }
            }
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText("Build and test", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Build and test", substring = true).assertIsDisplayed()
            compose.onNodeWithText("main", substring = true).assertIsDisplayed()
            compose.onAllNodesWithText("Falhou", substring = true).assertCountEquals(2)
            assertTrue(api.paths.none { it.contains("/logs") || it.contains("/rerun") || it.contains("/cancel") })
        }
    }

    @Test fun repositoryActionsCanShowAllRecentRuns() {
        DetailApi().use { api ->
            showRepositories(api)
            openActions(api)
            compose.onNodeWithText("Todas as execuções").performClick()
            compose.waitUntil(10_000) {
                api.responsesSent.any { it.startsWith("GET /repos/acme/Mobile/actions/runs?") }
            }
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText("#17", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithText("Não foi possível interpretar a resposta do GitHub.")
                        .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Falhou", substring = true).assertIsDisplayed()
        }
    }

    @Test fun actionsEmptyStateIsNotAnError() {
        DetailApi(emptyActionsWorkflows = true).use { api ->
            showRepositories(api)
            openActions(api)
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText("Este repositório não tem workflows do GitHub Actions.")
                    .fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithText("Sua conta não tem permissão para acessar este recurso.")
                        .fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithText("Este recurso não está disponível para sua conta.")
                        .fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithText("Não foi possível interpretar a resposta do GitHub.")
                        .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Este repositório não tem workflows do GitHub Actions.").assertIsDisplayed()
            compose.onNodeWithText("Sua conta não tem permissão para acessar este recurso.").assertDoesNotExist()
        }
    }

    @Test fun forbiddenActionsRequestIsNotPresentedAsAnEmptyList() {
        DetailApi(actionsWorkflowsStatus = 403).use { api ->
            showRepositories(api)
            openActions(api)
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Sua conta não tem permissão para acessar este recurso.")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Este repositório não tem workflows do GitHub Actions.").assertDoesNotExist()
        }
    }

    @Test fun actionsPaginationLoadsOnlyTheNextValidatedPageAndDeduplicates() {
        DetailApi(paginatedActionsWorkflows = true).use { api ->
            showRepositories(api)
            openActions(api)
            compose.waitUntil(10_000) {
                api.responsesSent.any { it.startsWith("GET /repos/acme/Mobile/actions/workflows?") } &&
                    compose.onAllNodesWithText("CI Android", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Carregar mais").performClick()
            compose.waitUntil(10_000) {
                api.responsesSent.any { it.contains("/actions/workflows?page=2") } &&
                    compose.onAllNodesWithText("Deploy", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(1, compose.onAllNodesWithText("CI Android", substring = true).fetchSemanticsNodes().size)
            assertEquals(2, api.paths.count { it.contains("/actions/workflows") })
            compose.onNodeWithText("Carregar mais").assertDoesNotExist()
        }
    }

    @Test fun unauthorizedActionsRequestExpiresSessionOnce() {
        DetailApi(actionsWorkflowsStatus = 401).use { api ->
            val expired = AtomicInteger()
            showRepositories(api) { expired.incrementAndGet() }
            openActions(api)
            compose.waitUntil(10_000) { expired.get() == 1 }
            assertEquals(1, expired.get())
            compose.onNodeWithText("Sua sessão expirou. Conecte-se novamente.").assertIsDisplayed()
        }
    }

    @Test fun metadataFailureIsNotPresentedAsAnEmptyReadme() {
        DetailApi(metadataStatus = 404).use { api ->
            showRepositories(api)
            openDetail(api, expectedRequests = 2, expectedText = "Este recurso não está disponível para sua conta.")
            compose.onNodeWithText("Este recurso não está disponível para sua conta.").assertIsDisplayed()
            compose.onNodeWithText("README não encontrado para este repositório.").assertDoesNotExist()
        }
    }

    @Test fun unauthorizedMetadataExpiresSessionAndStopsBeforeReadme() {
        DetailApi(metadataStatus = 401).use { api ->
            val expired = AtomicInteger()
            showRepositories(api) { expired.incrementAndGet() }
            openDetail(api, expectedRequests = 2, expectedText = "Sua sessão expirou. Conecte-se novamente.")
            compose.onNodeWithText("Sua sessão expirou. Conecte-se novamente.").assertIsDisplayed()
            compose.waitUntil(5_000) { expired.get() == 1 }
            assertTrue("Unauthorized metadata should stop before README", api.readmeRequests.get() == 0)
        }
    }

    @Test fun unauthorizedReadmeExpiresSessionWhileKeepingMetadata() {
        DetailApi(readmeStatus = 401).use { api ->
            val expired = AtomicInteger()
            showRepositories(api) { expired.incrementAndGet() }
            openDetail(api, expectedText = "Sua sessão expirou. Conecte-se novamente.")
            compose.onNodeWithText("Sua sessão expirou. Conecte-se novamente.").assertIsDisplayed()
            compose.onNodeWithText("Branch padrão").assertIsDisplayed()
            compose.waitUntil(5_000) { expired.get() == 1 }
            assertTrue("README authorization error should be requested once", api.readmeRequests.get() == 1)
        }
    }

    @Test fun forbiddenReadmeOffersRetryWithoutExpiringSession() {
        DetailApi(readmeStatus = 403).use { api ->
            val expired = AtomicInteger()
            showRepositories(api) { expired.incrementAndGet() }
            openDetail(api, expectedText = "Sua conta não tem permissão para acessar este recurso.")
            compose.onNodeWithText("Sua conta não tem permissão para acessar este recurso.").assertIsDisplayed()
            compose.onNodeWithText("Tentar novamente").assertIsDisplayed()
            assertTrue("Forbidden is not an expired session", expired.get() == 0)
        }
    }

    @Test fun rateLimitedReadmeShowsRetryDeadlineAndAction() {
        DetailApi(readmeStatus = 429).use { api ->
            showRepositories(api)
            openDetail(api, expectedText = "O GitHub limitou temporariamente as consultas.")
            compose.onNodeWithText("O GitHub limitou temporariamente as consultas.").assertIsDisplayed()
            compose.onNodeWithText("Tente novamente após", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Tentar novamente").assertIsDisplayed()
        }
    }

    @Test fun transientNetworkFailureCanBeRetriedSuccessfully() {
        DetailApi(failMetadataNetworkOnce = true).use { api ->
            showRepositories(api)
            openDetail(api, expectedRequests = 2, expectedText = "Não foi possível conectar ao GitHub. Verifique sua rede.")
            compose.onNodeWithText("Não foi possível conectar ao GitHub. Verifique sua rede.").assertIsDisplayed()
            compose.onNodeWithText("Tentar novamente").performClick()
            compose.waitUntil(10_000) {
                api.requestCount.get() == 4 &&
                    compose.onAllNodesWithText("Branch padrão").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("README").assertIsDisplayed()
        }
    }

    @Test fun serverFailureCanBeRetriedSuccessfully() {
        DetailApi(failMetadataServerOnce = true, metadataRetryDelayMillis = 1_000).use { api ->
            showRepositories(api)
            openDetail(api, expectedRequests = 2, expectedText = "O GitHub está com instabilidade. Tente novamente mais tarde.")
            compose.onNodeWithText("O GitHub está com instabilidade. Tente novamente mais tarde.").assertIsDisplayed()
            compose.onNodeWithText("Tentar novamente").performClick()
            compose.waitUntil(5_000) {
                api.metadataRequests.get() == 2 &&
                    compose.onAllNodesWithText("Tentar novamente").fetchSemanticsNodes()
                        .any { it.config.contains(SemanticsProperties.Disabled) }
            }
            compose.onNodeWithText("Tentar novamente").assertIsNotEnabled()
            assertTrue("A disabled retry must not issue another metadata request", api.metadataRequests.get() == 2)
            compose.waitUntil(10_000) {
                api.requestCount.get() >= 4 &&
                    compose.onAllNodesWithText("Branch padrão").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("README").assertIsDisplayed()
        }
    }

    private fun openDetail(api: DetailApi, expectedRequests: Int = 3, expectedText: String = "Branch padrão") {
        compose.waitUntil(10_000) {
            api.requestCount.get() == 1 &&
                compose.onAllNodesWithText("acme/Mobile").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("acme/Mobile").performClick()
        compose.waitUntil(10_000) {
            api.requestCount.get() >= expectedRequests &&
                compose.onAllNodesWithText(expectedText).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openActions(api: DetailApi) {
        openDetail(api)
        compose.onNodeWithText("Ver GitHub Actions").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            api.responsesSent.any { it.startsWith("GET /repos/acme/Mobile/actions/workflows?") }
        }
    }

    private fun showRepositories(api: DetailApi, onSessionExpired: () -> Unit = {}) {
        val client = GitHubHttpClient(baseUrl = api.baseUrl)
        val session = GitHubSession(
            GitHubUser("fixture-user", "https://github.com/fixture-user"),
            SessionCredentials("instrumentation-fixture-token")
        )
        compose.setContent {
            ControleTheme(AppThemeMode.LIGHT) {
                GitHubRepositoriesScreen(
                    modifier = Modifier,
                    client = client,
                    session = session,
                    sessionRestoring = false,
                    sessionStorageError = false,
                    sessionExpired = false,
                    onConnected = { true },
                    onSessionExpired = { onSessionExpired() },
                    onLogout = {},
                    onAppearance = {}
                )
            }
        }
    }

    private class DetailApi(
        private val metadataStatus: Int = 200,
        private val readmeStatus: Int = 200,
        private val failMetadataNetworkOnce: Boolean = false,
        private val failMetadataServerOnce: Boolean = false,
        private val metadataRetryDelayMillis: Long = 0,
        private val actionsWorkflowsStatus: Int = 200,
        private val emptyActionsWorkflows: Boolean = false,
        private val paginatedActionsWorkflows: Boolean = false
    ) : AutoCloseable {
        private val server = ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))
        val baseUrl = URI.create("http://127.0.0.1:${server.localPort}/")
        val requestCount = AtomicInteger()
        val readmeRequests = AtomicInteger()
        val metadataRequests = AtomicInteger()
        val paths = ConcurrentLinkedQueue<String>()
        val actionsResponses = ConcurrentLinkedQueue<String>()
        val responsesSent = ConcurrentLinkedQueue<String>()
        @Volatile var description = "Fixture detail"
        private val readme = "# Intro\n<script>literal</script>\n[link](https://example.test)\n```sh\necho safe\n```"
        private val readmeBody = JSONObject()
            .put("encoding", "base64")
            .put("content", Base64.getEncoder().encodeToString(readme.toByteArray(StandardCharsets.UTF_8)))
            .toString()

        private val acceptThread = Thread {
            while (!server.isClosed) {
                try {
                    var responseRequestLine: String? = null
                    server.accept().use { socket ->
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                        val requestLine = reader.readLine().orEmpty()
                        var line = reader.readLine()
                        while (!line.isNullOrEmpty()) line = reader.readLine()
                        requestCount.incrementAndGet()
                        paths.add(requestLine)

                        if (requestLine.startsWith("GET /repos/acme/Mobile/readme")) readmeRequests.incrementAndGet()
                        val metadataAttempt = if (requestLine.startsWith("GET /repos/acme/Mobile ")) metadataRequests.incrementAndGet() else 0
                        if (requestLine.startsWith("GET /repos/acme/Mobile ") && failMetadataNetworkOnce && metadataAttempt == 1) {
                            continue
                        }
                        val (status, body) = when {
                            requestLine.startsWith("GET /user/repos?") -> 200 to repositoryList
                            requestLine.startsWith("GET /repos/acme/Mobile/actions/workflows?page=2") -> 200 to workflowsNextPageBody
                            requestLine.startsWith("GET /repos/acme/Mobile/actions/workflows?") -> actionsWorkflowsStatus to when {
                                actionsWorkflowsStatus != 200 -> "{}"
                                emptyActionsWorkflows -> """{"total_count":0,"workflows":[]}"""
                                paginatedActionsWorkflows -> workflowsFirstPageBody
                                else -> workflowsBody
                            }
                            requestLine.startsWith("GET /repos/acme/Mobile/actions/runs?") -> 200 to runsBody
                            requestLine.startsWith("GET /repos/acme/Mobile/actions/workflows/42/runs?") -> 200 to runsBody
                            requestLine.startsWith("GET /repos/acme/Mobile/actions/runs/9001/jobs?") -> 200 to jobsBody
                            requestLine.startsWith("GET /repos/acme/Mobile/readme") -> readmeStatus to if (readmeStatus == 200) readmeBody else "{}"
                            requestLine.startsWith("GET /repos/acme/Mobile ") -> {
                                val selectedStatus = if (failMetadataServerOnce && metadataAttempt == 1) 503 else metadataStatus
                                selectedStatus to if (selectedStatus == 200) metadataBody() else "{}"
                            }
                            else -> 404 to "{}"
                        }
                        if (requestLine.startsWith("GET /repos/acme/Mobile/actions/workflows?")) actionsResponses.add(body)
                        if (metadataAttempt > 1 && metadataRetryDelayMillis > 0) {
                            Thread.sleep(metadataRetryDelayMillis)
                        }
                        val bytes = body.toByteArray(StandardCharsets.UTF_8)
                        val reason = when (status) {
                            200 -> "OK"
                            401 -> "Unauthorized"
                            403 -> "Forbidden"
                            429 -> "Too Many Requests"
                            503 -> "Service Unavailable"
                            else -> "Not Found"
                        }
                        val headers = (
                            "HTTP/1.1 $status $reason\r\n" +
                                "Content-Type: application/json\r\n" +
                                (if (status == 429) "Retry-After: 2\r\n" else "") +
                                (if (paginatedActionsWorkflows && requestLine.startsWith("GET /repos/acme/Mobile/actions/workflows?"))
                                    "Link: <http://127.0.0.1:${server.localPort}/repos/acme/Mobile/actions/workflows?page=2&per_page=50>; rel=\"next\"\r\n"
                                else "") +
                                "Content-Length: ${bytes.size}\r\n" +
                                "Connection: close\r\n\r\n"
                            ).toByteArray(StandardCharsets.UTF_8)
                        socket.getOutputStream().apply {
                            write(headers)
                            write(bytes)
                            flush()
                        }
                        responseRequestLine = requestLine
                    }
                    responseRequestLine?.let(responsesSent::add)
                } catch (_: java.net.SocketException) { }
            }
        }.apply { name = "repository-detail-fixture"; isDaemon = true; start() }

        override fun close() {
            server.close()
            acceptThread.join(1_000)
        }

        private fun metadataBody() = metadata.replace("Fixture detail", description)

        private val workflowsBody = """{"total_count":2,"workflows":[
            {"id":42,"name":"CI Android","path":".github/workflows/android.yml","state":"active"},
            {"id":43,"name":"Deploy","path":".github/workflows/deploy.yml","state":"disabled_manually"}]}"""
        private val workflowsFirstPageBody = """{"total_count":2,"workflows":[
            {"id":42,"name":"CI Android","path":".github/workflows/android.yml","state":"active"}]}"""
        private val workflowsNextPageBody = """{"total_count":2,"workflows":[
            {"id":42,"name":"CI Android duplicado","path":".github/workflows/android.yml","state":"active"},
            {"id":43,"name":"Deploy","path":".github/workflows/deploy.yml","state":"disabled_manually"}]}"""
        private val runsBody = """{"total_count":1,"workflow_runs":[
            {"id":9001,"workflow_id":42,"name":"CI Android","run_number":17,"event":"push","status":"completed","conclusion":"failure","head_branch":"main","head_sha":"${"a".repeat(40)}","created_at":"2026-10-05T11:00:00Z","run_started_at":"2026-10-05T11:00:01Z","updated_at":"2026-10-05T11:01:00Z"}]}"""
        private val jobsBody = """{"total_count":1,"jobs":[
            {"id":500,"run_id":9001,"name":"Build and test","status":"completed","conclusion":"failure","started_at":"2026-10-05T11:00:01Z","completed_at":"2026-10-05T11:01:00Z"}]}"""

        private companion object {
            val repositoryList = """[
              {"id":1,"name":"Mobile","full_name":"acme/Mobile","owner":{"login":"acme"},"private":true,"description":"fixture list row","language":"Kotlin","stargazers_count":0},
              {"id":2,"name":"docs","full_name":"acme/docs","owner":{"login":"acme"},"private":false,"description":null,"language":"Markdown","stargazers_count":0}
            ]"""
            const val metadata = """{
              "id":1,"name":"Mobile","full_name":"acme/Mobile","owner":{"login":"acme"},
              "private":true,"visibility":"internal","default_branch":"feature/mobile",
              "description":"Fixture detail","stargazers_count":0,"forks_count":null,
              "open_issues_count":4,"pushed_at":"2026-09-30T10:20:30Z"
            }"""
        }
    }
}
