package com.playertwo.controlegithub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ReleaseEndToEndAcceptanceTest {

    private fun findProjectRoot(): File {
        var current = File(System.getProperty("user.dir") ?: ".")
        while (!File(current, "plan").exists() && current.parentFile != null) {
            current = current.parentFile!!
        }
        return current
    }

    @Test
    fun allPrecedingCheckpointsFromC00ToC19AreCompleted() {
        val root = findProjectRoot()
        val planFile = File(root, "plan/tasks.json")
        assertTrue("plan/tasks.json deve existir", planFile.exists())
        val planText = planFile.readText(Charsets.UTF_8)

        // Verificação estrutural simples usando regex para evitar dependência de biblioteca externa
        val taskRegex = Regex("""\{[^{}]*"id":\s*"([^"]+)"[^{}]*"status":\s*"([^"]+)"[^{}]*"evidence":\s*(null|"[^"]+")[^{}]*\}""", RegexOption.DOT_MATCHES_ALL)
        val matches = taskRegex.findAll(planText).toList()

        assertTrue("Deve haver pelo menos 40 tarefas mapeadas no plano", matches.size >= 40)

        for (match in matches) {
            val id = match.groupValues[1]
            val status = match.groupValues[2]
            val evidenceRaw = match.groupValues[3]

            // v2.0 (C21+) tem aceite próprio; aqui só o escopo da v1.0
            if (id == "C20.1" || id == "C20.2" || id.substring(1, 3).toInt() > 20) continue

            assertEquals("Tarefa $id deve estar com status DONE", "DONE", status)
            assertTrue("Tarefa $id deve possuir arquivo de evidência", evidenceRaw.startsWith("\""))
            val evidencePath = evidenceRaw.trim('"')
            val evidenceFile = File(root, evidencePath)
            assertTrue("Arquivo de evidência $evidencePath para tarefa $id deve existir no disco", evidenceFile.exists())
        }
    }

    @Test
    fun releaseApkArtifactMatchesOfficialSha256Checksum() {
        val root = findProjectRoot()
        val releaseApk = File(root, "app/build/outputs/apk/release/app-release.apk")
        assertTrue("app-release.apk deve existir", releaseApk.exists())
        assertTrue("APK de release deve possuir tamanho positivo (> 5MB)", releaseApk.length() > 5_000_000)

        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(releaseApk.readBytes())
        val hex = hashBytes.joinToString("") { "%02x".format(it) }

        val expectedHash = "1feff9f088f7b6136dd9525a6aa20ad5ea0c57bcc458e9dc703fabf62dbeeb45"
        assertEquals("O hash SHA-256 do APK de release deve corresponder ao auditado", expectedHash, hex.lowercase())
    }

    @Test
    fun releaseDocumentationAndManifestConsistency() {
        val root = findProjectRoot()
        val releaseDoc = File(root, "docs/RELEASE.md")
        assertTrue("docs/RELEASE.md deve existir", releaseDoc.exists())
        val docText = releaseDoc.readText(Charsets.UTF_8)

        val expectedHash = "1FEFF9F088F7B6136DD9525A6AA20AD5EA0C57BCC458E9DC703FABF62DBEEB45"
        assertTrue("docs/RELEASE.md deve documentar o hash SHA-256 oficial", docText.contains(expectedHash))

        val buildGradle = File(root, "app/build.gradle.kts")
        val gradleText = buildGradle.readText(Charsets.UTF_8)
        assertTrue("versionCode deve ser 102", gradleText.contains("versionCode = 102"))
        assertTrue("versionName deve ser 2.1.0", gradleText.contains("versionName = \"2.1.0\""))

        val manifest = File(root, "app/src/main/AndroidManifest.xml")
        val manifestText = manifest.readText(Charsets.UTF_8)
        assertTrue("allowBackup deve ser false", manifestText.contains("android:allowBackup=\"false\""))
    }
}
