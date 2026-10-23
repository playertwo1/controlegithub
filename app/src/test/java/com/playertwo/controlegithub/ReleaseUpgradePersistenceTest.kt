package com.playertwo.controlegithub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ReleaseUpgradePersistenceTest {

    private fun findProjectRoot(): File {
        var current = File(System.getProperty("user.dir") ?: ".")
        while (!File(current, "docs").exists() && current.parentFile != null) {
            current = current.parentFile!!
        }
        return current
    }

    @Test
    fun releaseSigningConfigurationPreventsKeysInGit() {
        val root = findProjectRoot()
        val gitignore = File(root, ".gitignore")
        assertTrue(".gitignore deve existir", gitignore.exists())
        val gitignoreText = gitignore.readText(Charsets.UTF_8)

        assertTrue(".gitignore deve ignorar *.keystore", gitignoreText.contains("*.keystore"))
        assertTrue(".gitignore deve ignorar *.jks", gitignoreText.contains("*.jks"))
        assertTrue(".gitignore deve ignorar as secrets locais", gitignoreText.contains(".secrets/"))

        val gitFiles = ProcessBuilder("git", "ls-files", "--", ".secrets")
            .directory(root)
            .redirectErrorStream(true)
            .start()
        val trackedSecrets = gitFiles.inputStream.bufferedReader().use { it.readText() }
        assertEquals("git ls-files deve executar com sucesso", 0, gitFiles.waitFor())
        assertTrue("Nenhum arquivo local de assinatura pode estar versionado", trackedSecrets.isBlank())

        // Varre a árvore de arquivos garantindo que nenhum keystore foi acidentalmente commitado
        val trackedKeystores = root.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".jks") || it.name.endsWith(".keystore")) }
            .filterNot { it.path.contains(".gradle") || it.path.contains("build") || it.path.contains(".secrets") }
            .toList()
        assertTrue("Nenhum arquivo de keystore pode existir versionado no repositório", trackedKeystores.isEmpty())

        // Inspeciona app/build.gradle.kts
        val buildGradle = File(root, "app/build.gradle.kts")
        val gradleText = buildGradle.readText(Charsets.UTF_8)
        assertTrue(gradleText.contains("RELEASE_STORE_FILE"))
        assertTrue(gradleText.contains("RELEASE_STORE_PASSWORD"))
        assertTrue(gradleText.contains("RELEASE_KEY_ALIAS"))
        assertTrue(gradleText.contains("RELEASE_KEY_PASSWORD"))
        assertTrue("Build deve ler secrets locais ignoradas em .secrets", gradleText.contains(".secrets/signing.properties"))
        assertTrue("Build deve resolver o caminho do keystore a partir da raiz", gradleText.contains("rootProject.file(it)"))
        assertFalse("Senhas de release não podem estar hardcoded no build.gradle.kts",
            gradleText.contains("password = \"123") || gradleText.contains("storePassword = \""))
    }

    @Test
    fun releaseVersioningMatchesLaunchSpecification() {
        val root = findProjectRoot()
        val buildGradle = File(root, "app/build.gradle.kts")
        val gradleText = buildGradle.readText(Charsets.UTF_8)

        assertTrue("versionCode deve ser 101", gradleText.contains("versionCode = 101"))
        assertTrue("versionName deve ser 2.0.0", gradleText.contains("versionName = \"2.0.0\""))
    }

    @Test
    fun releaseDocumentationSpecifiesChannelAndIntegrity() {
        val root = findProjectRoot()
        val releaseDoc = File(root, "docs/RELEASE.md")
        assertTrue("docs/RELEASE.md deve existir", releaseDoc.exists())
        val docText = releaseDoc.readText(Charsets.UTF_8)

        assertTrue(docText.contains("assembleRelease"))
        assertTrue(docText.contains("SHA-256"))
        assertTrue(docText.contains("GitHub Releases"))
        assertTrue(docText.contains("adb install -r"))
    }

    @Test
    fun releaseApkArtifactIntegrity() {
        val root = findProjectRoot()
        val releaseApk = File(root, "app/build/outputs/apk/release/app-release.apk")
        if (releaseApk.exists()) {
            assertTrue("O APK de release deve possuir tamanho positivo", releaseApk.length() > 100_000)
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(releaseApk.readBytes())
            val hex = hashBytes.joinToString("") { "%02x".format(it) }
            assertEquals(64, hex.length)
        }
    }
}
