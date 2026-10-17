package com.playertwo.controlegithub

import androidx.activity.compose.BackHandler
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
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

private enum class NotificationReadFilter(val label: String) {
    ALL("Todas"), UNREAD("Não lidas"), READ("Lidas")
}

private enum class NotificationTypeFilter(val label: String) {
    ALL("Todos"), ISSUE("Issue"), PULL_REQUEST("Pull request"), REPOSITORY("Repositório"), OTHER("Outro")
}

@Composable
internal fun GitHubNotificationsScreen(
    modifier: Modifier,
    client: GitHubHttpClient,
    session: GitHubSession?,
    sessionRestoring: Boolean,
    onSessionExpired: (GitHubSession) -> Unit,
    onAppearance: () -> Unit,
    cacheStore: GitHubCacheStore? = null
) {
    val login = session?.user?.login
    val token = session?.accessToken
    var refreshVersion by remember(token) { mutableIntStateOf(0) }
    val pager = remember(client, token, refreshVersion, cacheStore) {
        token?.let {
            GitHubNotificationPager(
                client = client,
                accessToken = it,
                cacheStore = cacheStore,
                accountLogin = login
            )
        }
    }
    var notifications by remember(client, login) { mutableStateOf(emptyList<GitHubNotification>()) }
    var hasNext by remember(pager) { mutableStateOf(false) }
    var loaded by remember(pager) { mutableStateOf(false) }
    var loading by remember(pager) { mutableStateOf(false) }
    var isOfflineCache by remember(client, login) { mutableStateOf(false) }
    var cachedAtMillis by remember(client, login) { mutableStateOf<Long?>(null) }
    var errorMessage by remember(pager) { mutableStateOf<String?>(null) }
    var retryAt by remember(pager) { mutableStateOf<Long?>(null) }
    var readFilter by rememberSaveable(login) { mutableStateOf(NotificationReadFilter.ALL) }
    var typeFilter by rememberSaveable(login) { mutableStateOf(NotificationTypeFilter.ALL) }
    var selectedNotification by remember(token) { mutableStateOf<GitHubNotification?>(null) }
    var showingOrigin by remember(token) { mutableStateOf(false) }
    var sessionExpiryReported by remember(token) { mutableStateOf(false) }
    var markingReadId by remember(token) { mutableStateOf<String?>(null) }
    var markReadError by remember(token) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    suspend fun loadNextPage() {
        val activePager = pager ?: return
        if (loading || (loaded && !hasNext && errorMessage == null)) return
        loading = true
        errorMessage = null
        retryAt = null
        try {
            when (val result = withContext(Dispatchers.IO) { activePager.loadNext() }) {
                is GitHubPageResult.Loaded -> {
                    notifications = result.items
                    hasNext = result.hasNext
                    loaded = true
                    isOfflineCache = result.isOfflineCache
                    cachedAtMillis = result.cachedAtMillis
                }
                is GitHubPageResult.RateLimited -> {
                    if (result.items.isNotEmpty() || notifications.isEmpty()) {
                        notifications = result.items
                    }
                    retryAt = result.retryAtEpochMillis
                    errorMessage = GitHubHttpError.RATE_LIMITED.userMessage
                }
                is GitHubPageResult.Failed -> {
                    if (result.items.isNotEmpty() || notifications.isEmpty()) {
                        notifications = result.items
                    }
                    errorMessage = if (result.error == GitHubHttpError.FORBIDDEN) {
                        "O GitHub não aceitou o acesso OAuth às notificações."
                    } else result.message
                    if (result.error == GitHubHttpError.UNAUTHORIZED && session != null && !sessionExpiryReported) {
                        sessionExpiryReported = true
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
    DisposableEffect(pager) { onDispose { pager?.cancel() } }

    val selected = selectedNotification
    if (selected != null && showingOrigin && session != null) {
        when (val destination = notificationDestination(selected)) {
            is GitHubNotificationDestination.Issue -> GitHubIssueDetailScreen(
                modifier = modifier,
                client = client,
                session = session,
                issue = GitHubIssue(
                    destination.number,
                    selected.title ?: "Título indisponível",
                    GitHubIssueState.OPEN,
                    destination.repository,
                    null,
                    emptyList(),
                    emptyList(),
                    selected.updatedAt
                ),
                onBack = { showingOrigin = false },
                onSessionExpired = onSessionExpired,
                onAppearance = onAppearance
            )
            is GitHubNotificationDestination.PullRequest -> GitHubPullRequestDetailScreen(
                modifier = modifier,
                client = client,
                session = session,
                pullRequest = GitHubPullRequest(
                    destination.number,
                    selected.title ?: "Título indisponível",
                    GitHubPullRequestState.OPEN,
                    destination.repository,
                    null,
                    emptyList(),
                    selected.updatedAt
                ),
                onBack = { showingOrigin = false },
                onSessionExpired = onSessionExpired,
                onAppearance = onAppearance
            )
            is GitHubNotificationDestination.Repository -> {
                val parts = destination.repository.split('/', limit = 2)
                if (parts.size == 2) GitHubRepositoryDetailScreen(
                    modifier = modifier,
                    client = client,
                    session = session,
                    repository = GitHubRepository(
                        selected.repositoryId, parts[1], destination.repository, parts[0],
                        false, null, null, 0
                    ),
                    onBack = { showingOrigin = false },
                    onSessionExpired = onSessionExpired,
                    onAppearance = onAppearance
                ) else NotificationOriginUnavailable(modifier) { showingOrigin = false }
            }
            GitHubNotificationDestination.Unavailable -> NotificationOriginUnavailable(modifier) {
                showingOrigin = false
            }
        }
        return
    }

    if (selected != null) {
        val activeToken = token
        val onMarkSelectedAsRead: (() -> Unit)? = if (activeToken != null && selected.unread) {
            {
                if (markingReadId == null) {
                    markingReadId = selected.id
                    markReadError = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            markNotificationAsRead(client, activeToken, selected.id)
                        }
                        markingReadId = null
                        when (result) {
                            is MarkNotificationReadResult.Success -> {
                                selectedNotification = selected.copy(unread = false)
                                notifications = notifications.map { item ->
                                    if (item.id == selected.id) item.copy(unread = false) else item
                                }
                            }
                            is MarkNotificationReadResult.Failure -> {
                                markReadError = result.error.userMessage
                                if (result.error == GitHubHttpError.UNAUTHORIZED && !sessionExpiryReported) {
                                    sessionExpiryReported = true
                                    onSessionExpired(session)
                                }
                            }
                        }
                    }
                }
            }
        } else null

        BackHandler {
            selectedNotification = null
            markReadError = null
        }
        NotificationDetail(
            modifier = modifier,
            notification = selected,
            onBack = {
                selectedNotification = null
                markReadError = null
            },
            onOpenOrigin = { showingOrigin = true },
            onAppearance = onAppearance,
            onMarkAsRead = onMarkSelectedAsRead,
            markingRead = markingReadId == selected.id,
            markReadError = markReadError
        )
        return
    }

    LazyColumn(
        modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CONTROLE / GITHUB", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
                    letterSpacing = 2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onAppearance) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Configurações de aparência")
                }
                IconButton(onClick = { refreshVersion++ }, enabled = !loading) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar avisos")
                }
            }
            Text("Avisos", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        item {
            FilterRow(readFilter.label, NotificationReadFilter.entries.map { it.label }) { label ->
                readFilter = NotificationReadFilter.entries.first { it.label == label }
            }
            FilterRow(typeFilter.label, NotificationTypeFilter.entries.map { it.label }) { label ->
                typeFilter = NotificationTypeFilter.entries.first { it.label == label }
            }
        }
        item {
            if (isOfflineCache) {
                OfflineCacheBadge(cachedAtMillis)
            }
        }
        if (sessionRestoring) item { Text("Restaurando sessão…") }
        if (loading && notifications.isEmpty()) item { CircularProgressIndicator() }
        errorMessage?.let { message ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    retryAt?.let { deadline ->
                        Text("Tente novamente após ${DateFormat.getTimeFormat(LocalContext.current).format(Date(deadline))}.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { scope.launch { loadNextPage() } }, enabled = !loading,
                        modifier = Modifier.testTag("notifications-retry")) { Text("Tentar novamente") }
                }
            }
        }
        val filtered = notifications.filter { notification ->
            val matchesRead = when (readFilter) {
                NotificationReadFilter.ALL -> true
                NotificationReadFilter.UNREAD -> notification.unread
                NotificationReadFilter.READ -> !notification.unread
            }
            val matchesType = when (typeFilter) {
                NotificationTypeFilter.ALL -> true
                NotificationTypeFilter.ISSUE -> notification.subjectType == "Issue"
                NotificationTypeFilter.PULL_REQUEST -> notification.subjectType == "PullRequest"
                NotificationTypeFilter.REPOSITORY -> notification.subjectType == "Repository" || notification.subjectType == null
                NotificationTypeFilter.OTHER -> notification.subjectType !in setOf(null, "Issue", "PullRequest", "Repository")
            }
            matchesRead && matchesType
        }
        if (loaded && filtered.isEmpty() && errorMessage == null) {
            item {
                Text(
                    if (notifications.isEmpty()) "Nenhuma notificação."
                    else "Nenhuma notificação encontrada com estes filtros.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(filtered, key = GitHubNotification::id) { notification ->
            NotificationCard(notification) { selectedNotification = notification }
        }
        if (hasNext) item {
            TextButton(onClick = { scope.launch { loadNextPage() } }, enabled = !loading,
                modifier = Modifier.fillMaxWidth()) {
                if (loading) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Carregar mais notificações")
            }
        }
    }
}

@Composable
private fun FilterRow(selected: String, options: List<String>, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(option) })
        }
    }
}

