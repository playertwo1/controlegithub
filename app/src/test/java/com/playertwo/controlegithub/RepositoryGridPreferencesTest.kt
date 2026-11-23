package com.playertwo.controlegithub

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RepositoryGridPreferencesTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun normalizesValidAndInvalidColumnCounts() {
        assertEquals(1, normalizeRepositoryGridColumns(null))
        assertEquals(1, normalizeRepositoryGridColumns(-1))
        assertEquals(1, normalizeRepositoryGridColumns(0))
        assertEquals(1, normalizeRepositoryGridColumns(1))
        assertEquals(2, normalizeRepositoryGridColumns(2))
        assertEquals(3, normalizeRepositoryGridColumns(3))
        assertEquals(1, normalizeRepositoryGridColumns(4))
        assertEquals(1, normalizeRepositoryGridColumns(99))
    }

    @Test
    fun defaultsToOneAndPersistsSelectionOnDiskAcrossInstances() = runBlocking {
        val file = File(tempFolder.newFolder("grid-prefs"), "app_preferences.preferences_pb")

        val job1 = SupervisorJob()
        val scope1 = CoroutineScope(job1 + Dispatchers.IO)
        try {
            val store1 = PreferenceDataStoreFactory.create(scope = scope1) { file }
            val prefs1 = RepositoryGridPreferences(store1)
            assertEquals(1, prefs1.columns.first())

            prefs1.setColumns(3)
            assertEquals(3, prefs1.columns.first())
        } finally {
            job1.cancelAndJoin()
        }

        val job2 = SupervisorJob()
        val scope2 = CoroutineScope(job2 + Dispatchers.IO)
        try {
            val store2 = PreferenceDataStoreFactory.create(scope = scope2) { file }
            val prefs2 = RepositoryGridPreferences(store2)
            assertEquals(3, prefs2.columns.first())
        } finally {
            job2.cancelAndJoin()
        }
    }

    @Test
    fun updatesSelectionAcrossValidAndInvalidValues() = runBlocking {
        val store = InMemoryPreferencesDataStore()
        val prefs = RepositoryGridPreferences(store)

        assertEquals(1, prefs.columns.first())
        prefs.setColumns(2)
        assertEquals(2, prefs.columns.first())
        prefs.setColumns(3)
        assertEquals(3, prefs.columns.first())
        prefs.setColumns(1)
        assertEquals(1, prefs.columns.first())
        prefs.setColumns(99)
        assertEquals(1, prefs.columns.first())
    }

    @Test
    fun outOfRangeStoredValueFallsBackToOne() = runBlocking {
        val file = File(tempFolder.newFolder("grid-invalid"), "app_preferences.preferences_pb")
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            store.edit { it[repositoryGridColumnsKey] = 7 }
            val prefs = RepositoryGridPreferences(store)
            assertEquals(1, prefs.columns.first())
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun readIoExceptionFallsBackToDefaultOne() = runBlocking {
        val failingStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("disk error") }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                throw IOException("disk error")
        }
        val prefs = RepositoryGridPreferences(failingStore)
        assertEquals(1, prefs.columns.first())
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
