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
    decode: (String) -> List<GitHubIssue> = ::parseGitHubIssues
) {
    private val pager = GitHubPaginator(
        client = client,
        firstPath = githubIssuesPath(scope, state),
        accessToken = accessToken,
        decode = decode,
        itemKey = GitHubIssue::identity
    )

    fun loadNext(): GitHubPageResult<GitHubIssue> = pager.loadNext()
    fun cancel() = pager.cancel()
}
