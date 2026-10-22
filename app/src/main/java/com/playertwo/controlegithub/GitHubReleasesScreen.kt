package com.playertwo.controlegithub

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun GitHubReleasesScreen(
    modifier: Modifier,
    client: GitHubHttpClient,
    accessToken: String,
    repository: GitHubRepository,
    cacheStore: GitHubCacheStore? = null,
    accountLogin: String? = null,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit
) {
    var refreshVersion by remember(repository.id, accessToken) { mutableIntStateOf(0) }
    val pager = remember(client, accessToken, repository.id, refreshVersion) {
        GitHubReleasePager(client, accessToken, repository, cacheStore, accountLogin)
    }
    var result by remember(pager) { mutableStateOf<GitHubPageResult<GitHubRelease>?>(null) }
    var loading by remember(pager) { mutableStateOf(false) }
    var loadMoreVersion by remember(pager) { mutableIntStateOf(0) }
    var selected by remember(repository.id, accessToken) { mutableStateOf<GitHubRelease?>(null) }

    BackHandler { if (selected != null) selected = null else onBack() }
    LaunchedEffect(pager, loadMoreVersion) {
        loading = true
        val page = withContext(Dispatchers.IO) { pager.loadNext() }
        result = page
        loading = false
        if ((page as? GitHubPageResult.Failed)?.error == GitHubHttpError.UNAUTHORIZED) onSessionExpired()
    }

    val releases = result?.items.orEmpty()
    val latestId = remember(releases) { latestReleaseId(releases) }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (selected != null) selected = null else onBack() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar")
            }
            Text(
                selected?.title ?: "Releases",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (selected == null) {
                IconButton(onClick = { refreshVersion++ }, enabled = !loading) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar releases")
                }
            }
        }

        val current = selected
        if (current != null) {
            ReleaseDetail(current, repository, isLatest = current.id == latestId)
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text(repository.fullName, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            (result as? GitHubPageResult.Loaded)?.takeIf { it.isOfflineCache }?.let { page ->
                item { OfflineCacheBadge(page.cachedAtMillis) }
            }
            when (val page = result) {
                null -> item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Carregando releases do GitHub…")
                    }
                }
                is GitHubPageResult.Failed -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(page.error?.userMessage ?: page.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { refreshVersion++ }, enabled = !loading) { Text("Tentar novamente") }
                    }
                }
                is GitHubPageResult.RateLimited -> item {
                    Text("Limite de requisições do GitHub atingido. Tente mais tarde.", color = MaterialTheme.colorScheme.error)
                }
                is GitHubPageResult.Loaded -> if (page.items.isEmpty()) item {
                    Text("Nenhuma release publicada neste repositório.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(releases, key = GitHubRelease::id) { release ->
                ReleaseCard(release, isLatest = release.id == latestId) { selected = release }
            }
            if ((result as? GitHubPageResult.Loaded)?.hasNext == true) item {
                OutlinedButton(onClick = { loadMoreVersion++ }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    Text(if (loading) "Carregando…" else "Carregar mais")
                }
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: GitHubRelease, isLatest: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(release.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(releaseBadges(release, isLatest), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(release.publishedAt?.let(::formatReleaseTime), release.author?.let { "por $it" }).joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            val apks = release.apkAssets.size
            Text(
                when (apks) {
                    0 -> "${release.assets.size} arquivo(s), nenhum APK"
                    1 -> "1 APK disponível"
                    else -> "$apks APKs disponíveis"
                },
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ReleaseDetail(release: GitHubRelease, repository: GitHubRepository, isLatest: Boolean) {
    val context = LocalContext.current
    var openError by remember(release.id) { mutableStateOf(false) }
    var operationError by remember(release.id) { mutableStateOf<String?>(null) }
    var notificationWarning by remember(release.id) { mutableStateOf(false) }
    var pendingNotificationAsset by remember(release.id) { mutableStateOf<GitHubReleaseAsset?>(null) }
    var pendingDestinationAsset by remember(release.id) { mutableStateOf<GitHubReleaseAsset?>(null) }
    var pendingInstallUri by remember(release.id) { mutableStateOf<Uri?>(null) }
    var showInstallPermission by remember(release.id) { mutableStateOf(false) }
    val workInfoByAsset = remember(repository.id, release.id) { mutableStateMapOf<Long, WorkInfo>() }
    val checksumAsset = remember(release) { release.assets.firstOrNull(GitHubReleaseAsset::isChecksum) }

    val destinationPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val asset = pendingDestinationAsset
        pendingDestinationAsset = null
        val uri = result.data?.data
        if (asset != null && result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            val flags = result.data?.flags ?: 0
            if (flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION == 0 ||
                flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0 || flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0
            ) {
                operationError = "O Android não permitiu manter acesso ao destino escolhido. Tente outro local em Downloads."
            } else {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    enqueueReleaseAssetDownload(context, repository, asset, checksumAsset, uri)
                } catch (_: SecurityException) {
                    operationError = "Não foi possível acessar o arquivo escolhido. Tente outro local em Downloads."
                } catch (_: IllegalStateException) {
                    operationError = "Não foi possível iniciar o download. Tente novamente."
                }
            }
        }
    }

    val continueDownload: (GitHubReleaseAsset) -> Unit = { asset ->
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                enqueueReleaseAssetDownload(context, repository, asset, checksumAsset)
            } catch (_: IllegalStateException) {
                operationError = "Não foi possível iniciar o download. Tente novamente."
            }
        } else {
            pendingDestinationAsset = asset
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_TITLE, releaseAssetFileName(asset.name))
                putExtra(
                    DocumentsContract.EXTRA_INITIAL_URI,
                    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download")
                )
            }
            destinationPicker.launch(intent)
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) notificationWarning = true
        pendingNotificationAsset?.let(continueDownload)
        pendingNotificationAsset = null
    }

    val installSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = pendingInstallUri
        pendingInstallUri = null
        if (uri != null) {
            if (context.packageManager.canRequestPackageInstalls()) {
                openApkInstaller(context, uri) { operationError = it }
            } else {
                operationError = "A permissão para instalar apps desta fonte não foi concedida."
            }
        }
    }

    fun open(url: String) {
        openError = try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            false
        } catch (_: ActivityNotFoundException) {
            true
        }
    }
    fun requestDownload(asset: GitHubReleaseAsset) {
        operationError = null
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingNotificationAsset = asset
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else continueDownload(asset)
    }
    fun install(assetInfo: WorkInfo) {
        val uri = assetInfo.outputData.getString(ReleaseDownloadKeys.OUTPUT_URI)?.let(Uri::parse) ?: return
        if (uri.scheme != "content") {
            operationError = "O arquivo baixado não tem uma origem segura para instalação."
            return
        }
        if (context.packageManager.canRequestPackageInstalls()) {
            openApkInstaller(context, uri) { operationError = it }
        } else {
            pendingInstallUri = uri
            showInstallPermission = true
        }
    }

    val workManager = remember(context) { WorkManager.getInstance(context) }
    DisposableEffect(workManager, repository.id, release.id) {
        val observations = release.apkAssets.map { asset ->
            val liveData = workManager.getWorkInfosForUniqueWorkLiveData(releaseAssetWorkName(repository.id, asset.id))
            val observer = Observer<List<WorkInfo>> { infos ->
                infos.lastOrNull()?.let { workInfoByAsset[asset.id] = it } ?: workInfoByAsset.remove(asset.id)
            }
            liveData.observeForever(observer)
            liveData to observer
        }
        onDispose { observations.forEach { (liveData, observer) -> liveData.removeObserver(observer) } }
    }

    val sortedAssets = remember(release) { release.assets.sortedWith(compareBy({ !it.isApk }, { !it.isChecksum }, { it.name })) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tag ${release.tagName}", fontWeight = FontWeight.Medium)
                Text(releaseBadges(release, isLatest), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                release.publishedAt?.let { Text(formatReleaseTime(it), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
            }
        }
        if (openError) item {
            Text("Nenhum aplicativo disponível para abrir o link.", color = MaterialTheme.colorScheme.error)
        }
        if (notificationWarning) item {
            Text("Notificações desativadas. O estado do download continua disponível nesta tela.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        operationError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        item { Text("Arquivos", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
        if (sortedAssets.isEmpty()) item {
            Text("Esta release não tem arquivos anexados.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(sortedAssets, key = GitHubReleaseAsset::id) { asset ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (asset.isApk) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(asset.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${formatByteSize(asset.sizeBytes)} · ${asset.downloadCount} download(s)" +
                                if (asset.isChecksum) " · verificação de integridade" else "",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (asset.isApk) {
                        Spacer(Modifier.padding(4.dp))
                        val info = workInfoByAsset[asset.id]
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            when (info?.state) {
                                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING -> {
                                    Button(onClick = { cancelReleaseAssetDownload(context, repository.id, asset.id, info) }) {
                                        Text("Cancelar")
                                    }
                                    Text(downloadProgressLabel(info), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                WorkInfo.State.SUCCEEDED -> {
                                    Text(downloadHashLabel(info), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Button(onClick = { install(info) }) { Text("Instalar") }
                                }
                                WorkInfo.State.FAILED -> {
                                    Text(downloadFailureLabel(info), fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                    if (info.outputData.getString(ReleaseDownloadKeys.ERROR) != "hash_mismatch") {
                                        Button(onClick = { requestDownload(asset) }) { Text("Tentar novamente") }
                                    }
                                }
                                else -> Button(onClick = { requestDownload(asset) }) { Text("Baixar") }
                            }
                        }
                    } else {
                        asset.browserDownloadUrl?.let { url ->
                            TextButton(onClick = { open(url) }) { Text("Abrir") }
                        }
                    }
                }
            }
        }
        item { Text("Notas", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Text(
                    release.body?.takeIf(String::isNotBlank) ?: "Sem notas de lançamento.",
                    modifier = Modifier.padding(18.dp),
                    lineHeight = 22.sp
                )
            }
        }
    }

    if (showInstallPermission) {
        AlertDialog(
            onDismissRequest = { showInstallPermission = false; pendingInstallUri = null },
            title = { Text("Permitir instalação manual") },
            text = { Text("O Android pede que você permita ao ControleGitHub abrir o instalador de APKs. A instalação ainda exige sua confirmação no instalador do sistema.") },
            confirmButton = {
                Button(onClick = {
                    showInstallPermission = false
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                    try {
                        installSettings.launch(settingsIntent)
                    } catch (_: ActivityNotFoundException) {
                        operationError = "Não foi possível abrir as configurações de instalação deste app."
                    }
                }) { Text("Abrir configurações") }
            },
            dismissButton = { TextButton(onClick = { showInstallPermission = false; pendingInstallUri = null }) { Text("Cancelar") } }
        )
    }
}

private fun downloadProgressLabel(info: WorkInfo): String {
    val downloaded = info.progress.getLong(ReleaseDownloadKeys.DOWNLOADED, 0)
    val total = info.progress.getLong(ReleaseDownloadKeys.TOTAL, -1)
    return if (total > 0) "${(downloaded * 100 / total).coerceIn(0, 100)}% · ${formatByteSize(downloaded)} de ${formatByteSize(total)}"
    else if (downloaded > 0) "${formatByteSize(downloaded)} baixados" else "Preparando download…"
}

private fun downloadHashLabel(info: WorkInfo): String = when (info.outputData.getString(ReleaseDownloadKeys.HASH_STATUS)) {
    "verified" -> "Baixado · SHA-256 confere (${info.outputData.getString(ReleaseDownloadKeys.HASH_SOURCE)})"
    else -> "Baixado · sem hash publicado"
}

private fun downloadFailureLabel(info: WorkInfo): String = when (info.outputData.getString(ReleaseDownloadKeys.ERROR)) {
    "hash_mismatch" -> "Hash não confere com ${info.outputData.getString(ReleaseDownloadKeys.HASH_SOURCE)?.takeIf(String::isNotBlank) ?: "a fonte publicada"}; arquivo descartado."
    "no_space" -> "Espaço insuficiente para baixar o APK."
    "session" -> "Conecte-se ao GitHub para baixar este asset."
    "storage_permission" -> "Não foi possível acessar o local escolhido."
    "http_401", "http_403" -> "Sua sessão não tem acesso a este asset."
    "http_404" -> "Este asset não está mais disponível."
    "size_mismatch" -> "O tamanho recebido não confere; arquivo descartado."
    else -> "Falha no download ou na gravação. Verifique a conexão e o espaço disponível."
}

private fun openApkInstaller(context: android.content.Context, uri: Uri, onError: (String) -> Unit) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    } catch (_: ActivityNotFoundException) {
        onError("Nenhum instalador de APK está disponível neste dispositivo.")
    } catch (_: SecurityException) {
        onError("O Android não permitiu abrir o APK selecionado.")
    }
}

private fun releaseBadges(release: GitHubRelease, isLatest: Boolean): String = listOfNotNull(
    "Mais recente".takeIf { isLatest },
    "Pré-release".takeIf { release.isPrerelease },
    "Rascunho".takeIf { release.isDraft }
).ifEmpty { listOf("Release") }.joinToString(" · ")

private fun formatReleaseTime(instant: Instant): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM)
    .withLocale(Locale.forLanguageTag("pt-BR"))
    .withZone(ZoneId.systemDefault())
    .format(instant)
