package com.playertwo.controlegithub

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    onBack: () -> Unit,
    onSessionExpired: () -> Unit
) {
    var refreshVersion by remember(repository.id, accessToken) { mutableIntStateOf(0) }
    val pager = remember(client, accessToken, repository.id, refreshVersion) {
        GitHubReleasePager(client, accessToken, repository)
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
            ReleaseDetail(current, isLatest = current.id == latestId)
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text(repository.fullName, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
private fun ReleaseDetail(release: GitHubRelease, isLatest: Boolean) {
    val context = LocalContext.current
    var openError by remember(release.id) { mutableStateOf(false) }
    fun open(url: String) {
        openError = try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            false
        } catch (_: ActivityNotFoundException) {
            true
        }
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
                    asset.browserDownloadUrl?.let { url ->
                        Spacer(Modifier.padding(4.dp))
                        if (asset.isApk) {
                            Button(onClick = { open(url) }) { Text("Baixar") }
                        } else {
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
