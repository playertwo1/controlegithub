package com.playertwo.controlegithub

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Locale

internal data class GitHubPullRequestFile(
    val filename: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val patch: String?
)

internal data class GitHubPullRequestCheck(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val startedAt: Instant?,
    val completedAt: Instant?
)

internal data class GitHubCommitStatus(
    val id: Long,
    val context: String,
    val state: String,
    val description: String?,
    val updatedAt: Instant?
)

internal data class GitHubCommitStatusSummary(val state: String, val statuses: List<GitHubCommitStatus>)

internal data class GitHubPullRequestFilesPage(
    val files: List<GitHubPullRequestFile>,
    val hasNext: Boolean,
    val limited: Boolean
)

internal fun limitedFilesPage(
    items: List<GitHubPullRequestFile>,
    hasNext: Boolean,
    pageNumber: Int,
    maxPages: Int = MAX_FILE_PAGES
): GitHubPullRequestFilesPage {
    val capped = items.size >= MAX_FILE_COUNT || pageNumber >= maxPages
    return GitHubPullRequestFilesPage(items.take(MAX_FILE_COUNT), hasNext && !capped, capped)
}

internal fun parseGitHubPullRequestFiles(json: String): List<GitHubPullRequestFile> {
    val array = JSONArray(json)
    return List(array.length()) { index ->
        val item = array.getJSONObject(index)
        val status = item.requiredArtifactString("status")
        if (status.length > 40) throw IOException("Arquivo de pull request inválido")
        GitHubPullRequestFile(
            filename = item.requiredArtifactString("filename"),
            status = status,
            additions = item.requiredNonNegativeInt("additions"),
            deletions = item.requiredNonNegativeInt("deletions"),
            changes = item.requiredNonNegativeInt("changes"),
            patch = item.optionalArtifactString("patch")
        )
    }
}

internal fun parseGitHubPullRequestChecks(json: String, expectedSha: String): List<GitHubPullRequestCheck> {
    requireValidSha(expectedSha)
    val response = JSONObject(json)
    response.optionalArtifactString("head_sha")?.let { if (!it.equals(expectedSha, true)) throw IOException("Checks pertencem a outro commit") }
    response.requiredNonNegativeInt("total_count")
    val runs = response.optJSONArray("check_runs") ?: throw IOException("Checks inválidos")
    return buildList {
        for (index in 0 until runs.length()) {
            val item = runs.optJSONObject(index) ?: continue
            val status = item.optionalArtifactString("status")?.takeIf { it in CHECK_STATUSES } ?: continue
            val runSha = item.optionalArtifactString("head_sha")
            if (runSha != null && !runSha.equals(expectedSha, true)) continue
            add(
                GitHubPullRequestCheck(
                    id = item.requiredPositiveLong("id"),
                    name = item.optionalArtifactString("name")?.takeIf(String::isNotBlank) ?: "Check sem nome",
                    status = status,
                    conclusion = item.optionalArtifactString("conclusion"),
                    startedAt = item.optionalArtifactString("started_at")?.let(::parseArtifactInstant),
                    completedAt = item.optionalArtifactString("completed_at")?.let(::parseArtifactInstant)
                )
            )
        }
    }
}

internal fun parseGitHubCommitStatuses(json: String, expectedSha: String): GitHubCommitStatusSummary {
    requireValidSha(expectedSha)
    val response = JSONObject(json)
    val sha = response.requiredArtifactString("sha")
    if (!sha.equals(expectedSha, true)) throw IOException("Statuses pertencem a outro commit")
    val state = response.requiredArtifactString("state")
    if (state !in COMMIT_STATES) throw IOException("Statuses inválidos")
    val array = response.optJSONArray("statuses") ?: throw IOException("Statuses inválidos")
    val statuses = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val itemState = item.optionalArtifactString("state")?.takeIf { it in STATUS_STATES } ?: continue
            val context = item.optionalArtifactString("context")?.takeIf(String::isNotBlank) ?: continue
            add(
                GitHubCommitStatus(
                    id = item.requiredPositiveLong("id"),
                    context = context,
                    state = itemState,
                    description = item.optionalArtifactString("description"),
                    updatedAt = item.optionalArtifactString("updated_at")?.let(::parseArtifactInstant)
                )
            )
        }
    }
    return GitHubCommitStatusSummary(state, statuses)
}

