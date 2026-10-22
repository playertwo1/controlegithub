package com.playertwo.controlegithub

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

internal const val MENTION_CACHE_MILLIS = 120_000L

internal enum class MarkdownShortcut { BOLD, ITALIC, CODE, LINK, LIST, QUOTE }

internal data class MarkdownEdit(val text: String, val selection: IntRange)

internal sealed interface CollaboratorLookup {
    data class Found(val logins: List<String>) : CollaboratorLookup
    data object Unavailable : CollaboratorLookup
    data object Unauthorized : CollaboratorLookup
}

internal data class MentionSuggestions(val logins: List<String>, val loading: Boolean)

internal interface MarkdownDraftStore {
    suspend fun load(key: String): String?
    suspend fun save(key: String, text: String)
    suspend fun delete(key: String)
    suspend fun clearAccount(account: String)
}

internal val LocalMarkdownDraftStore = staticCompositionLocalOf<MarkdownDraftStore?> { null }

internal fun markdownDraftKey(account: String, repository: String, item: String): String =
    "${account.lowercase(Locale.ROOT)}|${repository.lowercase(Locale.ROOT)}|$item"

internal fun applyMarkdownShortcut(text: String, start: Int, end: Int, shortcut: MarkdownShortcut): MarkdownEdit {
    val safeStart = start.coerceIn(0, text.length)
    val safeEnd = end.coerceIn(safeStart, text.length)
    return when (shortcut) {
        MarkdownShortcut.BOLD -> wrap(text, safeStart, safeEnd, "**")
        MarkdownShortcut.ITALIC -> wrap(text, safeStart, safeEnd, "*")
        MarkdownShortcut.CODE -> wrap(text, safeStart, safeEnd, "`")
        MarkdownShortcut.LINK -> link(text, safeStart, safeEnd)
        MarkdownShortcut.LIST -> prefixLines(text, safeStart, safeEnd, "- ")
        MarkdownShortcut.QUOTE -> prefixLines(text, safeStart, safeEnd, "> ")
    }
}

internal fun mentionQuery(text: String, cursor: Int): String? {
    if (cursor !in 0..text.length) return null
    val head = text.substring(0, cursor)
    val at = head.lastIndexOf('@')
    if (at < 0) return null
    if (at > 0 && (head[at - 1].isLetterOrDigit() || head[at - 1] == '-')) return null
    val query = head.substring(at + 1)
    if (query.any { !it.isLetterOrDigit() && it != '-' }) return null
    return query
}

internal fun insertMention(text: String, cursor: Int, login: String): MarkdownEdit {
    val safeCursor = cursor.coerceIn(0, text.length)
    val at = text.substring(0, safeCursor).lastIndexOf('@')
    if (at < 0) return MarkdownEdit(text, safeCursor..safeCursor)
    val next = text.substring(0, at) + "@$login " + text.substring(safeCursor)
    val caret = at + login.length + 2
    return MarkdownEdit(next, caret..caret)
}

internal fun filterMentionLogins(logins: List<String>, query: String): List<String> {
    val seen = linkedSetOf<String>()
    return logins.filter { login ->
        login.isNotBlank() && login.startsWith(query, ignoreCase = true) && seen.add(login.lowercase(Locale.ROOT))
    }.take(8)
}

internal fun parseCollaboratorLogins(json: String): List<String> {
    val array = JSONArray(json)
    return mergeMentionLogins(
        buildList {
            for (index in 0 until array.length()) {
                add(array.optJSONObject(index)?.optString("login").orEmpty())
            }
        },
        emptyList()
    )
}

internal fun mergeMentionLogins(collaborators: List<String>?, participants: List<String>): List<String> {
    val combined = if (collaborators == null) participants else collaborators + participants
    val seen = linkedSetOf<String>()
    return combined.map { it.trim() }.filter { login ->
        login.isNotEmpty() && seen.add(login.lowercase(Locale.ROOT))
    }
}

