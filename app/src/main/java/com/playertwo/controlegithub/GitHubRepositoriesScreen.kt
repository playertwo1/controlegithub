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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
    favoriteOperations: Mutex = remember { Mutex() },
    cacheStore: GitHubCacheStore? = null
) {
    var refreshVersion by remember(session?.accessToken) { mutableIntStateOf(0) }
    val pager = remember(client, session?.accessToken, refreshVersion, cacheStore) {
        session?.let {
            GitHubRepositoryPager(
                client = client,
                accessToken = it.accessToken,
                cacheStore = cacheStore,
                accountLogin = it.user.login
            )
        }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val favoriteStore = remember(context, favoritesStore) {
        favoritesStore ?: DataStoreRepositoryFavoritesStore(context)
    }
    val recentStore = LocalRecentRepositoriesStore.current ?: remember(context) {
        DataStoreRecentRepositoriesStore(context)
    }
    var recentRepositories by remember(session?.user?.login) { mutableStateOf(emptyList<RecentRepositoryEntry>()) }
    var repositories by remember(client, session?.user?.login) { mutableStateOf(emptyList<GitHubRepository>()) }
    var hasNext by remember(pager) { mutableStateOf(false) }
    var loaded by remember(pager) { mutableStateOf(false) }
    var loading by remember(pager) { mutableStateOf(false) }
    var retryPending by remember(pager) { mutableStateOf(false) }
    var isOfflineCache by remember(client, session?.user?.login) { mutableStateOf(false) }
    var cachedAtMillis by remember(client, session?.user?.login) { mutableStateOf<Long?>(null) }
    var errorMessage by remember(pager) { mutableStateOf<String?>(null) }
    var retryAt by remember(pager) { mutableStateOf<Long?>(null) }
    var selectedRepository by remember(pager) { mutableStateOf<GitHubRepository?>(null) }
    var searchQuery by remember(client, session?.user?.login) { mutableStateOf("") }
    var visibilityFilter by remember(client, session?.user?.login) { mutableStateOf(RepositoryVisibilityFilter.ALL) }
    var languageFilter by remember(client, session?.user?.login) { mutableStateOf<String?>(null) }
    var healthFilter by remember(client, session?.user?.login) { mutableStateOf<RepositoryHealthStatus?>(null) }
    var sortOrder by remember(client, session?.user?.login) { mutableStateOf(RepositorySort.NAME) }
    var favoriteIds by remember(client, session?.user?.login) { mutableStateOf<Set<Long>?>(null) }
    var favoritesLoadError by remember(client, session?.user?.login) { mutableStateOf(false) }
    var favoritesWriteError by remember(client, session?.user?.login) { mutableStateOf(false) }
    var favoritesSaving by remember(client, session?.user?.login) { mutableStateOf(false) }
    var favoritesOnly by remember(client, session?.user?.login) { mutableStateOf(false) }
    var favoritesRetry by remember(client, session?.user?.login) { mutableIntStateOf(0) }
    var globalSearchMode by remember(client, session?.user?.login) { mutableStateOf(false) }
    var globalQuery by remember(client, session?.user?.login) { mutableStateOf("") }
    var globalPager by remember(client, session?.accessToken) { mutableStateOf<GitHubGlobalRepositorySearchPager?>(null) }
    var globalResults by remember(client, session?.user?.login) { mutableStateOf(emptyList<GitHubRepository>()) }
    var globalHasNext by remember(client, session?.user?.login) { mutableStateOf(false) }
    var globalLoaded by remember(client, session?.user?.login) { mutableStateOf(false) }
    var globalLoading by remember(client, session?.user?.login) { mutableStateOf(false) }
    var globalRetryPending by remember(client, session?.user?.login) { mutableStateOf(false) }
    var globalErrorMessage by remember(client, session?.user?.login) { mutableStateOf<String?>(null) }
    var globalRetryAt by remember(client, session?.user?.login) { mutableStateOf<Long?>(null) }
    val accountLogin = session?.user?.login
    LaunchedEffect(recentStore, accountLogin, selectedRepository) {
        if (accountLogin == null) {
            recentRepositories = emptyList()
        } else if (selectedRepository == null) {
            recentRepositories = withContext(Dispatchers.IO) {
                runCatching { recentStore.load(accountLogin) }.getOrDefault(emptyList())
            }
        }
    }
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
    val visibleRepositories = remember(
        repositories, searchQuery, visibilityFilter, languageFilter, favoriteIds, favoritesOnly, healthFilter, sortOrder
    ) {
        val filtered = filterGitHubRepositories(
            repositories, searchQuery, visibilityFilter, languageFilter,
            favoriteIds.orEmpty(), favoritesOnly && favoriteIds != null
        )
        sortGitHubRepositories(filterByHealth(filtered, healthFilter), sortOrder)
    }
    val hasActiveFilters = searchQuery.isNotBlank() ||
        visibilityFilter != RepositoryVisibilityFilter.ALL || languageFilter != null || favoritesOnly ||
        healthFilter != null || sortOrder != RepositorySort.NAME

    fun clearFilters() {
        searchQuery = ""
        visibilityFilter = RepositoryVisibilityFilter.ALL
        languageFilter = null
        favoritesOnly = false
        healthFilter = null
        sortOrder = RepositorySort.NAME
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
                    isOfflineCache = result.isOfflineCache
                    cachedAtMillis = result.cachedAtMillis
                }
                is GitHubPageResult.RateLimited -> {
                    if (result.items.isNotEmpty() || repositories.isEmpty()) {
                        repositories = result.items
                    }
                    retryPending = true
                    retryAt = result.retryAtEpochMillis
                    errorMessage = GitHubHttpError.RATE_LIMITED.userMessage
                }
                is GitHubPageResult.Failed -> {
                    if (result.items.isNotEmpty() || repositories.isEmpty()) {
                        repositories = result.items
                    }
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

    suspend fun loadNextGlobalPage(activePager: GitHubGlobalRepositorySearchPager? = globalPager) {
        val targetPager = activePager ?: return
        if (globalLoading) return
        globalLoading = true
        globalErrorMessage = null
        globalRetryAt = null
        try {
            when (val result = withContext(Dispatchers.IO) { targetPager.loadNext() }) {
                is GitHubPageResult.Loaded -> {
                    if (globalPager === targetPager) {
                        globalResults = result.items
                        globalHasNext = result.hasNext
                        globalLoaded = true
                        globalRetryPending = false
                    }
                }
                is GitHubPageResult.RateLimited -> {
                    if (globalPager === targetPager) {
                        if (result.items.isNotEmpty() || globalResults.isEmpty()) {
                            globalResults = result.items
                        }
                        globalRetryPending = true
                        globalRetryAt = result.retryAtEpochMillis
                        globalErrorMessage = GitHubHttpError.RATE_LIMITED.userMessage
                    }
                }
                is GitHubPageResult.Failed -> {
                    if (globalPager === targetPager) {
                        if (result.items.isNotEmpty() || globalResults.isEmpty()) {
                            globalResults = result.items
                        }
                        if (result.error == GitHubHttpError.UNAUTHORIZED && session != null) {
                            globalRetryPending = false
                            onSessionExpired(session)
                        } else {
                            globalRetryPending = true
                            globalErrorMessage = result.message
                        }
                    }
                }
            }
        } finally {
            if (globalPager === targetPager) {
                globalLoading = false
            }
        }
    }

    fun submitGlobalSearch() {
        val activeSession = session ?: return
        val trimmed = globalQuery.trim()
        if (trimmed.isEmpty() || globalLoading || activeSession.isOffline) return
        globalPager?.cancel()
        val newPager = GitHubGlobalRepositorySearchPager(
            client = client,
            accessToken = activeSession.accessToken,
            query = trimmed
        )
        globalPager = newPager
        globalResults = emptyList()
        globalHasNext = false
        globalLoaded = false
        globalRetryPending = false
        globalErrorMessage = null
        globalRetryAt = null
        scope.launch {
            loadNextGlobalPage(newPager)
        }
    }

    LaunchedEffect(pager) {
        if (pager != null) loadNextPage()
    }
    DisposableEffect(pager) {
        onDispose { pager?.cancel() }
    }
    DisposableEffect(globalPager) {
        onDispose { globalPager?.cancel() }
    }

    if (selectedRepository != null && session != null) {
        GitHubRepositoryDetailScreen(
            modifier = modifier,
            client = client,
            session = session,
            repository = selectedRepository!!,
            onBack = { selectedRepository = null },
            onSessionExpired = onSessionExpired,
            onAppearance = onAppearance,
            isOffline = isOfflineCache || session.isOffline,
            cacheStore = cacheStore
        )
    } else LazyColumn(
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

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !globalSearchMode,
                        onClick = { globalSearchMode = false },
                        label = { Text("Meus repositórios") },
                        modifier = Modifier.testTag("my_repos_mode_chip")
                    )
                    FilterChip(
                        selected = globalSearchMode,
                        onClick = { globalSearchMode = true },
                        label = { Text("Explorar GitHub") },
                        modifier = Modifier.testTag("global_search_mode_chip")
                    )
                }
            }

            if (globalSearchMode) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Pesquise repositórios públicos de qualquer pessoa ou organização no GitHub.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        OutlinedTextField(
                            value = globalQuery,
                            onValueChange = { globalQuery = it },
                            modifier = Modifier.fillMaxWidth().testTag("global_search_input"),
                            label = { Text("Buscar no GitHub público") },
                            placeholder = { Text("Ex.: owner/repo, compose, linux") },
                            singleLine = true,
                            trailingIcon = {
                                if (globalQuery.isNotEmpty()) {
                                    IconButton(onClick = { globalQuery = "" }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Limpar busca global")
                                    }
                                }
                            }
                        )
                        Button(
                            onClick = ::submitGlobalSearch,
                            enabled = globalQuery.isNotBlank() && !globalLoading && !session.isOffline,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("global_search_submit_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Pesquisar")
                        }
                    }
                }

                if (globalLoading && globalResults.isEmpty()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Pesquisando repositórios no GitHub…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                globalErrorMessage?.let { message ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(message, color = MaterialTheme.colorScheme.error)
                            globalRetryAt?.let { deadline ->
                                val safeTime = android.text.format.DateFormat.getTimeFormat(LocalContext.current)
                                    .format(Date(deadline))
                                Text("Tente novamente após $safeTime.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            TextButton(onClick = { scope.launch { loadNextGlobalPage() } }, enabled = !globalLoading) {
                                Text("Tentar novamente")
                            }
                        }
                    }
                }

                if (globalLoaded && globalResults.isEmpty() && globalErrorMessage == null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Nenhum repositório público encontrado para a busca.", fontWeight = FontWeight.SemiBold)
                            Text("Tente outro termo, nome de repositório ou organização.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                items(globalResults, key = GitHubRepository::id) { repository ->
                    GitHubRepositoryCard(
                        repository = repository,
                        isFavorite = repository.id in favoriteIds.orEmpty(),
                        favoriteEnabled = favoriteIds != null && !favoritesSaving,
                        onFavoriteClick = { setFavorite(repository, repository.id !in favoriteIds.orEmpty()) },
                        onClick = { selectedRepository = repository }
                    )
                }

                if (globalLoaded && globalHasNext) {
                    item {
                        Button(
                            onClick = { scope.launch { loadNextGlobalPage() } },
                            enabled = !globalLoading && !globalRetryPending,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (globalLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("Carregar mais")
                        }
                    }
                }
            } else {
                if (isOfflineCache) {
                item {
                    OfflineCacheBadge(cachedAtMillis)
                }
            }

            if (recentRepositories.isNotEmpty()) {
                item(key = "recent_repositories_section") {
                    Column(
                        modifier = Modifier.fillMaxWidth().testTag("recent_repositories_section"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Visitados recentemente", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            TextButton(
                                onClick = {
                                    val login = accountLogin ?: return@TextButton
                                    scope.launch {
                                        withContext(Dispatchers.IO) { runCatching { recentStore.clear(login) } }
                                        recentRepositories = emptyList()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp).testTag("clear_recent_repositories_button")
                            ) {
                                Text("Limpar histórico", fontSize = 12.sp)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recentRepositories.forEach { entry ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        selectedRepository = repositories.firstOrNull {
                                            it.fullName.equals(entry.fullName, ignoreCase = true)
                                        } ?: entry.toGitHubRepository()
                                    },
                                    label = { Text("↺ ${entry.fullName}", fontSize = 12.sp) },
                                    modifier = Modifier.testTag("recent_repo_chip_${entry.fullName}")
                                )
                            }
                        }
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
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = healthFilter == null,
                                onClick = { healthFilter = null },
                                label = { Text("Qualquer saúde") }
                            )
                            RepositoryHealthStatus.entries.forEach { status ->
                                FilterChip(
                                    selected = healthFilter == status,
                                    onClick = { healthFilter = if (healthFilter == status) null else status },
                                    label = { Text(status.label) }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Ordenar:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            RepositorySort.entries.forEach { sort ->
                                FilterChip(
                                    selected = sortOrder == sort,
                                    onClick = { sortOrder = sort },
                                    label = { Text(sort.label) }
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
            Text(
                repository.health.summary,
                color = repository.health.status.color(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            repository.description?.takeIf(String::isNotBlank)?.let { description ->
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}
