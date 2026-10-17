package com.playertwo.controlegithub

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.time.Instant
import java.util.Locale

internal enum class GitHubIssueScope(val queryValue: String, val label: String) {
    ASSIGNED("assigned", "Atribuídas a mim"),
    CREATED("created", "Criadas por mim"),
    ALL("all", "Todas visíveis")
}

internal enum class GitHubIssueState(val queryValue: String, val label: String) {
    OPEN("open", "Abertas"),
    CLOSED("closed", "Fechadas"),
    ALL("all", "Todas")
}

internal data class GitHubIssue(
    val number: Int,
    val title: String,
    val state: GitHubIssueState,
    val repository: String,
    val author: String?,
    val assignees: List<String>,
    val labels: List<String>,
    val updatedAt: Instant?
) {
    val identity: String get() = "${repository.lowercase(Locale.ROOT)}/$number"
}

internal fun githubIssuesPath(scope: GitHubIssueScope, state: GitHubIssueState): String =
    "/issues?filter=${scope.queryValue}&state=${state.queryValue}&pulls=false&per_page=50"

internal fun parseGitHubIssues(json: String): List<GitHubIssue> {
    val array = JSONArray(json)
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: throw IOException("Resposta de issues inválida")
            if (item.has("pull_request") && item.opt("pull_request") != JSONObject.NULL) continue
            try {
                add(item.toGitHubIssue())
            } catch (_: Exception) {
                // O endpoint pode devolver itens removidos/inconsistentes durante a paginação.
                // Ignorar o item evita exibir metadados parciais como uma issue real.
            }
        }
    }
}

private fun JSONObject.toGitHubIssue(): GitHubIssue {
    val rawNumber = opt("number") as? Number ?: throw IOException("Issue inválida")
    val longNumber = rawNumber.toLong()
    if (rawNumber.toDouble() != longNumber.toDouble() || longNumber !in 1..Int.MAX_VALUE) {
        throw IOException("Issue inválida")
    }
    val number = longNumber.toInt()
    val title = requiredIssueString("title")
    val state = when (requiredIssueString("state")) {
        "open" -> GitHubIssueState.OPEN
        "closed" -> GitHubIssueState.CLOSED
        else -> throw IOException("Issue inválida")
    }
    val repository = optJSONObject("repository")?.requiredIssueString("full_name")
        ?: throw IOException("Issue inválida")
    val repositoryParts = repository.split('/')
    if (repositoryParts.size != 2 || repositoryParts.any { it.isBlank() || it.any(Char::isWhitespace) }) {
        throw IOException("Issue inválida")
    }
    validateIssueUrl(requiredIssueString("html_url"), repository, number)

    val author = optJSONObject("user")?.optionalIssueString("login")?.takeIf(String::isNotBlank)
    val assignees: List<String> = when (val raw = opt("assignees")) {
        null, JSONObject.NULL -> emptyList()
        is JSONArray -> buildList<String> {
            for (index in 0 until raw.length()) {
                val login = raw.optJSONObject(index)?.optionalIssueString("login")?.takeIf(String::isNotBlank)
                if (login != null) add(login)
            }
        }
        else -> throw IOException("Issue inválida")
    }
    val labels: List<String> = when (val raw = opt("labels")) {
        null, JSONObject.NULL -> emptyList()
        is JSONArray -> buildList<String> {
            for (index in 0 until raw.length()) {
                val label = raw.optJSONObject(index)?.optionalIssueString("name")
                if (!label.isNullOrBlank()) add(label)
            }
        }
        else -> throw IOException("Issue inválida")
    }
    val updatedAt = optionalIssueString("updated_at")?.let { value ->
        try { Instant.parse(value) } catch (_: Exception) { null }
    }
    return GitHubIssue(number, title, state, repository, author, assignees, labels, updatedAt)
}

private fun validateIssueUrl(value: String, repository: String, number: Int) {
    val uri = try { URI(value) } catch (_: Exception) { throw IOException("Issue inválida") }
    val expectedPath = "/$repository/issues/$number"
    if (uri.scheme != "https" || !uri.host.equals("github.com", ignoreCase = true) ||
        uri.port != -1 || uri.userInfo != null || uri.query != null || uri.fragment != null ||
        !uri.path.equals(expectedPath, ignoreCase = true)
    ) throw IOException("Issue inválida")
}

private fun JSONObject.requiredIssueString(key: String): String =
    optionalIssueString(key)?.takeIf(String::isNotBlank)
        ?: throw IOException("Issue inválida")

private fun JSONObject.optionalIssueString(key: String): String? = when (val value = opt(key)) {
    null, JSONObject.NULL -> null
    is String -> value
    else -> throw IOException("Issue inválida")
}

