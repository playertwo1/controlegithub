package com.playertwo.controlegithub

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryFavoritesDataStoreTest {
    @Test fun favoriteIdsPersistPerNormalizedLoginAndClearOnlyThatAccount() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = DataStoreRepositoryFavoritesStore(context)
        val login = "C14-fixture-${System.nanoTime()}"
        val otherLogin = "$login-other"
        try {
            assertEquals(setOf(101L), store.setFavorite(login, 101L, true))
            assertEquals(setOf(101L), DataStoreRepositoryFavoritesStore(context).load(login.lowercase()))
            assertEquals(emptySet<Long>(), store.load(otherLogin))

            store.clear(login)

            assertEquals(emptySet<Long>(), store.load(login))
            assertEquals(emptySet<Long>(), store.load(otherLogin))
        } finally {
            store.clear(login)
            store.clear(otherLogin)
        }
    }
}
