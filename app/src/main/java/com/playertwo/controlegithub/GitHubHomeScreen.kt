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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onAppearance: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var refreshVersion by remember(session.accessToken) { mutableIntStateOf(0) }
    var profile by remember(session.accessToken) { mutableStateOf<GitHubAccountProfile?>(null) }
    var loading by remember(session.accessToken) { mutableStateOf(false) }
    var error by remember(session.accessToken) { mutableStateOf<String?>(null) }
    var unauthorized by remember(session.accessToken) { mutableStateOf(false) }

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
