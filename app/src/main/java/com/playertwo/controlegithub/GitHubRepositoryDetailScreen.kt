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
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GitHubRepositoryDetailScreen(
    modifier: Modifier,
    client: GitHubHttpClient,
    session: GitHubSession,
    repository: GitHubRepository,
    onBack: () -> Unit,
    backContentDescription: String = "Voltar aos repositórios",
    onSessionExpired: (GitHubSession) -> Unit,
    onAppearance: () -> Unit,
    isOffline: Boolean = session.isOffline,
    cacheStore: GitHubCacheStore? = null,
    isActive: Boolean = true,
    initialReleasesOpen: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val recentStore = LocalRecentRepositoriesStore.current ?: remember(context) {
        DataStoreRecentRepositoriesStore(context)
    }
    var refreshVersion by remember(repository.id, session.accessToken) { mutableIntStateOf(0) }
    var result by remember(repository.id, session.accessToken) {
        mutableStateOf<GitHubRepositoryDetailResult?>(null)
    }
    var loading by remember(repository.id, session.accessToken) { mutableStateOf(false) }
    val loader = remember(client, session.accessToken) {
        GitHubRepositoryDetailLoader(client, session.accessToken)
    }
    val socialClient = remember(client, session.accessToken) {
        GitHubRepositorySocialClient(client, session.accessToken)
    }
    val coroutineScope = rememberCoroutineScope()
    var initialStarred by remember(repository.id, session.accessToken) {
        mutableStateOf<Boolean?>(null)
    }
    var socialState by remember(repository.id, session.accessToken) {
        mutableStateOf<GitHubRepositorySocialState?>(null)
    }
    var socialBusy by remember(repository.id, session.accessToken) { mutableStateOf(false) }
    var socialError by remember(repository.id, session.accessToken) { mutableStateOf<String?>(null) }

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

    var createIssueOpen by remember(repository.id, session.accessToken) { mutableStateOf(false) }
    if (createIssueOpen) {
        GitHubCreateIssueScreen(
            modifier = modifier,
            client = client,
            accessToken = session.accessToken,
            repositoryFullName = repository.fullName,
            accountLogin = session.user.login,
            isOffline = isOffline,
            onIssueCreated = {
                createIssueOpen = false
                refreshVersion++
            },
            onCancel = { createIssueOpen = false },
            onSessionExpired = { onSessionExpired(session) }
        )
        return
    }

    var releasesOpen by remember(repository.id, session.accessToken, initialReleasesOpen) {
        mutableStateOf(initialReleasesOpen)
    }
    var workOpen by remember(repository.id, session.accessToken) { mutableStateOf(false) }
    var codeOpen by remember(repository.id, session.accessToken) { mutableStateOf(false) }
    var codeBranch by remember(repository.id, session.accessToken) { mutableStateOf<String?>(null) }
    if (workOpen) {
        GitHubRepositoryWorkScreen(
            modifier = modifier,
            client = client,
            session = session,
            repository = repository,
            cacheStore = cacheStore,
            isOffline = isOffline,
            onBack = { workOpen = false },
            onCreateIssue = {
                workOpen = false
                createIssueOpen = true
            },
            onSessionExpired = onSessionExpired,
            onAppearance = onAppearance,
            isActive = isActive
        )
        return
    }
    if (codeOpen) {
        GitHubCodeBrowserScreen(
            modifier = modifier,
            client = client,
            accessToken = session.accessToken,
            owner = repository.owner,
            name = repository.name,
            defaultBranch = codeBranch,
            isOffline = isOffline,
            onBack = { codeOpen = false },
            onSessionExpired = { onSessionExpired(session) }
        )
        return
    }
    if (releasesOpen) {
        GitHubReleasesScreen(
            modifier = modifier,
            client = client,
            accessToken = session.accessToken,
            repository = repository,
            cacheStore = cacheStore,
            accountLogin = session.user.login,
            onBack = { releasesOpen = false },
            onSessionExpired = { onSessionExpired(session) }
        )
        return
    }

    BackHandler(enabled = isActive, onBack = onBack)
    LaunchedEffect(loader, socialClient, repository.id, refreshVersion) {
        loading = true
        socialError = null
        val (loadedResult, loadedSocial) = withContext(Dispatchers.IO) {
            val detailResult = loader.load(repository)
            val socialResult = if (
                detailResult is GitHubRepositoryDetailResult.Loaded &&
                detailResult.readme !is GitHubReadmeResult.Failed &&
                !isOffline
            ) {
                val parts = detailResult.detail.fullName.split('/')
                val owner = if (parts.size == 2) parts[0] else repository.owner
                val name = if (parts.size == 2) parts[1] else repository.name
                socialClient.loadState(owner, name)
            } else {
                null
            }
            detailResult to socialResult
        }
        result = loadedResult
        when (loadedSocial) {
            is GitHubRepositorySocialResult.Loaded -> {
                initialStarred = loadedSocial.state.starred
                socialState = loadedSocial.state
            }
            is GitHubRepositorySocialResult.Failed -> {
                if (loadedSocial.error == GitHubHttpError.UNAUTHORIZED) {
                    onSessionExpired(session)
                } else {
                    socialError = loadedSocial.message
                }
            }
            null -> Unit
        }
        loading = false
    }
    LaunchedEffect(result, repository.id, repository.fullName, session.user.login) {
        val expired = when (val current = result) {
            is GitHubRepositoryDetailResult.Failed -> current.error == GitHubHttpError.UNAUTHORIZED
            is GitHubRepositoryDetailResult.Loaded ->
                (current.readme as? GitHubReadmeResult.Failed)?.error == GitHubHttpError.UNAUTHORIZED
            null -> false
        }
        if (expired) {
            onSessionExpired(session)
            return@LaunchedEffect
        }
        val detail = (result as? GitHubRepositoryDetailResult.Loaded)?.detail
        if (detail != null) {
            val parts = detail.fullName.split('/')
            if (parts.size == 2) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        recentStore.recordVisit(
                            session.user.login,
                            RecentRepositoryEntry(
                                id = detail.id,
                                owner = parts[0],
                                name = parts[1],
                                fullName = detail.fullName,
                                isPrivate = detail.visibility == "private"
                            )
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 24.dp, vertical = 20.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = backContentDescription)
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    codeBranch = (result as? GitHubRepositoryDetailResult.Loaded)?.detail?.defaultBranch
                    codeOpen = true
                },
                enabled = result is GitHubRepositoryDetailResult.Loaded,
                modifier = Modifier.testTag("code_browser_button")
            ) {
                Text("Código")
            }
            IconButton(onClick = onAppearance) {
                Icon(Icons.Outlined.Settings, contentDescription = "Configurações de aparência")
            }
            IconButton(onClick = { refreshVersion++ }, enabled = !loading) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar detalhe")
            }
        }
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
                    val displayedStars = current.detail.stars?.let { baseStars ->
                        val activeSocial = socialState
                        val initial = initialStarred
                        val delta = when {
                            activeSocial == null || initial == null -> 0L
                            activeSocial.starred && !initial -> 1L
                            !activeSocial.starred && initial -> -1L
                            else -> 0L
                        }
                        (baseStars + delta).coerceAtLeast(0L)
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(current.detail.description?.takeIf(String::isNotBlank) ?: "Sem descrição", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = socialState?.starred == true,
                                    onClick = {
                                        val activeSocial = socialState ?: return@FilterChip
                                        if (socialBusy || isOffline) return@FilterChip
                                        val parts = current.detail.fullName.split('/')
                                        val owner = if (parts.size == 2) parts[0] else repository.owner
                                        val name = if (parts.size == 2) parts[1] else repository.name
                                        val targetStarred = !activeSocial.starred
                                        socialBusy = true
                                        socialError = null
                                        coroutineScope.launch {
                                            val outcome = withContext(Dispatchers.IO) {
                                                socialClient.setStarred(
                                                    owner = owner,
                                                    repo = name,
                                                    targetStarred = targetStarred,
                                                    currentWatching = activeSocial.watching
                                                )
                                            }
                                            socialBusy = false
                                            when (outcome) {
                                                is GitHubRepositorySocialResult.Loaded -> socialState = outcome.state
                                                is GitHubRepositorySocialResult.Failed -> {
                                                    if (outcome.error == GitHubHttpError.UNAUTHORIZED) {
                                                        onSessionExpired(session)
                                                    } else {
                                                        socialError = outcome.message
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isOffline && !socialBusy && socialState != null,
                                    label = {
                                        Text(
                                            if (socialState?.starred == true) "Com estrela no GitHub" else "Estrela no GitHub",
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    },
                                    modifier = Modifier.height(32.dp).testTag("repo_star_button")
                                )
                                FilterChip(
                                    selected = socialState?.watching == true,
                                    onClick = {
                                        val activeSocial = socialState ?: return@FilterChip
                                        if (socialBusy || isOffline) return@FilterChip
                                        val parts = current.detail.fullName.split('/')
                                        val owner = if (parts.size == 2) parts[0] else repository.owner
                                        val name = if (parts.size == 2) parts[1] else repository.name
                                        val targetWatching = !activeSocial.watching
                                        socialBusy = true
                                        socialError = null
                                        coroutineScope.launch {
                                            val outcome = withContext(Dispatchers.IO) {
                                                socialClient.setWatching(
                                                    owner = owner,
                                                    repo = name,
                                                    targetWatching = targetWatching,
                                                    currentStarred = activeSocial.starred
                                                )
                                            }
                                            socialBusy = false
                                            when (outcome) {
                                                is GitHubRepositorySocialResult.Loaded -> socialState = outcome.state
                                                is GitHubRepositorySocialResult.Failed -> {
                                                    if (outcome.error == GitHubHttpError.UNAUTHORIZED) {
                                                        onSessionExpired(session)
                                                    } else {
                                                        socialError = outcome.message
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isOffline && !socialBusy && socialState != null,
                                    label = {
                                        Text(
                                            if (socialState?.watching == true) "Acompanhando (Watch)" else "Acompanhar (Watch)",
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    },
                                    modifier = Modifier.height(32.dp).testTag("repo_watch_button")
                                )
                            }
                            socialError?.let { errorMessage ->
                                Text(
                                    text = errorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 13.sp,
                                    modifier = Modifier.testTag("repo_social_error")
                                )
                            }
                            DetailValue("Visibilidade", current.detail.visibility?.visibilityLabel())
                            DetailValue("Branch padrão", current.detail.defaultBranch)
                            DetailValue("Estrelas", displayedStars?.toString())
                            DetailValue("Forks", current.detail.forks?.toString())
                            DetailValue("Issues + PRs abertas", current.detail.openIssuesAndPullRequests?.toString())
                            DetailValue("Último push", current.detail.pushedAt?.let(::formatPushTime))
                            DetailValue("Saúde", repositoryHealth(current.detail.pushedAt, current.detail.archived).summary)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { actionsOpen = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("GitHub Actions")
                        }
                        Button(
                            onClick = { createIssueOpen = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Nova issue")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { workOpen = true },
                            modifier = Modifier.weight(1f).testTag("repository_work_button")
                        ) {
                            Text("Issues e PRs")
                        }
                        Button(
                            onClick = { releasesOpen = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Releases e APKs")
                        }
                    }
                    when (val readme = current.readme) {
                        is GitHubReadmeResult.Found -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("README", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                GitHubMarkdownContent(
                                    markdown = readme.text.ifBlank { "O README está vazio." },
                                    contentPadding = PaddingValues(18.dp)
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
