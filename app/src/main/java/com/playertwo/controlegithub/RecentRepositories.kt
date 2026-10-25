package com.playertwo.controlegithub

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

internal const val MAX_RECENT_REPOSITORIES = 10

internal data class RecentRepositoryEntry(
    val id: Long,
    val owner: String,
    val name: String,
    val fullName: String,
    val isPrivate: Boolean = false
) {
    fun toGitHubRepository(): GitHubRepository = GitHubRepository(
        id = id,
        name = name,
        fullName = fullName,
        owner = owner,
        isPrivate = isPrivate,
        description = null,
        language = null,
        stars = 0
    )
}

internal fun GitHubRepository.toRecentRepositoryEntry(): RecentRepositoryEntry =
    RecentRepositoryEntry(
        id = id,
        owner = owner,
        name = name,
        fullName = fullName,
        isPrivate = isPrivate
    )

internal interface RecentRepositoriesStore {
    suspend fun load(accountLogin: String): List<RecentRepositoryEntry>
    suspend fun recordVisit(accountLogin: String, entry: RecentRepositoryEntry): List<RecentRepositoryEntry>
    suspend fun clear(accountLogin: String)
}

internal val LocalRecentRepositoriesStore = staticCompositionLocalOf<RecentRepositoriesStore?> { null }

internal fun isValidRecentRepositoryEntry(entry: RecentRepositoryEntry): Boolean {
    if (entry.id < 0L) return false
    if (entry.owner.isBlank() || entry.name.isBlank() || entry.fullName.isBlank()) return false
    if (entry.owner.contains('/') || entry.name.contains('/')) return false
    if (entry.owner.any(Char::isWhitespace) || entry.name.any(Char::isWhitespace) || entry.fullName.any(Char::isWhitespace)) {
        return false
    }
    return entry.fullName.equals("${entry.owner}/${entry.name}", ignoreCase = true)
}

internal fun applyRecentVisit(
    current: List<RecentRepositoryEntry>,
    entry: RecentRepositoryEntry,
    maxItems: Int = MAX_RECENT_REPOSITORIES
): List<RecentRepositoryEntry> {
    if (maxItems <= 0 || !isValidRecentRepositoryEntry(entry)) return current.take(maxItems.coerceAtLeast(0))

    val matchingExisting = current.firstOrNull { existing ->
        existing.fullName.equals(entry.fullName, ignoreCase = true) ||
            (existing.id > 0L && entry.id > 0L && existing.id == entry.id)
    }
    val resolvedId = when {
        entry.id > 0L -> entry.id
        matchingExisting != null && matchingExisting.id > 0L -> matchingExisting.id
        else -> UNRESOLVED_GITHUB_REPOSITORY_ID
    }
    val normalizedEntry = entry.copy(id = resolvedId)
    val remaining = current.filterNot { existing ->
        existing.fullName.equals(normalizedEntry.fullName, ignoreCase = true) ||
            (existing.id > 0L && normalizedEntry.id > 0L && existing.id == normalizedEntry.id)
    }
    return (listOf(normalizedEntry) + remaining).take(maxItems)
}

internal fun encodeRecentRepositories(entries: List<RecentRepositoryEntry>): String {
    val array = JSONArray()
    entries.filter(::isValidRecentRepositoryEntry).take(MAX_RECENT_REPOSITORIES).forEach { entry ->
        array.put(
            JSONObject()
                .put("id", entry.id)
                .put("owner", entry.owner)
                .put("name", entry.name)
                .put("full_name", entry.fullName)
                .put("private", entry.isPrivate)
        )
    }
    return array.toString()
}

