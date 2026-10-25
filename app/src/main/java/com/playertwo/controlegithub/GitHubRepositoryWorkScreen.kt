package com.playertwo.controlegithub

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class RepositoryWorkTab(val label: String, val tag: String) {
    ISSUES("Issues", "repo_work_tab_issues"),
    PULL_REQUESTS("Pull requests", "repo_work_tab_pulls")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GitHubRepositoryWorkScreen(
    modifier: Modifier,
    client: GitHubHttpClient,
    session: GitHubSession,
    repository: GitHubRepository,
    cacheStore: GitHubCacheStore? = null,
    isOffline: Boolean = session.isOffline,
    onBack: () -> Unit,
    onCreateIssue: () -> Unit,
    onSessionExpired: (GitHubSession) -> Unit,
    onAppearance: () -> Unit,
    isActive: Boolean = true
) {
    var selectedTab by rememberSaveable(repository.id, session.accessToken) {
        mutableStateOf(RepositoryWorkTab.ISSUES)
    }
    var selectedState by rememberSaveable(repository.id, session.accessToken) {
        mutableStateOf(GitHubIssueState.OPEN)
    }
    var refreshVersion by rememberSaveable(repository.id, session.accessToken) {
        mutableIntStateOf(0)
    }

    val issuesPager = remember(
        client,
        session.accessToken,
        repository.fullName,
        selectedState,
        refreshVersion,
        cacheStore
    ) {
        GitHubRepositoryIssuePager(
            client = client,
            accessToken = session.accessToken,
            repository = repository.fullName,
            state = selectedState,
            cacheStore = cacheStore,
            accountLogin = session.user.login
        )
    }

    val pullState = selectedState.toPullRequestState()
    val pullsPager = remember(
        client,
        session.accessToken,
        repository.fullName,
        pullState,
        refreshVersion,
        cacheStore
    ) {
        GitHubRepositoryPullRequestPager(
            client = client,
            accessToken = session.accessToken,
            repository = repository.fullName,
            state = pullState,
            cacheStore = cacheStore,
            accountLogin = session.user.login
        )
    }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var issues by remember(issuesPager) { mutableStateOf(emptyList<GitHubIssue>()) }
    var issuesHasNext by remember(issuesPager) { mutableStateOf(false) }
    var issuesLoaded by remember(issuesPager) { mutableStateOf(false) }
    var issuesLoading by remember(issuesPager) { mutableStateOf(false) }
    var issuesOfflineCache by remember(issuesPager) { mutableStateOf(false) }
    var issuesCachedAtMillis by remember(issuesPager) { mutableStateOf<Long?>(null) }
    var issuesError by remember(issuesPager) { mutableStateOf<String?>(null) }
    var issuesRetryAt by remember(issuesPager) { mutableStateOf<Long?>(null) }

    var pullRequests by remember(pullsPager) { mutableStateOf(emptyList<GitHubPullRequest>()) }
    var pullsHasNext by remember(pullsPager) { mutableStateOf(false) }
    var pullsLoaded by remember(pullsPager) { mutableStateOf(false) }
    var pullsLoading by remember(pullsPager) { mutableStateOf(false) }
    var pullsOfflineCache by remember(pullsPager) { mutableStateOf(false) }
    var pullsCachedAtMillis by remember(pullsPager) { mutableStateOf<Long?>(null) }
    var pullsError by remember(pullsPager) { mutableStateOf<String?>(null) }
    var pullsRetryAt by remember(pullsPager) { mutableStateOf<Long?>(null) }

    var selectedIssue by remember(repository.id, session.accessToken) {
        mutableStateOf<GitHubIssue?>(null)
    }
    var selectedPullRequest by remember(repository.id, session.accessToken) {
        mutableStateOf<GitHubPullRequest?>(null)
    }

    suspend fun loadNextIssuesPage() {
        if (issuesLoading || (issuesLoaded && !issuesHasNext && issuesError == null)) return
        issuesLoading = true
        issuesError = null
        issuesRetryAt = null
        try {
            val result = withContext(Dispatchers.IO) { issuesPager.loadNext() }
            withContext(Dispatchers.Main.immediate) {
                when (result) {
                    is GitHubPageResult.Loaded -> {
                        issues = result.items
                        issuesHasNext = result.hasNext
                        issuesLoaded = true
                        issuesOfflineCache = result.isOfflineCache
                        issuesCachedAtMillis = result.cachedAtMillis
                    }
                    is GitHubPageResult.RateLimited -> {
                        if (result.items.isNotEmpty() || issues.isEmpty()) {
                            issues = result.items
                        }
                        issuesRetryAt = result.retryAtEpochMillis
                        issuesError = GitHubHttpError.RATE_LIMITED.userMessage
                    }
                    is GitHubPageResult.Failed -> {
                        if (result.items.isNotEmpty() || issues.isEmpty()) {
                            issues = result.items
                        }
                        issuesError = result.message
                        if (result.error == GitHubHttpError.UNAUTHORIZED) {
                            onSessionExpired(session)
                        }
                    }
                }
            }
        } finally {
            withContext(Dispatchers.Main.immediate) { issuesLoading = false }
        }
    }

    suspend fun loadNextPullsPage() {
        if (pullsLoading || (pullsLoaded && !pullsHasNext && pullsError == null)) return
        pullsLoading = true
        pullsError = null
        pullsRetryAt = null
        try {
            val result = withContext(Dispatchers.IO) { pullsPager.loadNext() }
            withContext(Dispatchers.Main.immediate) {
                when (result) {
                    is GitHubPageResult.Loaded -> {
                        pullRequests = result.items
                        pullsHasNext = result.hasNext
                        pullsLoaded = true
                        pullsOfflineCache = result.isOfflineCache
                        pullsCachedAtMillis = result.cachedAtMillis
                    }
                    is GitHubPageResult.RateLimited -> {
                        if (result.items.isNotEmpty() || pullRequests.isEmpty()) {
                            pullRequests = result.items
                        }
                        pullsRetryAt = result.retryAtEpochMillis
                        pullsError = GitHubHttpError.RATE_LIMITED.userMessage
                    }
                    is GitHubPageResult.Failed -> {
                        if (result.items.isNotEmpty() || pullRequests.isEmpty()) {
                            pullRequests = result.items
                        }
                        pullsError = result.message
                        if (result.error == GitHubHttpError.UNAUTHORIZED) {
                            onSessionExpired(session)
                        }
                    }
                }
            }
        } finally {
            withContext(Dispatchers.Main.immediate) { pullsLoading = false }
        }
    }

    LaunchedEffect(selectedTab, issuesPager, pullsPager) {
        when (selectedTab) {
            RepositoryWorkTab.ISSUES -> if (!issuesLoaded) loadNextIssuesPage()
            RepositoryWorkTab.PULL_REQUESTS -> if (!pullsLoaded) loadNextPullsPage()
        }
    }

    DisposableEffect(issuesPager) {
        onDispose { issuesPager.cancel() }
    }
    DisposableEffect(pullsPager) {
        onDispose { pullsPager.cancel() }
    }

    val currentIssue = selectedIssue
    if (currentIssue != null) {
        GitHubIssueDetailScreen(
            modifier = modifier,
            client = client,
            session = session,
            issue = currentIssue,
            onBack = { selectedIssue = null },
            onSessionExpired = onSessionExpired,
            onAppearance = onAppearance,
            isOffline = isOffline || issuesOfflineCache,
            isActive = isActive
        )
        return
    }

    val currentPullRequest = selectedPullRequest
    if (currentPullRequest != null) {
        GitHubPullRequestDetailScreen(
            modifier = modifier,
            client = client,
            session = session,
            pullRequest = currentPullRequest,
            onBack = { selectedPullRequest = null },
            onSessionExpired = onSessionExpired,
            onAppearance = onAppearance,
            isOffline = isOffline || pullsOfflineCache,
            isActive = isActive
        )
        return
    }

    BackHandler(enabled = isActive, onBack = onBack)

    val activeLoading = when (selectedTab) {
        RepositoryWorkTab.ISSUES -> issuesLoading
        RepositoryWorkTab.PULL_REQUESTS -> pullsLoading
    }
    val activeOfflineCache = when (selectedTab) {
        RepositoryWorkTab.ISSUES -> issuesOfflineCache
        RepositoryWorkTab.PULL_REQUESTS -> pullsOfflineCache
    }
    val activeCachedAtMillis = when (selectedTab) {
        RepositoryWorkTab.ISSUES -> issuesCachedAtMillis
        RepositoryWorkTab.PULL_REQUESTS -> pullsCachedAtMillis
    }
    val activeError = when (selectedTab) {
        RepositoryWorkTab.ISSUES -> issuesError
        RepositoryWorkTab.PULL_REQUESTS -> pullsError
    }
    val activeRetryAt = when (selectedTab) {
        RepositoryWorkTab.ISSUES -> issuesRetryAt
        RepositoryWorkTab.PULL_REQUESTS -> pullsRetryAt
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "repo-work-top-bar") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Voltar ao repositório"
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onCreateIssue,
                    modifier = Modifier.testTag("repo_work_new_issue_button")
                ) {
                    Text("Nova issue")
                }
                IconButton(onClick = onAppearance) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Configurações de aparência"
                    )
                }
                IconButton(onClick = { refreshVersion++ }, enabled = !activeLoading) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "Atualizar demandas do repositório"
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(repository.fullName, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "Issues e Pull Requests do repositório",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item(key = "repo-work-tabs") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RepositoryWorkTab.entries.forEach { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        }

        item(key = "repo-work-state-filter") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estado", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GitHubIssueState.entries.forEach { choice ->
                        FilterChip(
                            selected = selectedState == choice,
                            onClick = { selectedState = choice },
                            label = { Text(choice.label) },
                            modifier = Modifier.testTag(choice.workStateTestTag())
                        )
                    }
                }
            }
        }

        if (activeOfflineCache) {
            item(key = "repo-work-offline-cache") {
                OfflineCacheBadge(activeCachedAtMillis)
            }
        }

        if (activeError != null) {
            item(key = "repo-work-error") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(activeError, color = MaterialTheme.colorScheme.error)
                    if (activeRetryAt != null) {
                        Text(
                            "Disponível após ${Instant.ofEpochMilli(activeRetryAt)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                when (selectedTab) {
                                    RepositoryWorkTab.ISSUES -> loadNextIssuesPage()
                                    RepositoryWorkTab.PULL_REQUESTS -> loadNextPullsPage()
                                }
                            }
                        },
                        enabled = !activeLoading
                    ) {
                        Text("Tentar novamente")
                    }
                }
            }
        }

        when (selectedTab) {
            RepositoryWorkTab.ISSUES -> {
                if (issuesLoading && issues.isEmpty()) {
                    item(key = "repo-issues-loading") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                "Carregando issues do repositório…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (issuesLoaded && issues.isEmpty() && issuesError == null) {
                    item(key = "repo-issues-empty") {
                        Text(
                            "Nenhuma issue encontrada neste repositório para o filtro selecionado.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(issues, key = GitHubIssue::identity) { issue ->
                    RepositoryIssueCard(issue = issue, onClick = { selectedIssue = issue })
                }
                if (issuesHasNext && issuesError == null) {
                    item(key = "repo-issues-more") {
                        TextButton(
                            onClick = { scope.launch { loadNextIssuesPage() } },
                            enabled = !issuesLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (issuesLoading) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Carregar mais")
                            }
                        }
                    }
                }
            }
            RepositoryWorkTab.PULL_REQUESTS -> {
                if (pullsLoading && pullRequests.isEmpty()) {
                    item(key = "repo-pulls-loading") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                "Carregando pull requests do repositório…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (pullsLoaded && pullRequests.isEmpty() && pullsError == null) {
                    item(key = "repo-pulls-empty") {
                        Text(
                            "Nenhum pull request encontrado neste repositório para o filtro selecionado.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(pullRequests, key = GitHubPullRequest::identity) { pullRequest ->
                    RepositoryPullRequestCard(
                        pullRequest = pullRequest,
                        onClick = { selectedPullRequest = pullRequest }
                    )
                }
                if (pullsHasNext && pullsError == null) {
                    item(key = "repo-pulls-more") {
                        TextButton(
                            onClick = { scope.launch { loadNextPullsPage() } },
                            enabled = !pullsLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (pullsLoading) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Carregar mais")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepositoryIssueCard(issue: GitHubIssue, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(issue.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(
                "${issue.repository} · #${issue.number}",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp
            )
            Text(
                "${if (issue.state == GitHubIssueState.OPEN) "Aberta" else "Fechada"} · ${issue.author?.let { "por @$it" } ?: "autor indisponível"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            if (issue.assignees.isNotEmpty()) {
                Text(
                    "Responsáveis: ${issue.assignees.joinToString { "@$it" }}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            if (issue.labels.isNotEmpty()) {
                Text(
                    issue.labels.joinToString(" · "),
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp
                )
            }
            issue.updatedAt?.let {
                val date = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                    .withZone(ZoneId.systemDefault())
                    .format(it)
                Text(
                    "Atualizada em $date",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun RepositoryPullRequestCard(pullRequest: GitHubPullRequest, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(pullRequest.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(
                "${pullRequest.repository} · #${pullRequest.number}",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp
            )
            Text(
                "${pullRequest.state.label} · ${pullRequest.author?.let { "por @$it" } ?: "autor indisponível"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            if (pullRequest.labels.isNotEmpty()) {
                Text(
                    pullRequest.labels.joinToString(" · "),
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun GitHubIssueState.toPullRequestState(): GitHubRepositoryPullRequestState = when (this) {
    GitHubIssueState.OPEN -> GitHubRepositoryPullRequestState.OPEN
    GitHubIssueState.CLOSED -> GitHubRepositoryPullRequestState.CLOSED
    GitHubIssueState.ALL -> GitHubRepositoryPullRequestState.ALL
}

private fun GitHubIssueState.workStateTestTag(): String = when (this) {
    GitHubIssueState.OPEN -> "repo_work_state_open"
    GitHubIssueState.CLOSED -> "repo_work_state_closed"
    GitHubIssueState.ALL -> "repo_work_state_all"
}
