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
    val repository: GitHubRepository,
    val updatedAt: Instant?,
    val subjectApiUrl: String?
)

internal sealed interface GitHubNotificationDestination {
    data class Issue(val repository: String, val number: Int) : GitHubNotificationDestination
    data class PullRequest(val repository: String, val number: Int) : GitHubNotificationDestination
    data class Repository(val repository: String) : GitHubNotificationDestination
    data object Unavailable : GitHubNotificationDestination
}

internal fun parseGitHubNotifications(json: String): List<GitHubNotification> {
    try {
        val array = JSONArray(json)
        return List(array.length()) { index ->
            val item = array.optJSONObject(index) ?: throw IOException(INVALID_NOTIFICATIONS_RESPONSE)
            val id = item.requiredNotificationString("id")
            if (!id.matches(NUMERIC_ID)) throw IOException(INVALID_NOTIFICATIONS_RESPONSE)
            val unread = item.opt("unread") as? Boolean ?: throw IOException(INVALID_NOTIFICATIONS_RESPONSE)
            val reason = item.requiredNotificationString("reason")
            val repositoryJson = item.optJSONObject("repository") ?: throw IOException(INVALID_NOTIFICATIONS_RESPONSE)
            val repository = parseGitHubRepositories(JSONArray().put(repositoryJson).toString()).single()
            val subject = when (val raw = item.opt("subject")) {
                null, JSONObject.NULL -> null
                is JSONObject -> raw
                else -> throw IOException(INVALID_NOTIFICATIONS_RESPONSE)
            }
            val updatedAt = item.optionalNotificationString("updated_at")
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            GitHubNotification(
                id = id,
                unread = unread,
                reason = reason,
                title = subject?.optionalNotificationString("title"),
                subjectType = subject?.optionalNotificationString("type"),
                repository = repository,
                updatedAt = updatedAt,
                subjectApiUrl = subject?.optionalNotificationString("url")
            )
        }
    } catch (error: IOException) {
        throw error
    } catch (_: Exception) {
        throw IOException(INVALID_NOTIFICATIONS_RESPONSE)
    }
}

internal fun notificationTypeLabel(item: GitHubNotification): String = when (item.subjectType) {
    "Issue" -> "Issue"
    "PullRequest" -> "Pull request"
    "Repository" -> "Repositório"
    else -> "Outro"
}

internal fun notificationReasonLabel(reason: String): String = when (reason) {
    "assign" -> "Atribuída a você"
    "author" -> "Você criou esta conversa"
    "comment" -> "Você comentou nesta conversa"
    "invitation" -> "Convite"
    "manual" -> "Inscrito manualmente"
    "mention" -> "Você foi mencionado"
    "review_requested" -> "Revisão solicitada"
    "security_alert" -> "Alerta de segurança"
    "state_change" -> "Estado alterado"
    "subscribed" -> "Inscrito na conversa"
    "team_mention" -> "Equipe mencionada"
    "ci_activity" -> "Atividade de CI"
    else -> reason
}

internal fun notificationDestination(item: GitHubNotification): GitHubNotificationDestination {
    val repository = item.repository.fullName
    val repositoryParts = repository.split('/')
    if (repositoryParts.size != 2 || repositoryParts.any { it.isBlank() || it.any(Char::isWhitespace) }) {
        return GitHubNotificationDestination.Unavailable
    }
    return when (item.subjectType) {
        "Repository", null -> GitHubNotificationDestination.Repository(repository)
        "Issue" -> subjectNumber(item.subjectApiUrl, repository, setOf("issues"))
            ?.let { GitHubNotificationDestination.Issue(repository, it) }
            ?: GitHubNotificationDestination.Unavailable
        "PullRequest" -> subjectNumber(item.subjectApiUrl, repository, setOf("issues", "pulls"))
            ?.let { GitHubNotificationDestination.PullRequest(repository, it) }
            ?: GitHubNotificationDestination.Unavailable
        else -> GitHubNotificationDestination.Unavailable
    }
}

private fun subjectNumber(url: String?, repository: String, allowedCollections: Set<String>): Int? {
    val uri = url?.let { runCatching { URI(it) }.getOrNull() } ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true) ||
        !uri.host.equals("api.github.com", ignoreCase = true) || uri.port != -1 ||
        uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null
    ) return null
    val path = uri.path.split('/')
    val repo = repository.split('/')
    if (path.size != 6 || !path[1].equals("repos", ignoreCase = true) ||
        !path[2].equals(repo[0], ignoreCase = true) ||
        !path[3].equals(repo[1], ignoreCase = true) ||
        allowedCollections.none { it.equals(path[4], ignoreCase = true) } ||
        !path[5].matches(POSITIVE_NUMBER)
    ) return null
    return path[5].toIntOrNull()?.takeIf { it > 0 }
}

private fun JSONObject.requiredNotificationString(name: String): String =
    optionalNotificationString(name)?.takeIf(String::isNotBlank) ?: throw IOException(INVALID_NOTIFICATIONS_RESPONSE)

private fun JSONObject.optionalNotificationString(name: String): String? = when (val raw = opt(name)) {
    null, JSONObject.NULL -> null
    is String -> raw
    else -> throw IOException(INVALID_NOTIFICATIONS_RESPONSE)
}

private val NUMERIC_ID = Regex("[1-9][0-9]*")
private val POSITIVE_NUMBER = Regex("[1-9][0-9]*")
private const val INVALID_NOTIFICATIONS_RESPONSE = "Resposta de notificações inválida"