internal fun decodeRecentRepositories(raw: String?): List<RecentRepositoryEntry> {
    if (raw.isNullOrBlank()) return emptyList()
    val array = try {
        JSONArray(raw)
    } catch (_: Exception) {
        return emptyList()
    }
    var result = emptyList<RecentRepositoryEntry>()
    for (index in (array.length() - 1) downTo 0) {
        val item = array.optJSONObject(index) ?: continue
        val id = (item.opt("id") as? Number)?.toLong() ?: UNRESOLVED_GITHUB_REPOSITORY_ID
        val owner = (item.opt("owner") as? String)?.trim().orEmpty()
        val name = (item.opt("name") as? String)?.trim().orEmpty()
        val fullName = (item.opt("full_name") as? String)?.trim().orEmpty()
        val isPrivate = item.opt("private") as? Boolean ?: false
        val entry = RecentRepositoryEntry(
            id = id,
            owner = owner,
            name = name,
            fullName = fullName,
            isPrivate = isPrivate
        )
        if (isValidRecentRepositoryEntry(entry)) {
            result = applyRecentVisit(result, entry, MAX_RECENT_REPOSITORIES)
        }
    }
    return result
}

internal class DataStoreRecentRepositoriesStore(context: Context) : RecentRepositoriesStore {
    private val dataStore = context.applicationContext.preferencesDataStore

    override suspend fun load(accountLogin: String): List<RecentRepositoryEntry> {
        val normalizedAccount = accountLogin.trim().lowercase(Locale.ROOT)
        if (normalizedAccount.isBlank()) return emptyList()
        val raw = dataStore.data.first()[key(normalizedAccount)]
        return decodeRecentRepositories(raw)
    }

    override suspend fun recordVisit(
        accountLogin: String,
        entry: RecentRepositoryEntry
    ): List<RecentRepositoryEntry> {
        val normalizedAccount = accountLogin.trim().lowercase(Locale.ROOT)
        if (normalizedAccount.isBlank()) return emptyList()
        if (!isValidRecentRepositoryEntry(entry)) return load(normalizedAccount)
        var updated = emptyList<RecentRepositoryEntry>()
        dataStore.edit { preferences ->
            val preferenceKey = key(normalizedAccount)
            val current = decodeRecentRepositories(preferences[preferenceKey])
            updated = applyRecentVisit(current, entry, MAX_RECENT_REPOSITORIES)
            preferences[preferenceKey] = encodeRecentRepositories(updated)
        }
        return updated
    }

    override suspend fun clear(accountLogin: String) {
        val normalizedAccount = accountLogin.trim().lowercase(Locale.ROOT)
        if (normalizedAccount.isBlank()) return
        dataStore.edit { preferences ->
            preferences.remove(key(normalizedAccount))
        }
    }

    private fun key(normalizedAccountLogin: String) =
        stringPreferencesKey("recent_repositories_$normalizedAccountLogin")
}

internal class InMemoryRecentRepositoriesStore(
    initialByAccount: Map<String, List<RecentRepositoryEntry>> = emptyMap()
) : RecentRepositoriesStore {
    private val mutex = Mutex()
    private val entriesByAccount = initialByAccount.entries.associate { (login, entries) ->
        login.trim().lowercase(Locale.ROOT) to entries.foldRight(emptyList<RecentRepositoryEntry>()) { item, acc ->
            applyRecentVisit(acc, item, MAX_RECENT_REPOSITORIES)
        }
    }.toMutableMap()

    override suspend fun load(accountLogin: String): List<RecentRepositoryEntry> = mutex.withLock {
        val key = accountLogin.trim().lowercase(Locale.ROOT)
        if (key.isBlank()) emptyList() else entriesByAccount[key].orEmpty()
    }

    override suspend fun recordVisit(
        accountLogin: String,
        entry: RecentRepositoryEntry
    ): List<RecentRepositoryEntry> = mutex.withLock {
        val key = accountLogin.trim().lowercase(Locale.ROOT)
        if (key.isBlank()) return@withLock emptyList()
        val current = entriesByAccount[key].orEmpty()
        val updated = applyRecentVisit(current, entry, MAX_RECENT_REPOSITORIES)
        entriesByAccount[key] = updated
        updated
    }

    override suspend fun clear(accountLogin: String) = mutex.withLock {
        val key = accountLogin.trim().lowercase(Locale.ROOT)
        if (key.isNotBlank()) {
            entriesByAccount.remove(key)
        }
    }
}

internal suspend fun clearRecentRepositoriesBeforeLogout(
    accountLogin: String,
    store: RecentRepositoriesStore
): Boolean = try {
    store.clear(accountLogin)
    true
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    false
}