internal object GitHubPullRequestArtifacts {
    fun filesPath(pullRequest: GitHubPullRequest) = "${pullsPath(pullRequest)}/files?per_page=100"

    fun checkRunsPath(pullRequest: GitHubPullRequest, headSha: String) =
        "${commitPath(pullRequest, headSha)}/check-runs?filter=latest&per_page=100"

    fun statusPath(pullRequest: GitHubPullRequest, headSha: String) =
        "${commitPath(pullRequest, headSha)}/status?per_page=100"

    private fun pullsPath(pullRequest: GitHubPullRequest) =
        "/repos/${pullRequest.repositoryPart(0)}/${pullRequest.repositoryPart(1)}/pulls/${pullRequest.number}"

    private fun commitPath(pullRequest: GitHubPullRequest, headSha: String): String {
        requireValidSha(headSha)
        return "/repos/${pullRequest.repositoryPart(0)}/${pullRequest.repositoryPart(1)}/commits/${headSha.lowercase(Locale.ROOT)}"
    }
}

internal class GitHubPullRequestFilesPager(client: GitHubHttpClient, token: String, pullRequest: GitHubPullRequest) {
    private var loadedPages = 0
    private var currentFiles = emptyList<GitHubPullRequestFile>()
    private val pager = GitHubPaginator(client, GitHubPullRequestArtifacts.filesPath(pullRequest), token, ::parseGitHubPullRequestFiles) { it.filename }

    fun loadNext(): GitHubPullRequestFilesPageResult {
        if (loadedPages >= MAX_FILE_PAGES) return GitHubPullRequestFilesPageResult.Loaded(GitHubPullRequestFilesPage(currentFiles, false, true))
        return when (val result = pager.loadNext()) {
            is GitHubPageResult.Loaded -> {
                loadedPages++
                currentFiles = result.items.take(MAX_FILE_COUNT)
                val page = limitedFilesPage(result.items, result.hasNext, loadedPages)
                GitHubPullRequestFilesPageResult.Loaded(page)
            }
            is GitHubPageResult.RateLimited -> GitHubPullRequestFilesPageResult.RateLimited(result.items.take(MAX_FILE_COUNT), result.retryAtEpochMillis)
            is GitHubPageResult.Failed -> GitHubPullRequestFilesPageResult.Failed(result.items.take(MAX_FILE_COUNT), result.message, result.error)
        }
    }

    fun cancel() = pager.cancel()
}

internal sealed interface GitHubPullRequestFilesPageResult {
    data class Loaded(val page: GitHubPullRequestFilesPage) : GitHubPullRequestFilesPageResult
    data class RateLimited(val items: List<GitHubPullRequestFile>, val retryAtEpochMillis: Long) : GitHubPullRequestFilesPageResult
    data class Failed(val items: List<GitHubPullRequestFile>, val message: String, val error: GitHubHttpError?) : GitHubPullRequestFilesPageResult
}

internal class GitHubPullRequestCheckPager(client: GitHubHttpClient, token: String, pullRequest: GitHubPullRequest, headSha: String) {
    private var pages = 0
    private var currentItems = emptyList<GitHubPullRequestCheck>()
    private val pager = GitHubPaginator(client, GitHubPullRequestArtifacts.checkRunsPath(pullRequest, headSha), token, { parseGitHubPullRequestChecks(it, headSha) }) { it.id.toString() }
    fun loadNext(): GitHubLimitedPageResult<GitHubPullRequestCheck> {
        if (pages >= MAX_CHECK_PAGES) return GitHubLimitedPageResult.Loaded(GitHubLimitedPage(currentItems, false, true))
        val result = pager.loadNext()
        if (result is GitHubPageResult.Loaded) { pages++; currentItems = result.items }
        return result.limitPage(pages)
    }
    fun cancel() = pager.cancel()
}

