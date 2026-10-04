package com.playertwo.controlegithub

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GitHubRepositoriesEmptyStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyApiResponseShowsEmptyStateAndRefreshRequestsAgain() {
        EmptyRepositoriesApi().use { api ->
            val session = GitHubSession(
                GitHubUser("fixture-user", "https://github.com/fixture-user"),
                SessionCredentials("instrumentation-fixture-token")
            )
            compose.setContent {
                MaterialTheme {
                    GitHubRepositoriesScreen(
                        modifier = Modifier,
                        client = GitHubHttpClient(baseUrl = api.baseUrl),
                        session = session,
                        sessionRestoring = false,
                        sessionStorageError = false,
                        sessionExpired = false,
                        onConnected = { true },
                        onSessionExpired = {},
                        onLogout = {},
                        onAppearance = {}
                    )
                }
            }

            compose.waitUntil(10_000) {
                api.requestCount.get() == 1 &&
                    compose.onAllNodesWithText("Nenhum repositório acessível")
                        .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Nenhum repositório acessível").assertIsDisplayed()
            compose.onNodeWithText("Atualizar").performClick()

            compose.waitUntil(10_000) { api.requestCount.get() == 2 }
            compose.onNodeWithText("Nenhum repositório acessível").assertIsDisplayed()
            assertEquals(2, api.requestCount.get())
        }
    }

    private class EmptyRepositoriesApi : AutoCloseable {
        private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val baseUrl = URI.create("http://127.0.0.1:${server.localPort}/")
        val requestCount = AtomicInteger()

        private val acceptThread = Thread {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        val reader = BufferedReader(
                            InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
                        )
                        var line = reader.readLine()
                        while (!line.isNullOrEmpty()) line = reader.readLine()
                        requestCount.incrementAndGet()

                        val body = "[]".toByteArray(StandardCharsets.UTF_8)
                        val headers = (
                            "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/json\r\n" +
                                "Content-Length: ${body.size}\r\n" +
                                "Connection: close\r\n\r\n"
                            ).toByteArray(StandardCharsets.UTF_8)
                        socket.getOutputStream().apply {
                            write(headers)
                            write(body)
                            flush()
                        }
                    }
                } catch (_: java.net.SocketException) { }
            }
        }.apply {
            name = "empty-repositories-fixture"
            isDaemon = true
            start()
        }

        override fun close() {
            server.close()
            acceptThread.join(1_000)
        }
    }
}
