package com.playertwo.controlegithub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GitHubIssuesScreen(
    modifier: Modifier,
    client: GitHubHttpClient,
    session: GitHubSession?,
    sessionRestoring: Boolean,
    sessionStorageError: Boolean,
    onConnected: suspend (GitHubSession) -> Boolean,
    onSessionExpired: (GitHubSession) -> Unit,
    onAppearance: () -> Unit,
    onShowPullRequests: () -> Unit = {}
) {
    var selectedScope by rememberSaveable(session?.user?.login) { mutableStateOf(GitHubIssueScope.ASSIGNED) }
    var selectedState by rememberSaveable(session?.user?.login) { mutableStateOf(GitHubIssueState.OPEN) }
    var refreshVersion by rememberSaveable(session?.user?.login) { mutableIntStateOf(0) }
    val pager = remember(client, session?.accessToken, selectedScope, selectedState, refreshVersion) {
        session?.let { GitHubIssuePager(client, it.accessToken, selectedScope, selectedState) }
    }
    val scope = rememberCoroutineScope()
    var issues by remember(pager) { mutableStateOf(emptyList<GitHubIssue>()) }
    var hasNext by remember(pager) { mutableStateOf(false) }
    var loaded by remember(pager) { mutableStateOf(false) }
    var loading by remember(pager) { mutableStateOf(false) }
    var errorMessage by remember(pager) { mutableStateOf<String?>(null) }
    var retryAt by remember(pager) { mutableStateOf<Long?>(null) }
    var openedIssue by remember { mutableStateOf<GitHubIssue?>(null) }
    val listState = remember(pager) { LazyListState() }

    suspend fun loadNextPage() {
        val activePager = pager ?: return
        if (loading || (loaded && !hasNext && errorMessage == null)) return
        loading = true
        errorMessage = null
        retryAt = null
        try {
            when (val result = withContext(Dispatchers.IO) { activePager.loadNext() }) {
                is GitHubPageResult.Loaded -> {
                    issues = result.items
                    hasNext = result.hasNext
                    loaded = true
                }
                is GitHubPageResult.RateLimited -> {
                    issues = result.items
                    retryAt = result.retryAtEpochMillis
                    errorMessage = GitHubHttpError.RATE_LIMITED.userMessage
                }
                is GitHubPageResult.Failed -> {
                    issues = result.items
                    errorMessage = result.message
                    if (result.error == GitHubHttpError.UNAUTHORIZED && session != null) {
                        onSessionExpired(session)
                    }
                }
            }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(pager, sessionRestoring) {
        if (pager != null && !sessionRestoring) loadNextPage()
    }
    DisposableEffect(pager) {
        onDispose { pager?.cancel() }
    }

    if (openedIssue != null && session != null) {
        GitHubIssueDetailScreen(
            modifier = modifier,
            client = client,
            session = session,
            issue = openedIssue!!,
            onBack = { openedIssue = null },
            onSessionExpired = onSessionExpired,
            onAppearance = onAppearance
        )
    } else key(pager) {
        LazyColumn(
            modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        item(key = "issues-header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "CONTROLE / GITHUB",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onAppearance) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Configurações de aparência")
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Seu trabalho", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }

        item(key = "work-type-filter") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = true, onClick = {}, label = { Text("Issues") })
                FilterChip(selected = false, onClick = onShowPullRequests, label = { Text("Pull requests") })
            }
        }

        when {
            sessionRestoring -> item(key = "issues-restoring") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Restaurando sessão segura…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            session == null -> item(key = "issues-sign-in") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Conecte sua conta GitHub para consultar as issues que ela pode acessar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sessionStorageError) {
                        Text("O armazenamento seguro informou uma falha ao encerrar a sessão.", color = MaterialTheme.colorScheme.error)
                    }
                    GitHubSignIn(onConnected)
                }
            }
            else -> {
                item(key = "issues-account") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Issues do GitHub", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                            Text("@${session.user.login}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        IconButton(onClick = { refreshVersion++ }, enabled = !loading) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar issues")
                        }
                    }
                }
                item(key = "issues-filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Participação", fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GitHubIssueScope.entries.forEach { choice ->
                                FilterChip(
                                    selected = selectedScope == choice,
                                    onClick = { selectedScope = choice },
                                    label = { Text(choice.label) }
                                )
                            }
                        }
                        Text("Estado", fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GitHubIssueState.entries.forEach { choice ->
                                FilterChip(
                                    selected = selectedState == choice,
                                    onClick = { selectedState = choice },
                                    label = { Text(choice.label) }
                                )
                            }
                        }
                    }
                }
                item(key = "issues-loading") {
                    if (loading && issues.isEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Carregando issues…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    } else Spacer(Modifier.height(0.dp))
                }
                item(key = "issues-error") {
                    if (errorMessage != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                        if (retryAt != null) Text("Disponível após ${java.time.Instant.ofEpochMilli(retryAt!!)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { scope.launch { loadNextPage() } }, enabled = !loading) { Text("Tentar novamente") }
                    }
                    } else Spacer(Modifier.height(0.dp))
                }
                item(key = "issues-empty") {
                    if (loaded && issues.isEmpty() && errorMessage == null) {
                        Text("Nenhuma issue encontrada com estes filtros.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else Spacer(Modifier.height(0.dp))
                }
                items(issues, key = GitHubIssue::identity) { issue -> GitHubIssueCard(issue) { openedIssue = issue } }
                item(key = "issues-more") {
                    if (hasNext && errorMessage == null) {
                        TextButton(
                            onClick = { scope.launch { loadNextPage() } },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("Carregar mais issues")
                        }
                    } else Spacer(Modifier.height(0.dp))
                    }
            }
        }
        }
    }
}

@Composable
private fun GitHubIssueCard(issue: GitHubIssue, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(issue.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text("${issue.repository} · #${issue.number}", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Text(
                "${if (issue.state == GitHubIssueState.OPEN) "Aberta" else "Fechada"} · ${issue.author?.let { "por @$it" } ?: "autor indisponível"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            if (issue.assignees.isNotEmpty()) {
                Text("Responsáveis: ${issue.assignees.joinToString { "@$it" }}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            if (issue.labels.isNotEmpty()) {
                Text(issue.labels.joinToString(" · "), color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
            }
            issue.updatedAt?.let {
                val date = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault()).format(it)
                Text("Atualizada em $date", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}
