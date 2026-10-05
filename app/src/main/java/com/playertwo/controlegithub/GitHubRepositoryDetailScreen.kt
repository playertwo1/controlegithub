package com.playertwo.controlegithub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun GitHubRepositoryDetailScreen(
    modifier: Modifier,
    client: GitHubHttpClient,
    session: GitHubSession,
    repository: GitHubRepository,
    onBack: () -> Unit,
    onSessionExpired: (GitHubSession) -> Unit,
    onAppearance: () -> Unit
) {
    var refreshVersion by remember(repository.id, session.accessToken) { mutableIntStateOf(0) }
    var result by remember(repository.id, session.accessToken) {
        mutableStateOf<GitHubRepositoryDetailResult?>(null)
    }
    var loading by remember(repository.id, session.accessToken) { mutableStateOf(false) }
    val loader = remember(client, session.accessToken) {
        GitHubRepositoryDetailLoader(client, session.accessToken)
    }

    var actionsOpen by remember(repository.id, session.accessToken) { mutableStateOf(false) }
    if (actionsOpen) {
        GitHubActionsScreen(
            modifier = modifier,
            client = client,
            accessToken = session.accessToken,
            repository = repository,
            onBack = { actionsOpen = false },
            onSessionExpired = { onSessionExpired(session) }
        )
        return
    }

    BackHandler(onBack = onBack)
    LaunchedEffect(loader, repository.id, refreshVersion) {
        loading = true
        result = withContext(Dispatchers.IO) { loader.load(repository) }
        loading = false
    }
    LaunchedEffect(result) {
        val expired = when (val current = result) {
            is GitHubRepositoryDetailResult.Failed -> current.error == GitHubHttpError.UNAUTHORIZED
            is GitHubRepositoryDetailResult.Loaded ->
                (current.readme as? GitHubReadmeResult.Failed)?.error == GitHubHttpError.UNAUTHORIZED
            null -> false
        }
        if (expired) onSessionExpired(session)
    }

    Column(
        modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 24.dp, vertical = 20.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar aos repositórios")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onAppearance) {
                Icon(Icons.Outlined.Settings, contentDescription = "Configurações de aparência")
            }
            IconButton(onClick = { refreshVersion++ }, enabled = !loading) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar detalhe")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(repository.fullName, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Detalhe do repositório", color = MaterialTheme.colorScheme.onSurfaceVariant)

        when (val current = result) {
            null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Carregando detalhes do GitHub…")
            }
            is GitHubRepositoryDetailResult.Failed ->
                DetailError(current.error, current.retryAtEpochMillis, enabled = !loading) { refreshVersion++ }
            is GitHubRepositoryDetailResult.Loaded -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(current.detail.description?.takeIf(String::isNotBlank) ?: "Sem descrição", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            DetailValue("Visibilidade", current.detail.visibility?.visibilityLabel())
                            DetailValue("Branch padrão", current.detail.defaultBranch)
                            DetailValue("Estrelas", current.detail.stars?.toString())
                            DetailValue("Forks", current.detail.forks?.toString())
                            DetailValue("Issues + PRs abertas", current.detail.openIssuesAndPullRequests?.toString())
                            DetailValue("Último push", current.detail.pushedAt?.let(::formatPushTime))
                        }
                    }
                    Button(onClick = { actionsOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Ver GitHub Actions")
                    }
                    when (val readme = current.readme) {
                        is GitHubReadmeResult.Found -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("README", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = readme.text.ifBlank { "O README está vazio." },
                                    modifier = Modifier.padding(18.dp),
                                    lineHeight = 22.sp
                                )
                            }
                        }
                        GitHubReadmeResult.Missing -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("README", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Text("README não encontrado para este repositório.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        is GitHubReadmeResult.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("README", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            DetailError(readme.error, readme.retryAtEpochMillis, enabled = !loading) { refreshVersion++ }
                        }
                    }
            }
        }

        if (loading && result != null) {
            Text("Atualizando dados…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailValue(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "Indisponível", fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DetailError(
    error: GitHubHttpError,
    retryAtEpochMillis: Long?,
    enabled: Boolean,
    onRetry: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(error.userMessage, color = MaterialTheme.colorScheme.error)
        retryAtEpochMillis?.let { deadline ->
            val safeTime = android.text.format.DateFormat.getTimeFormat(androidx.compose.ui.platform.LocalContext.current)
                .format(Date(deadline))
            Text("Tente novamente após $safeTime.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onRetry, enabled = enabled) { Text("Tentar novamente") }
    }
}

private fun String.visibilityLabel(): String = when (lowercase(Locale.ROOT)) {
    "public" -> "Público"
    "private" -> "Privado"
    "internal" -> "Interno"
    else -> "Indisponível"
}

private fun formatPushTime(instant: java.time.Instant): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM)
    .withLocale(Locale.forLanguageTag("pt-BR"))
    .withZone(ZoneId.systemDefault())
    .format(instant)
