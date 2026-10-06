package com.playertwo.controlegithub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
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
internal fun GitHubNotificationsScreen(
    modifier: Modifier,
    client: GitHubHttpClient,
    session: GitHubSession?,
    sessionRestoring: Boolean,
    onSessionExpired: (GitHubSession) -> Unit,
    onAppearance: () -> Unit
) {
    key(session?.user?.login, session?.accessToken) {
        GitHubNotificationsContent(modifier, client, session, sessionRestoring, onSessionExpired, onAppearance)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun GitHubNotificationsContent(
    modifier: Modifier,
    client: GitHubHttpClient,
    session: GitHubSession?,
    sessionRestoring: Boolean,
    onSessionExpired: (GitHubSession) -> Unit,
    onAppearance: () -> Unit
) {
    var refreshVersion by remember { mutableIntStateOf(0) }
    var stateFilter by remember { mutableStateOf(NotificationStateFilter.ALL) }
    var typeFilter by remember { mutableStateOf(NotificationTypeFilter.ALL) }
    val pager = remember(client, session?.accessToken, refreshVersion) {
        session?.let { GitHubNotificationPager(client, it.accessToken) }
    }
    var notifications by remember(pager) { mutableStateOf(emptyList<GitHubNotification>()) }
    var hasNext by remember(pager) { mutableStateOf(false) }
    var loaded by remember(pager) { mutableStateOf(false) }
    var loading by remember(pager) { mutableStateOf(false) }
    var errorMessage by remember(pager) { mutableStateOf<String?>(null) }
    var retryAt by remember(pager) { mutableStateOf<Long?>(null) }
    var selectedNotification by remember { mutableStateOf<GitHubNotification?>(null) }
    var openedDestination by remember { mutableStateOf<GitHubNotificationDestination?>(null) }
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
                }
                is GitHubPageResult.RateLimited -> {
                    notifications = result.items
                    retryAt = result.retryAtEpochMillis
                    errorMessage = GitHubHttpError.RATE_LIMITED.userMessage
                }
                is GitHubPageResult.Failed -> {
                    notifications = result.items
                    errorMessage = if (result.error == GitHubHttpError.FORBIDDEN) {
                        "O GitHub não aceitou o acesso OAuth às notificações."
                    } else result.message
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

    val filtered = notifications.filter { notification ->
        (stateFilter == NotificationStateFilter.ALL || notification.unread == (stateFilter == NotificationStateFilter.UNREAD)) &&
            (typeFilter == NotificationTypeFilter.ALL || notificationTypeLabel(notification) == typeFilter.label)
    }
    val covered = selectedNotification != null
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().then(if (covered) Modifier.clearAndSetSemantics {} else Modifier),
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "notifications-header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CONTROLE / GITHUB", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onAppearance) { Icon(Icons.Outlined.Settings, contentDescription = "Configurações de aparência") }
                }
                Spacer(Modifier.height(22.dp))
                Text("Notificações", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            when {
                sessionRestoring -> item(key = "notifications-restoring") { Text("Restaurando sessão segura…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                session == null -> item(key = "notifications-sign-in") {
                    Text("Conecte sua conta GitHub para consultar suas notificações.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    item(key = "notifications-account") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Caixa de entrada", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                                Text("@${session.user.login}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            IconButton(onClick = { refreshVersion++ }, enabled = !loading) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar notificações")
                            }
                        }
                    }
                    item(key = "notifications-filters") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NotificationStateFilter.entries.forEach { choice ->
                                    FilterChip(selected = stateFilter == choice, onClick = { stateFilter = choice }, label = { Text(choice.label) })
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NotificationTypeFilter.entries.forEach { choice ->
                                    FilterChip(selected = typeFilter == choice, onClick = { typeFilter = choice }, label = { Text(choice.label) })
                                }
                            }
                        }
                    }
                    item(key = "notifications-status") {
                        when {
                            loading && notifications.isEmpty() -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Carregando notificações…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            errorMessage != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                                retryAt?.let { Text("Disponível após ${java.time.Instant.ofEpochMilli(it)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                TextButton(onClick = { scope.launch { loadNextPage() } }, enabled = !loading) { Text("Tentar novamente") }
                            }
                            loaded && notifications.isEmpty() -> Text("Nenhuma notificação.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            loaded && errorMessage == null && filtered.isEmpty() -> Text("Nenhuma notificação corresponde a estes filtros.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(filtered, key = GitHubNotification::id) { notification ->
                        NotificationCard(notification) { selectedNotification = notification }
                    }
                    item(key = "notifications-more") {
                        if (hasNext && errorMessage == null) {
                            TextButton(onClick = { scope.launch { loadNextPage() } }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Carregar mais notificações")
                            }
                        }
                    }
                }
            }
        }

        val notification = selectedNotification
        if (notification != null && session != null) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (val destination = openedDestination) {
                    is GitHubNotificationDestination.Issue -> GitHubIssueDetailScreen(
                        modifier = Modifier.fillMaxSize(), client = client, session = session,
                        issue = GitHubIssue(destination.number, "", GitHubIssueState.OPEN, destination.repository, null, emptyList(), emptyList(), notification.updatedAt),
                        onBack = { openedDestination = null }, onSessionExpired = onSessionExpired, onAppearance = onAppearance
                    )
                    is GitHubNotificationDestination.PullRequest -> GitHubPullRequestDetailScreen(
                        modifier = Modifier.fillMaxSize(), client = client, session = session,
                        pullRequest = GitHubPullRequest(destination.number, "", GitHubPullRequestState.OPEN, destination.repository, null, emptyList(), notification.updatedAt),
                        onBack = { openedDestination = null }, onSessionExpired = onSessionExpired, onAppearance = onAppearance
                    )
                    is GitHubNotificationDestination.Repository -> GitHubRepositoryDetailScreen(
                        modifier = Modifier.fillMaxSize(), client = client, session = session,
                        repository = notification.repository, onBack = { openedDestination = null },
                        onSessionExpired = onSessionExpired, onAppearance = onAppearance
                    )
                    else -> NotificationDetail(notification, onBack = { selectedNotification = null }) {
                        when (val safeDestination = notificationDestination(notification)) {
                            GitHubNotificationDestination.Unavailable -> Unit
                            else -> openedDestination = safeDestination
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(notification: GitHubNotification, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(notification.title?.takeIf(String::isNotBlank) ?: "Título indisponível", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(notification.repository.fullName, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Text("${notificationTypeLabel(notification)} · ${notificationReasonLabel(notification.reason)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Text(if (notification.unread) "Não lida" else "Lida", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun NotificationDetail(notification: GitHubNotification, onBack: () -> Unit, onOpenOrigin: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar às notificações") }
            Text("Detalhe da notificação", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text(notification.title?.takeIf(String::isNotBlank) ?: "Título indisponível", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        Text(notification.repository.fullName, color = MaterialTheme.colorScheme.primary)
        Text(notificationTypeLabel(notification), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(notificationReasonLabel(notification.reason), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (notification.unread) "Não lida" else "Lida", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale("pt", "BR")).withZone(ZoneId.systemDefault()).format(notification.updatedAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (notificationDestination(notification)) {
            GitHubNotificationDestination.Unavailable -> Text("A origem desta notificação não está disponível.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> TextButton(onClick = onOpenOrigin) { Text("Abrir origem") }
        }
    }
}

private enum class NotificationStateFilter(val label: String) {
    ALL("Todas"), UNREAD("Não lidas"), READ("Lidas")
}

private enum class NotificationTypeFilter(val label: String) {
    ALL("Todos"), ISSUE("Issue"), PULL_REQUEST("Pull request"), REPOSITORY("Repositório"), OTHER("Outro")
}
