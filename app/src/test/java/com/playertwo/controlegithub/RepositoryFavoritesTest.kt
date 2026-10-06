package com.playertwo.controlegithub

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryFavoritesTest {
    @Test fun logoutCannotProceedWhenFavoriteCleanupFails() = runBlocking {
        val store = MemoryFavoritesStore(setOf(101L)).apply { failClear = true }
        var logoutCalled = false

        val result = logoutWithFavoriteCleanup("fixture-user", store, Mutex()) { logoutCalled = true }

        assertEquals(FavoriteLogoutResult.FAVORITES_NOT_CLEARED, result)
        assertFalse(logoutCalled)
        assertEquals(setOf(101L), store.ids)
    }

    @Test fun logoutFailureRestoresFavoritesAndKeepsFailureVisible() = runBlocking {
        val store = MemoryFavoritesStore(setOf(101L, 202L))

        val result = logoutWithFavoriteCleanup("fixture-user", store, Mutex()) {
            throw IOException("fixture logout failure")
        }

        assertEquals(FavoriteLogoutResult.SESSION_NOT_CLOSED, result)
        assertEquals(setOf(101L, 202L), store.ids)
    }

    @Test fun successfulLogoutLeavesNoFavorites() = runBlocking {
        val store = MemoryFavoritesStore(setOf(101L))

        val result = logoutWithFavoriteCleanup("fixture-user", store, Mutex()) {}

        assertEquals(FavoriteLogoutResult.LOGGED_OUT, result)
        assertTrue(store.ids.isEmpty())
    }

    @Test fun logoutWaitsForFavoriteWriteAndClearsItsCommittedValue() = runBlocking {
        val store = MemoryFavoritesStore(emptySet())
        val operations = Mutex()
        val writeGate = CompletableDeferred<Unit>()
        val writeStarted = CompletableDeferred<Unit>()
        val write = launch {
            operations.withLock {
                writeStarted.complete(Unit)
                writeGate.await()
                store.setFavorite("fixture-user", 303L, true)
            }
        }
        writeStarted.await()

        val logout = async {
            logoutWithFavoriteCleanup("fixture-user", store, operations) {}
        }
        assertFalse(logout.isCompleted)
        writeGate.complete(Unit)
        write.join()

        assertEquals(FavoriteLogoutResult.LOGGED_OUT, logout.await())
        assertTrue(store.ids.isEmpty())
    }

    @Test fun logoutCancellationRestoresFavoritesBeforePropagating() = runBlocking {
        val store = MemoryFavoritesStore(setOf(404L))
        var cancelled = false

        try {
            logoutWithFavoriteCleanup("fixture-user", store, Mutex()) {
                throw CancellationException("fixture cancellation")
            }
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertEquals(setOf(404L), store.ids)
    }

    private class MemoryFavoritesStore(var ids: Set<Long>) : RepositoryFavoritesStore {
        var failClear = false

        override suspend fun load(accountLogin: String) = ids
        override suspend fun setFavorite(accountLogin: String, repositoryId: Long, favorite: Boolean): Set<Long> {
            ids = ids.toMutableSet().apply { if (favorite) add(repositoryId) else remove(repositoryId) }
            return ids
        }
        override suspend fun clear(accountLogin: String) {
            if (failClear) throw IOException("fixture clear failure")
            ids = emptySet()
        }
    }
}