internal fun githubCollaboratorsPath(repository: String): String {
    val parts = repository.split('/')
    require(parts.size == 2 && parts.all(String::isNotBlank))
    return "/repos/${encodeSegment(parts[0])}/${encodeSegment(parts[1])}/collaborators?per_page=100"
}

internal fun lookupCollaborators(
    client: GitHubHttpClient,
    accessToken: String,
    repository: String
): CollaboratorLookup {
    val result = try {
        client.get(githubCollaboratorsPath(repository), accessToken).execute()
    } catch (_: IllegalArgumentException) {
        return CollaboratorLookup.Unavailable
    }
    return when (result) {
        is GitHubHttpResult.Success -> if (result.statusCode == 200) {
            runCatching { CollaboratorLookup.Found(parseCollaboratorLogins(result.body)) }
                .getOrDefault(CollaboratorLookup.Unavailable)
        } else {
            CollaboratorLookup.Unavailable
        }
        is GitHubHttpResult.Failure -> if (result.error == GitHubHttpError.UNAUTHORIZED) {
            CollaboratorLookup.Unauthorized
        } else {
            CollaboratorLookup.Unavailable
        }
    }
}

internal class MentionLoginCache(private val now: () -> Long = System::currentTimeMillis) {
    private val values = HashMap<String, Pair<Long, List<String>>>()

    fun get(key: String): List<String>? {
        val entry = values[key] ?: return null
        if (now() - entry.first >= MENTION_CACHE_MILLIS) {
            values.remove(key)
            return null
        }
        return entry.second
    }

    fun put(key: String, logins: List<String>) {
        values[key] = now() to logins
    }
}

internal val mentionLoginCache = MentionLoginCache()

internal class MemoryMarkdownDraftStore : MarkdownDraftStore {
    private val values = mutableMapOf<String, String>()

    override suspend fun load(key: String): String? = values[key]

    override suspend fun save(key: String, text: String) {
        values[key] = text
    }

    override suspend fun delete(key: String) {
        values.remove(key)
    }

    override suspend fun clearAccount(account: String) {
        val prefix = "${account.lowercase(Locale.ROOT)}|"
        values.keys.filter { it.startsWith(prefix) }.toList().forEach(values::remove)
    }
}

internal class DataStoreMarkdownDraftStore(context: Context) : MarkdownDraftStore {
    private val dataStore = context.applicationContext.preferencesDataStore

    override suspend fun load(key: String): String? =
        dataStore.data.first()[stringPreferencesKey(preferenceName(key))]

    override suspend fun save(key: String, text: String) {
        dataStore.edit { preferences ->
            val name = preferenceName(key)
            preferences[stringPreferencesKey(name)] = text
            val index = indexKey(accountOf(key))
            preferences[index] = preferences[index].orEmpty() + name
        }
    }

    override suspend fun delete(key: String) {
        dataStore.edit { preferences ->
            val name = preferenceName(key)
            preferences.remove(stringPreferencesKey(name))
            val index = indexKey(accountOf(key))
            preferences[index] = preferences[index].orEmpty() - name
        }
    }

    override suspend fun clearAccount(account: String) {
        dataStore.edit { preferences ->
            val index = indexKey(account)
            preferences[index].orEmpty().forEach { preferences.remove(stringPreferencesKey(it)) }
            preferences.remove(index)
        }
    }

    private fun accountOf(key: String) = key.substringBefore('|')

