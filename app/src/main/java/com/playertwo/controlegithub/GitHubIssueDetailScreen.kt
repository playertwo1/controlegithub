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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GitHubIssueDetailScreen(
    modifier: Modifier,
    client: GitHubHttpClient,
    session: GitHubSession,
    issue: GitHubIssue,
    onBack: () -> Unit,
    onSessionExpired: (GitHubSession) -> Unit,
    onAppearance: () -> Unit
) {
    var refreshVersion by remember(issue.identity, session.accessToken) { mutableIntStateOf(0) }
    val loader = remember(client, session.accessToken, issue.identity, refreshVersion) {
        GitHubIssueDetailLoader(client, session.accessToken)
    }
    val pager = remember(client, session.accessToken, issue.identity, refreshVersion) {
        GitHubIssueCommentPager(client, session.accessToken, issue)
    }
    var detail by remember(issue.identity, session.accessToken) { mutableStateOf<GitHubIssueDetailResult?>(null) }
    var loadingDetail by remember(issue.identity, session.accessToken) { mutableStateOf(false) }
    var comments by remember(issue.identity, session.accessToken, refreshVersion) { mutableStateOf(emptyList<GitHubIssueComment>()) }
    var commentsLoaded by remember(issue.identity, session.accessToken, refreshVersion) { mutableStateOf(false) }
    var hasMoreComments by remember(issue.identity, session.accessToken, refreshVersion) { mutableStateOf(false) }
    var loadingComments by remember(issue.identity, session.accessToken, refreshVersion) { mutableStateOf(false) }
    var commentsError by remember(issue.identity, session.accessToken, refreshVersion) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun loadComments() {
        if (loadingComments || (commentsLoaded && !hasMoreComments && commentsError == null)) return
        loadingComments = true
        commentsError = null
        try {
            when (val result = withContext(Dispatchers.IO) { pager.loadNext() }) {
                is GitHubPageResult.Loaded -> {
                    comments = result.items
                    hasMoreComments = result.hasNext
                    commentsLoaded = true
                }
                is GitHubPageResult.RateLimited -> {
                    comments = result.items
                    commentsError = GitHubHttpError.RATE_LIMITED.userMessage
                }
                is GitHubPageResult.Failed -> {
                    comments = result.items
                    commentsError = result.message
                }
            }
        } finally {
            loadingComments = false
        }
    }

    BackHandler(onBack = onBack)
    LaunchedEffect(loader, issue.identity, refreshVersion) {
        loadingDetail = true
        detail = withContext(Dispatchers.IO) { loader.load(issue) }
        loadingDetail = false
    }
    LaunchedEffect(detail, pager, refreshVersion) {
        if (detail is GitHubIssueDetailResult.Loaded && !commentsLoaded) loadComments()
    }
    LaunchedEffect(detail, commentsError) {
        val expired = (detail as? GitHubIssueDetailResult.Failed)?.error == GitHubHttpError.UNAUTHORIZED ||
            commentsError == GitHubHttpError.UNAUTHORIZED.userMessage
        if (expired) onSessionExpired(session)
    }
    DisposableEffect(pager) { onDispose { pager.cancel() } }
    DisposableEffect(loader) { onDispose { loader.cancel() } }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 24.dp, vertical = 20.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar às issues")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onAppearance) {
                Icon(Icons.Outlined.Settings, contentDescription = "Configurações de aparência")
            }
            IconButton(onClick = { refreshVersion++; detail = null }, enabled = !loadingDetail) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar issue")
            }
        }

        when (val current = detail) {
            null -> if (loadingDetail) LoadingIssueDetail()
            GitHubIssueDetailResult.Removed -> Text("Esta issue foi removida do GitHub.", color = MaterialTheme.colorScheme.error)
            GitHubIssueDetailResult.Unavailable -> Text("Esta issue não está disponível para sua conta.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            is GitHubIssueDetailResult.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(current.error.userMessage, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { refreshVersion++; detail = null }, enabled = !loadingDetail) { Text("Tentar novamente") }
            }
            is GitHubIssueDetailResult.Loaded -> {
                Text(current.detail.issue.title, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${current.detail.issue.repository} · #${current.detail.issue.number} · ${if (current.detail.issue.state == GitHubIssueState.OPEN) "Aberta" else "Fechada"}",
                    color = MaterialTheme.colorScheme.primary
                )
                Text("Por ${current.detail.issue.author?.let { "@$it" } ?: "autor indisponível"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (current.detail.issue.labels.isNotEmpty()) {
                    Text(current.detail.issue.labels.joinToString(" · "), color = MaterialTheme.colorScheme.secondary)
                }
                Text("Descrição", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                InertMarkdown(current.detail.body?.takeIf(String::isNotBlank) ?: "Sem descrição.")
                Text("Comentários", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                if (!commentsLoaded && loadingComments) LoadingIssueDetail("Carregando comentários…")
                if (commentsLoaded && comments.isEmpty() && commentsError == null) {
                    Text("Esta issue ainda não tem comentários.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                comments.forEach { comment -> IssueCommentCard(comment) }
                commentsError?.let { message ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { scope.launch { loadComments() } }, enabled = !loadingComments, modifier = Modifier.testTag("issue-comments-retry")) { Text("Tentar novamente") }
                    }
                }
                if (hasMoreComments && commentsError == null) {
                    TextButton(
                        onClick = { scope.launch { loadComments() } },
                        enabled = !loadingComments,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (loadingComments) CircularProgressIndicator(strokeWidth = 2.dp)
                        else Text("Carregar mais comentários")
                    }
                }
            }
        }
    }
}

@Composable
private fun InertMarkdown(text: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, modifier = Modifier.padding(18.dp), lineHeight = 22.sp, fontFamily = FontFamily.Default)
    }
}

@Composable
private fun IssueCommentCard(comment: GitHubIssueComment) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(comment.author?.let { "@$it" } ?: "Autor indisponível", fontWeight = FontWeight.SemiBold)
            comment.createdAt?.let { instant ->
                Text(
                    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                        .withLocale(Locale.forLanguageTag("pt-BR"))
                        .withZone(ZoneId.systemDefault()).format(instant),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            InertMarkdown(comment.body?.takeIf(String::isNotBlank) ?: "Comentário sem texto.")
        }
    }
}

@Composable
private fun LoadingIssueDetail(label: String = "Carregando issue do GitHub…") {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator()
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
