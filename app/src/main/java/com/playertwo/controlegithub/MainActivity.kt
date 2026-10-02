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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Mint = Color(0xFF3E85FF)
private val Muted = Color(0xFF9AA8BB)
private val Palette = darkColorScheme(primary = Mint, onPrimary = Color.White, secondaryContainer = Color(0xFF153562), onSecondaryContainer = Mint, background = Color(0xFF080808), surface = Color(0xFF1C1C1E), onSurface = Color(0xFFF5F5F7))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { MaterialTheme(colorScheme = Palette) { ControleApp() } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControleApp() {
    var started by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(0) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var repositoryQuery by rememberSaveable { mutableStateOf("") }
    val selected = DemoData.repositories.firstOrNull { it.name == selectedName }
    val keyboardOpen = WindowInsets.isImeVisible
    BackHandler(started) { if (selected != null) selectedName = null else started = false }
    Scaffold(containerColor = Palette.background, bottomBar = {
        if (started && !keyboardOpen) Box(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                color = Palette.surface,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, Color(0xFF343438))
            ) {
                NavigationBar(containerColor = Palette.surface, windowInsets = WindowInsets(0, 0, 0, 0)) {
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
        if (!started) Welcome(Modifier.padding(padding)) { started = true }
        else LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("CONTROLE / GITHUB", color = Mint, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(48.dp))
                Text(if (selected != null) selected.name else listOf("Seu centro de comando", "Repositórios", "Seu trabalho", "Notificações")[page], fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Modo demonstração · dados fictícios", color = Muted, fontSize = 13.sp)
            }
            if (selected != null) {
                item { TextButton(onClick = { selectedName = null }) { Text("← Voltar") } }
                item { Panel { Text(selected.description, fontSize = 18.sp); Spacer(Modifier.height(16.dp)); Text("${selected.language}  ·  ${selected.stars} estrelas  ·  ${selected.issues} issues", color = Mint) } }
                item { Section("Visão geral"); Text("Branch principal: main\nVisibilidade: público\nÚltima atividade: hoje (exemplo)", color = Muted, lineHeight = 28.sp) }
                item { WorkCard("#18", "Preparar primeira versão Android", "Issue · planejamento", Mint) }
                item { WorkCard("#12", "Adicionar tema e navegação", "Pull request · em revisão", Color(0xFFB3A0FF)) }
            } else when (page) {
                0 -> {
                    item { Panel { Text("BOM DIA, DEVELOPER", color = Mint, fontSize = 11.sp, letterSpacing = 1.sp); Spacer(Modifier.height(10.dp)); Text("Menos abas.\nMais clareza.", fontSize = 32.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text("Acompanhe o que precisa de você em um só lugar.", color = Muted) } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Metric("3", "Repositórios", Modifier.weight(1f)); Metric("13", "Issues abertas", Modifier.weight(1f)) } }
                    item { Section("Precisa da sua atenção"); WorkCard("PR #12", "Revisar navegação Android", "controlegithub · há 2 horas", Color(0xFFB3A0FF)) }
                    item { Section("Seus repositórios") }
                    items(DemoData.repositories.take(2)) { repo -> RepoCard(repo) { selectedName = repo.name } }
                }
                1 -> {
                    item { SearchRepositories(repositoryQuery, { repositoryQuery = it }) { selectedName = it } }
                }
                2 -> {
                    item { Section("Issues e pull requests") }
                    item { WorkCard("ISSUE #18", "Preparar primeira versão Android", "controlegithub · planejamento", Mint) }
                    item { WorkCard("PR #12", "Adicionar tema e navegação", "controlegithub · em revisão", Color(0xFFB3A0FF)) }
                    item { WorkCard("ISSUE #7", "Documentar padrões de projeto", "ideias_standard · documentação", Color(0xFFFFCB7D)) }
                    item { Text("Triagem e ações no GitHub estarão disponíveis após a integração da conta.", color = Muted) }
                }
                3 -> {
                    item { Section("Caixa de entrada") }
                    item { WorkCard("REVISÃO", "Uma revisão está esperando", "controlegithub · PR #12", Color(0xFFB3A0FF)) }
                    item { WorkCard("MENÇÃO", "Você foi mencionado em uma issue", "ideias_standard · issue #7", Mint) }
                    item { Text("Exemplos de notificações. Nenhuma conta está conectada.", color = Muted) }
                }
            }
        }
    }
}

@Composable
private fun Welcome(modifier: Modifier, onStart: () -> Unit) {
    Column(modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text("CONTROLE / GITHUB", color = Mint, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Column {
            Icon(Icons.Outlined.Code, contentDescription = null, tint = Mint, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(28.dp))
            Text("Seu código.\nSua visão.\nSeu controle.", fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            Text("Repositórios, issues e pull requests.\nTudo encontra seu lugar, no seu Android.", color = Muted, fontSize = 17.sp, lineHeight = 26.sp)
        }
        Column {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Text("Explorar demonstração", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(16.dp))
            Text("Prévia local · sem login ou acesso à sua conta", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SearchRepositories(query: String, onQueryChange: (String) -> Unit, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(value = query, onValueChange = onQueryChange, label = { Text("Buscar nome ou linguagem") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        val repos = DemoData.search(query)
        Text("${repos.size} repositórios · conta de exemplo", color = Muted, fontSize = 13.sp)
        repos.forEach { RepoCard(it) { onSelect(it.name) } }
        if (repos.isEmpty()) Text("Nenhum repositório encontrado. Tente outro termo.", color = Muted)
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = Palette.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun Metric(value: String, label: String, modifier: Modifier) {
    Column(modifier.background(Palette.surface, RoundedCornerShape(18.dp)).padding(18.dp)) {
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Mint)
        Text(label, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun Section(title: String) { Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)) }

@Composable
private fun RepoCard(repo: Repository, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = Palette.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("playertwo1 /", color = Muted, fontSize = 12.sp)
            Text(repo.name, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(repo.description, color = Muted, fontSize = 13.sp)
            Text("● ${repo.language}    ☆ ${repo.stars}    ◉ ${repo.issues}", color = Mint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun WorkCard(badge: String, title: String, subtitle: String, accent: Color) {
    Panel {
        Text(badge, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}
