package com.playertwo.controlegithub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun GitHubPullRequestsScreen(
    modifier: Modifier,
    client: GitHubHttpClient,
    session: GitHubSession?,
    sessionRestoring: Boolean,
    sessionStorageError: Boolean,
    onConnected: suspend (GitHubSession) -> Boolean,
    onSessionExpired: (GitHubSession) -> Unit,
    onAppearance: () -> Unit,
    onShowIssues: () -> Unit
) {
    var selectedFilter by rememberSaveable(session?.user?.login) { mutableStateOf(GitHubPullRequestFilter.OPEN) }
    var refreshVersion by rememberSaveable(session?.user?.login) { mutableIntStateOf(0) }
    val pager = remember(client, session?.accessToken, selectedFilter, refreshVersion) {
        session?.let { GitHubPullRequestPager(client, it.accessToken, selectedFilter) }
    }
    val scope = rememberCoroutineScope()
    var pullRequests by remember(pager) { mutableStateOf(emptyList<GitHubPullRequest>()) }
    var hasNext by remember(pager) { mutableStateOf(false) }
    var loaded by remember(pager) { mutableStateOf(false) }
    var loading by remember(pager) { mutableStateOf(false) }
    var errorMessage by remember(pager) { mutableStateOf<String?>(null) }
    var retryAt by remember(pager) { mutableStateOf<Long?>(null) }
    var incomplete by remember(pager) { mutableStateOf(false) }
    var selectedPullRequest by remember { mutableStateOf<GitHubPullRequest?>(null) }
    val listState = rememberLazyListState()

    suspend fun loadNextPage() {
        val activePager = pager ?: return
        if (loading || (loaded && !hasNext && errorMessage == null)) return
        loading = true
        errorMessage = null
        retryAt = null
        try {
            when (val result = withContext(Dispatchers.IO) { activePager.loadNext() }) {
                is GitHubPullRequestPageResult.Loaded -> {
                    pullRequests = result.page.items
                    hasNext = result.page.hasNext
                    incomplete = result.page.incomplete || result.page.totalCount >= 1_000
                    loaded = true
                }
                is GitHubPullRequestPageResult.RateLimited -> {
                    pullRequests = result.page.items
                    incomplete = result.page.incomplete || result.page.totalCount >= 1_000
                    retryAt = result.retryAtEpochMillis
                    errorMessage = GitHubHttpError.RATE_LIMITED.userMessage
                }
                is GitHubPullRequestPageResult.Failed -> {
                    pullRequests = result.page.items
                    incomplete = result.page.incomplete || result.page.totalCount >= 1_000
                    errorMessage = result.message
                    if (result.error == GitHubHttpError.UNAUTHORIZED && session != null) onSessionExpired(session)
                }
            }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(pager, sessionRestoring) {
        if (pager != null && !sessionRestoring) loadNextPage()
    }
    DisposableEffect(pager) { onDispose { pager?.cancel() } }

    val selected = selectedPullRequest
    if (selected != null && session != null) {
        GitHubPullRequestDetailScreen(
            modifier = modifier,
            client = client,
            session = session,
            pullRequest = selected,
            onBack = { selectedPullRequest = null },
            onSessionExpired = onSessionExpired,
            onAppearance = onAppearance
        )
        return
    }

    LazyColumn(
        modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "pull-requests-header") {
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
                FilterChip(selected = false, onClick = onShowIssues, label = { Text("Issues") })
                FilterChip(selected = true, onClick = {}, label = { Text("Pull requests") })
            }
        }
        when {
            sessionRestoring -> item(key = "pull-requests-restoring") {
                LoadingPullRequests("Restaurando sessão segura…")
            }
            session == null -> item(key = "pull-requests-sign-in") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Conecte sua conta GitHub para consultar pull requests que envolvem você.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sessionStorageError) {
                        Text("O armazenamento seguro informou uma falha ao encerrar a sessão.", color = MaterialTheme.colorScheme.error)
                    }
                    GitHubSignIn(onConnected)
                }
            }
            else -> {
                item(key = "pull-requests-account") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Pull requests do GitHub", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                            Text("@${session.user.login} · Envolvem você", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        IconButton(onClick = { refreshVersion++ }, enabled = !loading) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar pull requests")
                        }
                    }
                }
                item(key = "pull-requests-state-filter") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Estado", fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GitHubPullRequestFilter.entries.forEach { choice ->
                                FilterChip(
                                    selected = selectedFilter == choice,
                                    onClick = { selectedFilter = choice },
                                    label = { Text(choice.label) }
                                )
                            }
                        }
                    }
                }
                if (incomplete) item(key = "pull-requests-incomplete") {
                    Text(
                        "A busca do GitHub retornou resultados parciais ou atingiu o limite de 1.000 itens.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item(key = "pull-requests-loading") {
                    if (loading && pullRequests.isEmpty()) LoadingPullRequests("Carregando pull requests…")
                }
                item(key = "pull-requests-error") {
                    errorMessage?.let { message ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(message, color = MaterialTheme.colorScheme.error)
                            retryAt?.let { Text("Disponível após ${java.time.Instant.ofEpochMilli(it)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            TextButton(onClick = { scope.launch { loadNextPage() } }, enabled = !loading) { Text("Tentar novamente") }
                        }
                    }
                }
                item(key = "pull-requests-empty") {
                    if (loaded && pullRequests.isEmpty() && errorMessage == null) {
                        Text("Nenhum pull request encontrado com este filtro.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(pullRequests, key = GitHubPullRequest::identity) { pullRequest ->
                    PullRequestCard(pullRequest) { selectedPullRequest = pullRequest }
                }
                item(key = "pull-requests-more") {
                    if (hasNext && errorMessage == null) {
                        TextButton(onClick = { scope.launch { loadNextPage() } }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("Carregar mais pull requests")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PullRequestCard(pullRequest: GitHubPullRequest, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(pullRequest.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text("${pullRequest.repository} · #${pullRequest.number}", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Text(
                "${pullRequest.state.label} · ${pullRequest.author?.let { "por @$it" } ?: "autor indisponível"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            if (pullRequest.labels.isNotEmpty()) {
                Text(pullRequest.labels.joinToString(" · "), color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
internal fun GitHubPullRequestDetailScreen(
    modifier: Modifier,
    client: GitHubHttpClient,
    session: GitHubSession,
    pullRequest: GitHubPullRequest,
    onBack: () -> Unit,
    onSessionExpired: (GitHubSession) -> Unit,
    onAppearance: () -> Unit
) {
    var refreshVersion by remember(pullRequest.identity, session.accessToken) { mutableIntStateOf(0) }
    val loader = remember(client, session.accessToken, pullRequest.identity, refreshVersion) {
        GitHubPullRequestLoader(client, session.accessToken)
    }
    val commentsPager = remember(client, session.accessToken, pullRequest.identity, refreshVersion) {
        GitHubPullRequestCommentPager(client, session.accessToken, pullRequest)
    }
    val reviewsPager = remember(client, session.accessToken, pullRequest.identity, refreshVersion) {
        GitHubPullRequestReviewPager(client, session.accessToken, pullRequest)
    }
    var detail by remember(pullRequest.identity, session.accessToken, refreshVersion) { mutableStateOf<GitHubPullRequestDetailResult?>(null) }
    var loadingDetail by remember(pullRequest.identity, session.accessToken, refreshVersion) { mutableStateOf(false) }
    var comments by remember(pullRequest.identity, session.accessToken, refreshVersion) { mutableStateOf(emptyList<GitHubPullRequestComment>()) }
    var commentsLoaded by remember(pullRequest.identity, session.accessToken, refreshVersion) { mutableStateOf(false) }
    var hasMoreComments by remember(pullRequest.identity, session.accessToken, refreshVersion) { mutableStateOf(false) }
    var loadingComments by remember(pullRequest.identity, session.accessToken, refreshVersion) { mutableStateOf(false) }
    var commentsError by remember(pullRequest.identity, session.accessToken, refreshVersion) { mutableStateOf<String?>(null) }
    var reviews by remember(pullRequest.identity, session.accessToken, refreshVersion) { mutableStateOf(emptyList<GitHubPullRequestReview>()) }
    var reviewsLoaded by remember(pullRequest.identity, session.accessToken, refreshVersion) { mutableStateOf(false) }
    var hasMoreReviews by remember(pullRequest.identity, session.accessToken, refreshVersion) { mutableStateOf(false) }
    var loadingReviews by remember(pullRequest.identity, session.accessToken, refreshVersion) { mutableStateOf(false) }
    var reviewsError by remember(pullRequest.identity, session.accessToken, refreshVersion) { mutableStateOf<String?>(null) }
    var sessionExpiryReported by remember(pullRequest.identity, session.accessToken, refreshVersion) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reportSessionExpiredOnce() {
        if (!sessionExpiryReported) {
            sessionExpiryReported = true
            onSessionExpired(session)
        }
    }

    suspend fun loadComments() {
        if (loadingComments || (commentsLoaded && !hasMoreComments && commentsError == null)) return
        loadingComments = true
        commentsError = null
        try {
            when (val result = withContext(Dispatchers.IO) { commentsPager.loadNext() }) {
                is GitHubPageResult.Loaded -> { comments = result.items; hasMoreComments = result.hasNext; commentsLoaded = true }
                is GitHubPageResult.RateLimited -> { comments = result.items; commentsError = GitHubHttpError.RATE_LIMITED.userMessage }
                is GitHubPageResult.Failed -> {
                    comments = result.items
                    commentsError = result.message
                    if (result.error == GitHubHttpError.UNAUTHORIZED) reportSessionExpiredOnce()
                }
            }
        } finally { loadingComments = false }
    }

    suspend fun loadReviews() {
        if (loadingReviews || (reviewsLoaded && !hasMoreReviews && reviewsError == null)) return
        loadingReviews = true
        reviewsError = null
        try {
            when (val result = withContext(Dispatchers.IO) { reviewsPager.loadNext() }) {
                is GitHubPageResult.Loaded -> { reviews = result.items; hasMoreReviews = result.hasNext; reviewsLoaded = true }
                is GitHubPageResult.RateLimited -> { reviews = result.items; reviewsError = GitHubHttpError.RATE_LIMITED.userMessage }
                is GitHubPageResult.Failed -> {
                    reviews = result.items
                    reviewsError = result.message
                    if (result.error == GitHubHttpError.UNAUTHORIZED) reportSessionExpiredOnce()
                }
            }
        } finally { loadingReviews = false }
    }

    BackHandler(onBack = onBack)
    LaunchedEffect(loader, pullRequest.identity, refreshVersion) {
        loadingDetail = true
        val result = withContext(Dispatchers.IO) { loader.load(pullRequest) }
        detail = result
        if ((result as? GitHubPullRequestDetailResult.Failed)?.error == GitHubHttpError.UNAUTHORIZED) {
            reportSessionExpiredOnce()
        }
        loadingDetail = false
    }
    LaunchedEffect(detail, commentsPager, reviewsPager) {
        if (detail is GitHubPullRequestDetailResult.Loaded) {
            launch { loadComments() }
            launch { loadReviews() }
        }
    }
    DisposableEffect(commentsPager) { onDispose { commentsPager.cancel() } }
    DisposableEffect(reviewsPager) { onDispose { reviewsPager.cancel() } }
    DisposableEffect(loader) { onDispose { loader.cancel() } }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(PaddingValues(horizontal = 24.dp, vertical = 20.dp)), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar aos pull requests") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onAppearance) { Icon(Icons.Outlined.Settings, contentDescription = "Configurações de aparência") }
            IconButton(onClick = { refreshVersion++; detail = null }, enabled = !loadingDetail) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar pull request")
            }
        }
        when (val current = detail) {
            null -> if (loadingDetail) LoadingPullRequests("Carregando pull request…")
            GitHubPullRequestDetailResult.Unavailable -> Text("Esta pull request não está disponível para sua conta.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            is GitHubPullRequestDetailResult.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(current.error.userMessage, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { refreshVersion++; detail = null }, enabled = !loadingDetail) { Text("Tentar novamente") }
            }
            is GitHubPullRequestDetailResult.Loaded -> {
                val value = current.detail
                Text(value.title, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                Text("${value.repository} · #${value.number} · ${value.state.label}", color = MaterialTheme.colorScheme.primary)
                Text("Por ${value.author?.let { "@$it" } ?: "autor indisponível"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (value.labels.isNotEmpty()) Text(value.labels.joinToString(" · "), color = MaterialTheme.colorScheme.secondary)
                Text("Origem", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("${value.headRepository ?: "repositório indisponível"}:${value.headBranch}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Destino", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("${value.baseRepository}:${value.baseBranch}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Descrição", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                PullRequestMarkdown(value.body?.takeIf(String::isNotBlank) ?: "Sem descrição.")
                Text("Comentários", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                if (!commentsLoaded && loadingComments) LoadingPullRequests("Carregando comentários…")
                if (commentsLoaded && comments.isEmpty() && commentsError == null) Text("Esta pull request ainda não tem comentários.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                comments.forEach { comment -> PullRequestCommentCard(comment) }
                commentsError?.let { message -> PullRequestLoadError(message, loadingComments) { scope.launch { loadComments() } } }
                if (hasMoreComments && commentsError == null) {
                    TextButton(onClick = { scope.launch { loadComments() } }, enabled = !loadingComments, modifier = Modifier.fillMaxWidth()) {
                        if (loadingComments) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Carregar mais comentários")
                    }
                }
                Text("Reviews", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                if (!reviewsLoaded && loadingReviews) LoadingPullRequests("Carregando reviews…")
                if (reviewsLoaded && reviews.isEmpty() && reviewsError == null) Text("Esta pull request ainda não tem reviews.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                reviews.forEach { review -> PullRequestReviewCard(review) }
                reviewsError?.let { message -> PullRequestLoadError(message, loadingReviews) { scope.launch { loadReviews() } } }
                if (hasMoreReviews && reviewsError == null) {
                    TextButton(onClick = { scope.launch { loadReviews() } }, enabled = !loadingReviews, modifier = Modifier.fillMaxWidth()) {
                        if (loadingReviews) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Carregar mais reviews")
                    }
                }
            }
        }
    }
}

@Composable
private fun PullRequestMarkdown(text: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(18.dp), lineHeight = 22.sp, fontFamily = FontFamily.Default)
    }
}

@Composable
private fun PullRequestCommentCard(comment: GitHubPullRequestComment) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(comment.author?.let { "@$it" } ?: "Autor indisponível", fontWeight = FontWeight.SemiBold)
            PullRequestMarkdown(comment.body?.takeIf(String::isNotBlank) ?: "Comentário sem texto.")
        }
    }
}

@Composable
private fun PullRequestReviewCard(review: GitHubPullRequestReview) {
    val label = if (review.state == "PENDING" && review.submittedAt == null) "Review pendente" else when (review.state) {
        "APPROVED" -> "Aprovou"
        "CHANGES_REQUESTED" -> "Solicitou alterações"
        "DISMISSED" -> "Review dispensada"
        "PENDING" -> "Review pendente"
        else -> "Comentou"
    }
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$label · ${review.author?.let { "@$it" } ?: "autor indisponível"}", fontWeight = FontWeight.SemiBold)
            review.submittedAt?.let { submittedAt ->
                val date = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault()).format(submittedAt)
                Text("Enviada em $date", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            review.body?.takeIf(String::isNotBlank)?.let { PullRequestMarkdown(it) }
        }
    }
}

@Composable
private fun PullRequestLoadError(message: String, loading: Boolean, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry, enabled = !loading) { Text("Tentar novamente") }
    }
}

@Composable
private fun LoadingPullRequests(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
