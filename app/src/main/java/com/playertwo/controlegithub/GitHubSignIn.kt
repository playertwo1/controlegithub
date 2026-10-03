package com.playertwo.controlegithub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class GitHubSession(val user: GitHubUser, val accessToken: String)

@Composable
internal fun GitHubSignIn(onConnected: (GitHubSession) -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var job by remember { mutableStateOf<Job?>(null) }
    var authorization by remember { mutableStateOf<DeviceAuthorization?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = {
                if (BuildConfig.GITHUB_OAUTH_CLIENT_ID.isBlank()) {
                    message = "Integração GitHub indisponível · app OAuth não configurado"
                } else {
                    job?.cancel()
                    authorization = null
                    message = "Aguardando autorização no GitHub…"
                    connecting = true
                    job = scope.launch {
                        try {
                            val client = GitHubOAuthClient(BuildConfig.GITHUB_OAUTH_CLIENT_ID)
                            val session = authorizeDeviceFlow(
                                requestCode = { client.requestDeviceCode().await() },
                                poll = { deviceCode, interval -> client.poll(deviceCode, interval).await() },
                                loadProfile = { token ->
                                    when (val response = client.user(token).await()) {
                                        is GitHubHttpResult.Success -> if (response.statusCode == 200) {
                                            runCatching { GitHubUser.parse(response.body) }.getOrNull()
                                        } else null
                                        is GitHubHttpResult.Failure -> null
                                    }
                                },
                                onAuthorization = {
                                    authorization = it
                                    message = "Insira o código no GitHub para continuar."
                                },
                                nowMillis = android.os.SystemClock::elapsedRealtime
                            )
                            authorization = null
                            message = null
                            onConnected(session)
                        } catch (error: SignInException) {
                            authorization = null
                            message = when (error.reason) {
                                SignInFailure.DENIED -> "Autorização negada. Você pode tentar novamente."
                                SignInFailure.EXPIRED -> "O código expirou. Solicite outro para tentar novamente."
                                SignInFailure.INVALID_PROFILE -> "Não foi possível confirmar o perfil no GitHub."
                                SignInFailure.FAILED -> "O GitHub não concluiu a autorização. Tente novamente."
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            authorization = null
                            message = "Não foi possível conectar ao GitHub. Verifique a rede e tente novamente."
                        } finally {
                            connecting = false
                        }
                    }
                }
            },
            enabled = !connecting,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Conectar ao GitHub", fontWeight = FontWeight.Bold)
        }

        authorization?.let { device ->
            Text("Código de autorização", color = Muted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(device.userCode, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(device.userCode)) }) { Text("Copiar") }
            }
            OutlinedButton(onClick = { uriHandler.openUri(device.verificationUri) }, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir GitHub")
            }
        }
        if (connecting) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                CircularProgressIndicator(strokeWidth = 2.dp)
                Text(message.orEmpty(), color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            }
            OutlinedButton(
                onClick = {
                    job?.cancel()
                    job = null
                    connecting = false
                    authorization = null
                    message = "Conexão cancelada. Nenhuma sessão foi criada."
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cancelar autorização") }
        } else {
            message?.let { Text(it, color = Muted, fontSize = 12.sp) }
        }
        Spacer(Modifier.height(2.dp))
    }
}