@Composable
private fun NotificationCard(notification: GitHubNotification, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(notification.title ?: "Título indisponível", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("${notification.repositoryFullName} · ${notificationTypeLabel(notification.subjectType)}",
                color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Text("${if (notification.unread) "Não lida" else "Lida"} · ${notificationReasonLabel(notification.reason)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            notification.updatedAt?.let { instant -> Text(notificationDateFormatter.format(instant), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun NotificationDetail(
    modifier: Modifier,
    notification: GitHubNotification,
    onBack: () -> Unit,
    onOpenOrigin: () -> Unit,
    onAppearance: () -> Unit,
    onMarkAsRead: (() -> Unit)? = null,
    markingRead: Boolean = false,
    markReadError: String? = null
) {
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize().padding(PaddingValues(horizontal = 24.dp, vertical = 20.dp)),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar aos avisos")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onAppearance) {
                Icon(Icons.Outlined.Settings, contentDescription = "Configurações de aparência")
            }
        }
        Text("Detalhe da notificação", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        Text(notification.title ?: "Título indisponível", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(notification.repositoryFullName, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(notificationTypeLabel(notification.subjectType), fontWeight = FontWeight.Medium)
        Text("${if (notification.unread) "Não lida" else "Lida"} · ${notificationReasonLabel(notification.reason)}")
        notification.updatedAt?.let { Text(notificationDateFormatter.format(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        TextButton(onClick = onOpenOrigin, enabled = notificationDestination(notification) != GitHubNotificationDestination.Unavailable,
            modifier = Modifier.testTag("notification-open-origin")) { Text("Abrir item de origem") }
        if (notificationDestination(notification) == GitHubNotificationDestination.Unavailable) {
            Text("A origem desta notificação não está disponível.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (notification.unread && onMarkAsRead != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onMarkAsRead,
                    enabled = !markingRead,
                    modifier = Modifier.testTag("notification-mark-read")
                ) {
                    if (markingRead) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Marcar como lida")
                }
            }
        }
        markReadError?.let { errorText ->
            Text(errorText, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
    }
}

@Composable
private fun NotificationOriginUnavailable(modifier: Modifier, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("Voltar à notificação") }
        Text("A origem desta notificação não está disponível.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val notificationDateFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
    .withLocale(Locale.forLanguageTag("pt-BR"))
    .withZone(ZoneId.systemDefault())

private fun notificationTypeLabel(type: String?): String = when (type) {
    "Issue" -> "Issue"
    "PullRequest" -> "Pull request"
    "Repository", null -> "Repositório"
    else -> "Outro"
}

private fun notificationReasonLabel(reason: String): String = when (reason) {
    "assign" -> "Atribuída a você"
    "author" -> "Você criou esta conversa"
    "comment" -> "Novo comentário"
    "ci_activity" -> "Atividade de integração contínua"
    "invitation" -> "Convite"
    "manual" -> "Notificação direta"
    "mention" -> "Você foi mencionado"
    "review_requested" -> "Revisão solicitada"
    "security_alert" -> "Alerta de segurança"
    "state_change" -> "Estado alterado"
    "subscribed" -> "Você acompanha esta conversa"
    "team_mention" -> "Sua equipe foi mencionada"
    "your_activity" -> "Sua atividade"
    else -> "Outro motivo"
}
