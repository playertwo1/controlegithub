package com.playertwo.controlegithub

import java.io.IOException
import java.net.URI
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

internal data class GitHubNotification(
    val id: String,
    val unread: Boolean,
    val reason: String,
    val title: String?,
    val subjectType: String?,
    val repositoryId: Long,
    val repositoryFullName: String,
    val updatedAt: Instant?,
    val subjectApiUrl: String?
)

internal sealed interface GitHubNotificationDestination {
    data class Issue(val repository: String, val number: Int) : GitHubNotificationDestination
    data class PullRequest(val repository: String, val number: Int) : GitHubNotificationDestination
    data class Repository(val repository: String) : GitHubNotificationDestination
    data object Unavailable : GitHubNotificationDestination
}

internal fun parseGitHubNotifications(json: String): List<GitHubNotification> = try {
    val items = JSONArray(json)
    List(items.length()) { index ->
        val item = items.optJSONObject(index) ?: throw IOException("Notificação inválida")
        val repository = item.optJSONObject("repository") ?: throw IOException("Notificação inválida")
        val subject = when (val raw = item.opt("subject")) {
            null, JSONObject.NULL -> JSONObject()
            is JSONObject -> raw
            else -> throw IOException("Notificação inválida")
        }
        val id = item.requiredNotificationId()
        val unread = item.opt("unread") as? Boolean ?: throw IOException("Notificação inválida")
        val reason = item.requiredNotificationCode("reason")
        val repositoryId = repository.requiredPositiveLong("id")
        val repositoryFullName = repository.requiredRepositoryFullName()
        val updatedAt = item.optionalNotificationString("updated_at")?.let { value ->
            runCatching { Instant.parse(value) }.getOrNull()
        }
        GitHubNotification(
            id = id,
            unread = unread,
            reason = reason,
            title = subject.optionalNotificationString("title")?.take(200),
            subjectType = subject.optionalNotificationCode("type"),
            repositoryId = repositoryId,
            repositoryFullName = repositoryFullName,
            updatedAt = updatedAt,
            subjectApiUrl = subject.optionalNotificationString("url")?.takeIf { it.length <= 2_048 }
        )
    }
} catch (error: Exception) {
    if (error is IOException) throw error
    throw IOException("Resposta de notificações inválida", error)
}

internal fun notificationDestination(item: GitHubNotification): GitHubNotificationDestination {
    if (item.subjectType != "Issue" && item.subjectType != "PullRequest") {
        if (item.repositoryId <= 0 || !REPOSITORY_NAME.matches(item.repositoryFullName)) {
            return GitHubNotificationDestination.Unavailable
        }
        return GitHubNotificationDestination.Repository(item.repositoryFullName)
    }

    val uri = item.subjectApiUrl?.let { runCatching { URI(it) }.getOrNull() }
        ?: return GitHubNotificationDestination.Unavailable
    if (uri.scheme != "https" || !uri.host.equals("api.github.com", ignoreCase = true) ||
        uri.port != -1 || uri.userInfo != null || uri.query != null || uri.fragment != null
    ) return GitHubNotificationDestination.Unavailable

    val match = SUBJECT_PATH.matchEntire(uri.path ?: "")
        ?: return GitHubNotificationDestination.Unavailable
    val repository = "${match.groupValues[1]}/${match.groupValues[2]}"
    val number = match.groupValues[4].toIntOrNull()
        ?: return GitHubNotificationDestination.Unavailable
    if (!repository.equals(item.repositoryFullName, ignoreCase = true) || number <= 0) {
        return GitHubNotificationDestination.Unavailable
    }

    return when (item.subjectType) {
        "Issue" -> if (match.groupValues[3].equals("issues", ignoreCase = true)) {
            GitHubNotificationDestination.Issue(item.repositoryFullName, number)
        } else GitHubNotificationDestination.Unavailable
        else -> GitHubNotificationDestination.PullRequest(item.repositoryFullName, number)
    }
}

internal class GitHubNotificationPager(
    client: GitHubHttpClient,
    accessToken: String,
    cacheStore: GitHubCacheStore? = null,
    accountLogin: String? = null
) {
    private val pager = GitHubPaginator(
        client = client,
        firstPath = FIRST_PAGE_PATH,
        accessToken = accessToken,
        decode = ::parseGitHubNotifications,
        itemKey = GitHubNotification::id,
        cacheStore = cacheStore,
        accountLogin = accountLogin
    )

    fun loadNext(): GitHubPageResult<GitHubNotification> = pager.loadNext()

    fun cancel() = pager.cancel()

    internal companion object {
        const val FIRST_PAGE_PATH = "/notifications?all=true&per_page=50"
    }
}

internal sealed interface MarkNotificationReadResult {
    data object Success : MarkNotificationReadResult
    data class Failure(val error: GitHubHttpError, val retryAtEpochMillis: Long? = null) : MarkNotificationReadResult
}

internal fun markNotificationAsRead(
    client: GitHubHttpClient,
    accessToken: String,
    threadId: String
): MarkNotificationReadResult {
    require(NOTIFICATION_ID.matches(threadId)) { "ID de notificação inválido" }
    val call = client.patch("/notifications/threads/$threadId", accessToken)
    return when (val result = call.execute()) {
        is GitHubHttpResult.Success -> MarkNotificationReadResult.Success
        is GitHubHttpResult.Failure -> MarkNotificationReadResult.Failure(result.error, result.retryAtEpochMillis)
    }
}


private fun JSONObject.requiredNotificationId(): String = when (val value = opt("id")) {
    is String -> value.takeIf { NOTIFICATION_ID.matches(it) }
        ?: throw IOException("Notificação inválida")
    is Number -> value.toLong().takeIf { it > 0 && value.toDouble() == it.toDouble() }
        ?.toString() ?: throw IOException("Notificação inválida")
    else -> throw IOException("Notificação inválida")
}

private fun JSONObject.requiredPositiveLong(key: String): Long {
    val value = opt(key) as? Number ?: throw IOException("Notificação inválida")
    return value.toLong().takeIf { it > 0 && value.toDouble() == it.toDouble() }
        ?: throw IOException("Notificação inválida")
}

private fun JSONObject.requiredRepositoryFullName(): String {
    val value = optionalNotificationString("full_name")?.takeIf { REPOSITORY_NAME.matches(it) }
        ?: throw IOException("Notificação inválida")
    return value
}

private fun JSONObject.requiredNotificationCode(key: String): String =
    optionalNotificationCode(key) ?: throw IOException("Notificação inválida")

private fun JSONObject.optionalNotificationCode(key: String): String? =
    optionalNotificationString(key)?.takeIf { NOTIFICATION_CODE.matches(it) }
        ?: if (isNull(key)) null else throw IOException("Notificação inválida")

private fun JSONObject.optionalNotificationString(key: String): String? = when (val value = opt(key)) {
    null, JSONObject.NULL -> null
    is String -> value.filterNot(Char::isISOControl).trim().takeIf(String::isNotEmpty)
    else -> throw IOException("Notificação inválida")
}

private fun JSONObject.isNull(key: String) = !has(key) || opt(key) == JSONObject.NULL

private val NOTIFICATION_ID = Regex("[A-Za-z0-9_-]{1,80}")
private val NOTIFICATION_CODE = Regex("[A-Za-z0-9_-]{1,80}")
private val REPOSITORY_NAME = Regex("[^/\\s]{1,100}/[^/\\s]{1,100}")
private val SUBJECT_PATH = Regex("/repos/([^/]+)/([^/]+)/(issues|pulls)/([1-9][0-9]*)", RegexOption.IGNORE_CASE)
