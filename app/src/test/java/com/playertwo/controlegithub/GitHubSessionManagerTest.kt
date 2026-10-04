package com.playertwo.controlegithub

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubSessionManagerTest {
    @Test
    fun restoresValidSessionAfterProfileCheck() = runBlocking {
        val store = FakeStore(SessionCredentials("access", "refresh", 200_000, 500_000))
        var refreshCalls = 0
        val manager = manager(store, now = 100_000, refresh = { refreshCalls++; error("must not refresh") })

        val result = manager.restore()

        assertTrue(result is SessionRestoreResult.Restored)
        assertEquals("octocat", (result as SessionRestoreResult.Restored).session.user.login)
        assertEquals(0, refreshCalls)
        assertEquals("access", store.value?.accessToken)
    }

    @Test
    fun refreshesExpiringSessionAndPersistsRotatedPairBeforeLoadingProfile() = runBlocking {
        val store = FakeStore(SessionCredentials("old-access", "old-refresh", 150_000, 900_000))
        val rotated = SessionCredentials("new-access", "new-refresh", 500_000, 1_000_000)
        var profileToken = ""
        val manager = manager(store, now = 100_000, refresh = {
            OAuthTokenResult.Success(rotated)
        }, load = { token -> profileToken = token; success() })

        val result = manager.restore()

        assertTrue(result is SessionRestoreResult.Restored)
        assertEquals("new-access", profileToken)
        assertEquals(rotated, store.value)
    }

    @Test
    fun unauthorizedProfileRefreshesOnceThenClearsAfterSecondUnauthorized() = runBlocking {
        val store = FakeStore(SessionCredentials("old-access", "old-refresh", 800_000, 900_000))
        var profileCalls = 0
        var refreshCalls = 0
        val manager = manager(
            store,
            now = 100_000,
            refresh = { refreshCalls++; OAuthTokenResult.Success(SessionCredentials("new-access", "new-refresh", 800_000, 900_000)) },
            load = { profileCalls++; unauthorized() }
        )

        val result = manager.restore()

        assertEquals(SessionRestoreResult.SignedOut, result)
        assertEquals(1, refreshCalls)
        assertEquals(2, profileCalls)
        assertNull(store.value)
        assertEquals(1, store.clearCalls)
    }

    @Test
    fun invalidRefreshRequiresNewSignInAndClearsStoredCredentials() = runBlocking {
        val store = FakeStore(SessionCredentials("old-access", "expired-refresh", 800_000, 900_000))
        val manager = manager(
            store,
            now = 100_000,
            refresh = { OAuthTokenResult.Failure(BAD_REFRESH_TOKEN) },
            load = { unauthorized() }
        )

        assertEquals(SessionRestoreResult.SignedOut, manager.restore())
        assertNull(store.value)
        assertEquals(1, store.clearCalls)
    }

    @Test
    fun reportsStorageFailureWhenInvalidSessionCannotBeCleared() = runBlocking {
        val store = FakeStore(SessionCredentials("old-access", "expired-refresh", 800_000, 900_000), failClear = true)
        val manager = manager(store, now = 100_000, load = { unauthorized() })

        assertEquals(SessionRestoreResult.CleanupFailed, manager.restore())
        assertEquals("old-access", store.value?.accessToken)
    }

    @Test
    fun cancellationDuringRefreshPropagatesAndPreservesStoredCredentials() = runBlocking {
        val stored = SessionCredentials("access", "refresh", 120_000, 900_000)
        val store = FakeStore(stored)
        val manager = manager(store, now = 100_000, refresh = { throw CancellationException("cancelled") })

        var cancelled = false
        try {
            manager.restore()
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertEquals(stored, store.value)
        assertEquals(0, store.clearCalls)
    }

    @Test
    fun networkFailureKeepsEncryptedSessionForRetry() = runBlocking {
        val stored = SessionCredentials("access", "refresh", 800_000, 900_000)
        val store = FakeStore(stored)
        val manager = manager(store, now = 100_000, load = { GitHubHttpResult.Failure(GitHubHttpError.NETWORK) })

        assertEquals(SessionRestoreResult.Retry, manager.restore())
        assertEquals(stored, store.value)
        assertEquals(0, store.clearCalls)
    }

    @Test
    fun logoutRemovesSavedCredentials() = runBlocking {
        val store = FakeStore(SessionCredentials("access", "refresh"))
        manager(store).logout()
        assertNull(store.value)
        assertEquals(1, store.clearCalls)
    }

    private fun manager(
        store: FakeStore,
        now: Long = 100_000,
        refresh: suspend (String) -> OAuthTokenResult = { OAuthTokenResult.Failure(BAD_REFRESH_TOKEN) },
        load: suspend (String) -> GitHubHttpResult = { success() }
    ) = GitHubSessionManager(store, refresh, load, { now }) { GitHubUser("octocat", "https://github.com/octocat") }

    private class FakeStore(var value: SessionCredentials?, private val failClear: Boolean = false) : SessionCredentialStore {
        var clearCalls = 0
        override suspend fun save(credentials: SessionCredentials) { value = credentials }
        override suspend fun load() = value
        override suspend fun clear() {
            clearCalls++
            if (failClear) error("storage unavailable")
            value = null
        }
    }

    private fun success() = GitHubHttpResult.Success(
        200,
        """{"login":"octocat","html_url":"https://github.com/octocat"}"""
    )

    private fun unauthorized() = GitHubHttpResult.Failure(GitHubHttpError.UNAUTHORIZED, 401)
}
