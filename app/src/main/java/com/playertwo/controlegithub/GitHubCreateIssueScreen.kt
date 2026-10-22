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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GitHubCreateIssueScreen(
    modifier: Modifier = Modifier,
    client: GitHubHttpClient,
    accessToken: String,
    repositoryFullName: String,
    accountLogin: String? = null,
    isOffline: Boolean = false,
    onIssueCreated: (GitHubIssue) -> Unit,
    onCancel: () -> Unit,
    onSessionExpired: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var titleError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val drafts = LocalMarkdownDraftStore.current
    val draftKey = accountLogin?.let { markdownDraftKey(it, repositoryFullName, "issue-new") }
    val mentions = rememberMentionLogins(
        client = client,
        accessToken = accessToken,
        accountLogin = accountLogin.orEmpty(),
        repository = repositoryFullName,
        participants = emptyList(),
        enabled = !isOffline && !accountLogin.isNullOrBlank(),
        onUnauthorized = onSessionExpired
    )

    BackHandler(enabled = !submitting, onBack = onCancel)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 24.dp, vertical = 20.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel, enabled = !submitting) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Cancelar criação de issue"
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        Text(
            text = "Nova issue",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = repositoryFullName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )

        if (isOffline) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Modo offline: Conexão necessária para abrir issues. A submissão foi desabilitada.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = {
                title = it
                if (titleError != null && it.isNotBlank()) {
                    titleError = null
                }
            },
            label = { Text("Título *") },
            placeholder = { Text("Resumo objetivo do problema ou tarefa") },
            isError = titleError != null,
            supportingText = titleError?.let { error ->
                { Text(text = error, color = MaterialTheme.colorScheme.error) }
            },
            singleLine = true,
            enabled = !submitting && !isOffline,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("create_issue_title_input")
        )

        MarkdownEditor(
            value = body,
            onValueChange = { body = it },
            label = "Descrição (opcional)",
            placeholder = "Descreva contexto, passos para reproduzir ou detalhes adicionais",
            fieldTag = "create_issue_body_input",
            minLines = 5,
            enabled = !submitting && !isOffline,
            draftKey = draftKey,
            mentionLogins = mentions.logins,
            mentionsLoading = mentions.loading
        )

        if (errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCancel,
                enabled = !submitting,
                modifier = Modifier.testTag("create_issue_cancel_button")
            ) {
                Text("Cancelar")
            }

            Button(
                onClick = {
                    if (isOffline) {
                        errorMessage = "Não é possível criar issues no modo offline."
                        return@Button
                    }
                    val trimmedTitle = title.trim()
                    if (trimmedTitle.isEmpty()) {
                        titleError = "O título da issue não pode ficar em branco."
                        return@Button
                    }
                    titleError = null
                    errorMessage = null
                    submitting = true

                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            createGitHubIssue(
                                client = client,
                                accessToken = accessToken,
                                repository = repositoryFullName,
                                title = trimmedTitle,
                                body = body.takeIf(String::isNotBlank),
                                isOffline = isOffline
                            )
                        }

                        when (result) {
                            is CreateIssueResult.Success -> {
                                draftKey?.let { drafts?.delete(it) }
                                submitting = false
                                onIssueCreated(result.issue)
                            }
                            CreateIssueResult.EmptyTitle -> {
                                titleError = "O título da issue não pode ficar em branco."
                                submitting = false
                            }
                            CreateIssueResult.Offline -> {
                                errorMessage = "Não é possível criar issues no modo offline."
                                submitting = false
                            }
                            is CreateIssueResult.Failure -> {
                                submitting = false
                                if (result.error == GitHubHttpError.UNAUTHORIZED) {
                                    onSessionExpired()
                                } else if (result.statusCode == 403) {
                                    errorMessage = "Você não tem permissão para abrir issues neste repositório."
                                } else if (result.statusCode == 404) {
                                    errorMessage = "Repositório não encontrado no GitHub."
                                } else {
                                    errorMessage = result.error.userMessage
                                }
                            }
                        }
                    }
                },
                enabled = !submitting && !isOffline,
                modifier = Modifier.testTag("create_issue_submit_button")
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .testTag("create_issue_progress"),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Criando...")
                } else {
                    Text("Criar issue")
                }
            }
        }
    }
}
