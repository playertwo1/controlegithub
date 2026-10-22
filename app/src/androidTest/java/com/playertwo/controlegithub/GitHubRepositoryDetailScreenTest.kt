package com.playertwo.controlegithub

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.workDataOf
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.pressBack
import androidx.test.filters.SdkSuppress
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GitHubRepositoryDetailScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun opensRealSelectionRendersMarkdownAndConfirmsExternalLinks() {
        DetailApi().use { api ->
            showRepositories(api)
            compose.waitUntil(10_000) {
                api.requestCount.get() == 1 &&
                    compose.onAllNodesWithText("acme/Mobile").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(hasSetTextAction()).performTextInput("mobile")
            compose.waitForIdle()
            compose.onNodeWithText("acme/Mobile").performClick()

            compose.waitUntil(10_000) {
                api.requestCount.get() == 3 &&
                    compose.onAllNodesWithText("Branch padrão").fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Intro").fetchSemanticsNodes().isNotEmpty()
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

            compose.onNodeWithText("Intro").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("<script>literal</script>").assertIsDisplayed().assertHasNoClickAction()
            compose.onNodeWithText("unsafe").assertIsDisplayed().assertHasNoClickAction()
            compose.onNodeWithText("Imagem: logo").assertIsDisplayed().performTouchInput { click() }
            compose.onNodeWithText("Abrir link externo?").assertIsDisplayed()
            compose.onNodeWithText("example.test").assertIsDisplayed()
            compose.onNodeWithText("Cancelar").performClick()
            compose.onNodeWithText("echo safe", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("external").performScrollTo().performTouchInput { click() }
            compose.onNodeWithText("Abrir link externo?").assertIsDisplayed()
            compose.onNodeWithText("example.test").assertIsDisplayed()
            compose.onNodeWithText("Cancelar").performClick()
            compose.onNodeWithText("Abrir link externo?").assertDoesNotExist()
            assertEquals("Markdown must not fetch linked media or external pages", 3, api.requestCount.get())
            assertTrue(api.paths.any { it.contains("/repos/acme/Mobile/readme?ref=feature%2Fmobile") })
            if (InstrumentationRegistry.getArguments().getString("maestroPreview") == "true") {
                Thread.sleep(60_000)
            }

            api.description = "Descrição atualizada no GitHub"
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("Atualizar detalhe").fetchSemanticsNodes()
                    .any { !it.config.contains(SemanticsProperties.Disabled) }
            }
            compose.onNodeWithContentDescription("Atualizar detalhe").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                api.requestCount.get() >= 5 &&
                    compose.onAllNodesWithText("Descrição atualizada no GitHub").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Descrição atualizada no GitHub").assertIsDisplayed()
            compose.onNodeWithText("Intro").assertIsDisplayed()

            compose.onAllNodesWithContentDescription("Voltar aos repositórios").onLast().performClick()
            compose.onNodeWithText("Buscar repositórios").assertIsDisplayed()
            compose.onNodeWithText("mobile").assertIsDisplayed()
            compose.onNodeWithText("acme/Mobile").assertIsDisplayed()
            compose.onNodeWithText("acme/docs").assertDoesNotExist()
            assertTrue("Returning to the list triggered an unnecessary request", api.requestCount.get() == 5)
        }
    }

    @Test fun internalMarkdownIssueLinkOpensAppDetailAndBackRestoresReadme() {
        DetailApi().use { api ->
            showFullApp(api)
            compose.onNodeWithText("Abrir painel").performClick()
            compose.onNodeWithText("Repos").performClick()
            compose.waitUntil(10_000) {
                api.paths.any { it.startsWith("GET /user/repos?") } &&
                    compose.onAllNodesWithText("acme/Mobile").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("acme/Mobile").performClick()
            compose.waitUntil(10_000) {
                api.requestCount.get() >= 3 &&
                    compose.onAllNodesWithText("README").fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("issue").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("issue").performScrollTo().performTouchInput { click() }
            compose.waitUntil(10_000) {
                api.paths.any { it.startsWith("GET /repos/acme/Mobile/issues/9 ") } &&
                    compose.onAllNodesWithText("Esta issue não está disponível para sua conta.").fetchSemanticsNodes().isNotEmpty()
            }
            val requestsBeforeBack = api.requestCount.get()
            compose.onNodeWithContentDescription("Voltar às issues").performClick()
            compose.onNodeWithText("README").assertIsDisplayed()
            compose.onNodeWithText("Intro").assertIsDisplayed()
            assertEquals(requestsBeforeBack, api.requestCount.get())
        }
    }

    @Test fun internalMarkdownRepositoryLinkLoadsTheNewRepositoryAndBackRestoresReadme() {
        DetailApi().use { api ->
            showFullApp(api)
            compose.onNodeWithText("Abrir painel").performClick()
            compose.onNodeWithText("Repos").performClick()
            compose.waitUntil(10_000) {
                api.paths.any { it.startsWith("GET /user/repos?") } &&
                    compose.onAllNodesWithText("acme/Mobile").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("acme/Mobile").performClick()
            compose.waitUntil(10_000) {
                api.paths.any { it.startsWith("GET /repos/acme/Mobile/readme") } &&
                    compose.onAllNodesWithText("README").fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText("docs repository").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("docs repository").performScrollTo().performTouchInput { click() }
            compose.waitUntil(10_000) {
                api.paths.any { it.startsWith("GET /repos/acme/docs ") }
            }
            compose.waitUntil(10_000) {
                api.paths.any { it.startsWith("GET /repos/acme/docs/readme") }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Docs README loaded").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("acme/docs").assertIsDisplayed()
            compose.onNodeWithText("Docs README loaded").assertIsDisplayed()
            compose.onAllNodesWithContentDescription("Voltar aos repositórios").assertCountEquals(1)

            val requestsBeforeAppearance = api.requestCount.get()
            compose.onNodeWithContentDescription("Configurações de aparência").performClick()
            compose.onNodeWithText("Aparência").assertIsDisplayed()
            compose.onNodeWithText("Tema do aplicativo").assertIsDisplayed()
            pressBack()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Docs README loaded").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Docs README loaded").assertIsDisplayed()
            assertEquals(requestsBeforeAppearance, api.requestCount.get())

            compose.onNodeWithText("other repository").performScrollTo().performTouchInput { click() }
            compose.waitUntil(10_000) {
                api.paths.any { it.startsWith("GET /repos/acme/other/readme") } &&
                    compose.onAllNodesWithText("Other README loaded").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("acme/other").assertIsDisplayed()
            compose.onNodeWithText("Other README loaded").assertIsDisplayed()
            compose.onAllNodesWithContentDescription("Voltar aos repositórios").assertCountEquals(1)

            val requestsBeforeBack = api.requestCount.get()
            compose.onAllNodesWithContentDescription("Voltar aos repositórios").onLast().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Docs README loaded").fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithContentDescription("Voltar aos repositórios").fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithText("acme/docs").assertIsDisplayed()
            compose.onNodeWithText("Docs README loaded").assertIsDisplayed()
            compose.onAllNodesWithContentDescription("Voltar aos repositórios").assertCountEquals(1)
            assertEquals(requestsBeforeBack, api.requestCount.get())
            val requestsAfterReturningToDocs = api.requestCount.get()

            compose.onAllNodesWithContentDescription("Voltar aos repositórios").onLast().performClick()
            compose.onNodeWithText("acme/Mobile").assertIsDisplayed()
            compose.onNodeWithText("issue").assertIsDisplayed()
            compose.onAllNodesWithContentDescription("Voltar aos repositórios").assertCountEquals(1)
            assertEquals(requestsAfterReturningToDocs, api.requestCount.get())
        }
    }

    @Test fun markdownRouteIsClearedWhenItsSessionExpires() {
        DetailApi(docsReadmeStatus = 401).use { api ->
            showFullApp(api)
            compose.onNodeWithText("Abrir painel").performClick()
            compose.onNodeWithText("Repos").performClick()
            compose.waitUntil(10_000) {
                api.paths.any { it.startsWith("GET /user/repos?") } &&
                    compose.onAllNodesWithText("acme/Mobile").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("acme/Mobile").performClick()
            compose.waitUntil(10_000) {
                api.paths.any { it.startsWith("GET /repos/acme/Mobile/readme") } &&
                    compose.onAllNodesWithText("issue").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("docs repository").performScrollTo().performTouchInput { click() }
            compose.waitUntil(10_000) {
                api.paths.any { it.startsWith("GET /repos/acme/docs/readme") } &&
                    compose.onAllNodesWithText("Início").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onAllNodesWithContentDescription("Voltar aos repositórios").assertCountEquals(0)
            compose.onNodeWithText("Início").assertIsDisplayed()
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

    @Test fun repositoryReleasesShowLatestApkChecksumAndNotes() {
        DetailApi().use { api ->
            showRepositories(api)
            openDetail(api)
            compose.onNodeWithText("Releases e APKs").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                api.responsesSent.any { it.startsWith("GET /repos/acme/Mobile/releases?") } &&
                    compose.onAllNodesWithText("Versão 1.0").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Mais recente").assertIsDisplayed()
            compose.onNodeWithText("Versão 1.0").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("app-release.apk").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("app-release.apk").assertIsDisplayed()
            compose.onNodeWithText("5,0 MB · 3 download(s)").assertIsDisplayed()
            compose.onNodeWithText("SHA256SUMS.txt").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Notas da release").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Baixar").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Voltar").performClick()
            compose.onNodeWithText("Versão 1.0").assertIsDisplayed()
        }
    }

    @Test @SdkSuppress(minSdkVersion = 26, maxSdkVersion = 28)
    fun cancellingSafDestinationPickerDoesNotQueueDownload() {
        val assetId = nextReleaseAssetIdForTest()
        val workName = releaseAssetWorkName(1, assetId)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).bufferedReader().use { it.readText() }
        fun resumedPackage() = shell("dumpsys activity activities")
            .lineSequence().firstOrNull { "mResumedActivity:" in it }
            ?.substringAfter(" u0 ")?.substringBefore('/')
        DetailApi(releaseAssetId = assetId).use { api ->
            showRepositories(api)
            openDetail(api)
            compose.onNodeWithText("Releases e APKs").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                api.responsesSent.any { it.startsWith("GET /repos/acme/Mobile/releases?") } &&
                    compose.onAllNodesWithText("Versão 1.0").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Versão 1.0").performClick()
            compose.onNodeWithText("Baixar").performScrollTo().performClick()
            instrumentation.uiAutomation.waitForIdle(500, 5_000)
            assertEquals("com.android.documentsui", resumedPackage())
            repeat(2) {
                shell("input keyevent 4")
                Thread.sleep(500)
            }
            instrumentation.uiAutomation.waitForIdle(500, 5_000)
            assertEquals(instrumentation.targetContext.packageName, resumedPackage())
            assertTrue(WorkManager.getInstance(
                instrumentation.targetContext
            ).getWorkInfosForUniqueWork(workName).get().isEmpty())
        }
    }

    @Test fun completedReleaseAssetShowsManualInstallAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequestBuilder<CompletedReleaseDownloadWorkerForUiTest>().setInputData(
            workDataOf(
                ReleaseDownloadKeys.OUTPUT_URI to "content://media/external/downloads/1",
                ReleaseDownloadKeys.HASH_STATUS to "verified",
                ReleaseDownloadKeys.HASH_SOURCE to "GitHub"
            )
        ).build()
        val workName = releaseAssetWorkName(1, 19)
        manager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request).result.get()
        compose.waitUntil(10_000) { manager.getWorkInfoById(request.id).get()?.state == WorkInfo.State.SUCCEEDED }

        DetailApi(releaseAssetId = 19).use { api ->
            showRepositories(api)
            openDetail(api)
            compose.onNodeWithText("Releases e APKs").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                api.responsesSent.any { it.startsWith("GET /repos/acme/Mobile/releases?") } &&
                    compose.onAllNodesWithText("Versão 1.0").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Versão 1.0").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Instalar").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("SHA-256 confere (GitHub)", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Instalar").performScrollTo().assertIsDisplayed()

            assertTrue(
                "O teste requer permissão de instalação manual negada no AVD",
                !context.packageManager.canRequestPackageInstalls()
            )
            compose.onNodeWithText("Instalar").performClick()
            compose.onNodeWithText("Permitir instalação manual").assertIsDisplayed()
            compose.onNodeWithText("Cancelar").performClick()
            compose.onNodeWithText("Permitir instalação manual").assertDoesNotExist()
        }
        manager.cancelUniqueWork(workName)
    }

    @Test fun checksumMismatchShowsSourceAndBlocksInstall() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = WorkManager.getInstance(context)
        val assetId = nextReleaseAssetIdForTest()
        val workName = releaseAssetWorkName(1, assetId)
        val request = OneTimeWorkRequestBuilder<FailedReleaseDownloadWorkerForUiTest>().setInputData(
            workDataOf(
                ReleaseDownloadKeys.ERROR to "hash_mismatch",
                ReleaseDownloadKeys.HASH_SOURCE to "GitHub"
            )
        ).build()
        manager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request).result.get()
        compose.waitUntil(10_000) { manager.getWorkInfoById(request.id).get()?.state == WorkInfo.State.FAILED }

        try {
            DetailApi(releaseAssetId = assetId).use { api ->
                showRepositories(api)
                openDetail(api)
                compose.onNodeWithText("Releases e APKs").performScrollTo().performClick()
                compose.waitUntil(10_000) {
                    api.responsesSent.any { it.startsWith("GET /repos/acme/Mobile/releases?") } &&
                        compose.onAllNodesWithText("Versão 1.0", useUnmergedTree = true)
                            .fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("Versão 1.0", useUnmergedTree = true).performClick()
                compose.onNodeWithText("Hash não confere com GitHub; arquivo descartado.")
                    .performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Instalar").assertDoesNotExist()
            }
        } finally {
            manager.cancelUniqueWork(workName)
        }
    }

    @Test @SdkSuppress(minSdkVersion = 29)
    fun workerFailureBeforeTransferDeletesSelectedDestination() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val destination = newPendingDownload(context, "worker-failure.apk")
        val manager = WorkManager.getInstance(context)
        val assetId = nextReleaseAssetIdForTest()
        val workName = releaseAssetWorkName(1, assetId)
        val request = OneTimeWorkRequestBuilder<GitHubReleaseDownloadWorker>().setInputData(
            workDataOf(
                ReleaseDownloadKeys.OWNER to "acme",
                ReleaseDownloadKeys.REPO to "Mobile",
                ReleaseDownloadKeys.REPOSITORY_ID to 1L,
                ReleaseDownloadKeys.ASSET_ID to assetId,
                ReleaseDownloadKeys.ASSET_NAME to "worker-failure.apk",
                ReleaseDownloadKeys.ASSET_SIZE to Long.MAX_VALUE,
                ReleaseDownloadKeys.DESTINATION_URI to destination.toString()
            )
        ).build()

        try {
            manager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request).result.get()
            compose.waitUntil(10_000) { manager.getWorkInfoById(request.id).get()?.state == WorkInfo.State.FAILED }
            assertEquals("no_space", manager.getWorkInfoById(request.id).get()?.outputData?.getString(ReleaseDownloadKeys.ERROR))
            assertTrue("failed worker should remove the empty destination", !mediaStoreRowExists(resolver, destination))
        } finally {
            manager.cancelUniqueWork(workName)
            resolver.delete(destination, null, null)
        }
    }

    @Test @SdkSuppress(minSdkVersion = 29)
    fun cancellingQueuedDownloadDeletesSelectedDestination() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val destination = newPendingDownload(context, "queued-cancel.apk")
        val manager = WorkManager.getInstance(context)
        val assetId = nextReleaseAssetIdForTest()
        val workName = releaseAssetWorkName(1, assetId)
        val request = OneTimeWorkRequestBuilder<QueuedReleaseDownloadWorkerForUiTest>()
            .setInitialDelay(1, TimeUnit.HOURS)
            .setInputData(
                workDataOf(
                    ReleaseDownloadKeys.OWNER to "acme",
                    ReleaseDownloadKeys.REPO to "Mobile",
                    ReleaseDownloadKeys.REPOSITORY_ID to 1L,
                    ReleaseDownloadKeys.ASSET_ID to assetId,
                    ReleaseDownloadKeys.ASSET_NAME to "queued-cancel.apk",
                    ReleaseDownloadKeys.ASSET_SIZE to 1L,
                    ReleaseDownloadKeys.DESTINATION_URI to destination.toString()
                )
            ).build()

        try {
            rememberReleaseDownloadDestination(context, workName, destination)
            manager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request).result.get()
            compose.waitUntil(10_000) { manager.getWorkInfoById(request.id).get()?.state == WorkInfo.State.ENQUEUED }
            DetailApi(releaseAssetId = assetId).use { api ->
                showRepositories(api)
                openDetail(api)
                compose.onNodeWithText("Releases e APKs").performScrollTo().performClick()
                compose.waitUntil(10_000) {
                    api.responsesSent.any { it.startsWith("GET /repos/acme/Mobile/releases?") } &&
                        compose.onAllNodesWithText("Versão 1.0", useUnmergedTree = true)
                            .fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("Versão 1.0", useUnmergedTree = true).performClick()
                compose.onNodeWithText("Cancelar", useUnmergedTree = true).performScrollTo().performClick()
                compose.waitUntil(10_000) { !mediaStoreRowExists(resolver, destination) }
            }
        } finally {
            manager.cancelUniqueWork(workName)
            resolver.delete(destination, null, null)
        }
    }

    @Test @SdkSuppress(minSdkVersion = 29)
    fun logoutCancellationDeletesQueuedDownloadDestination() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val destination = newPendingDownload(context, "logout-cancel.apk")
        val manager = WorkManager.getInstance(context)
        val assetId = nextReleaseAssetIdForTest()
        val workName = releaseAssetWorkName(1, assetId)
        val request = OneTimeWorkRequestBuilder<QueuedReleaseDownloadWorkerForUiTest>()
            .setInitialDelay(1, TimeUnit.HOURS)
            .addTag(RELEASE_DOWNLOAD_WORK_TAG)
            .addTag(workName)
            .build()

        try {
            rememberReleaseDownloadDestination(context, workName, destination)
            manager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request).result.get()
            compose.waitUntil(10_000) { manager.getWorkInfoById(request.id).get()?.state == WorkInfo.State.ENQUEUED }
            cancelReleaseDownloads(context)
            compose.waitUntil(10_000) { !mediaStoreRowExists(resolver, destination) }
        } finally {
            manager.cancelUniqueWork(workName)
            resolver.delete(destination, null, null)
        }
    }

    @Test fun repositoryWithoutReleasesShowsAnHonestEmptyState() {
        DetailApi(emptyReleases = true).use { api ->
            showRepositories(api)
            openDetail(api)
            compose.onNodeWithText("Releases e APKs").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                api.responsesSent.any { it.startsWith("GET /repos/acme/Mobile/releases?") } &&
                    compose.onAllNodesWithText("Nenhuma release publicada neste repositório.")
                        .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Nenhuma release publicada neste repositório.").assertIsDisplayed()
            compose.onNodeWithText("Versão 1.0").assertDoesNotExist()
        }
    }

    @Test fun repositoryReleaseFailureOffersRetryWithoutDemoData() {
        DetailApi(releaseStatus = 503).use { api ->
            showRepositories(api)
            openDetail(api)
            compose.onNodeWithText("Releases e APKs").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                api.responsesSent.any { it.startsWith("GET /repos/acme/Mobile/releases?") } &&
                    compose.onAllNodesWithText("O GitHub está com instabilidade. Tente novamente mais tarde.")
                        .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("O GitHub está com instabilidade. Tente novamente mais tarde.").assertIsDisplayed()
            compose.onNodeWithText("Versão 1.0").assertDoesNotExist()
            compose.onNodeWithText("Tentar novamente").assertIsDisplayed()
        }
    }

    @Test fun repositoryDetailOpensWorkflowRunAndJobStatusesForSelectedRepository() {
        DetailApi().use { api ->
            showRepositories(api)
            openDetail(api)

            compose.onNodeWithText("GitHub Actions").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                api.responsesSent.any { it.startsWith("GET /repos/acme/Mobile/actions/workflows?") }
            }
            assertEquals(listOf("CI Android", "Deploy"), parseGitHubWorkflows(api.actionsResponses.single()).map { it.name })
            val workflowsRendered = runCatching {
                compose.waitUntil(20_000) {
                    compose.onAllNodesWithText("CI Android", substring = true).fetchSemanticsNodes().isNotEmpty()
                }
            }.isSuccess
            assertTrue(
                "Workflow list was not rendered. Requests: ${api.paths}; responses: ${api.responsesSent}. Compose tree: ${compose.onRoot().printToString()}",
                workflowsRendered
            )
            compose.onNodeWithText("CI Android", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Desativado manualmente", substring = true).assertIsDisplayed()
            compose.onNodeWithText("CI Android", substring = true).performClick()
            compose.waitUntil(10_000) {
                api.responsesSent.any { it.startsWith("GET /repos/acme/Mobile/actions/workflows/42/runs?") }
            }
            assertTrue("Workflow run endpoint was not called: ${api.paths}; responses: ${api.responsesSent}",
                api.paths.any { it.contains("/actions/workflows/42/runs?") })
            val runRendered = runCatching {
                compose.waitUntil(20_000) {
                    compose.onAllNodesWithText("#17", substring = true).fetchSemanticsNodes().isNotEmpty()
                }
            }.isSuccess
            assertTrue(
                "Workflow run was not rendered. Requests: ${api.paths}; responses: ${api.responsesSent}. Compose tree: ${compose.onRoot().printToString()}",
                runRendered
            )
            compose.onNodeWithText("Falhou", substring = true).assertIsDisplayed()
            compose.onNodeWithText("#17", substring = true).performClick()
            compose.waitUntil(10_000) {
                api.responsesSent.any { it.startsWith("GET /repos/acme/Mobile/actions/runs/9001/jobs?") }
            }
            val jobsRendered = runCatching {
                compose.waitUntil(20_000) {
                    compose.onAllNodesWithText("Build and test", substring = true).fetchSemanticsNodes().isNotEmpty()
                }
            }.isSuccess
            assertTrue(
                "Job row not rendered. Requests: ${api.paths}; responses: ${api.responsesSent}. Compose tree: ${compose.onRoot().printToString()}",
                jobsRendered
            )
            compose.onNodeWithText("Build and test", substring = true).assertIsDisplayed()
            compose.onNodeWithText("main", substring = true).assertIsDisplayed()
            compose.onAllNodesWithText("Falhou", substring = true).assertCountEquals(2)
            compose.onNodeWithText("Ver logs").performClick()
            compose.waitUntil(10_000) {
                api.responsesSent.any { it.startsWith("GET /repos/acme/Mobile/actions/jobs/500/logs") }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Este recurso não está disponível para sua conta.")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Este recurso não está disponível para sua conta.").assertIsDisplayed()
            compose.onNodeWithText("Este job ainda não tem logs.").assertDoesNotExist()
            assertTrue(api.paths.none { it.contains("/rerun") || it.contains("/cancel") })
            if (InstrumentationRegistry.getArguments().getString("maestroPreview") == "true") {
                Thread.sleep(60_000)
            }
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
            compose.waitUntil(10_000) {
                expired.get() == 1 &&
                    compose.onAllNodesWithText("Sua sessão expirou. Conecte-se novamente.").fetchSemanticsNodes().isNotEmpty()
            }
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
            api.requestCount.get() >= 1 &&
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
        compose.onNodeWithText("GitHub Actions").performScrollTo().performClick()
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

    private fun showFullApp(api: DetailApi) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = GitHubSession(
            GitHubUser("fixture-user", "https://github.com/fixture-user"),
            SessionCredentials("instrumentation-fixture-token")
        )
        val sessionState = androidx.compose.runtime.mutableStateOf<GitHubSession?>(session)
        compose.setContent {
            ControleTheme(AppThemeMode.LIGHT) {
                val activeSession = sessionState.value
                ControleApp(
                    themeMode = AppThemeMode.LIGHT,
                    preferenceError = false,
                    favoritesStore = DataStoreRepositoryFavoritesStore(context),
                    favoriteOperations = kotlinx.coroutines.sync.Mutex(),
                    session = activeSession,
                    sessionRestoring = false,
                    sessionRetry = false,
                    sessionStorageError = false,
                    favoriteCleanupError = false,
                    onRetrySession = {},
                    onRetrySessionCleanup = {},
                    onConnected = { connected -> sessionState.value = connected; true },
                    onSessionExpired = { sessionState.value = null },
                    onLogout = {},
                    onThemeModeChange = {},
                    apiClient = GitHubHttpClient(api.baseUrl)
                )
            }
        }
    }

    private fun newPendingDownload(context: Context, name: String) =
        requireNotNull(context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        ))

    private fun mediaStoreRowExists(resolver: android.content.ContentResolver, uri: android.net.Uri) =
        resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.moveToFirst() } ?: false

    private fun nextReleaseAssetIdForTest() = System.nanoTime().coerceAtLeast(1L)

    private class DetailApi(
        private val metadataStatus: Int = 200,
        private val readmeStatus: Int = 200,
        private val docsReadmeStatus: Int = 200,
        private val failMetadataNetworkOnce: Boolean = false,
        private val failMetadataServerOnce: Boolean = false,
        private val metadataRetryDelayMillis: Long = 0,
        private val actionsWorkflowsStatus: Int = 200,
        private val emptyActionsWorkflows: Boolean = false,
        private val paginatedActionsWorkflows: Boolean = false,
        private val releaseStatus: Int = 200,
        private val emptyReleases: Boolean = false,
        private val releaseAssetId: Long = nextTestReleaseAssetId()
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
        private val readme = """# Intro
            |<script>literal</script>
            |
            |[issue](https://github.com/acme/Mobile/issues/9)
            |
            |[docs repository](https://github.com/acme/docs)
            |
            |[external](https://example.test/docs)
            |
            |[unsafe](javascript:alert(1))
            |
            |![logo](https://example.test/logo.png)
            |
            |```sh
            |echo safe
            |```
        """.trimMargin()
        private val readmeBody = JSONObject()
            .put("encoding", "base64")
            .put("content", Base64.getEncoder().encodeToString(readme.toByteArray(StandardCharsets.UTF_8)))
            .toString()
        private val docsReadmeBody = JSONObject()
            .put("encoding", "base64")
            .put("content", Base64.getEncoder().encodeToString(
                "# Docs README loaded\n\n[other repository](https://github.com/acme/other)".toByteArray(StandardCharsets.UTF_8)
            ))
            .toString()
        private val otherReadmeBody = JSONObject()
            .put("encoding", "base64")
            .put("content", Base64.getEncoder().encodeToString("# Other README loaded".toByteArray(StandardCharsets.UTF_8)))
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
                            requestLine.startsWith("GET /repos/acme/docs/readme") -> docsReadmeStatus to if (docsReadmeStatus == 200) docsReadmeBody else "{}"
                            requestLine.startsWith("GET /repos/acme/docs ") -> 200 to docsMetadata
                            requestLine.startsWith("GET /repos/acme/other/readme") -> 200 to otherReadmeBody
                            requestLine.startsWith("GET /repos/acme/other ") -> 200 to otherMetadata
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
                            requestLine.startsWith("GET /repos/acme/Mobile/releases?") -> releaseStatus to when {
                                releaseStatus != 200 -> "{}"
                                emptyReleases -> "[]"
                                else -> releasesBody
                            }
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

        private val docsMetadata = """{
          "id":2,"name":"docs","full_name":"acme/docs","owner":{"login":"acme"},
          "private":false,"visibility":"public","default_branch":"main",
          "description":"Docs metadata loaded","stargazers_count":1,"forks_count":0,
          "open_issues_count":0,"pushed_at":"2026-10-01T10:20:30Z"
        }"""
        private val otherMetadata = """{
          "id":3,"name":"other","full_name":"acme/other","owner":{"login":"acme"},
          "private":false,"visibility":"public","default_branch":"main",
          "description":"Other metadata loaded","stargazers_count":2,"forks_count":0,
          "open_issues_count":0,"pushed_at":"2026-10-02T10:20:30Z"
        }"""

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
        private val releasesBody = """[{"id":9,"tag_name":"v1.0.0","name":"Versão 1.0","body":"Notas da release","draft":false,"prerelease":false,"published_at":"2026-10-18T10:00:00Z","author":{"login":"acme"},"assets":[{"id":$releaseAssetId,"name":"app-release.apk","size":5242880,"download_count":3,"content_type":"application/vnd.android.package-archive","browser_download_url":"https://github.com/acme/Mobile/releases/download/v1.0.0/app-release.apk"},{"id":12,"name":"SHA256SUMS.txt","size":128,"download_count":1,"content_type":"text/plain","browser_download_url":"https://github.com/acme/Mobile/releases/download/v1.0.0/SHA256SUMS.txt"}]}]"""

        private companion object {
            private val nextReleaseAssetId = AtomicLong(System.currentTimeMillis() * 1_000)
            private fun nextTestReleaseAssetId() = nextReleaseAssetId.incrementAndGet()

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

class CompletedReleaseDownloadWorkerForUiTest(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = Result.success(inputData)
}

class FailedReleaseDownloadWorkerForUiTest(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = Result.failure(inputData)
}

class QueuedReleaseDownloadWorkerForUiTest(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = Result.success()
}
