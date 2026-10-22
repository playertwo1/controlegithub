package com.playertwo.controlegithub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SecurityAndPrivacyAuditTest {

    private fun findProjectRoot(): File {
        var current = File(System.getProperty("user.dir") ?: ".")
        while (!File(current, "PRIVACY.md").exists() && current.parentFile != null) {
            current = current.parentFile!!
        }
        return current
    }

    @Test
    fun privacyPolicyMatchesPermissionsAndDeclaredArchitecture() {
        val root = findProjectRoot()
        val privacyFile = File(root, "PRIVACY.md")
        assertTrue("PRIVACY.md deve existir na raiz", privacyFile.exists())
        val privacyContent = privacyFile.readText(Charsets.UTF_8)

        // Verifica princípios essenciais declarados
        assertTrue(privacyContent.contains("local-first", ignoreCase = true))
        assertTrue(privacyContent.contains("Sem servidores intermediários"))
        assertTrue(privacyContent.contains("https://api.github.com"))
        assertTrue(privacyContent.contains("AES-256"))
        assertTrue(privacyContent.contains("AndroidKeyStore"))
        assertTrue(privacyContent.contains("android:allowBackup=\"false\""))

        // Verifica mapeamento das permissões
        assertTrue(privacyContent.contains("INTERNET"))
        assertTrue(privacyContent.contains("ACCESS_NETWORK_STATE"))
        assertTrue(privacyContent.contains("POST_NOTIFICATIONS"))
        assertTrue(privacyContent.contains("REQUEST_INSTALL_PACKAGES"))
        assertTrue(privacyContent.contains("FOREGROUND_SERVICE_DATA_SYNC"))
        assertTrue(privacyContent.contains("sem encaminhar o token"))

        // Verifica ausência explícita de permissões invasivas
        assertTrue(privacyContent.contains("Localização geográfica"))
        assertTrue(privacyContent.contains("Contatos"))
        assertTrue(privacyContent.contains("Câmera"))
    }

    @Test
    fun manifestDeclaresOnlyMinimalPermissionsAndDisablesBackup() {
        val root = findProjectRoot()
        val manifestFile = File(root, "app/src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml deve existir", manifestFile.exists())
        val manifestContent = manifestFile.readText(Charsets.UTF_8)

        // Extrai permissões declaradas
        val permissionRegex = Regex("""<uses-permission\s+android:name="([^"]+)"""")
        val declaredPermissions = permissionRegex.findAll(manifestContent).map { it.groupValues[1] }.toSet()

        val expectedPermissions = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "android.permission.REQUEST_INSTALL_PACKAGES"
        )
        assertEquals("Manifesto deve declarar somente permissões explicadas na política de privacidade", expectedPermissions, declaredPermissions)

        // Verifica proteções contra backup e extração
        assertTrue(manifestContent.contains("android:allowBackup=\"false\""))
        assertTrue(manifestContent.contains("android:fullBackupContent=\"false\""))
        assertTrue(manifestContent.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
    }

    @Test
    fun dataExtractionRulesExcludeAllSensitiveStorageDomains() {
        val root = findProjectRoot()
        val rulesFile = File(root, "app/src/main/res/xml/data_extraction_rules.xml")
        assertTrue("data_extraction_rules.xml deve existir", rulesFile.exists())
        val rulesContent = rulesFile.readText(Charsets.UTF_8)

        val requiredDomains = listOf("root", "file", "database", "sharedpref", "external")
        for (domain in requiredDomains) {
            assertTrue("data_extraction_rules deve excluir domínio $domain em cloud-backup",
                rulesContent.contains("<exclude domain=\"$domain\" path=\".\" />"))
        }
    }

    @Test
    fun licenseIsPresentAndApprovedMit() {
        val root = findProjectRoot()
        val licenseFile = File(root, "LICENSE")
        assertTrue("Arquivo LICENSE deve existir na raiz", licenseFile.exists())
        val licenseText = licenseFile.readText(Charsets.UTF_8)

        assertTrue(licenseText.startsWith("MIT License"))
        assertTrue(licenseText.contains("Copyright (c) 2026 ControleGitHub Contributors"))
        assertTrue(licenseText.contains("Permission is hereby granted, free of charge"))
    }

    @Test
    fun securityPolicyMatchesControlsAndGuidance() {
        val root = findProjectRoot()
        val securityFile = File(root, "SECURITY.md")
        assertTrue("SECURITY.md deve existir na raiz", securityFile.exists())
        val securityText = securityFile.readText(Charsets.UTF_8)

        assertTrue(securityText.contains("AndroidKeyStore"))
        assertTrue(securityText.contains("AES-256-GCM"))
        assertTrue(securityText.contains("android:allowBackup=\"false\""))
        assertTrue(securityText.contains("Security Advisories"))
    }

    @Test
    fun sensitiveEndpointsAndTokensAreBlockedFromCache() {
        val samplePayload = """{"id": 1, "name": "sample"}"""
        // Rejeita rotas de autenticação
        assertFalse(isCacheableResource("/login/oauth/authorize", samplePayload))
        assertFalse(isCacheableResource("/login/oauth/access_token", samplePayload))
        assertFalse(isCacheableResource("/oauth/token", samplePayload))
        assertFalse(isCacheableResource("/user/authorizations", samplePayload))
        assertFalse(isCacheableResource("/tokens", samplePayload))
        assertFalse(isCacheableResource("/login/device/code", samplePayload))

        // Rejeita logs
        assertFalse(isCacheableResource("/repos/owner/repo/actions/jobs/123/logs", samplePayload))

        // Rejeita payloads com tokens
        assertFalse(isCacheableResource("/user/repos", """{"access_token": "ghp_1234567890"}"""))
        assertFalse(isCacheableResource("/user/repos", """{"refresh_token": "ghr_abcdefghij"}"""))
        assertFalse(isCacheableResource("/user/repos", """{"client_secret": "sec_9876543210"}"""))

        // Aceita consultas normais de leitura
        assertTrue(isCacheableResource("/user/repos?per_page=30", samplePayload))
        assertTrue(isCacheableResource("/repos/owner/repo/issues", samplePayload))
    }
}
