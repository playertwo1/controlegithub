package com.playertwo.controlegithub

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject

internal data class GitHubRepositorySocialState(
    val starred: Boolean,
    val watching: Boolean
)

internal sealed interface GitHubRepositorySocialResult {
    data class Loaded(val state: GitHubRepositorySocialState) : GitHubRepositorySocialResult
    data class Failed(
        val error: GitHubHttpError,
        val message: String = error.userMessage,
        val retryAtEpochMillis: Long? = null
    ) : GitHubRepositorySocialResult
}

internal class GitHubRepositorySocialClient(
    private val client: GitHubHttpClient,
    private val accessToken: String
) {
    fun loadState(owner: String, repo: String): GitHubRepositorySocialResult {
        if (!isValidSegment(owner) || !isValidSegment(repo)) {
            return GitHubRepositorySocialResult.Failed(GitHubHttpError.UNEXPECTED)
        }
        val starPath = starredPath(owner, repo)
        val starred = when (val response = client.get(starPath, accessToken = accessToken).execute()) {
            is GitHubHttpResult.Success -> response.statusCode in 200..299
            is GitHubHttpResult.Failure -> when (response.statusCode) {
                404 -> false
                else -> return GitHubRepositorySocialResult.Failed(
                    error = response.error,
                    message = response.error.userMessage,
                    retryAtEpochMillis = response.retryAtEpochMillis
                )
            }
        }

        val subPath = subscriptionPath(owner, repo)
        val watching = when (val response = client.get(subPath, accessToken = accessToken).execute()) {
            is GitHubHttpResult.Success -> when (response.statusCode) {
                204 -> true
                else -> try {
                    parseSubscribedFlag(response.body)
                } catch (_: Exception) {
                    return GitHubRepositorySocialResult.Failed(GitHubHttpError.UNEXPECTED)
                }
            }
            is GitHubHttpResult.Failure -> when (response.statusCode) {
                404 -> false
                else -> return GitHubRepositorySocialResult.Failed(
                    error = response.error,
                    message = response.error.userMessage,
                    retryAtEpochMillis = response.retryAtEpochMillis
                )
            }
        }

        return GitHubRepositorySocialResult.Loaded(
            GitHubRepositorySocialState(starred = starred, watching = watching)
        )
    }

    fun setStarred(
        owner: String,
        repo: String,
        targetStarred: Boolean,
        currentWatching: Boolean = false
    ): GitHubRepositorySocialResult {
        if (!isValidSegment(owner) || !isValidSegment(repo)) {
            return GitHubRepositorySocialResult.Failed(GitHubHttpError.UNEXPECTED)
        }
        val path = starredPath(owner, repo)
        val call = if (targetStarred) {
            client.put(path, accessToken = accessToken)
        } else {
            client.delete(path, accessToken = accessToken)
        }
        return when (val response = call.execute()) {
            is GitHubHttpResult.Success -> GitHubRepositorySocialResult.Loaded(
                GitHubRepositorySocialState(starred = targetStarred, watching = currentWatching)
            )
            is GitHubHttpResult.Failure -> GitHubRepositorySocialResult.Failed(
                error = response.error,
                message = response.error.userMessage,
                retryAtEpochMillis = response.retryAtEpochMillis
            )
        }
    }

    fun setWatching(
        owner: String,
        repo: String,
        targetWatching: Boolean,
        currentStarred: Boolean = false
    ): GitHubRepositorySocialResult {
        if (!isValidSegment(owner) || !isValidSegment(repo)) {
            return GitHubRepositorySocialResult.Failed(GitHubHttpError.UNEXPECTED)
        }
        val path = subscriptionPath(owner, repo)
        val call = if (targetWatching) {
            client.put(path, accessToken = accessToken, jsonBody = """{"subscribed":true}""")
        } else {
            client.delete(path, accessToken = accessToken)
        }
        return when (val response = call.execute()) {
            is GitHubHttpResult.Success -> {
                val watching = if (!targetWatching || response.statusCode == 204 || response.body.isBlank()) {
                    targetWatching
                } else {
                    try {
                        parseSubscribedFlag(response.body)
                    } catch (_: Exception) {
                        return GitHubRepositorySocialResult.Failed(GitHubHttpError.UNEXPECTED)
                    }
                }
                GitHubRepositorySocialResult.Loaded(
                    GitHubRepositorySocialState(starred = currentStarred, watching = watching)
                )
            }
            is GitHubHttpResult.Failure -> GitHubRepositorySocialResult.Failed(
                error = response.error,
                message = response.error.userMessage,
                retryAtEpochMillis = response.retryAtEpochMillis
            )
        }
    }

    private fun starredPath(owner: String, repo: String): String =
        "/user/starred/${encodeSegment(owner)}/${encodeSegment(repo)}"

    private fun subscriptionPath(owner: String, repo: String): String =
        "/repos/${encodeSegment(owner)}/${encodeSegment(repo)}/subscription"

    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun isValidSegment(segment: String): Boolean =
        segment.isNotBlank() && segment != "." && segment != ".." && SAFE_SEGMENT.matches(segment)

    private fun parseSubscribedFlag(json: String): Boolean {
        val root = JSONObject(json)
        val ignored = root.optBoolean("ignored", false)
        if (ignored) return false
        return when (val raw = root.opt("subscribed")) {
            is Boolean -> raw
            else -> throw IllegalArgumentException("Campo subscribed ausente")
        }
    }

    private companion object {
        val SAFE_SEGMENT = Regex("^[A-Za-z0-9_.-]+$")
    }
}