    private fun preferenceName(key: String) =
        "mdraft_" + URLEncoder.encode(key, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun indexKey(account: String) =
        stringSetPreferencesKey("mdraft_index_${account.lowercase(Locale.ROOT)}")
}

@Composable
internal fun rememberMentionLogins(
    client: GitHubHttpClient,
    accessToken: String,
    accountLogin: String,
    repository: String,
    participants: List<String>,
    enabled: Boolean,
    onUnauthorized: () -> Unit
): MentionSuggestions {
    var collaborators by remember(accountLogin, repository, accessToken) { mutableStateOf<List<String>?>(null) }
    var resolved by remember(accountLogin, repository, accessToken) { mutableStateOf(false) }
    LaunchedEffect(accountLogin, repository, accessToken, enabled) {
        if (!enabled || resolved || repository.isBlank()) return@LaunchedEffect
        val cacheKey = "$accountLogin|${repository.lowercase(Locale.ROOT)}"
        mentionLoginCache.get(cacheKey)?.let { cached ->
            collaborators = cached
            resolved = true
            return@LaunchedEffect
        }
        when (val lookup = withContext(Dispatchers.IO) { lookupCollaborators(client, accessToken, repository) }) {
            is CollaboratorLookup.Found -> {
                collaborators = lookup.logins
                mentionLoginCache.put(cacheKey, lookup.logins)
            }
            CollaboratorLookup.Unavailable -> collaborators = null
            CollaboratorLookup.Unauthorized -> onUnauthorized()
        }
        resolved = true
    }
    return MentionSuggestions(mergeMentionLogins(if (resolved) collaborators else null, participants), !resolved && enabled)
}

@Composable
internal fun MarkdownEditor(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    fieldTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minLines: Int = 4,
    maxLines: Int = 10,
    isError: Boolean = false,
    supportingText: String? = null,
    placeholder: String? = null,
    draftKey: String? = null,
    mentionLogins: List<String> = emptyList(),
    mentionsLoading: Boolean = false
) {
    val store = LocalMarkdownDraftStore.current
    val scope = rememberCoroutineScope()
    var writing by remember(draftKey) { mutableStateOf(true) }
    var field by remember(draftKey) { mutableStateOf(TextFieldValue(value)) }
    var restored by remember(draftKey) { mutableStateOf(store == null || draftKey == null) }
    var userEdited by remember(draftKey) { mutableStateOf(false) }
    var persistJob by remember(draftKey) { mutableStateOf<Job?>(null) }

    fun persist(text: String) {
        if (store == null || draftKey == null) return
        persistJob?.cancel()
        persistJob = scope.launch {
            if (text.isEmpty()) store.delete(draftKey) else store.save(draftKey, text)
        }
    }

    LaunchedEffect(draftKey) {
        if (store != null && draftKey != null && !userEdited) {
            val saved = store.load(draftKey)
            if (!saved.isNullOrEmpty()) {
                field = TextFieldValue(saved, TextRange(saved.length))
                if (saved != value) onValueChange(saved)
            }
        }
        restored = true
    }
    LaunchedEffect(value, restored) {
        if (!restored || value == field.text) return@LaunchedEffect
        val clearing = value.isEmpty() && field.text.isNotEmpty()
        field = TextFieldValue(value, TextRange(value.length))
        if (clearing) persist("")
    }

    fun edit(next: TextFieldValue) {
        userEdited = true
        field = next
        onValueChange(next.text)
        persist(next.text)
    }

    val query = mentionQuery(field.text, field.selection.end)
    val suggestions = if (query == null) emptyList() else filterMentionLogins(mentionLogins, query)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = { writing = true },
                modifier = Modifier.heightIn(min = 48.dp).testTag("${fieldTag}_write")
            ) { Text("Escrever") }
            TextButton(
                onClick = { writing = false },
                modifier = Modifier.heightIn(min = 48.dp).testTag("${fieldTag}_preview")
            ) { Text("Prévia") }
            MarkdownShortcut.entries.forEach { shortcut ->
                TextButton(
                    onClick = {
                        val edited = applyMarkdownShortcut(field.text, field.selection.start, field.selection.end, shortcut)
                        val range = if (field.selection.collapsed) TextRange(edited.selection.first)
                        else TextRange(edited.selection.first, edited.selection.last + 1)
                        edit(TextFieldValue(edited.text, range))
                        writing = true
                    },
                    enabled = enabled && writing,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(shortcut.label) }
            }
        }
        if (writing) {
            OutlinedTextField(
                value = field,
                onValueChange = ::edit,
                label = { Text(label) },
                placeholder = placeholder?.let { { Text(it) } },
                isError = isError,
                supportingText = supportingText?.let { error -> { Text(error, color = MaterialTheme.colorScheme.error) } },
                enabled = enabled,
                minLines = minLines,
                maxLines = maxLines,
                modifier = Modifier.fillMaxWidth().testTag(fieldTag)
            )
            if (query != null) {
                if (suggestions.isEmpty() && !mentionsLoading) {
                    Text("Nenhuma sugestão de usuário.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                suggestions.forEach { login ->
                    TextButton(
                        onClick = {
                            val edited = insertMention(field.text, field.selection.end, login)
                            edit(TextFieldValue(edited.text, TextRange(edited.selection.first)))
                        },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("markdown_mention_$login")
                    ) { Text("@$login") }
                }
            }
        } else {
            GitHubMarkdownContent(field.text.ifBlank { "Sem texto para pré-visualizar." }, modifier = Modifier.testTag("${fieldTag}_rendered"))
        }
    }
}

