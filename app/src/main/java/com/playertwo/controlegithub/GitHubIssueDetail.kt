package com.playertwo.controlegithub

import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

internal data class GitHubIssueDetail(
    val issue: GitHubIssue,
    val body: String?
)

internal data class GitHubIssueComment(
    val id: Long,
    val author: String?,
    val body: String?,
    val createdAt: Instant?
)

internal sealed interface GitHubIssueDetailResult {
    data class Loaded(val detail: GitHubIssueDetail) : GitHubIssueDetailResult
    data object Removed : GitHubIssueDetailResult
    data object Unavailable : GitHubIssueDetailResult
    data class Failed(val error: GitHubHttpError, val retryAtEpochMillis: Long? = null) : GitHubIssueDetailResult
}

internal sealed interface CreateIssueCommentResult {
    data class Success(val comment: GitHubIssueComment) : CreateIssueCommentResult
    data class Failure(val error: GitHubHttpError, val statusCode: Int? = null) : CreateIssueCommentResult
    data object EmptyBody : CreateIssueCommentResult
    data object Offline : CreateIssueCommentResult
}

internal sealed interface EditIssueResult {
    data class Success(val detail: GitHubIssueDetail) : EditIssueResult
    data class Failure(val error: GitHubHttpError, val statusCode: Int? = null) : EditIssueResult
    data object EmptyTitle : EditIssueResult
    data object Offline : EditIssueResult
}

internal class GitHubIssueDetailLoader(
    private val client: GitHubHttpClient,
    private val accessToken: String
) {
    private val lock = Any()
    private var activeCall: GitHubHttpCall? = null
    private var cancelled = false

    fun cancel() {
        synchronized(lock) {
            cancelled = true
            activeCall?.cancel()
        }
    }

    fun load(issue: GitHubIssue): GitHubIssueDetailResult {
        val path = issuePath(issue)
        val call = synchronized(lock) {
            if (cancelled) return GitHubIssueDetailResult.Failed(GitHubHttpError.CANCELLED)
            client.get(path, accessToken).also { activeCall = it }
        }
        return try {
            when (val result = call.execute()) {
                is GitHubHttpResult.Failure -> when (result.statusCode) {
                    410 -> GitHubIssueDetailResult.Removed
                    404 -> GitHubIssueDetailResult.Unavailable
                    else -> GitHubIssueDetailResult.Failed(result.error, result.retryAtEpochMillis)
                }
                is GitHubHttpResult.Success -> try {
                    GitHubIssueDetailResult.Loaded(parseGitHubIssueDetail(result.body, issue))
                } catch (_: Exception) {
                    GitHubIssueDetailResult.Failed(GitHubHttpError.UNEXPECTED)
                }
            }
        } finally {
            synchronized(lock) {
                if (activeCall === call) activeCall = null
            }
        }
    }

    companion object {
        fun issuePath(issue: GitHubIssue): String {
            val parts = issue.repository.split('/')
            require(parts.size == 2 && parts.all(String::isNotBlank) && issue.number > 0)
            return "/repos/${encodeSegment(parts[0])}/${encodeSegment(parts[1])}/issues/${issue.number}"
        }

        fun commentsPath(issue: GitHubIssue) = "${issuePath(issue)}/comments?per_page=50"
    }
}

internal fun parseGitHubIssueDetail(json: String, expected: GitHubIssue): GitHubIssueDetail {
    val item = JSONObject(json)
    val number = item.requiredPositiveLong("number")
    if (number != expected.number.toLong() ||
        (item.has("pull_request") && item.opt("pull_request") != JSONObject.NULL)
    ) throw IOException("Issue inválida")
    val title = item.requiredIssueDetailString("title")
    val state = when (item.requiredIssueDetailString("state")) {
        "open" -> GitHubIssueState.OPEN
        "closed" -> GitHubIssueState.CLOSED
        else -> throw IOException("Issue inválida")
    }
    val url = item.requiredIssueDetailString("html_url")
    val uri = try { java.net.URI(url) } catch (_: Exception) { throw IOException("Issue inválida") }
    val expectedPath = "/${expected.repository}/issues/$number"
    if (uri.scheme != "https" || !uri.host.equals("github.com", true) || uri.port != -1 ||
        uri.userInfo != null || uri.query != null || uri.fragment != null || !uri.path.equals(expectedPath, true)
    ) throw IOException("Issue inválida")

    val labels = parseIssueDetailLabels(item.opt("labels"))
    val updatedAt = item.optionalIssueDetailString("updated_at")?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val author = item.optJSONObject("user")?.optionalIssueDetailString("login")?.takeIf(String::isNotBlank)
    val mapped = expected.copy(title = title, state = state, author = author, labels = labels, updatedAt = updatedAt)
    return GitHubIssueDetail(mapped, item.optionalIssueDetailString("body"))
}

internal fun parseGitHubIssueComments(json: String): List<GitHubIssueComment> {
    val array = JSONArray(json)
    return buildList {
        for (index in 0 until array.length()) {
            val comment = array.optJSONObject(index) ?: continue
            val id = runCatching { comment.requiredPositiveLong("id") }.getOrNull() ?: continue
            val createdAt = runCatching {
                comment.optionalIssueDetailString("created_at")?.let(Instant::parse)
            }.getOrNull()
            val author = runCatching {
                comment.optJSONObject("user")?.optionalIssueDetailString("login")?.takeIf(String::isNotBlank)
            }.getOrNull()
            add(
                GitHubIssueComment(
                    id = id,
                    author = author,
                    body = runCatching { comment.optionalIssueDetailString("body") }.getOrNull(),
                    createdAt = createdAt
                )
            )
        }
    }
}

