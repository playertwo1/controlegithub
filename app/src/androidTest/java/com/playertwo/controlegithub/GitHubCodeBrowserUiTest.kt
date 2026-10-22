package com.playertwo.controlegithub

import android.util.Base64
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import org.junit.Rule
import org.junit.Test

class GitHubCodeBrowserUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun opensFolderFileWithLineNumber() {
        CodeMock().use { server ->
            compose.setContent {
                GitHubCodeBrowserScreen(
                    client = server.client(),
                    accessToken = "fixture-token",
                    owner = "acme",
                    name = "app",
                    defaultBranch = "main",
                    onBack = {},
                    onSessionExpired = {}
                )
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("code_entry_App.kt").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("code_entry_App.kt").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("code_line_1").fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText("fun", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }
}

private class CodeMock : AutoCloseable {
    private val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    private val encoded = Base64.encodeToString("fun main()".toByteArray(), Base64.NO_WRAP)
    private val worker = Thread {
        while (!server.isClosed) runCatching {
            server.accept().use { socket ->
                val input = socket.getInputStream()
                val first = readLine(input)
                while (readLine(input).isNotEmpty()) Unit
                val body = when {
                    first.contains("/branches") || first.contains("/tags") -> """[{"name":"main"}]"""
                    first.contains("App.kt") -> """{"name":"App.kt","path":"App.kt","type":"file","size":11,"encoding":"base64","content":"$encoded"}"""
                    else -> """[{"name":"App.kt","path":"App.kt","type":"file","size":11}]"""
                }
                val bytes = body.toByteArray()
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(bytes)
                    flush()
                }
            }
        }
    }.apply { isDaemon = true; start() }

    fun client() = GitHubHttpClient(URI.create("http://127.0.0.1:${server.localPort}/"), 5_000, 5_000)

    override fun close() {
        server.close()
        worker.join(1_000)
    }
}

private fun readLine(input: InputStream): String {
    val out = ByteArrayOutputStream()
    while (true) {
        val value = input.read()
        if (value == -1 || value == '\n'.code) break
        if (value != '\r'.code) out.write(value)
    }
    return out.toString(StandardCharsets.US_ASCII.name())
}