private val MarkdownShortcut.label: String
    get() = when (this) {
        MarkdownShortcut.BOLD -> "Negrito"
        MarkdownShortcut.ITALIC -> "Itálico"
        MarkdownShortcut.CODE -> "Código"
        MarkdownShortcut.LINK -> "Link"
        MarkdownShortcut.LIST -> "Lista"
        MarkdownShortcut.QUOTE -> "Citação"
    }

private fun wrap(text: String, start: Int, end: Int, marker: String): MarkdownEdit {
    val selected = text.substring(start, end)
    val next = text.substring(0, start) + marker + selected + marker + text.substring(end)
    val inner = start + marker.length
    return MarkdownEdit(next, if (selected.isEmpty()) inner..inner else inner..(inner + selected.length - 1))
}

private fun link(text: String, start: Int, end: Int): MarkdownEdit {
    val label = text.substring(start, end).ifEmpty { "texto" }
    val url = "https://"
    val next = text.substring(0, start) + "[$label]($url)" + text.substring(end)
    val urlStart = start + label.length + 3
    return MarkdownEdit(next, urlStart..(urlStart + url.length - 1))
}

private fun prefixLines(text: String, start: Int, end: Int, prefix: String): MarkdownEdit {
    val first = lineStart(text, start.coerceIn(0, text.length))
    val endPos = end.coerceIn(start, text.length)
    val lastPos = (if (endPos > start) endPos - 1 else start).coerceIn(0, text.lastIndex.coerceAtLeast(0))
    val last = lineStart(text, lastPos)
    val out = StringBuilder()
    var index = 0
    var shiftBeforeCaret = 0
    var selectedEnd = first
    while (index <= text.length) {
        val newline = text.indexOf('\n', index).let { if (it < 0) text.length else it }
        if (index in first..last && !text.startsWith(prefix, index)) {
            out.append(prefix)
            if (index <= start) shiftBeforeCaret += prefix.length
        }
        out.append(text, index, newline)
        if (index in first..last) selectedEnd = out.length
        if (newline == text.length) break
        out.append('\n')
        index = newline + 1
    }
    val caret = (start + shiftBeforeCaret).coerceIn(0, out.length)
    val selection = if (endPos > start) first..(selectedEnd - 1).coerceAtLeast(first) else caret..caret
    return MarkdownEdit(out.toString(), selection)
}

private fun lineStart(text: String, index: Int): Int {
    if (index <= 0) return 0
    val found = text.lastIndexOf('\n', index - 1)
    return if (found < 0) 0 else found + 1
}

private fun encodeSegment(value: String) =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