internal class GitHubIssueCommentPager(
    client: GitHubHttpClient,
    accessToken: String,
    issue: GitHubIssue
) {
    private val pager = GitHubPaginator(
        client = client,
        firstPath = GitHubIssueDetailLoader.commentsPath(issue),
        accessToken = accessToken,
        decode = ::parseGitHubIssueComments,
        itemKey = { it.id.toString() }
    )

    fun loadNext() = pager.loadNext()
    fun cancel() = pager.cancel()
}

private fun parseIssueDetailLabels(raw: Any?): List<String> = when (raw) {
    null, JSONObject.NULL -> emptyList()
    is JSONArray -> buildList {
        for (index in 0 until raw.length()) {
            raw.optJSONObject(index)?.optionalIssueDetailString("name")?.takeIf(String::isNotBlank)?.let(::add)
        }
    }
    else -> throw IOException("Issue inválida")
}

private fun JSONObject.requiredPositiveLong(key: String): Long =
    (opt(key) as? Number)?.let { raw -> raw.toLong().takeIf { it > 0 && raw.toDouble() == it.toDouble() } }
        ?: throw IOException("Issue inválida")

private fun JSONObject.requiredIssueDetailString(key: String): String =
    optionalIssueDetailString(key)?.takeIf(String::isNotBlank) ?: throw IOException("Issue inválida")

private fun JSONObject.optionalIssueDetailString(key: String): String? = when (val value = opt(key)) {
    null, JSONObject.NULL -> null
    is String -> value
    else -> throw IOException("Issue inválida")
}

private fun encodeSegment(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

internal fun parseCreatedIssueComment(json: String): GitHubIssueComment {
    val obj = JSONObject(json)
    val id = obj.requiredPositiveLong("id")
    val author = runCatching {
        obj.optJSONObject("user")?.optionalIssueDetailString("login")?.takeIf(String::isNotBlank)
    }.getOrNull()
    val body = runCatching { obj.optionalIssueDetailString("body") }.getOrNull()
    val createdAt = runCatching {
        obj.optionalIssueDetailString("created_at")?.let(Instant::parse)
    }.getOrNull()
    return GitHubIssueComment(id, author, body, createdAt)
}

internal fun createIssueComment(
    client: GitHubHttpClient,
    accessToken: String,
    repository: String,
    issueNumber: Int,
    body: String,
    isOffline: Boolean = false
): CreateIssueCommentResult {
    if (isOffline) return CreateIssueCommentResult.Offline
    val trimmedBody = body.trim()
    if (trimmedBody.isEmpty()) return CreateIssueCommentResult.EmptyBody

    val parts = repository.trim().split('/')
    if (parts.size != 2 || parts.any { it.isBlank() || it.any(Char::isWhitespace) } || issueNumber <= 0) {
        return CreateIssueCommentResult.Failure(GitHubHttpError.UNEXPECTED)
    }

    val payload = buildString {
        append("{\"body\":\"").append(escapeJson(trimmedBody)).append("\"}")
    }

    val path = "/repos/$repository/issues/$issueNumber/comments"
    val call = client.post(path, accessToken = accessToken, jsonBody = payload)
    return when (val result = call.execute()) {
        is GitHubHttpResult.Success -> {
            try {
                val created = parseCreatedIssueComment(result.body)
                CreateIssueCommentResult.Success(created)
            } catch (_: Exception) {
                CreateIssueCommentResult.Failure(GitHubHttpError.UNEXPECTED, result.statusCode)
            }
        }
        is GitHubHttpResult.Failure -> {
            CreateIssueCommentResult.Failure(result.error, result.statusCode)
        }
    }
}

internal fun editGitHubIssue(
    client: GitHubHttpClient,
    accessToken: String,
    currentIssue: GitHubIssue,
    title: String,
    body: String?,
    isOffline: Boolean = false
): EditIssueResult {
    if (isOffline) return EditIssueResult.Offline
    val trimmedTitle = title.trim()
    if (trimmedTitle.isEmpty()) return EditIssueResult.EmptyTitle

    val parts = currentIssue.repository.trim().split('/')
    if (parts.size != 2 || parts.any { it.isBlank() || it.any(Char::isWhitespace) } || currentIssue.number <= 0) {
        return EditIssueResult.Failure(GitHubHttpError.UNEXPECTED)
    }

    val payload = buildString {
        append("{\"title\":\"").append(escapeJson(trimmedTitle)).append("\"")
        val trimmedBody = body?.trim()
        if (trimmedBody != null) {
            append(",\"body\":\"").append(escapeJson(trimmedBody)).append("\"")
        }
        append("}")
    }

    val path = "/repos/${currentIssue.repository}/issues/${currentIssue.number}"
    val call = client.patch(path, accessToken = accessToken, jsonBody = payload)
    return when (val result = call.execute()) {
        is GitHubHttpResult.Success -> {
            try {
                val updatedDetail = parseGitHubIssueDetail(result.body, currentIssue)
                EditIssueResult.Success(updatedDetail)
            } catch (_: Exception) {
                EditIssueResult.Failure(GitHubHttpError.UNEXPECTED, result.statusCode)
            }
        }
        is GitHubHttpResult.Failure -> {
            EditIssueResult.Failure(result.error, result.statusCode)
        }
    }
}
