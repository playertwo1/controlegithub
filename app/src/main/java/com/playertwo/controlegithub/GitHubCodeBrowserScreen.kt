package com.playertwo.controlegithub

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun GitHubCodeBrowserScreen(
    modifier: Modifier = Modifier,
    client: GitHubHttpClient,
    accessToken: String,
    owner: String,
    name: String,
    defaultBranch: String?,
    isOffline: Boolean = false,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit
) {
    var path by remember(owner, name, defaultBranch) { mutableStateOf("") }
    var ref by remember(owner, name, defaultBranch) { mutableStateOf(defaultBranch) }
    var listing by remember(owner, name, defaultBranch) { mutableStateOf<CodeListing?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var refs by remember(owner, name) { mutableStateOf(listOfNotNull(defaultBranch)) }
    var choosingRef by remember { mutableStateOf(false) }
    var pendingLink by remember { mutableStateOf<GitHubMarkdownLinkTarget.External?>(null) }
    var browserUnavailable by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activeRef = ref

    BackHandler {
        if (path.isNotEmpty()) path = parentCodePath(path) else onBack()
    }
    LaunchedEffect(owner, name, accessToken, isOffline) {
        if (isOffline || defaultBranch.isNullOrBlank()) return@LaunchedEffect
        val branches = withContext(Dispatchers.IO) { loadRefNames(client, accessToken, owner, name, "branches") }
        if (branches is RefLoad.Unauthorized) {
            onSessionExpired()
            return@LaunchedEffect
        }
        val tags = withContext(Dispatchers.IO) { loadRefNames(client, accessToken, owner, name, "tags") }
        if (tags is RefLoad.Unauthorized) {
            onSessionExpired()
            return@LaunchedEffect
        }
        refs = (listOf(defaultBranch) + (branches as? RefLoad.Found)?.names.orEmpty() + (tags as? RefLoad.Found)?.names.orEmpty())
            .distinct()
    }
    LaunchedEffect(path, activeRef, isOffline, retry) {
        if (isOffline || activeRef.isNullOrBlank()) return@LaunchedEffect
        loading = true
        error = null
        when (val result = withContext(Dispatchers.IO) {
            loadCodeListing(client, accessToken, owner, name, path, activeRef)
        }) {
            is CodeLoad.Found -> listing = result.listing
            CodeLoad.Unauthorized -> onSessionExpired()
            is CodeLoad.Failed -> {
                listing = null
                error = result.message
            }
        }
        loading = false
    }

    Column(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (path.isNotEmpty()) path = parentCodePath(path) else onBack() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar")
            }
            Text("Código", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { choosingRef = true }, enabled = activeRef != null, modifier = Modifier.testTag("code_ref_button")) {
                Text(activeRef ?: "Sem ref")
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { path = "" }, modifier = Modifier.heightIn(min = 48.dp)) { Text(name) }
            path.split('/').filter(String::isNotEmpty).forEachIndexed { index, segment ->
                val target = path.split('/').filter(String::isNotEmpty).take(index + 1).joinToString("/")
                TextButton(onClick = { path = target }, modifier = Modifier.heightIn(min = 48.dp)) { Text(segment) }
            }
        }
        when {
            isOffline -> Text("Modo offline: conexão necessária para ler o código.", color = MaterialTheme.colorScheme.error)
            activeRef.isNullOrBlank() -> Text("Branch padrão indisponível.", color = MaterialTheme.colorScheme.error)
            loading && listing == null -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text("Carregando código…")
            }
            error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }) { Text("Tentar novamente") }
            }
            listing is CodeListing.Directory -> DirectoryList(listing as CodeListing.Directory) { entry ->
                if (entry.kind == CodeEntryKind.DIR || entry.kind == CodeEntryKind.FILE) path = entry.path
            }
            listing is CodeListing.File -> FileBody(
                (listing as CodeListing.File).body,
                onOpenLink = { url ->
                    pendingLink = classifyGitHubMarkdownLink(url) as? GitHubMarkdownLinkTarget.External
                }
            )
        }
    }
    if (choosingRef) {
        AlertDialog(
            onDismissRequest = { choosingRef = false },
            title = { Text("Branch ou tag") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    refs.forEach { item ->
                        TextButton(onClick = {
                            choosingRef = false
                            if (item != ref) {
                                path = ""
                                listing = null
                                ref = item
                            }
                        }, modifier = Modifier.fillMaxWidth().testTag("code_ref_$item")) { Text(item) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingRef = false }) { Text("Fechar") } }
        )
    }
    pendingLink?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingLink = null },
            title = { Text("Abrir link externo?") },
            text = { Text("${target.host}\n${target.url}") },
            confirmButton = {
                TextButton(onClick = {
                    pendingLink = null
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target.url)))
                    } catch (_: ActivityNotFoundException) {
                        browserUnavailable = true
                    }
                }) { Text("Abrir no navegador") }
            },
            dismissButton = { TextButton(onClick = { pendingLink = null }) { Text("Cancelar") } }
        )
    }
    if (browserUnavailable) {
        AlertDialog(
            onDismissRequest = { browserUnavailable = false },
            title = { Text("Não foi possível abrir o endereço") },
            confirmButton = { TextButton(onClick = { browserUnavailable = false }) { Text("Fechar") } }
        )
    }
}

