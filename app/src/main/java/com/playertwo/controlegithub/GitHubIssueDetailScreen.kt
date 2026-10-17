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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
    onAppearance: () -> Unit,
    isOffline: Boolean = session.isOffline
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

    var newCommentText by rememberSaveable(issue.identity) { mutableStateOf("") }
    var commentSubmitting by remember { mutableStateOf(false) }
    var commentError by remember { mutableStateOf<String?>(null) }
    var commentFieldError by remember { mutableStateOf<String?>(null) }

    var editingIssue by rememberSaveable(issue.identity) { mutableStateOf(false) }
    var editTitle by rememberSaveable(issue.identity) { mutableStateOf("") }
    var editBody by rememberSaveable(issue.identity) { mutableStateOf("") }
    var editLabels by rememberSaveable(issue.identity) { mutableStateOf("") }
    var editAssignees by rememberSaveable(issue.identity) { mutableStateOf("") }
    var editSubmitting by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }
    var editTitleError by remember { mutableStateOf<String?>(null) }

    var stateChanging by remember { mutableStateOf(false) }
    var stateChangeError by remember { mutableStateOf<String?>(null) }

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

    BackHandler(onBack = {
        if (stateChanging) return@BackHandler
        if (editingIssue) {
            editingIssue = false
            editError = null
            editTitleError = null
        } else {
            onBack()
        }
    })
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
            IconButton(onClick = onBack, enabled = !commentSubmitting && !editSubmitting && !stateChanging) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar às issues")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onAppearance, enabled = !commentSubmitting && !editSubmitting && !stateChanging) {
                Icon(Icons.Outlined.Settings, contentDescription = "Configurações de aparência")
            }
            IconButton(onClick = { refreshVersion++; detail = null }, enabled = !loadingDetail && !commentSubmitting && !editSubmitting && !stateChanging) {
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
                if (editingIssue) {
                    Text("Editar issue", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = {
                            editTitle = it
                            if (editTitleError != null && it.isNotBlank()) editTitleError = null
                        },
                        label = { Text("Título *") },
                        isError = editTitleError != null,
                        supportingText = editTitleError?.let { error -> { Text(error, color = MaterialTheme.colorScheme.error) } },
                        enabled = !editSubmitting,
                        modifier = Modifier.fillMaxWidth().testTag("issue_edit_title_input")
                    )
                    OutlinedTextField(
                        value = editBody,
                        onValueChange = { editBody = it },
                        label = { Text("Descrição (opcional)") },
                        minLines = 4,
                        maxLines = 10,
                        enabled = !editSubmitting,
                        modifier = Modifier.fillMaxWidth().testTag("issue_edit_body_input")
                    )
                    OutlinedTextField(
                        value = editLabels,
                        onValueChange = { editLabels = it },
                        label = { Text("Rótulos (separados por vírgula)") },
                        enabled = !editSubmitting,
                        modifier = Modifier.fillMaxWidth().testTag("issue_edit_labels_input")
                    )
                    OutlinedTextField(
                        value = editAssignees,
                        onValueChange = { editAssignees = it },
                        label = { Text("Responsáveis (logins separados por vírgula)") },
                        enabled = !editSubmitting,
                        modifier = Modifier.fillMaxWidth().testTag("issue_edit_assignees_input")
                    )
                    if (editError != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = editError.orEmpty(),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { editingIssue = false; editError = null; editTitleError = null },
                            enabled = !editSubmitting,
                            modifier = Modifier.testTag("issue_edit_cancel_button")
                        ) {
                            Text("Cancelar")
                        }
                        Button(
                            onClick = {
                                val trimmed = editTitle.trim()
                                if (trimmed.isEmpty()) {
                                    editTitleError = "O título da issue não pode ficar em branco."
                                    return@Button
                                }
                                editTitleError = null
                                editError = null
                                editSubmitting = true
                                val parsedLabels = editLabels.split(',').map(String::trim).filter(String::isNotEmpty)
                                val parsedAssignees = editAssignees.split(',').map(String::trim).filter(String::isNotEmpty)
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        editGitHubIssue(
                                            client = client,
                                            accessToken = session.accessToken,
                                            currentIssue = current.detail.issue,
                                            title = trimmed,
                                            body = editBody.trim().takeIf(String::isNotBlank),
                                            labels = parsedLabels,
                                            assignees = parsedAssignees,
                                            isOffline = isOffline
                                        )
                                    }
                                    when (result) {
                                        is EditIssueResult.Success -> {
                                            detail = GitHubIssueDetailResult.Loaded(result.detail)
                                            editingIssue = false
                                            editSubmitting = false
                                        }
                                        EditIssueResult.EmptyTitle -> {
                                            editTitleError = "O título da issue não pode ficar em branco."
                                            editSubmitting = false
                                        }
                                        EditIssueResult.Offline -> {
                                            editError = "Não é possível editar issues no modo offline."
                                            editSubmitting = false
                                        }
                                        is EditIssueResult.Failure -> {
                                            editSubmitting = false
                                            if (result.statusCode == 403) {
                                                editError = "Você não tem permissão para editar esta issue."
                                            } else {
                                                editError = result.error.userMessage
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !editSubmitting,
                            modifier = Modifier.testTag("issue_edit_save_button")
                        ) {
                            if (editSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp).testTag("issue_edit_progress"),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.size(8.dp))
                                Text("Salvando…")
                            } else {
                                Text("Salvar alterações")
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(current.detail.issue.title, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                        }
                        if (!isOffline) {
                            TextButton(
                                onClick = {
                                    editTitle = current.detail.issue.title
                                    editBody = current.detail.body.orEmpty()
                                    editLabels = current.detail.issue.labels.joinToString(", ")
                                    editAssignees = current.detail.issue.assignees.joinToString(", ")
                                    editError = null
                                    editTitleError = null
                                    editingIssue = true
                                },
                                modifier = Modifier.testTag("issue_edit_button")
                            ) {
                                Text("Editar")
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${current.detail.issue.repository} · #${current.detail.issue.number} · ${if (current.detail.issue.state == GitHubIssueState.OPEN) "Aberta" else "Fechada"}",
                            color = if (current.detail.issue.state == GitHubIssueState.OPEN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!isOffline) {
                            val isOpen = current.detail.issue.state == GitHubIssueState.OPEN
                            val actionText = if (isOpen) "Fechar issue" else "Reabrir issue"
                            val targetState = if (isOpen) GitHubIssueState.CLOSED else GitHubIssueState.OPEN
                            Button(
                                onClick = {
                                    stateChanging = true
                                    stateChangeError = null
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            updateIssueState(
                                                client = client,
                                                accessToken = session.accessToken,
                                                currentIssue = current.detail.issue,
                                                newState = targetState,
                                                isOffline = isOffline
                                            )
                                        }
                                        stateChanging = false
                                        when (result) {
                                            is MutateIssueStateResult.Success -> {
                                                detail = GitHubIssueDetailResult.Loaded(result.detail)
                                            }
                                            is MutateIssueStateResult.Failure -> {
                                                stateChangeError = if (result.statusCode == 403) {
                                                    "Você não tem permissão para alterar o estado desta issue."
                                                } else {
                                                    result.error.userMessage
                                                }
                                            }
                                            MutateIssueStateResult.Offline -> {
                                                stateChangeError = "Não é possível alterar o estado da issue no modo offline."
                                            }
                                        }
                                    }
                                },
                                enabled = !stateChanging && !commentSubmitting && !editSubmitting,
                                modifier = Modifier.testTag(if (isOpen) "issue_close_button" else "issue_reopen_button")
                            ) {
                                if (stateChanging) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp).testTag("issue_state_progress"),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text("Atualizando…")
                                } else {
                                    Text(actionText)
                                }
                            }
                        }
                    }
                    if (stateChangeError != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stateChangeError.orEmpty(),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                    Text("Por ${current.detail.issue.author?.let { "@$it" } ?: "autor indisponível"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val assigneesText = if (current.detail.issue.assignees.isNotEmpty()) {
                        "Responsáveis: " + current.detail.issue.assignees.joinToString(", ") { "@$it" }
                    } else {
                        "Sem responsáveis"
                    }
                    Text(assigneesText, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("issue_assignees_text"))
                    val labelsText = if (current.detail.issue.labels.isNotEmpty()) {
                        "Rótulos: " + current.detail.issue.labels.joinToString(" · ")
                    } else {
                        "Sem rótulos"
                    }
                    Text(labelsText, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.testTag("issue_labels_text"))
                    Text("Descrição", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    InertMarkdown(current.detail.body?.takeIf(String::isNotBlank) ?: "Sem descrição.")
                }

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

                if (isOffline) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Modo offline: Conexão necessária para comentar ou editar issues.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    Text("Novo comentário", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = newCommentText,
                        onValueChange = {
                            newCommentText = it
                            if (commentFieldError != null && it.isNotBlank()) commentFieldError = null
                        },
                        label = { Text("Escreva um comentário…") },
                        placeholder = { Text("Deixe sua resposta ou atualização") },
                        isError = commentFieldError != null,
                        supportingText = commentFieldError?.let { error -> { Text(error, color = MaterialTheme.colorScheme.error) } },
                        enabled = !commentSubmitting,
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth().testTag("issue_comment_input")
                    )
                    if (commentError != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = commentError.orEmpty(),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                val trimmed = newCommentText.trim()
                                if (trimmed.isEmpty()) {
                                    commentFieldError = "O comentário não pode ficar em branco."
                                    return@Button
                                }
                                commentFieldError = null
                                commentError = null
                                commentSubmitting = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        createIssueComment(
                                            client = client,
                                            accessToken = session.accessToken,
                                            repository = current.detail.issue.repository,
                                            issueNumber = current.detail.issue.number,
                                            body = trimmed,
                                            isOffline = isOffline
                                        )
                                    }
                                    when (result) {
                                        is CreateIssueCommentResult.Success -> {
                                            comments = comments + result.comment
                                            newCommentText = ""
                                            commentSubmitting = false
                                        }
                                        CreateIssueCommentResult.EmptyBody -> {
                                            commentFieldError = "O comentário não pode ficar em branco."
                                            commentSubmitting = false
                                        }
                                        CreateIssueCommentResult.Offline -> {
                                            commentError = "Não é possível comentar no modo offline."
                                            commentSubmitting = false
                                        }
                                        is CreateIssueCommentResult.Failure -> {
                                            commentSubmitting = false
                                            if (result.statusCode == 403) {
                                                commentError = "Você não tem permissão para comentar nesta issue."
                                            } else {
                                                commentError = result.error.userMessage
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !commentSubmitting,
                            modifier = Modifier.testTag("issue_comment_submit_button")
                        ) {
                            if (commentSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp).testTag("issue_comment_progress"),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.size(8.dp))
                                Text("Enviando…")
                            } else {
                                Text("Comentar")
                            }
                        }
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
