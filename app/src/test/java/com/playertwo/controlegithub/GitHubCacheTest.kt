package com.playertwo.controlegithub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubCacheTest {

    @Test
    fun rejectsSensitiveOAuthAndAuthRoutes() {
        val payload = """[{"id": 1, "name": "repo"}]"""

        assertFalse(isCacheableResource("/login/oauth/authorize", payload))
        assertFalse(isCacheableResource("/login/oauth/access_token", payload))
        assertFalse(isCacheableResource("/oauth/token", payload))
        assertFalse(isCacheableResource("/user/authorizations", payload))
        assertFalse(isCacheableResource("/authorizations/123", payload))
        assertFalse(isCacheableResource("/tokens", payload))
        assertFalse(isCacheableResource("/login/device/code", payload))
    }

    @Test
    fun rejectsActionsRawLogs() {
        val payload = "raw log content"

        assertFalse(isCacheableResource("/repos/octocat/hello-world/actions/jobs/12345/logs", payload))
        assertFalse(isCacheableResource("/repos/octocat/hello-world/actions/runs/67890/logs", payload))
    }

    @Test
    fun rejectsPayloadsContainingCredentialsOrSecrets() {
        val tokenPayload = """{"access_token": "gho_secret123", "token_type": "bearer"}"""
        val refreshPayload = """{"refresh_token": "ghr_secret456"}"""
        val secretPayload = """{"client_secret": "my_super_secret"}"""

        assertFalse(isCacheableResource("/user/repos", tokenPayload))
        assertFalse(isCacheableResource("/user/repos", refreshPayload))
        assertFalse(isCacheableResource("/user/repos", secretPayload))
    }

    @Test
    fun rejectsPayloadExceedingSizeLimit() {
        val hugePayload = "x".repeat(GitHubCacheStore.MAX_PAYLOAD_BYTES + 1)
        assertFalse(isCacheableResource("/user/repos", hugePayload))
    }

    @Test
    fun acceptsStandardReadRoutes() {
        val normalPayload = """[{"id": 42, "name": "controlegithub"}]"""

        assertTrue(isCacheableResource("/user/repos?per_page=100", normalPayload))
        assertTrue(isCacheableResource("/search/issues?q=state%3Aopen", normalPayload))
        assertTrue(isCacheableResource("/notifications?all=true", normalPayload))
        assertTrue(isCacheableResource("/repos/octocat/hello-world/pulls/1", normalPayload))
    }

    @Test
    fun cachedResponseModelIntegrity() {
        val item = CachedResponse(
            accountLogin = "octocat",
            resourceKey = "/user/repos",
            payload = "[]",
            statusCode = 200,
            createdAtMillis = 1000L,
            expiresAtMillis = 2000L
        )

        assertEquals("octocat", item.accountLogin)
        assertEquals("/user/repos", item.resourceKey)
        assertEquals("[]", item.payload)
        assertEquals(200, item.statusCode)
        assertEquals(1000L, item.createdAtMillis)
        assertEquals(2000L, item.expiresAtMillis)
    }
}