@Composable
private fun DirectoryList(directory: CodeListing.Directory, onOpen: (CodeEntry) -> Unit) {
    if (directory.entries.isEmpty()) {
        Text("Esta pasta está vazia.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(directory.entries, key = { it.path + it.kind }) { entry ->
            TextButton(
                onClick = { onOpen(entry) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("code_entry_${entry.name}")
            ) {
                Text(if (entry.kind == CodeEntryKind.DIR) "${entry.name}/" else entry.name, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun FileBody(body: CodeFileBody, onOpenLink: (String) -> Unit) {
    when (body) {
        is CodeFileBody.Unavailable -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Este arquivo é binário, um link especial ou maior que 1 MB. O conteúdo não foi aberto aqui.")
            body.htmlUrl?.let { url ->
                Button(onClick = { onOpenLink(url) }, modifier = Modifier.testTag("code_external_link")) { Text("Abrir no GitHub") }
            }
        }
        is CodeFileBody.Text -> if (body.language == CodeLanguage.MARKDOWN) {
            GitHubMarkdownContent(body.text, contentPadding = PaddingValues(0.dp))
        } else {
            val lines = remember(body.text) { body.text.lines() }
            val keyword = MaterialTheme.colorScheme.primary
            val string = MaterialTheme.colorScheme.tertiary
            val comment = MaterialTheme.colorScheme.onSurfaceVariant
            LazyColumn {
                items(lines.size) { index ->
                    val line = lines[index]
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        Text("${index + 1}", modifier = Modifier.width(40.dp).testTag("code_line_${index + 1}"), fontFamily = FontFamily.Monospace, color = comment)
                        Text(highlighted(line, body.language, keyword, string, comment), fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

private fun highlighted(line: String, language: CodeLanguage, keyword: androidx.compose.ui.graphics.Color, string: androidx.compose.ui.graphics.Color, comment: androidx.compose.ui.graphics.Color) = buildAnnotatedString {
    val tokens = highlightCode(line, language)
    var cursor = 0
    tokens.forEach { token ->
        if (token.start > cursor) append(line.substring(cursor, token.start))
        val color = when (token.kind) {
            CodeTokenKind.KEYWORD -> keyword
            CodeTokenKind.STRING -> string
            CodeTokenKind.COMMENT -> comment
        }
        withStyle(SpanStyle(color = color)) { append(line.substring(token.start, token.end)) }
        cursor = token.end
    }
    if (cursor < line.length) append(line.substring(cursor))
}
