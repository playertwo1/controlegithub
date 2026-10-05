package com.playertwo.controlegithub

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubActionsMappingTest {
    @Test fun selectedWorkflowRunUsesTheFilteredEndpointAndAcceptsItsRun() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val body = """{"total_count":1,"workflow_runs":[{"id":9001,"workflow_id":42,"name":"CI Android","run_number":17,"event":"push","status":"completed","conclusion":"failure"}]}"""
        val responseSent = java.util.concurrent.CountDownLatch(1)
        val serverThread = thread(isDaemon = true) {
            server.accept().use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                val request = reader.readLine()
                var line = reader.readLine()
                while (!line.isNullOrEmpty()) line = reader.readLine()
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(bytes)
                    flush()
                }
                assertEquals("GET /repos/acme/Mobile/actions/workflows/42/runs?per_page=50 HTTP/1.1", request)
                responseSent.countDown()
            }
        }
        try {
            val repository = GitHubRepository(1, "Mobile", "acme/Mobile", "acme", true, null, "Kotlin", 0)
            val result = GitHubActionsRepository(
                GitHubHttpClient(URI.create("http://127.0.0.1:${server.localPort}/")),
                "fixture-token",
                repository
            ).runs(42).loadNext()

            assertTrue(result is GitHubPageResult.Loaded)
            assertEquals(17L, (result as GitHubPageResult.Loaded).items.single().runNumber)
            assertTrue(responseSent.await(1, java.util.concurrent.TimeUnit.SECONDS))
            serverThread.join(1_000)
        } finally {
            server.close()
        }
    }

    @Test fun incompleteAndUnknownStatesNeverBecomeSuccess() {
        val runs = parseGitHubWorkflowRuns(
            """{"workflow_runs":[{"id":9,"workflow_id":3,"run_number":4,"event":"push","status":"future_state","conclusion":null,"created_at":"bad"}]}"""
        )
        val run = runs.single()

        assertEquals("Aguardando conclusão", workflowConclusionLabel(run.conclusion))
        assertEquals("Conclusão não informada", workflowConclusionLabel(null, "completed"))
        assertEquals("Aguardando conclusão", workflowConclusionLabel(null, "in_progress"))
        assertEquals("Sucesso", workflowConclusionLabel("success"))
        assertEquals("Conclusão desconhecida: future", workflowConclusionLabel("future"))
        assertEquals("Estado desconhecido: future_state", workflowRunStatusLabel(run.status))
        assertEquals(null, run.createdAt)
    }

    @Test fun jobsCannotBeAttributedToAnotherWorkflowRun() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        try {
            val body = """{"total_count":1,"jobs":[{"id":5,"run_id":9002,"name":"wrong run","status":"completed","conclusion":"success"}]}"""
            val responseSent = java.util.concurrent.CountDownLatch(1)
            val serverThread = thread(isDaemon = true) {
                server.accept().use { socket ->
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                    reader.readLine()
                    var line = reader.readLine()
                    while (!line.isNullOrEmpty()) line = reader.readLine()
                    val bytes = body.toByteArray(StandardCharsets.UTF_8)
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes)
                        flush()
                    }
                    responseSent.countDown()
                }
            }
            val repository = GitHubRepository(1, "Mobile", "acme/Mobile", "acme", true, null, "Kotlin", 0)
            val actions = GitHubActionsRepository(
                GitHubHttpClient(URI.create("http://127.0.0.1:${server.localPort}/")),
                "fixture-token",
                repository
            )

            val result = actions.jobs(9001).loadNext()
            responseSent.await()
            serverThread.join(1_000)

            assertTrue(result is GitHubPageResult.Failed)
            assertEquals(
                "Não foi possível interpretar a resposta do GitHub.",
                (result as GitHubPageResult.Failed).message
            )
        } finally {
            server.close()
        }
    }
}
