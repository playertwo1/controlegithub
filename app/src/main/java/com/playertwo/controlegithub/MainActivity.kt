package com.playertwo.controlegithub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val preferences = remember(context) { ThemePreferences(context) }
            val apiClient = remember(context) { GitHubHttpClient() }
            val sessions = remember(context) {
                GitHubSessionManager(
                    SecureSessionStore(context),
                    GitHubOAuthClient(BuildConfig.GITHUB_OAUTH_CLIENT_ID, apiClient = apiClient)
                )
            }
            val themeMode by preferences.themeMode.collectAsState(initial = AppThemeMode.SYSTEM)
            val scope = rememberCoroutineScope()
            var preferenceError by remember { mutableStateOf(false) }
            var session by remember { mutableStateOf<GitHubSession?>(null) }
            var sessionRestoring by remember { mutableStateOf(true) }
            var sessionRetry by remember { mutableStateOf(false) }
            var sessionStorageError by remember { mutableStateOf(false) }
            suspend fun restoreSession() {
                sessionRestoring = true
                sessionRetry = false
                sessionStorageError = false
                when (val result = sessions.restore()) {
                    is SessionRestoreResult.Restored -> session = result.session
                    SessionRestoreResult.SignedOut -> session = null
                    SessionRestoreResult.Retry -> sessionRetry = true
                    SessionRestoreResult.CleanupFailed -> {
                        session = null
                        sessionStorageError = true
                    }
                }
                sessionRestoring = false
            }
            LaunchedEffect(sessions) { restoreSession() }
            val darkSystemBars = appIsDark(themeMode, androidx.compose.foundation.isSystemInDarkTheme())
            DisposableEffect(darkSystemBars) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkSystemBars) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (darkSystemBars) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                )
                onDispose {}
            }
            ControleTheme(themeMode) {
                ControleApp(
                    themeMode = themeMode,
                    preferenceError = preferenceError,
                    apiClient = apiClient,
                    session = session,
                    sessionRestoring = sessionRestoring,
                    sessionRetry = sessionRetry,
                    sessionStorageError = sessionStorageError,
                    onRetrySession = { scope.launch { restoreSession() } },
                    onRetrySessionCleanup = {
                        scope.launch {
                            try {
                                sessions.logout()
                                sessionStorageError = false
                                sessionRetry = false
                            } catch (_: Exception) {
                                sessionStorageError = true
                            } finally {
                                session = null
                            }
                        }
                    },
                    onConnected = { connected ->
                        try {
                            sessions.persist(connected)
                            session = connected
                            sessionStorageError = false
                            true
                        } catch (_: Exception) {
                            sessionStorageError = true
                            false
                        }
                    },
                    onSessionExpired = { expired ->
                        scope.launch {
                            if (session?.accessToken == expired.accessToken) {
                                try {
                                    sessions.logout()
                                    sessionStorageError = false
                                } catch (_: Exception) {
                                    sessionStorageError = true
                                } finally {
                                    session = null
                                }
                            }
                        }
                    },
                    onLogout = {
                        scope.launch {
                            try {
                                sessions.logout()
                                sessionStorageError = false
                            } catch (_: Exception) {
                                sessionStorageError = true
                            } finally {
                                session = null
                            }
                        }
                    },
                    onThemeModeChange = { mode ->
                        scope.launch {
                            try {
                                preferences.setThemeMode(mode)
                                preferenceError = false
                            } catch (_: Exception) {
                                preferenceError = true
                            }
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ControleApp(
    themeMode: AppThemeMode,
    preferenceError: Boolean,
    apiClient: GitHubHttpClient,
    session: GitHubSession?,
    sessionRestoring: Boolean,
    sessionRetry: Boolean,
    sessionStorageError: Boolean,
    onRetrySession: () -> Unit,
    onRetrySessionCleanup: () -> Unit,
    onConnected: suspend (GitHubSession) -> Boolean,
    onSessionExpired: (GitHubSession) -> Unit,
    onLogout: () -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    var started by rememberSaveable { mutableStateOf(false) }
    var appearanceOpen by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(0) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var logoutConfirm by rememberSaveable { mutableStateOf(false) }
    var repositorySessionExpired by remember { mutableStateOf(false) }
    val connectAndPersist: suspend (GitHubSession) -> Boolean = { connected ->
        val saved = onConnected(connected)
        if (saved) repositorySessionExpired = false
        saved
    }
    val selected = DemoData.repositories.firstOrNull { it.name == selectedName }
    val keyboardOpen = WindowInsets.isImeVisible
    BackHandler(appearanceOpen || started) {
        if (appearanceOpen) appearanceOpen = false
        else if (selected != null) selectedName = null
        else started = false
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        if (started && !appearanceOpen && !keyboardOpen) Box(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, windowInsets = WindowInsets(0, 0, 0, 0)) {
                    val labels = listOf("Início", "Repos", "Trabalho", "Avisos")
                    val icons = listOf(Icons.Outlined.Home, Icons.Outlined.Folder, Icons.Outlined.CheckCircle, Icons.Outlined.Notifications)
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(selected = page == index, onClick = { page = index; selectedName = null },
                            icon = { Icon(icons[index], contentDescription = label) }, label = { Text(label) })
                    }
                }
            }
        }
    }) { padding ->
        if (appearanceOpen) AppearanceScreen(
            Modifier.padding(padding), themeMode, preferenceError,
            onBack = { appearanceOpen = false }, onThemeModeChange = onThemeModeChange
        )
        else if (!started) Welcome(
            Modifier.padding(padding), session, sessionRestoring, sessionRetry, sessionStorageError,
            onRetrySession, onRetrySessionCleanup, connectAndPersist, onLogout = { logoutConfirm = true },
            onAppearance = { appearanceOpen = true }, onStart = { started = true }
        )
        else if (page == 1 && selected == null) GitHubRepositoriesScreen(
            modifier = Modifier.padding(padding),
            client = apiClient,
            session = session,
            sessionRestoring = sessionRestoring,
            sessionStorageError = sessionStorageError,
            sessionExpired = repositorySessionExpired,
            onConnected = connectAndPersist,
            onSessionExpired = { expired ->
                repositorySessionExpired = true
                onSessionExpired(expired)
            },
            onLogout = { repositorySessionExpired = false; logoutConfirm = true },
            onAppearance = { appearanceOpen = true }
        )
        else if (page == 0 && selected == null && session != null) GitHubHomeScreen(
            modifier = Modifier.padding(padding),
            client = apiClient,
            session = session,
            sessionRestoring = sessionRestoring,
            onOpenRepositories = { page = 1 },
            onSessionExpired = onSessionExpired,
            onLogout = { logoutConfirm = true },
            onAppearance = { appearanceOpen = true }
        )
        else LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CONTROLE / GITHUB", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { appearanceOpen = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Configurações de aparência")
                    }
                }
                Spacer(Modifier.height(48.dp))
                Text(if (selected != null) selected.name else listOf("Seu centro de comando", "Repositórios", "Seu trabalho", "Notificações")[page], fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(session?.let { "Conta conectada: @${it.user.login}" } ?: "Nenhuma conta conectada", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                session?.let { Text(it.user.profileUrl, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                session?.let { TextButton(onClick = { logoutConfirm = true }) { Text("Sair da conta") } }
                if (sessionRestoring) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                if (sessionRetry) TextButton(onClick = onRetrySession) { Text("Tentar restaurar sessão") }
                if (sessionStorageError) Text("A sessão saiu da tela, mas o armazenamento seguro informou falha.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                if (sessionStorageError) TextButton(onClick = onRetrySessionCleanup) { Text("Tentar limpar a sessão") }
                if (session == null) Text("Modo demonstração · dados fictícios", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            if (selected != null) {
                item { TextButton(onClick = { selectedName = null }) { Text("← Voltar") } }
                item { Panel { Text(selected.description, fontSize = 18.sp); Spacer(Modifier.height(16.dp)); Text("${selected.language}  ·  ${selected.stars} estrelas  ·  ${selected.issues} issues", color = MaterialTheme.colorScheme.primary) } }
                item { Section("Visão geral"); Text("Branch principal: main\nVisibilidade: público\nÚltima atividade: hoje (exemplo)", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 28.sp) }
                item { WorkCard("#18", "Preparar primeira versão Android", "Issue · planejamento") }
                item { WorkCard("#12", "Adicionar tema e navegação", "Pull request · em revisão") }
            } else when (page) {
                0 -> {
                    item { Panel { Text("BOM DIA, DEVELOPER", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, letterSpacing = 1.sp); Spacer(Modifier.height(10.dp)); Text("Menos abas.\nMais clareza.", fontSize = 32.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text("Acompanhe o que precisa de você em um só lugar.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Metric("3", "Repositórios", Modifier.weight(1f)); Metric("13", "Issues abertas", Modifier.weight(1f)) } }
                    item { Section("Precisa da sua atenção"); WorkCard("PR #12", "Revisar navegação Android", "controlegithub · há 2 horas") }
                    item { Section("Seus repositórios") }
                    items(DemoData.repositories.take(2)) { repo -> RepoCard(repo) { selectedName = repo.name } }
                }
                1 -> Unit
                2 -> {
                    if (session != null) item { NotIntegratedScreen("Seu trabalho", "Issues e pull requests") }
                    else {
                        item { Section("Issues e pull requests") }
                        item { WorkCard("ISSUE #18", "Preparar primeira versão Android", "controlegithub · planejamento") }
                        item { WorkCard("PR #12", "Adicionar tema e navegação", "controlegithub · em revisão") }
                        item { WorkCard("ISSUE #7", "Documentar padrões de projeto", "ideias_standard · documentação") }
                        item { Text("Triagem e ações no GitHub estarão disponíveis após a integração da conta.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                3 -> {
                    if (session != null) item { NotIntegratedScreen("Avisos", "Notificações do GitHub") }
                    else {
                        item { Section("Caixa de entrada") }
                        item { WorkCard("REVISÃO", "Uma revisão está esperando", "controlegithub · PR #12") }
                        item { WorkCard("MENÇÃO", "Você foi mencionado em uma issue", "ideias_standard · issue #7") }
                        item { Text("Exemplos de notificações. Nenhuma conta está conectada.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
    if (logoutConfirm) LogoutConfirmation(
        storageError = sessionStorageError,
        onDismiss = { logoutConfirm = false },
        onConfirm = { logoutConfirm = false; onLogout() }
    )
}

@Composable
private fun Welcome(
    modifier: Modifier,
    session: GitHubSession?,
    sessionRestoring: Boolean,
    sessionRetry: Boolean,
    sessionStorageError: Boolean,
    onRetrySession: () -> Unit,
    onRetrySessionCleanup: () -> Unit,
    onConnected: suspend (GitHubSession) -> Boolean,
    onLogout: () -> Unit,
    onAppearance: () -> Unit,
    onStart: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CONTROLE / GITHUB", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onAppearance) {
                Icon(Icons.Outlined.Settings, contentDescription = "Configurações de aparência")
            }
        }
        Column {
            Icon(Icons.Outlined.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(28.dp))
            Text("Seu código.\nSua visão.\nSeu controle.", fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            Text("Repositórios, issues e pull requests.\nTudo encontra seu lugar, no seu Android.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp, lineHeight = 26.sp)
        }
        Column {
            if (sessionRestoring) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Restaurando sessão segura…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            session?.let {
                Text("Conta conectada: @${it.user.login}", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                Text(it.user.profileUrl, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                TextButton(onClick = onLogout) { Text("Sair da conta") }
            }
            if (sessionRetry) {
                Text("Não foi possível validar a sessão agora. Ela continua protegida no dispositivo.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                TextButton(onClick = onRetrySession) { Text("Tentar novamente") }
            }
            if (sessionStorageError) Text("Não foi possível atualizar o armazenamento seguro da sessão.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            if (sessionStorageError) TextButton(onClick = onRetrySessionCleanup) { Text("Tentar limpar a sessão") }
            if (session != null) Spacer(Modifier.height(12.dp))
            if (session == null) GitHubSignIn(onConnected, enabled = !sessionRestoring)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
                Text(if (session == null) "Explorar demonstração" else "Abrir painel", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LogoutConfirmation(storageError: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sair do GitHub?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Isso remove a sessão e os dados privados deste dispositivo. A autorização do aplicativo continuará ativa no GitHub até ser revogada nas configurações da conta.")
                TextButton(onClick = {
                    uriHandler.openUri("https://github.com/settings/connections/applications/${BuildConfig.GITHUB_OAUTH_CLIENT_ID}")
                }) { Text("Abrir autorizações do GitHub") }
                if (storageError) Text("O armazenamento seguro informou uma falha na operação anterior.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Sair") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun Metric(value: String, label: String, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp)).padding(18.dp)) {
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Section(title: String) { Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)) }

@Composable
private fun RepoCard(repo: Repository, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("playertwo1 /", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(repo.name, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(repo.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Text("● ${repo.language}    ☆ ${repo.stars}    ◉ ${repo.issues}", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun WorkCard(badge: String, title: String, subtitle: String) {
    Panel {
        Text(badge, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun AppearanceScreen(
    modifier: Modifier,
    selectedMode: AppThemeMode,
    preferenceError: Boolean,
    onBack: () -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar")
            }
            Text("Aparência", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        Text("Tema do aplicativo", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Escolha como o ControleGitHub deve aparecer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        AppThemeMode.entries.forEach { mode ->
            Row(
                modifier = Modifier.fillMaxWidth().selectable(
                    selected = selectedMode == mode,
                    role = Role.RadioButton,
                    onClick = { onThemeModeChange(mode) }
                ).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selectedMode == mode, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(mode.label, fontSize = 16.sp)
            }
        }
        if (preferenceError) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Não foi possível salvar a preferência. O app continuará usando o tema atual.",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
