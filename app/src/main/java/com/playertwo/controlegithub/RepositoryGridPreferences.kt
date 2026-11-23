package com.playertwo.controlegithub

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal const val DEFAULT_REPOSITORY_GRID_COLUMNS = 1
internal val REPOSITORY_GRID_COLUMN_OPTIONS = listOf(1, 2, 3)

internal val repositoryGridColumnsKey = intPreferencesKey("repository_grid_columns")

internal fun normalizeRepositoryGridColumns(value: Int?): Int =
    if (value != null && value in REPOSITORY_GRID_COLUMN_OPTIONS) value else DEFAULT_REPOSITORY_GRID_COLUMNS

internal class RepositoryGridPreferences(
    private val dataStore: DataStore<Preferences>
) {
    constructor(context: Context) : this(context.applicationContext.preferencesDataStore)

    val columns: Flow<Int> = dataStore.data
        .map { preferences -> normalizeRepositoryGridColumns(preferences[repositoryGridColumnsKey]) }
        .catch { error ->
            if (error is IOException) emit(DEFAULT_REPOSITORY_GRID_COLUMNS) else throw error
        }

    suspend fun setColumns(columns: Int) {
        val normalized = normalizeRepositoryGridColumns(columns)
        dataStore.edit { preferences ->
            preferences[repositoryGridColumnsKey] = normalized
        }
    }
}
