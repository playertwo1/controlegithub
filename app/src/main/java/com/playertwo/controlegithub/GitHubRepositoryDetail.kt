package com.playertwo.controlegithub

import java.io.IOException
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Base64
import org.json.JSONObject

internal data class GitHubRepositoryDetail(
    val id: Long,
    val fullName: String,
    val description: String?,
    val visibility: String?,
    val defaultBranch: String?,
    val stars: Long?,
    val forks: Long?,
    val openIssuesAndPullRequests: Long?,
    val pushedAt: Instant?
)

internal sealed interface GitHubReadmeResult {
    data class Found(val text: String) : GitHubReadmeResult
    data object Missing : GitHubReadmeResult
    data class Failed(val error: GitHubHttpError, val retryAtEpochMillis: Long? = null) : GitHubReadmeResult
}

internal sealed interface GitHubRepositoryDetailResult {
    data class Loaded(
        val detail: GitHubRepositoryDetail,
        val readme: GitHubReadmeResult
    ) : GitHubRepositoryDetailResult

    data class Failed(
        val error: GitHubHttpError,
        val retryAtEpochMillis: Long? = null
    ) : GitHubRepositoryDetailResult
}

internal class GitHubRepositoryDetailLoader(
    private val client: GitHubHttpClient,
    private val accessToken: String
) {
    fun load(repository: GitHubRepository): GitHubRepositoryDetailResult {
        val route = repositoryRoute(repository.owner, repository.name)
        val metadata = when (val response = client.get(route, accessToken).execute()) {
            is GitHubHttpResult.Failure -> return GitHubRepositoryDetailResult.Failed(
                response.error,
                response.retryAtEpochMillis
            )
            is GitHubHttpResult.Success -> try {
                parseGitHubRepositoryDetail(response.body)
            } catch (_: Exception) {
                return GitHubRepositoryDetailResult.Failed(GitHubHttpError.UNEXPECTED)
            }
        }
        if (metadata.id != repository.id || !metadata.fullName.equals(repository.fullName, ignoreCase = true)) {
            return GitHubRepositoryDetailResult.Failed(GitHubHttpError.UNEXPECTED)
        }

        val readmePath = "$route/readme" + metadata.defaultBranch?.let {
            "?ref=${encodePathSegment(it)}"
        }.orEmpty()
        val readme = when (val response = client.get(readmePath, accessToken).execute()) {
            is GitHubHttpResult.Success -> try {
                GitHubReadmeResult.Found(parseGitHubReadme(response.body))
            } catch (_: Exception) {
                GitHubReadmeResult.Failed(GitHubHttpError.UNEXPECTED)
            }
            is GitHubHttpResult.Failure -> when (response.statusCode) {
                404 -> GitHubReadmeResult.Missing
                else -> GitHubReadmeResult.Failed(response.error, response.retryAtEpochMillis)
            }
        }
        return GitHubRepositoryDetailResult.Loaded(metadata, readme)
    }

    private fun repositoryRoute(owner: String, name: String) =
        "/repos/${encodePathSegment(owner)}/${encodePathSegment(name)}"

    private fun encodePathSegment(value: String) =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}

internal fun parseGitHubRepositoryDetail(json: String): GitHubRepositoryDetail {
    val repository = JSONObject(json)
    val id = repository.requiredNonNegativeLong("id")
    val name = repository.requiredString("name")
    val fullName = repository.requiredString("full_name")
    val owner = repository.optJSONObject("owner")?.requiredString("login")
        ?: throw IOException("Resposta de repositório inválida")
    if (!fullName.equals("$owner/$name", ignoreCase = true)) {
        throw IOException("Resposta de repositório inválida")
    }

    val isPrivate = repository.opt("private") as? Boolean
        ?: throw IOException("Resposta de repositório inválida")
    val visibility = repository.optionalString("visibility")
        ?.lowercase()
        ?.takeIf { it in setOf("public", "private", "internal") }
        ?: if (isPrivate) "private" else "public"
    val pushedAt = repository.optionalString("pushed_at")?.let { value ->
        try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    return GitHubRepositoryDetail(
        id = id,
        fullName = fullName,
        description = repository.optionalString("description"),
        visibility = visibility,
        defaultBranch = repository.optionalString("default_branch")?.takeIf(String::isNotBlank),
        stars = repository.optionalNonNegativeLong("stargazers_count"),
        forks = repository.optionalNonNegativeLong("forks_count"),
        openIssuesAndPullRequests = repository.optionalNonNegativeLong("open_issues_count"),
        pushedAt = pushedAt
    )
}

internal fun parseGitHubReadme(json: String): String {
    val readme = JSONObject(json)
    if (readme.optString("encoding") != "base64") throw IOException("Formato de README inválido")
    val encoded = readme.opt("content") as? String ?: throw IOException("README inválido")
    val bytes = try {
        Base64.getDecoder().decode(encoded.filterNot(Char::isWhitespace))
    } catch (_: IllegalArgumentException) {
        throw IOException("README inválido")
    }
    return try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: Exception) {
        throw IOException("README inválido")
    }
}

private fun JSONObject.requiredString(key: String): String =
    (opt(key) as? String)?.takeIf(String::isNotBlank)
        ?: throw IOException("Resposta de repositório inválida")

private fun JSONObject.optionalString(key: String): String? = when (val value = opt(key)) {
    null, JSONObject.NULL -> null
    is String -> value
    else -> throw IOException("Resposta de repositório inválida")
}

private fun JSONObject.requiredNonNegativeLong(key: String): Long =
    optionalNonNegativeLong(key) ?: throw IOException("Resposta de repositório inválida")

private fun JSONObject.optionalNonNegativeLong(key: String): Long? = when (val value = opt(key)) {
    null, JSONObject.NULL -> null
    is Number -> value.toLong().takeIf { it >= 0 && value.toDouble() == it.toDouble() }
        ?: throw IOException("Resposta de repositório inválida")
    else -> throw IOException("Resposta de repositório inválida")
}
