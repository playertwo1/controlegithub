package com.playertwo.controlegithub

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface RepositoryFavoritesStore {
    suspend fun load(accountLogin: String): Set<Long>
    suspend fun setFavorite(accountLogin: String, repositoryId: Long, favorite: Boolean): Set<Long>
    suspend fun clear(accountLogin: String)
}

internal class DataStoreRepositoryFavoritesStore(context: Context) : RepositoryFavoritesStore {
    private val dataStore = context.applicationContext.preferencesDataStore

    override suspend fun load(accountLogin: String): Set<Long> =
        dataStore.data.first()[key(accountLogin)].orEmpty().mapNotNull(String::toLongOrNull)
            .filter { it > 0 }.toSet()

    override suspend fun setFavorite(accountLogin: String, repositoryId: Long, favorite: Boolean): Set<Long> {
        require(repositoryId > 0)
        var updated = emptySet<Long>()
        dataStore.edit { preferences ->
            val preferenceKey = key(accountLogin)
            val ids = preferences[preferenceKey].orEmpty().toMutableSet()
            if (favorite) ids += repositoryId.toString() else ids -= repositoryId.toString()
            preferences[preferenceKey] = ids
            updated = ids.mapNotNull(String::toLongOrNull).filter { it > 0 }.toSet()
        }
        return updated
    }

    override suspend fun clear(accountLogin: String) {
        dataStore.edit { it.remove(key(accountLogin)) }
    }

    private fun key(accountLogin: String) =
        stringSetPreferencesKey("favorite_repository_ids_${accountLogin.lowercase(Locale.ROOT)}")
}

internal suspend fun clearFavoritesBeforeLogout(
    accountLogin: String,
    store: RepositoryFavoritesStore
): Boolean = try {
    store.clear(accountLogin)
    true
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    false
}

internal enum class FavoriteLogoutResult { LOGGED_OUT, FAVORITES_NOT_CLEARED, SESSION_NOT_CLOSED, RESTORE_FAILED }

internal suspend fun logoutWithFavoriteCleanup(
    accountLogin: String,
    store: RepositoryFavoritesStore,
    operations: Mutex,
    logout: suspend () -> Unit
): FavoriteLogoutResult = operations.withLock {
    withContext(NonCancellable) {
        val previousFavorites = try {
            store.load(accountLogin)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@withContext FavoriteLogoutResult.FAVORITES_NOT_CLEARED
        }
        if (!clearFavoritesBeforeLogout(accountLogin, store)) {
            return@withContext FavoriteLogoutResult.FAVORITES_NOT_CLEARED
        }

        try {
            logout()
            FavoriteLogoutResult.LOGGED_OUT
        } catch (cancelled: CancellationException) {
            try {
                previousFavorites.forEach { store.setFavorite(accountLogin, it, true) }
            } catch (_: Exception) { }
            throw cancelled
        } catch (_: Exception) {
            try {
                previousFavorites.forEach { store.setFavorite(accountLogin, it, true) }
                FavoriteLogoutResult.SESSION_NOT_CLOSED
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                FavoriteLogoutResult.RESTORE_FAILED
            }
        }
    }
}
