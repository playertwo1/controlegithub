package com.playertwo.controlegithub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.core.view.WindowCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.URI
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

/** Maestro-only notification flow backed by in-process fixture data. */
class NotificationsFixtureActivity : ComponentActivity() {
    private var fixtureApi: NotificationsFixtureApi? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        if (intent?.data?.let { it.scheme == "controlegithub" && it.host == "debug" && it.path == "/notifications" } != true) {
            finish()
            return
        }

        val markReadStatus = intent.data?.getQueryParameter("markReadStatus")?.toIntOrNull() ?: 205
        val api = NotificationsFixtureApi(markReadStatus).also { fixtureApi = it }
        val session = GitHubSession(
            GitHubUser("fixture-user", "https://github.com/fixture-user"),
            SessionCredentials("debug-fixture-token")
        )
        setContent {
            ControleTheme(AppThemeMode.LIGHT) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().statusBarsPadding()) {
                        GitHubNotificationsScreen(
                            modifier = Modifier.fillMaxSize().padding(top = 52.dp),
                            client = GitHubHttpClient(api.baseUri),
                            session = session,
                            sessionRestoring = false,
                            onSessionExpired = {},
                            onAppearance = {}
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                "MODO DE TESTE - DADOS FICTICIOS",
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        fixtureApi?.close()
        fixtureApi = null
        super.onDestroy()
    }
}

private class NotificationsFixtureApi(private val markReadStatus: Int) : AutoCloseable {
    private val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    val baseUri: URI = URI.create("http://127.0.0.1:${server.localPort}/")
    private val worker = Thread({ serve() }, "notifications-debug-fixture").apply {
        isDaemon = true
        start()
    }

    private fun serve() {
        while (!server.isClosed) {
            try {
                server.accept().use { socket -> respond(socket) }
            } catch (_: SocketException) {
                break
            }
        }
    }

    private fun respond(socket: java.net.Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
        val request = reader.readLine().orEmpty()
        while (reader.readLine()?.isNotEmpty() == true) Unit
        val path = request.substringAfter(' ').substringBefore(" HTTP")
        val (status, body) = when {
            request.startsWith("GET ") && path == "/notifications?all=true&per_page=50" -> 200 to notificationFixture()
            request.startsWith("PATCH /notifications/threads/9001 ") -> markReadStatus to ""
            request.startsWith("GET ") && path == "/repos/fixture-org/app/issues/12" -> 200 to ISSUE_FIXTURE
            request.startsWith("GET ") && path == "/repos/fixture-org/app/issues/12/comments?per_page=50" -> 200 to "[]"
            else -> 404 to "{}"
        }
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val output = socket.getOutputStream()
        val reason = when (status) {
            200 -> "OK"
            403 -> "Forbidden"
            205 -> "Reset Content"
            503 -> "Service Unavailable"
            else -> "Not Found"
        }
        output.write("HTTP/1.1 $status $reason\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write("Content-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }

    override fun close() {
        server.close()
        worker.join(1_000)
    }

    private fun notificationFixture(): String {
        val repository = JSONObject()
            .put("id", 1).put("name", "app").put("full_name", "fixture-org/app")
            .put("owner", JSONObject().put("login", "fixture-org"))
            .put("private", false).put("description", JSONObject.NULL)
            .put("language", JSONObject.NULL).put("stargazers_count", 0)
        val notification = JSONObject()
            .put("id", "9001").put("unread", true).put("reason", "assign")
            .put("updated_at", "2026-10-01T12:00:00Z")
            .put("repository", repository)
            .put("subject", JSONObject()
                .put("title", "Fixture issue de notificacao")
                .put("type", "Issue")
                .put("url", "https://api.github.com/repos/fixture-org/app/issues/12"))
        return JSONArray().put(notification).toString()
    }

    private companion object {
        const val ISSUE_FIXTURE = """{"number":12,"title":"Fixture issue da origem","state":"open","html_url":"https://github.com/fixture-org/app/issues/12","labels":[],"updated_at":"2026-10-01T12:00:00Z","body":"Conteudo ficticio para teste visual.","user":{"login":"fixture-author"}}"""
    }
}