internal class GitHubCommitStatusPager(client: GitHubHttpClient, token: String, pullRequest: GitHubPullRequest, headSha: String) {
    private var pages = 0
    private var currentItems = emptyList<GitHubCommitStatus>()
    @Volatile var summaryState: String? = null
        private set
    private val pager = GitHubPaginator(client, GitHubPullRequestArtifacts.statusPath(pullRequest, headSha), token, { json ->
        parseGitHubCommitStatuses(json, headSha).also { summaryState = it.state }.statuses
    }) { it.id.toString() }
    fun loadNext(): GitHubLimitedPageResult<GitHubCommitStatus> {
        if (pages >= MAX_CHECK_PAGES) return GitHubLimitedPageResult.Loaded(GitHubLimitedPage(currentItems, false, true))
        val result = pager.loadNext()
        if (result is GitHubPageResult.Loaded) { pages++; currentItems = result.items }
        return result.limitPage(pages)
    }
    fun cancel() = pager.cancel()
}

internal data class GitHubLimitedPage<T>(val items: List<T>, val hasNext: Boolean, val limited: Boolean)
internal sealed interface GitHubLimitedPageResult<T> {
    data class Loaded<T>(val page: GitHubLimitedPage<T>) : GitHubLimitedPageResult<T>
    data class RateLimited<T>(val items: List<T>, val retryAtEpochMillis: Long) : GitHubLimitedPageResult<T>
    data class Failed<T>(val items: List<T>, val message: String, val error: GitHubHttpError?) : GitHubLimitedPageResult<T>
}

internal fun <T> GitHubPageResult<T>.limitPage(pageNumber: Int, maxPages: Int = MAX_CHECK_PAGES): GitHubLimitedPageResult<T> = when (this) {
    is GitHubPageResult.Loaded -> GitHubLimitedPageResult.Loaded(
        GitHubLimitedPage(items, hasNext && pageNumber < maxPages, pageNumber >= maxPages)
    )
    is GitHubPageResult.RateLimited -> GitHubLimitedPageResult.RateLimited(items, retryAtEpochMillis)
    is GitHubPageResult.Failed -> GitHubLimitedPageResult.Failed(items, message, error)
}

private fun JSONObject.requiredArtifactString(key: String): String =
    optionalArtifactString(key)?.takeIf(String::isNotBlank) ?: throw IOException("Resposta de PR inválida")

private fun JSONObject.optionalArtifactString(key: String): String? = when (val value = opt(key)) {
    null, JSONObject.NULL -> null
    is String -> value
    else -> throw IOException("Resposta de PR inválida")
}

private fun JSONObject.requiredNonNegativeInt(key: String): Int {
    val raw = opt(key) as? Number ?: throw IOException("Resposta de PR inválida")
    val value = raw.toInt()
    if (value < 0 || raw.toDouble() != value.toDouble()) throw IOException("Resposta de PR inválida")
    return value
}

private fun JSONObject.requiredPositiveLong(key: String): Long {
    val raw = opt(key) as? Number ?: throw IOException("Resposta de PR inválida")
    val value = raw.toLong()
    if (value <= 0 || raw.toDouble() != value.toDouble()) throw IOException("Resposta de PR inválida")
    return value
}

private fun parseArtifactInstant(value: String): Instant = try {
    Instant.parse(value)
} catch (_: Exception) {
    throw IOException("Data de PR inválida")
}

private fun requireValidSha(value: String) {
    require(value.matches(Regex("[0-9a-fA-F]{40}"))) { "Commit SHA inválido" }
}

private fun encodeArtifactSegment(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

private fun GitHubPullRequest.repositoryPart(index: Int): String {
    val parts = repository.split('/', limit = 2)
    require(parts.size == 2 && parts.all(String::isNotBlank)) { "Repositório inválido" }
    return encodeArtifactSegment(parts[index])
}

private const val MAX_FILE_COUNT = 3_000
private const val MAX_FILE_PAGES = 30
private const val MAX_CHECK_PAGES = 100
private val CHECK_STATUSES = setOf("queued", "in_progress", "completed")
private val STATUS_STATES = setOf("error", "failure", "pending", "success")
private val COMMIT_STATES = setOf("error", "failure", "pending", "success")
