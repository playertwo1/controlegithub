package com.playertwo.controlegithub

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.Locale

internal enum class KanbanColumn(val label: String) {
    TODO("A fazer"),
    IN_PROGRESS("Em andamento"),
    IN_REVIEW("Em revisão"),
    DONE("Concluído")
}

internal interface KanbanStore {
    suspend fun load(accountLogin: String): Map<String, KanbanColumn>
    suspend fun setColumn(accountLogin: String, issueIdentity: String, column: KanbanColumn): Map<String, KanbanColumn>
    suspend fun clear(accountLogin: String)
}

internal class DataStoreKanbanStore(context: Context) : KanbanStore {
    private val dataStore = context.applicationContext.preferencesDataStore

    override suspend fun load(accountLogin: String): Map<String, KanbanColumn> {
        val rawSet = dataStore.data.first()[key(accountLogin)].orEmpty()
        return parseEntries(rawSet)
    }

    override suspend fun setColumn(
        accountLogin: String,
        issueIdentity: String,
        column: KanbanColumn
    ): Map<String, KanbanColumn> {
        require(issueIdentity.isNotBlank())
        var result = emptyMap<String, KanbanColumn>()
        dataStore.edit { preferences ->
            val prefKey = key(accountLogin)
            val currentEntries = preferences[prefKey].orEmpty().toMutableSet()
            currentEntries.removeAll { it.substringBeforeLast('=', "").equals(issueIdentity, ignoreCase = true) }
            currentEntries.add("${issueIdentity.lowercase(Locale.ROOT)}=${column.name}")
            preferences[prefKey] = currentEntries
            result = parseEntries(currentEntries)
        }
        return result
    }

    override suspend fun clear(accountLogin: String) {
        dataStore.edit { preferences ->
            preferences.remove(key(accountLogin))
        }
    }

    private fun key(accountLogin: String) =
        stringSetPreferencesKey("kanban_issue_columns_${accountLogin.lowercase(Locale.ROOT)}")

    companion object {
        internal fun parseEntries(entries: Set<String>): Map<String, KanbanColumn> {
            val map = mutableMapOf<String, KanbanColumn>()
            for (entry in entries) {
                val separatorIndex = entry.lastIndexOf('=')
                if (separatorIndex <= 0 || separatorIndex == entry.length - 1) continue
                val identity = entry.substring(0, separatorIndex).trim().lowercase(Locale.ROOT)
                val rawCol = entry.substring(separatorIndex + 1).trim()
                val col = try {
                    KanbanColumn.valueOf(rawCol)
                } catch (_: IllegalArgumentException) {
                    null
                }
                if (identity.isNotBlank() && col != null) {
                    map[identity] = col
                }
            }
            return map
        }
    }
}

internal fun resolveKanbanColumn(
    issue: GitHubIssue,
    mapping: Map<String, KanbanColumn>
): KanbanColumn {
    return mapping[issue.identity.lowercase(Locale.ROOT)]
        ?: if (issue.state == GitHubIssueState.CLOSED) KanbanColumn.DONE else KanbanColumn.TODO
}

internal suspend fun clearKanbanBeforeLogout(
    accountLogin: String,
    store: KanbanStore
): Boolean = try {
    store.clear(accountLogin)
    true
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    false
}
