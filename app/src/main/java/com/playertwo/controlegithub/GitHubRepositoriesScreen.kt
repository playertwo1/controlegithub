package com.playertwo.controlegithub

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
internal fun GitHubRepositoriesScreen(
    modifier: Modifier,
    client: GitHubHttpClient,
    session: GitHubSession?,
    sessionRestoring: Boolean,
    sessionStorageError: Boolean,
    sessionExpired: Boolean,
    onConnected: suspend (GitHubSession) -> Boolean,
    onSessionExpired: (GitHubSession) -> Unit,
    onLogout: () -> Unit,
    onAppearance: () -> Unit
) {
    var refreshVersion by remember(session?.accessToken) { mutableIntStateOf(0) }
    val pager = remember(client, session?.accessToken, refreshVersion) {
        session?.let { GitHubRepositoryPager(client, it.accessToken) }
    }
    val scope = rememberCoroutineScope()
    var repositories by remember(pager) { mutableStateOf(emptyList<GitHubRepository>()) }
    var hasNext by remember(pager) { mutableStateOf(false) }
    var loaded by remember(pager) { mutableStateOf(false) }
    var loading by remember(pager) { mutableStateOf(false) }
    var retryPending by remember(pager) { mutableStateOf(false) }
    var errorMessage by remember(pager) { mutableStateOf<String?>(null) }
    var retryAt by remember(pager) { mutableStateOf<Long?>(null) }

    suspend fun loadNextPage() {
        val activePager = pager ?: return
        if (loading) return
        loading = true
        errorMessage = null
        retryAt = null
        try {
            when (val result = withContext(Dispatchers.IO) { activePager.loadNext() }) {
                is GitHubPageResult.Loaded -> {
                    repositories = result.items
                    hasNext = result.hasNext
                    loaded = true
                    retryPending = false
                }
                is GitHubPageResult.RateLimited -> {
                    repositories = result.items
                    retryPending = true
                    retryAt = result.retryAtEpochMillis
                    errorMessage = GitHubHttpError.RATE_LIMITED.userMessage
                }
                is GitHubPageResult.Failed -> {
                    repositories = result.items
                    if (result.error == GitHubHttpError.UNAUTHORIZED && session != null) {
                        retryPending = false
                        onSessionExpired(session)
                    } else {
                        retryPending = true
                        errorMessage = result.message
                    }
                }
            }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(pager) {
        if (pager != null) loadNextPage()
    }
    DisposableEffect(pager) {
        onDispose { pager?.cancel() }
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
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
            Text("Seus repositórios", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }

        if (sessionRestoring) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Restaurando sessão segura…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (session == null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (sessionExpired) "Sua sessão expirou. Conecte-se novamente para carregar seus repositórios."
                        else "Conecte sua conta GitHub para ver os repositórios que ela pode acessar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sessionStorageError) {
                        Text("O armazenamento seguro informou uma falha ao encerrar a sessão.", color = MaterialTheme.colorScheme.error)
                    }
                    GitHubSignIn(onConnected)
                }
            }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Conta conectada", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                        Text("@${session.user.login}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    TextButton(onClick = onLogout) { Text("Sair") }
                    IconButton(onClick = { refreshVersion++ }, enabled = !loading) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar repositórios")
                    }
                }
            }

            if (loading && repositories.isEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Carregando repositórios…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            errorMessage?.let { message ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(message, color = MaterialTheme.colorScheme.error)
                        retryAt?.let { deadline ->
                            val safeTime = android.text.format.DateFormat.getTimeFormat(androidx.compose.ui.platform.LocalContext.current)
                                .format(Date(deadline))
                            Text("Tente novamente após $safeTime.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        TextButton(onClick = { scope.launch { loadNextPage() } }, enabled = !loading) {
                            Text("Tentar novamente")
                        }
                    }
                }
            }

            if (loaded && repositories.isEmpty() && errorMessage == null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                        Text("Nenhum repositório acessível", fontWeight = FontWeight.SemiBold)
                        Text("A lista da conta está vazia ou não há repositórios concedidos a este app.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { refreshVersion++ }, enabled = !loading) { Text("Atualizar") }
                    }
                }
            }

            items(repositories, key = GitHubRepository::id) { repository ->
                GitHubRepositoryCard(repository)
            }

            if (loaded && hasNext) {
                item {
                    Button(
                        onClick = { scope.launch { loadNextPage() } },
                        enabled = !loading && !retryPending,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Carregar mais")
                    }
                }
            } else if (retryPending && repositories.isNotEmpty() && errorMessage == null) {
                item {
                    TextButton(onClick = { scope.launch { loadNextPage() } }, enabled = !loading) {
                        Text("Tentar carregar a próxima página")
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHubRepositoryCard(repository: GitHubRepository) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(repository.fullName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (repository.isPrivate) "Privado" else "Público",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                repository.language?.let { language ->
                    Text("  ·  $language", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text("  ·  ★ ${repository.stars}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            repository.description?.takeIf(String::isNotBlank)?.let { description ->
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}
