package com.playertwo.controlegithub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GitHubActionsScreen(
    modifier: Modifier,
    client: GitHubHttpClient,
    accessToken: String,
    repository: GitHubRepository,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit
) {
    var refreshVersion by remember(repository.id, accessToken) { mutableIntStateOf(0) }
    var selectedWorkflow by remember(repository.id, accessToken) { mutableStateOf<GitHubWorkflow?>(null) }
    var selectedRun by remember(repository.id, accessToken) { mutableStateOf<GitHubWorkflowRun?>(null) }
    var selectedJob by remember(repository.id, accessToken) { mutableStateOf<GitHubActionJob?>(null) }
    var allRuns by remember(repository.id, accessToken) { mutableStateOf(false) }
    var sessionExpiryReported by remember(repository.id, accessToken) { mutableStateOf(false) }
    val actions = remember(client, accessToken, repository) {
        GitHubActionsRepository(client, accessToken, repository)
    }
    val title = when {
        selectedJob != null -> "Logs do job"
        selectedRun != null -> "Execução #${selectedRun!!.runNumber}"
        selectedWorkflow != null -> selectedWorkflow!!.name
        allRuns -> "Todas as execuções"
        else -> "GitHub Actions"
    }
    fun back() {
        when {
            selectedJob != null -> selectedJob = null
            selectedRun != null -> selectedRun = null
            selectedWorkflow != null || allRuns -> {
                selectedWorkflow = null
                allRuns = false
            }
            else -> onBack()
        }
    }

    BackHandler(onBack = ::back)
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = ::back) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar")
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(repository.fullName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            IconButton(onClick = { refreshVersion++ }) {
                Icon(Icons.Outlined.Refresh, contentDescription = if (selectedJob != null) "Atualizar logs" else "Atualizar Actions")
            }
        }

        when {
            selectedJob != null -> {
                val run = selectedRun
                val job = selectedJob!!
                JobLogsContent(
                    client = client,
                    accessToken = accessToken,
                    repository = repository,
                    job = job,
                    refreshVersion = refreshVersion,
                    workflowName = run?.workflowName ?: selectedWorkflow?.name ?: "Workflow",
                    runNumber = run?.runNumber,
                    onSessionExpired = {
                        if (!sessionExpiryReported) {
                            sessionExpiryReported = true
                            onSessionExpired()
                        }
                    }
                )
            }
            selectedRun != null -> {
                val run = selectedRun!!
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(run.workflowName ?: selectedWorkflow?.name ?: "Workflow indisponível", fontWeight = FontWeight.SemiBold)
                    Text("Branch: ${run.branch ?: "Indisponível"} · Evento: ${run.event}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Status: ${workflowRunStatusLabel(run.status)}")
                    Text("Conclusão: ${workflowConclusionLabel(run.conclusion, run.status)}")
                    run.createdAt?.let { Text("Iniciada: ${formatActionsDate(it)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("Jobs", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                val pager = remember(actions, refreshVersion, run.id) { actions.jobs(run.id) }
                ActionsPagedList(
                    modifier = Modifier.weight(1f),
                    pager = pager,
                    emptyMessage = "Esta execução ainda não tem jobs disponíveis.",
                    onSessionExpired = onSessionExpired
                ) { job ->
                    ActionsCard {
                        Text(job.name, fontWeight = FontWeight.SemiBold)
                        Text("Status: ${actionJobStatusLabel(job.status)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Conclusão: ${workflowConclusionLabel(job.conclusion, job.status)}")
                        TextButton(onClick = { selectedJob = job }) { Text("Ver logs") }
                    }
                }
            }
            selectedWorkflow != null || allRuns -> {
                selectedWorkflow?.let { workflow -> Text(
                    "Execuções de ${workflow.name}",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ) }
                val pager = remember(actions, refreshVersion, selectedWorkflow?.id, allRuns) {
                    actions.runs(selectedWorkflow?.id)
                }
                ActionsPagedList(
                    modifier = Modifier.weight(1f),
                    pager = pager,
                    emptyMessage = "Este workflow ainda não tem execuções.",
                    onSessionExpired = onSessionExpired
                ) { run ->
                    RunCard(run, selectedWorkflow?.name ?: run.workflowName ?: "Workflow", { selectedRun = run })
                }
            }
            else -> {
                val workflowPager = remember(actions, refreshVersion) { actions.workflows() }
                Column(Modifier.weight(1f)) {
                    Button(
                        onClick = { allRuns = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                    ) { Text("Todas as execuções") }
                    ActionsPagedList(
                        modifier = Modifier.weight(1f),
                        pager = workflowPager,
                        emptyMessage = "Este repositório não tem workflows do GitHub Actions.",
                        onSessionExpired = onSessionExpired
                    ) { workflow ->
                        ActionsCard(onClick = { selectedWorkflow = workflow }) {
                            Text(workflow.name, fontWeight = FontWeight.SemiBold)
                            Text(workflow.path, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            Text(workflowStateLabel(workflow.state), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunCard(run: GitHubWorkflowRun, fallbackWorkflowName: String, onClick: () -> Unit) {
    ActionsCard(onClick) {
        Text(run.workflowName ?: fallbackWorkflowName, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("#${run.runNumber}")
            Text(workflowConclusionLabel(run.conclusion, run.status), color = MaterialTheme.colorScheme.primary)
        }
        Text("${workflowRunStatusLabel(run.status)} · ${run.branch ?: "Branch indisponível"} · ${run.event}",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        run.createdAt?.let { Text(formatActionsDate(it), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
    }
}

@Composable
private fun <T> ActionsPagedList(
    modifier: Modifier,
    pager: GitHubPaginator<T>,
    emptyMessage: String,
    onSessionExpired: () -> Unit,
    item: @Composable (T) -> Unit
) {
    val scope = rememberCoroutineScope()
    var values by remember(pager) { mutableStateOf(emptyList<T>()) }
    var hasNext by remember(pager) { mutableStateOf(false) }
    var loaded by remember(pager) { mutableStateOf(false) }
    var loading by remember(pager) { mutableStateOf(false) }
    var error by remember(pager) { mutableStateOf<String?>(null) }
    var retryAt by remember(pager) { mutableStateOf<Long?>(null) }
    var sessionExpiryReported by remember(pager) { mutableStateOf(false) }
    val listState = remember(pager) { LazyListState() }

    suspend fun loadNext() {
        if (loading) return
        loading = true
        error = null
        retryAt = null
        try {
            when (val result = withContext(Dispatchers.IO) { pager.loadNext() }) {
                is GitHubPageResult.Loaded -> {
                    values = result.items
                    hasNext = result.hasNext
                    loaded = true
                }
                is GitHubPageResult.RateLimited -> {
                    values = result.items
                    loaded = true
                    retryAt = result.retryAtEpochMillis
                    error = GitHubHttpError.RATE_LIMITED.userMessage
                }
                is GitHubPageResult.Failed -> {
                    values = result.items
                    loaded = true
                    error = result.message
                    if (result.error == GitHubHttpError.UNAUTHORIZED && !sessionExpiryReported) {
                        sessionExpiryReported = true
                        onSessionExpired()
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loaded = true
            error = GitHubHttpError.UNEXPECTED.userMessage
        } finally {
            loading = false
        }
    }

    LaunchedEffect(pager) {
        listState.scrollToItem(0)
        loadNext()
    }
    DisposableEffect(pager) { onDispose { pager.cancel() } }

    LazyColumn(
        modifier,
        state = listState,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (loading && !loaded) item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Carregando dados do GitHub…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (loaded && values.isEmpty() && error == null) item {
            Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(values) { value -> item(value) }
        error?.let { message -> item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(message, color = MaterialTheme.colorScheme.error)
                retryAt?.let { deadline ->
                    val safeTime = android.text.format.DateFormat.getTimeFormat(androidx.compose.ui.platform.LocalContext.current)
                        .format(Date(deadline))
                    Text("Tente novamente após $safeTime.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { scope.launch { loadNext() } }, enabled = !loading) {
                    Text("Tentar novamente")
                }
            }
        } }
        if (hasNext && error == null) item {
            TextButton(onClick = { scope.launch { loadNext() } }, enabled = !loading) {
                Text(if (loading) "Carregando…" else "Carregar mais")
            }
        }
    }
}

@Composable
private fun ActionsCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun JobLogsContent(
    client: GitHubHttpClient,
    accessToken: String,
    repository: GitHubRepository,
    job: GitHubActionJob,
    refreshVersion: Int,
    workflowName: String,
    runNumber: Long?,
    onSessionExpired: () -> Unit
) {
    val loader = remember(client, accessToken, repository, job.id, refreshVersion) {
        GitHubActionJobLogsLoader(client, accessToken, repository)
    }
    var result by remember(loader) { mutableStateOf<GitHubActionJobLogsResult?>(null) }

    LaunchedEffect(loader) {
        val loaded = withContext(Dispatchers.IO) { loader.load(job.id) }
        result = loaded
        if (loaded is GitHubActionJobLogsResult.Failed && loaded.error == GitHubHttpError.UNAUTHORIZED) {
            onSessionExpired()
        }
    }
    DisposableEffect(loader) { onDispose { loader.cancel() } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(workflowName, fontWeight = FontWeight.SemiBold)
            if (runNumber != null) Text("Execução #$runNumber", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(job.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Text(
                "Os logs podem conter dados sensíveis. Evite compartilhá-los.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        when (val current = result) {
            null -> item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Carregando logs do GitHub…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            GitHubActionJobLogsResult.Empty -> item {
                Text("Este job ainda não tem logs.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is GitHubActionJobLogsResult.Failed -> item {
                Text(current.message, color = MaterialTheme.colorScheme.error)
                Text("Use Atualizar para tentar novamente.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is GitHubActionJobLogsResult.Loaded -> {
                if (current.truncated) item {
                    Text("Prévia limitada a 1 MiB; o restante dos logs não é exibido.", color = MaterialTheme.colorScheme.error)
                }
                item {
                    val chunks = remember(current.text) { splitJobLogPreview(current.text) }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            chunks.forEach { chunk ->
                                Text(text = chunk, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatActionsDate(instant: java.time.Instant): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM)
    .withLocale(Locale.forLanguageTag("pt-BR"))
    .withZone(ZoneId.systemDefault())
    .format(instant)
