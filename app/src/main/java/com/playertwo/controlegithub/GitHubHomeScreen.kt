package com.playertwo.controlegithub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun GitHubHomeScreen(
    modifier: Modifier,
    client: GitHubHttpClient,
    session: GitHubSession,
    sessionRestoring: Boolean,
    onOpenRepositories: () -> Unit,
    onSessionExpired: (GitHubSession) -> Unit,
    onLogout: () -> Unit,
    onAppearance: () -> Unit,
    cacheStore: GitHubCacheStore? = null,
    providedSummary: DailySummary? = null
) {
    val uriHandler = LocalUriHandler.current
    var refreshVersion by remember(session.accessToken) { mutableIntStateOf(0) }
    var profile by remember(session.accessToken) { mutableStateOf<GitHubAccountProfile?>(null) }
    var loading by remember(session.accessToken) { mutableStateOf(false) }
    var error by remember(session.accessToken) { mutableStateOf<String?>(null) }
    var unauthorized by remember(session.accessToken) { mutableStateOf(false) }
    var summary by remember(session.accessToken, providedSummary) { mutableStateOf(providedSummary) }

    LaunchedEffect(session.accessToken, refreshVersion, sessionRestoring) {
        if (sessionRestoring || unauthorized) return@LaunchedEffect
        loading = true
        error = null
        profile = null
        try {
            when (val response = withContext(Dispatchers.IO) {
                client.get("/user", session.accessToken).await()
            }) {
                is GitHubHttpResult.Success -> {
                    profile = GitHubAccountProfile.parse(response.body, session.user.login)
                }
                is GitHubHttpResult.Failure -> {
                    if (response.error == GitHubHttpError.UNAUTHORIZED) {
                        unauthorized = true
                        onSessionExpired(session)
                    } else {
                        error = response.error.userMessage
                    }
                }
            }
        } catch (_: Exception) {
            error = "A resposta do perfil do GitHub é inválida. Tente novamente."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(session.accessToken, refreshVersion, sessionRestoring, providedSummary) {
        if (providedSummary != null) {
            summary = providedSummary
            return@LaunchedEffect
        }
        if (sessionRestoring || unauthorized) return@LaunchedEffect
        val now = Instant.now()
        val windowStart = now.minusSeconds(86400)
        val windowEnd = now
        val zone = ZoneId.systemDefault()
        var notifs = emptyList<GitHubNotification>()
        var isOffline = session.isOffline
        var cachedAt: Long? = null
        var partialFailure = false

        try {
            when (val res = withContext(Dispatchers.IO) { client.get("/notifications", session.accessToken).await() }) {
                is GitHubHttpResult.Success -> {
                    notifs = parseGitHubNotifications(res.body)
                    cacheStore?.put(session.user.login, "/notifications", res.body)
                }
                is GitHubHttpResult.Failure -> {
                    val cached = cacheStore?.get(session.user.login, "/notifications")
                        ?: cacheStore?.get(session.user.login, "/notifications?all=true&per_page=50")
                    if (cached != null) {
                        notifs = parseGitHubNotifications(cached.payload)
                        isOffline = true
                        cachedAt = cached.createdAtMillis
                    } else {
                        partialFailure = true
                    }
                }
            }
        } catch (_: Exception) {
            val cached = cacheStore?.get(session.user.login, "/notifications")
                ?: cacheStore?.get(session.user.login, "/notifications?all=true&per_page=50")
            if (cached != null) {
                notifs = parseGitHubNotifications(cached.payload)
                isOffline = true
                cachedAt = cached.createdAtMillis
            } else {
                partialFailure = true
            }
        }

        summary = buildDailySummary(
            notifications = notifs,
            windowStart = windowStart,
            windowEnd = windowEnd,
            zoneId = zone,
            isOfflineCache = isOffline,
            cachedAtMillis = cachedAt,
            hasPartialFailure = partialFailure
        )
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
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
            IconButton(onClick = onAppearance) { Icon(Icons.Outlined.Settings, contentDescription = "Configurações de aparência") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Seu centro de comando", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Perfil da conta no GitHub", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            IconButton(onClick = { refreshVersion++ }, enabled = !loading && !sessionRestoring) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar perfil")
            }
        }
        Text("Sessão: @${session.user.login}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

        if (sessionRestoring || loading && profile == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Carregando perfil do GitHub…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (profile != null) {
            val loadedProfile = profile!!
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = "Perfil da conta, carregado de GET /user"
                }
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PERFIL DO GITHUB", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                    Text(loadedProfile.displayName ?: "@${loadedProfile.login}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("@${loadedProfile.login}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { uriHandler.openUri(loadedProfile.profileUrl) }, contentPadding = PaddingValues(0.dp)) {
                        Text("Abrir perfil no GitHub")
                    }
                    Text("Origem: Perfil do GitHub", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onLogout, contentPadding = PaddingValues(0.dp)) { Text("Sair da conta") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountMetric(loadedProfile.publicRepositories, "Repositórios públicos", Modifier.weight(1f))
                AccountMetric(loadedProfile.ownedPrivateRepositories, "Repositórios privados próprios", Modifier.weight(1f))
            }
            AccountMetric(loadedProfile.followers, "Seguidores", Modifier.fillMaxWidth())
            Text(
                "As contagens vêm de GET /user. Repositórios compartilhados por colaboração ou organização podem não estar incluídos.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }

        summary?.let { currentSummary ->
            DailySummaryCard(
                summary = currentSummary,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (loading && profile != null) Text("Atualizando perfil…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { refreshVersion++ }, enabled = !loading) { Text("Tentar novamente") }
        }

        Surface(
            onClick = onOpenRepositories,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("Seus repositórios", fontWeight = FontWeight.SemiBold)
                    Text("Abrir a lista real da conta", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
internal fun DailySummaryCard(
    summary: DailySummary,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val dateFormatter = remember(summary.zoneId) {
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(summary.zoneId)
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier.testTag("daily_summary_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "RESUMO DIÁRIO",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when (summary.dataSource) {
                        DailySummaryDataSource.ONLINE -> MaterialTheme.colorScheme.primaryContainer
                        DailySummaryDataSource.CACHED -> MaterialTheme.colorScheme.secondaryContainer
                        DailySummaryDataSource.PARTIAL -> MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Text(
                        summary.dataSource.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = when (summary.dataSource) {
                            DailySummaryDataSource.ONLINE -> MaterialTheme.colorScheme.onPrimaryContainer
                            DailySummaryDataSource.CACHED -> MaterialTheme.colorScheme.onSecondaryContainer
                            DailySummaryDataSource.PARTIAL -> MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("daily_summary_source")
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Período: ${dateFormatter.format(summary.windowStart)} até ${dateFormatter.format(summary.windowEnd)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.testTag("daily_summary_period")
                )
                Text(
                    "Fuso horário: ${summary.zoneId.id}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("daily_summary_timezone")
                )
                if (summary.dataSource == DailySummaryDataSource.CACHED && summary.cachedAtMillis != null) {
                    Text(
                        "Cache atualizado em: ${dateFormatter.format(Instant.ofEpochMilli(summary.cachedAtMillis))}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("daily_summary_cached_timestamp")
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DailyMetricBox(
                    title = "Notificações",
                    count = "${summary.metrics.totalNotifications}",
                    detail = "${summary.metrics.unreadNotifications} não lidas",
                    modifier = Modifier.weight(1f)
                )
                DailyMetricBox(
                    title = "Revisões PR",
                    count = "${summary.metrics.reviewRequests}",
                    detail = "solicitações",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DailyMetricBox(
                    title = "CI / Workflows",
                    count = "${summary.metrics.successfulWorkflows} / ${summary.metrics.failedWorkflows}",
                    detail = "sucesso / falha",
                    modifier = Modifier.weight(1f)
                )
                DailyMetricBox(
                    title = "Issues ativas",
                    count = "${summary.metrics.activeIssues}",
                    detail = "no período",
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedButton(
                onClick = {
                    val markdown = exportDailySummaryToMarkdown(summary)
                    clipboardManager.setText(AnnotatedString(markdown))
                    copied = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("daily_summary_export_button")
            ) {
                Text("Exportar Markdown")
            }

            if (copied) {
                Text(
                    "Resumo copiado em formato Markdown!",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("daily_summary_export_feedback")
                )
            }
        }
    }
}

@Composable
private fun DailyMetricBox(
    title: String,
    count: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(count, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(detail, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccountMetric(value: Long?, label: String, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.semantics { contentDescription = "$label: ${value ?: "Indisponível"}" }
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value?.toString() ?: "Indisponível", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
internal fun NotIntegratedScreen(title: String, feature: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Ainda não integrado", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text("$feature aparecerá aqui quando essa integração estiver disponível.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
