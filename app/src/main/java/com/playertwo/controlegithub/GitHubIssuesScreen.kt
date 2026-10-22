package com.playertwo.controlegithub

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class WorkIssueViewMode {
    LIST,
    KANBAN
}

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
    onShowPullRequests: () -> Unit = {},
    cacheStore: GitHubCacheStore? = null,
    kanbanStore: KanbanStore? = null,
    initialKanban: Boolean = false
) {
    val context = LocalContext.current
    val activeKanbanStore = remember(kanbanStore, context) {
        kanbanStore ?: DataStoreKanbanStore(context)
    }
    var viewMode by rememberSaveable(session?.user?.login) {
        mutableStateOf(if (initialKanban) WorkIssueViewMode.KANBAN else WorkIssueViewMode.LIST)
    }
    var selectedScope by rememberSaveable(session?.user?.login) { mutableStateOf(GitHubIssueScope.ASSIGNED) }
    var selectedState by rememberSaveable(session?.user?.login) { mutableStateOf(GitHubIssueState.OPEN) }
    var refreshVersion by rememberSaveable(session?.user?.login) { mutableIntStateOf(0) }
    val pager = remember(client, session?.accessToken, selectedScope, selectedState, refreshVersion, cacheStore) {
        session?.let {
            GitHubIssuePager(
                client = client,
                accessToken = it.accessToken,
                scope = selectedScope,
                state = selectedState,
                cacheStore = cacheStore,
                accountLogin = it.user.login
            )
        }
    }
    val scope = rememberCoroutineScope()
    var issues by remember(client, session?.user?.login, selectedScope, selectedState) { mutableStateOf(emptyList<GitHubIssue>()) }
    var hasNext by remember(pager) { mutableStateOf(false) }
    var loaded by remember(pager) { mutableStateOf(false) }
    var loading by remember(pager) { mutableStateOf(false) }
    var isOfflineCache by remember(client, session?.user?.login, selectedScope, selectedState) { mutableStateOf(false) }
    var cachedAtMillis by remember(client, session?.user?.login, selectedScope, selectedState) { mutableStateOf<Long?>(null) }
    var errorMessage by remember(pager) { mutableStateOf<String?>(null) }
    var retryAt by remember(pager) { mutableStateOf<Long?>(null) }
    var openedIssue by remember { mutableStateOf<GitHubIssue?>(null) }
    var movingIssue by remember { mutableStateOf<GitHubIssue?>(null) }
    var kanbanMapping by remember(session?.user?.login) { mutableStateOf(emptyMap<String, KanbanColumn>()) }
    val listState = rememberLazyListState()

    LaunchedEffect(session?.user?.login, activeKanbanStore) {
        val login = session?.user?.login
        if (login != null) {
            val mapping = withContext(Dispatchers.IO) { activeKanbanStore.load(login) }
            withContext(Dispatchers.Main.immediate) { kanbanMapping = mapping }
        } else {
            kanbanMapping = emptyMap()
        }
    }

    suspend fun loadNextPage() {
        val activePager = pager ?: return
        if (loading || (loaded && !hasNext && errorMessage == null)) return
        loading = true
        errorMessage = null
        retryAt = null
        try {
            val result = withContext(Dispatchers.IO) { activePager.loadNext() }
            withContext(Dispatchers.Main.immediate) {
                when (result) {
                    is GitHubPageResult.Loaded -> {
                        issues = result.items
                        hasNext = result.hasNext
                        loaded = true
                        isOfflineCache = result.isOfflineCache
                        cachedAtMillis = result.cachedAtMillis
                    }
                    is GitHubPageResult.RateLimited -> {
                        if (result.items.isNotEmpty() || issues.isEmpty()) {
                            issues = result.items
                        }
                        retryAt = result.retryAtEpochMillis
                        errorMessage = GitHubHttpError.RATE_LIMITED.userMessage
                    }
                    is GitHubPageResult.Failed -> {
                        if (result.items.isNotEmpty() || issues.isEmpty()) {
                            issues = result.items
                        }
                        errorMessage = result.message
                        if (result.error == GitHubHttpError.UNAUTHORIZED && session != null) {
                            onSessionExpired(session)
                        }
                    }
                }
            }
        } finally {
            withContext(Dispatchers.Main.immediate) { loading = false }
        }
    }

    LaunchedEffect(pager, sessionRestoring) {
        if (pager != null && !sessionRestoring) loadNextPage()
    }
    DisposableEffect(pager) {
        onDispose { pager?.cancel() }
    }

    if (movingIssue != null && session != null) {
        val currentIssue = movingIssue!!
        val currentColumn = resolveKanbanColumn(currentIssue, kanbanMapping)
        AlertDialog(
            onDismissRequest = { movingIssue = null },
            title = { Text("Mover cartão") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Selecione a coluna para #${currentIssue.number} · ${currentIssue.title}:",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    KanbanColumn.entries.forEach { targetColumn ->
                        val isCurrent = targetColumn == currentColumn
                        OutlinedButton(
                            onClick = {
                                val target = targetColumn
                                val issueToMove = currentIssue
                                movingIssue = null
                                scope.launch {
                                    val updated = withContext(Dispatchers.IO) {
                                        activeKanbanStore.setColumn(
                                            session.user.login,
                                            issueToMove.identity,
                                            target
                                        )
                                    }
                                    kanbanMapping = updated
                                }
                            },
                            enabled = !isCurrent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("kanban_move_target_${targetColumn.name.lowercase(Locale.ROOT)}")
                        ) {
                            Text(if (isCurrent) "${targetColumn.label} (atual)" else targetColumn.label)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { movingIssue = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (openedIssue != null && session != null) {
        GitHubIssueDetailScreen(
            modifier = modifier,
            client = client,
            session = session,
            issue = openedIssue!!,
            onBack = { openedIssue = null },
            onSessionExpired = onSessionExpired,
            onAppearance = onAppearance,
            isOffline = isOfflineCache || session.isOffline
        )
    } else if (viewMode == WorkIssueViewMode.KANBAN && session != null) {
        GitHubIssuesKanbanView(
            modifier = modifier,
            session = session,
            issues = issues,
            loading = loading,
            isOfflineCache = isOfflineCache,
            cachedAtMillis = cachedAtMillis,
            kanbanMapping = kanbanMapping,
            onRefresh = { refreshVersion++ },
            onAppearance = onAppearance,
            onShowPullRequests = onShowPullRequests,
            onSwitchToList = { viewMode = WorkIssueViewMode.LIST },
            onOpenIssue = { issue -> openedIssue = issue },
            onMoveIssue = { issue -> movingIssue = issue }
        )
    } else LazyColumn(
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
                FilterChip(
                    selected = viewMode == WorkIssueViewMode.LIST,
                    onClick = { viewMode = WorkIssueViewMode.LIST },
                    label = { Text("Issues") }
                )
                FilterChip(
                    selected = false,
                    onClick = onShowPullRequests,
                    label = { Text("Pull requests") }
                )
                if (session != null) {
                    FilterChip(
                        selected = viewMode == WorkIssueViewMode.KANBAN,
                        onClick = { viewMode = WorkIssueViewMode.KANBAN },
                        label = { Text("Quadro") },
                        modifier = Modifier.testTag("filter_chip_kanban")
                    )
                }
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
                item(key = "issues-offline-cache") {
                    if (isOfflineCache) {
                        OfflineCacheBadge(cachedAtMillis)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GitHubIssuesKanbanView(
    modifier: Modifier,
    session: GitHubSession,
    issues: List<GitHubIssue>,
    loading: Boolean,
    isOfflineCache: Boolean,
    cachedAtMillis: Long?,
    kanbanMapping: Map<String, KanbanColumn>,
    onRefresh: () -> Unit,
    onAppearance: () -> Unit,
    onShowPullRequests: () -> Unit,
    onSwitchToList: () -> Unit,
    onOpenIssue: (GitHubIssue) -> Unit,
    onMoveIssue: (GitHubIssue) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
        Text("Seu trabalho", fontSize = 28.sp, fontWeight = FontWeight.Bold)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = false,
                onClick = onSwitchToList,
                label = { Text("Issues") }
            )
            FilterChip(
                selected = false,
                onClick = onShowPullRequests,
                label = { Text("Pull requests") }
            )
            FilterChip(
                selected = true,
                onClick = {},
                label = { Text("Quadro") },
                modifier = Modifier.testTag("filter_chip_kanban")
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Quadro Kanban", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                Text("@${session.user.login}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            IconButton(onClick = onRefresh, enabled = !loading) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar issues")
            }
        }

        if (isOfflineCache) {
            OfflineCacheBadge(cachedAtMillis)
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("kanban_local_banner")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Organização local: mover os cartões organiza seu trabalho neste aparelho e não altera o estado da issue no GitHub.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (loading && issues.isEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Carregando issues…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            KanbanColumn.entries.forEach { col ->
                val columnIssues = issues.filter { issue ->
                    resolveKanbanColumn(issue, kanbanMapping) == col
                }
                KanbanColumnCard(
                    column = col,
                    issues = columnIssues,
                    onOpenIssue = onOpenIssue,
                    onMoveIssue = onMoveIssue,
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .testTag("kanban_column_${col.name.lowercase(Locale.ROOT)}")
                )
            }
        }
    }
}

@Composable
private fun KanbanColumnCard(
    column: KanbanColumn,
    issues: List<GitHubIssue>,
    onOpenIssue: (GitHubIssue) -> Unit,
    onMoveIssue: (GitHubIssue) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    column.label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Text(
                        "${issues.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (issues.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nenhuma issue",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(issues, key = GitHubIssue::identity) { issue ->
                        KanbanIssueCard(
                            issue = issue,
                            onOpen = { onOpenIssue(issue) },
                            onMove = { onMoveIssue(issue) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KanbanIssueCard(
    issue: GitHubIssue,
    onOpen: () -> Unit,
    onMove: () -> Unit
) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("kanban_card_${issue.number}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                issue.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${issue.repository} · #${issue.number}",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp
            )
            Text(
                "${if (issue.state == GitHubIssueState.OPEN) "Aberta" else "Fechada"} · ${issue.author?.let { "por @$it" } ?: "autor indisponível"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            if (issue.labels.isNotEmpty()) {
                Text(
                    issue.labels.joinToString(" · "),
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onMove,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("kanban_move_card_${issue.number}")
                ) {
                    Text("Mover", fontSize = 12.sp)
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
