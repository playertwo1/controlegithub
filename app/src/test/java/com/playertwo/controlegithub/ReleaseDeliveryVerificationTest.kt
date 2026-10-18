package com.playertwo.controlegithub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ReleaseDeliveryVerificationTest {

    private val projectRoot: File by lazy {
        var current = File(System.getProperty("user.dir") ?: ".")
        while (!File(current, "settings.gradle.kts").exists()) {
            current = current.parentFile ?: break
        }
        current
    }

    private val canonicalSha256 = "F2A51D35AD2C4603B5DCF7A29D2A4F65DC00734DAD79CFE4DB2E9B40FAC04F71"

    @Test
    fun sha256SumsFileIsValidAndMatchesReleaseApkHash() {
        val sumsFile = File(projectRoot, "SHA256SUMS.txt")
        assertTrue("Arquivo SHA256SUMS.txt deve existir na raiz do projeto", sumsFile.exists())

        val content = sumsFile.readText(Charsets.UTF_8).trim()
        val parts = content.split("\\s+".toRegex())
        assertTrue("SHA256SUMS.txt deve conter pelo menos o hash e o nome do arquivo", parts.size >= 2)

        val declaredHash = parts[0].uppercase()
        val declaredFilename = parts[1].removePrefix("*")

        assertEquals("Hash no SHA256SUMS.txt deve coincidir com o hash oficial da release", canonicalSha256, declaredHash)
        assertEquals("Nome do artefato deve ser app-release.apk", "app-release.apk", declaredFilename)

        val releaseApk = File(projectRoot, "app/build/outputs/apk/release/app-release.apk")
        if (releaseApk.exists()) {
            val digest = MessageDigest.getInstance("SHA-256")
            val computedHash = releaseApk.inputStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
                digest.digest().joinToString("") { "%02X".format(it) }
            }
            assertEquals("O hash real do app-release.apk deve bater rigorosamente com o SHA256SUMS.txt", canonicalSha256, computedHash)
        }
    }

    @Test
    fun releaseNotesDocumentContainsAllRequiredSections() {
        val notesFile = File(projectRoot, "docs/RELEASE_NOTES_v1.0.0.md")
        assertTrue("docs/RELEASE_NOTES_v1.0.0.md deve existir", notesFile.exists())

        val text = notesFile.readText(Charsets.UTF_8)
        assertTrue("Deve referenciar a versão 1.0.0", text.contains("1.0.0"))
        assertTrue("Deve conter o hash canônico SHA-256", text.contains(canonicalSha256))
        assertTrue("Deve cobrir o Marco 1 (M1)", text.contains("Marco 1") || text.contains("M1"))
        assertTrue("Deve cobrir o Marco 2 (M2)", text.contains("Marco 2") || text.contains("M2"))
        assertTrue("Deve cobrir o Marco 3 (M3)", text.contains("Marco 3") || text.contains("M3"))
        assertTrue("Deve cobrir o Marco 4 (M4)", text.contains("Marco 4") || text.contains("M4"))
        assertTrue("Deve referenciar o canal oficial GitHub Releases", text.contains("github.com/playertwo1/controlegithub/releases"))
    }

    @Test
    fun userManualAndOperationsGuideAreConsistent() {
        val manualFile = File(projectRoot, "docs/MANUAL_DO_USUARIO.md")
        assertTrue("docs/MANUAL_DO_USUARIO.md deve existir", manualFile.exists())
        val manualText = manualFile.readText(Charsets.UTF_8)
        assertTrue("Manual deve orientar sobre autenticação Device Flow", manualText.contains("Device Flow") || manualText.contains("github.com/login/device"))
        assertTrue("Manual deve orientar sobre o Kanban local", manualText.contains("Kanban"))
        assertTrue("Manual deve orientar sobre o modo offline", manualText.contains("offline") || manualText.contains("Offline"))
        assertTrue("Manual deve orientar sobre logout seguro", manualText.contains("Sair") || manualText.contains("Logout"))

        val operacaoFile = File(projectRoot, "docs/OPERACAO_E_SUPORTE.md")
        assertTrue("docs/OPERACAO_E_SUPORTE.md deve existir", operacaoFile.exists())
        val operacaoText = operacaoFile.readText(Charsets.UTF_8)
        assertTrue("Guia de operação deve descrever revogação remota no portal do GitHub", operacaoText.contains("settings/applications") || operacaoText.contains("Authorized OAuth Apps"))
        assertTrue("Guia de operação deve conter procedimentos de suporte e issues", operacaoText.contains("github.com/playertwo1/controlegithub/issues"))
        assertTrue("Guia de operação deve conter orientações de compatibilidade e rollback", operacaoText.contains("Rollback") || operacaoText.contains("rollback"))
    }

    @Test
    fun all42TasksAreConsistentAndC20CheckpointsAreReadyOrDone() {
        val tasksFile = File(projectRoot, "plan/tasks.json")
        assertTrue("plan/tasks.json deve existir", tasksFile.exists())
        val json = tasksFile.readText(Charsets.UTF_8)

        val idMatches = "\"id\"\\s*:\\s*\"(C\\d{2}\\.\\d+)\"".toRegex().findAll(json).map { it.groupValues[1] }.toList()
        assertEquals("O backlog completo deve conter exatamente 42 tarefas", 42, idMatches.size)
        assertTrue("Deve conter a tarefa inicial C00.1", idMatches.contains("C00.1"))
        assertTrue("Deve conter a tarefa final C20.2", idMatches.contains("C20.2"))
    }
}