internal class GitHubIssuePager(
    client: GitHubHttpClient,
    accessToken: String,
    scope: GitHubIssueScope,
    state: GitHubIssueState,
    cacheStore: GitHubCacheStore? = null,
    accountLogin: String? = null,
    decode: (String) -> List<GitHubIssue> = ::parseGitHubIssues
) {
    private val pager = GitHubPaginator(
        client = client,
        firstPath = githubIssuesPath(scope, state),
        accessToken = accessToken,
        decode = decode,
        itemKey = GitHubIssue::identity,
        cacheStore = cacheStore,
        accountLogin = accountLogin
    )

    fun loadNext(): GitHubPageResult<GitHubIssue> = pager.loadNext()
    fun cancel() = pager.cancel()
}

internal sealed interface CreateIssueResult {
    data class Success(val issue: GitHubIssue) : CreateIssueResult
    data class Failure(val error: GitHubHttpError, val statusCode: Int? = null) : CreateIssueResult
    data object EmptyTitle : CreateIssueResult
    data object Offline : CreateIssueResult
}

internal fun parseCreatedIssue(json: String, fallbackRepository: String): GitHubIssue {
    val obj = JSONObject(json)
    val rawNumber = obj.opt("number") as? Number ?: throw IOException("Issue criada inválida")
    val number = rawNumber.toInt()
    val title = obj.requiredIssueString("title")
    val state = when (obj.requiredIssueString("state")) {
        "open" -> GitHubIssueState.OPEN
        "closed" -> GitHubIssueState.CLOSED
        else -> GitHubIssueState.OPEN
    }
    val repository = obj.optJSONObject("repository")?.optionalIssueString("full_name")?.takeIf(String::isNotBlank)
        ?: fallbackRepository
    val repositoryParts = repository.split('/')
    if (repositoryParts.size != 2 || repositoryParts.any { it.isBlank() || it.any(Char::isWhitespace) }) {
        throw IOException("Repositório da issue inválido")
    }
    val author = obj.optJSONObject("user")?.optionalIssueString("login")?.takeIf(String::isNotBlank)
    val assignees: List<String> = when (val raw = obj.opt("assignees")) {
        null, JSONObject.NULL -> emptyList()
        is JSONArray -> buildList<String> {
            for (index in 0 until raw.length()) {
                val login = raw.optJSONObject(index)?.optionalIssueString("login")?.takeIf(String::isNotBlank)
                if (login != null) add(login)
            }
        }
        else -> emptyList()
    }
    val labels: List<String> = when (val raw = obj.opt("labels")) {
        null, JSONObject.NULL -> emptyList()
        is JSONArray -> buildList<String> {
            for (index in 0 until raw.length()) {
                val label = raw.optJSONObject(index)?.optionalIssueString("name")
                if (!label.isNullOrBlank()) add(label)
            }
        }
        else -> emptyList()
    }
    val updatedAt = obj.optionalIssueString("updated_at")?.let { value ->
        try { Instant.parse(value) } catch (_: Exception) { null }
    }
    return GitHubIssue(number, title, state, repository, author, assignees, labels, updatedAt)
}

internal fun escapeJson(value: String): String = buildString(value.length + 8) {
    for (char in value) {
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (char < ' ') {
                    append(String.format("\\u%04x", char.code))
                } else {
                    append(char)
                }
            }
        }
    }
}

internal fun createGitHubIssue(
    client: GitHubHttpClient,
    accessToken: String,
    repository: String,
    title: String,
    body: String?,
    isOffline: Boolean = false
): CreateIssueResult {
    if (isOffline) return CreateIssueResult.Offline

    val trimmedTitle = title.trim()
    if (trimmedTitle.isEmpty()) return CreateIssueResult.EmptyTitle

    val parts = repository.trim().split('/')
    if (parts.size != 2 || parts.any { it.isBlank() || it.any(Char::isWhitespace) }) {
        return CreateIssueResult.Failure(GitHubHttpError.UNEXPECTED)
    }

    val payload = buildString {
        append("{\"title\":\"").append(escapeJson(trimmedTitle)).append("\"")
        val trimmedBody = body?.trim()
        if (!trimmedBody.isNullOrBlank()) {
            append(",\"body\":\"").append(escapeJson(trimmedBody)).append("\"")
        }
        append("}")
    }

    val path = "/repos/$repository/issues"
    val call = client.post(path, accessToken = accessToken, jsonBody = payload)
    return when (val result = call.execute()) {
        is GitHubHttpResult.Success -> {
            try {
                val created = parseCreatedIssue(result.body, repository)
                CreateIssueResult.Success(created)
            } catch (_: Exception) {
                CreateIssueResult.Failure(GitHubHttpError.UNEXPECTED, result.statusCode)
            }
        }
        is GitHubHttpResult.Failure -> {
            CreateIssueResult.Failure(result.error, result.statusCode)
        }
    }
}
