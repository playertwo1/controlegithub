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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    onAppearance: () -> Unit,
    favoritesStore: RepositoryFavoritesStore? = null,
    favoriteCleanupError: Boolean = false,
    favoriteOperations: Mutex = remember { Mutex() }
) {
    var refreshVersion by remember(session?.accessToken) { mutableIntStateOf(0) }
    val pager = remember(client, session?.accessToken, refreshVersion) {
        session?.let { GitHubRepositoryPager(client, it.accessToken) }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val favoriteStore = remember(context, favoritesStore) {
        favoritesStore ?: DataStoreRepositoryFavoritesStore(context)
    }
    var repositories by remember(pager) { mutableStateOf(emptyList<GitHubRepository>()) }
    var hasNext by remember(pager) { mutableStateOf(false) }
    var loaded by remember(pager) { mutableStateOf(false) }
    var loading by remember(pager) { mutableStateOf(false) }
    var retryPending by remember(pager) { mutableStateOf(false) }
    var errorMessage by remember(pager) { mutableStateOf<String?>(null) }
    var retryAt by remember(pager) { mutableStateOf<Long?>(null) }
    var selectedRepository by remember(pager) { mutableStateOf<GitHubRepository?>(null) }
    var searchQuery by remember(session?.user?.login) { mutableStateOf("") }
    var visibilityFilter by remember(session?.user?.login) { mutableStateOf(RepositoryVisibilityFilter.ALL) }
    var languageFilter by remember(session?.user?.login) { mutableStateOf<String?>(null) }
    var favoriteIds by remember(session?.user?.login) { mutableStateOf<Set<Long>?>(null) }
    var favoritesLoadError by remember(session?.user?.login) { mutableStateOf(false) }
    var favoritesWriteError by remember(session?.user?.login) { mutableStateOf(false) }
    var favoritesSaving by remember(session?.user?.login) { mutableStateOf(false) }
    var favoritesOnly by remember(session?.user?.login) { mutableStateOf(false) }
    var favoritesRetry by remember(session?.user?.login) { mutableIntStateOf(0) }
    val accountLogin = session?.user?.login
    LaunchedEffect(favoriteStore, accountLogin, favoritesRetry) {
        if (accountLogin == null) {
            favoriteIds = null
            favoritesLoadError = false
        } else {
            favoriteIds = null
            favoritesLoadError = false
            try {
                favoriteIds = withContext(Dispatchers.IO) { favoriteStore.load(accountLogin) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                favoriteIds = null
                favoritesLoadError = true
                favoritesOnly = false
            }
        }
    }
    val availableLanguages = remember(repositories) {
        repositories.mapNotNull(GitHubRepository::language)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedBy { it.lowercase(Locale.ROOT) }
    }
    val visibleRepositories = remember(repositories, searchQuery, visibilityFilter, languageFilter, favoriteIds, favoritesOnly) {
        filterGitHubRepositories(
            repositories, searchQuery, visibilityFilter, languageFilter,
            favoriteIds.orEmpty(), favoritesOnly && favoriteIds != null
        )
    }
    val hasActiveFilters = searchQuery.isNotBlank() ||
        visibilityFilter != RepositoryVisibilityFilter.ALL || languageFilter != null || favoritesOnly

    fun clearFilters() {
        searchQuery = ""
        visibilityFilter = RepositoryVisibilityFilter.ALL
        languageFilter = null
        favoritesOnly = false
    }

    fun setFavorite(repository: GitHubRepository, favorite: Boolean) {
        val login = accountLogin ?: return
        if (favoriteIds == null || favoritesSaving) return
        scope.launch {
            favoritesSaving = true
            favoritesWriteError = false
            try {
                val updated = withContext(Dispatchers.IO) {
                    favoriteOperations.withLock {
                        favoriteStore.setFavorite(login, repository.id, favorite)
                    }
                }
                if (accountLogin == login) favoriteIds = updated
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (accountLogin == login) favoritesWriteError = true
            } finally {
                if (accountLogin == login) favoritesSaving = false
            }
        }
    }

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

    if (selectedRepository != null && session != null) {
        GitHubRepositoryDetailScreen(
            modifier = modifier,
            client = client,
            session = session,
            repository = selectedRepository!!,
            onBack = { selectedRepository = null },
            onSessionExpired = onSessionExpired,
            onAppearance = onAppearance
        )
    } else key(pager) {
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
                    TextButton(onClick = onLogout, enabled = !favoritesSaving) { Text("Sair") }
                    IconButton(onClick = { refreshVersion++ }, enabled = !loading) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar repositórios")
                    }
                }
            }

            if (favoriteCleanupError) {
                item { Text("Não foi possível apagar ou restaurar favoritos. Tente sair novamente.", color = MaterialTheme.colorScheme.error) }
            }
            if (sessionStorageError) {
                item { Text("Não foi possível encerrar a sessão segura. Tente sair novamente.", color = MaterialTheme.colorScheme.error) }
            }

            if (loading && repositories.isEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Carregando repositórios…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (loaded && repositories.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Buscar repositórios") },
                            placeholder = { Text("Nome, proprietário ou linguagem") },
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Limpar busca")
                                    }
                                }
                            }
                        )
                        Text(
                            "Busca e filtros aplicados aos ${repositories.size} repositórios carregados.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = visibilityFilter == RepositoryVisibilityFilter.ALL,
                                onClick = { visibilityFilter = RepositoryVisibilityFilter.ALL },
                                label = { Text("Todos") }
                            )
                            FilterChip(
                                selected = visibilityFilter == RepositoryVisibilityFilter.PUBLIC,
                                onClick = { visibilityFilter = RepositoryVisibilityFilter.PUBLIC },
                                label = { Text("Públicos") }
                            )
                            FilterChip(
                                selected = visibilityFilter == RepositoryVisibilityFilter.PRIVATE,
                                onClick = { visibilityFilter = RepositoryVisibilityFilter.PRIVATE },
                                label = { Text("Privados") }
                            )
                            FilterChip(
                                selected = favoritesOnly,
                                onClick = { favoritesOnly = !favoritesOnly },
                                enabled = favoriteIds != null && !favoritesSaving,
                                label = { Text("Favoritos") }
                            )
                        }
                        when {
                            favoritesLoadError -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Não foi possível carregar favoritos", color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                                TextButton(onClick = { favoritesRetry++ }) { Text("Tentar novamente") }
                            }
                            favoriteIds == null -> Text("Carregando favoritos…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            favoritesWriteError -> Text("Não foi possível salvar o favorito", color = MaterialTheme.colorScheme.error)
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = languageFilter == null,
                                onClick = { languageFilter = null },
                                label = { Text("Todas as linguagens") }
                            )
                            availableLanguages.forEach { language ->
                                FilterChip(
                                    selected = languageFilter.equals(language, ignoreCase = true),
                                    onClick = {
                                        languageFilter = if (languageFilter.equals(language, ignoreCase = true)) null else language
                                    },
                                    label = { Text(language) }
                                )
                            }
                        }
                        if (hasActiveFilters) {
                            TextButton(onClick = ::clearFilters) { Text("Limpar filtros") }
                        }
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

            if (loaded && repositories.isNotEmpty() && visibleRepositories.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nenhum repositório corresponde aos critérios", fontWeight = FontWeight.SemiBold)
                        Text("Os critérios consideram apenas os repositórios carregados.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            items(visibleRepositories, key = GitHubRepository::id) { repository ->
                GitHubRepositoryCard(
                    repository = repository,
                    isFavorite = repository.id in favoriteIds.orEmpty(),
                    favoriteEnabled = favoriteIds != null && !favoritesSaving,
                    onFavoriteClick = { setFavorite(repository, repository.id !in favoriteIds.orEmpty()) },
                    onClick = { selectedRepository = repository }
                )
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
}

@Composable
private fun GitHubRepositoryCard(
    repository: GitHubRepository,
    isFavorite: Boolean,
    favoriteEnabled: Boolean,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(repository.fullName, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onFavoriteClick, enabled = favoriteEnabled) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (isFavorite) {
                            "Remover ${repository.fullName} dos favoritos"
                        } else {
                            "Adicionar ${repository.fullName} aos favoritos"
                        },
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
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
