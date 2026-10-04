package com.playertwo.controlegithub

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Locale

internal enum class GitHubPullRequestFilter(val label: String, internal val query: String) {
    OPEN("Abertas", "is:pr involves:@me state:open"),
    CLOSED("Fechadas", "is:pr involves:@me state:closed -is:merged"),
    MERGED("Mescladas", "is:pr involves:@me is:merged")
}

internal enum class GitHubPullRequestState(val label: String) {
    OPEN("Aberto"),
    CLOSED("Fechado"),
    MERGED("Mesclado")
}

internal data class GitHubPullRequest(
    val number: Int,
    val title: String,
    val state: GitHubPullRequestState,
    val repository: String,
    val author: String?,
    val labels: List<String>,
    val updatedAt: Instant?
) {
    val identity: String get() = "${repository.lowercase(Locale.ROOT)}/$number"
}

internal data class GitHubPullRequestSearch(
    val totalCount: Int,
    val incomplete: Boolean,
    val items: List<GitHubPullRequest>
)

internal data class GitHubPullRequestDetail(
    val number: Int,
    val title: String,
    val state: GitHubPullRequestState,
    val repository: String,
    val author: String?,
    val body: String?,
    val labels: List<String>,
    val headBranch: String,
    val headRepository: String?,
    val baseBranch: String,
    val baseRepository: String
)

internal data class GitHubPullRequestComment(
    val id: Long,
    val author: String?,
    val body: String?,
    val createdAt: Instant?
)

internal data class GitHubPullRequestReview(
    val id: Long,
    val author: String?,
    val state: String,
    val body: String?,
    val submittedAt: Instant?
)

internal data class GitHubPullRequestPage(
    val items: List<GitHubPullRequest>,
    val hasNext: Boolean,
    val totalCount: Int,
    val incomplete: Boolean
)

internal sealed interface GitHubPullRequestPageResult {
    data class Loaded(val page: GitHubPullRequestPage) : GitHubPullRequestPageResult
    data class RateLimited(val page: GitHubPullRequestPage, val retryAtEpochMillis: Long) : GitHubPullRequestPageResult
    data class Failed(val page: GitHubPullRequestPage, val message: String, val error: GitHubHttpError?) : GitHubPullRequestPageResult
}

internal sealed interface GitHubPullRequestDetailResult {
    data class Loaded(val detail: GitHubPullRequestDetail) : GitHubPullRequestDetailResult
    data object Unavailable : GitHubPullRequestDetailResult
    data class Failed(val error: GitHubHttpError, val retryAtEpochMillis: Long? = null) : GitHubPullRequestDetailResult
}

internal fun githubPullRequestsPath(filter: GitHubPullRequestFilter): String {
    val encodedQuery = URLEncoder.encode(filter.query, StandardCharsets.UTF_8.name())
    return "/search/issues?q=$encodedQuery&sort=updated&order=desc&per_page=50"
}

internal fun parseGitHubPullRequestSearch(
    json: String,
    filter: GitHubPullRequestFilter
): GitHubPullRequestSearch {
    val response = JSONObject(json)
    val totalCount = response.optionalNonNegativeInt("total_count")
        ?: throw IOException("Busca de pull requests inválida")
    val incomplete = response.opt("incomplete_results") as? Boolean
        ?: throw IOException("Busca de pull requests inválida")
    val items = response.optJSONArray("items") ?: throw IOException("Busca de pull requests inválida")
    val unique = LinkedHashMap<String, GitHubPullRequest>()
    for (index in 0 until items.length()) {
        val item = items.optJSONObject(index) ?: continue
        try {
            val pullRequest = item.toGitHubPullRequest(filter)
            unique.putIfAbsent(pullRequest.identity, pullRequest)
        } catch (_: Exception) {
            // Search can contain items that disappear or change access during pagination.
        }
    }
    return GitHubPullRequestSearch(totalCount, incomplete, unique.values.toList())
}

internal fun parseGitHubPullRequestDetail(
    json: String,
    repository: String,
    expectedNumber: Int
): GitHubPullRequestDetail {
    val item = JSONObject(json)
    val number = item.requiredPositiveInt("number")
    if (number != expectedNumber) throw IOException("Pull request inválido")
    val title = item.requiredNonBlankString("title")
    val rawState = item.requiredNonBlankString("state")
    val mergedAt = when (val raw = item.opt("merged_at")) {
        null, JSONObject.NULL -> null
        is String -> try { Instant.parse(raw) } catch (_: Exception) { throw IOException("Pull request inválido") }
        else -> throw IOException("Pull request inválido")
    }
    val state = when {
        rawState == "closed" && mergedAt != null -> GitHubPullRequestState.MERGED
        rawState == "closed" -> GitHubPullRequestState.CLOSED
        rawState == "open" && mergedAt == null -> GitHubPullRequestState.OPEN
        else -> throw IOException("Pull request inválido")
    }
    val head = item.optJSONObject("head") ?: throw IOException("Pull request inválido")
    val base = item.optJSONObject("base") ?: throw IOException("Pull request inválido")
    val baseRepository = base.optJSONObject("repo")?.requiredRepositoryName()
        ?: throw IOException("Pull request inválido")
    if (!baseRepository.equals(repository, ignoreCase = true)) throw IOException("Pull request inválido")
    return GitHubPullRequestDetail(
        number = number,
        title = title,
        state = state,
        repository = repository,
        author = item.optJSONObject("user")?.optionalString("login")?.takeIf(String::isNotBlank),
        body = item.optionalString("body"),
        labels = item.optionalLabels("labels"),
        headBranch = head.requiredNonBlankString("ref"),
        headRepository = head.optJSONObject("repo")?.optionalRepositoryName(),
        baseBranch = base.requiredNonBlankString("ref"),
        baseRepository = baseRepository
    )
}

internal fun parseGitHubPullRequestComments(json: String): List<GitHubPullRequestComment> {
    val array = JSONArray(json)
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = runCatching { item.requiredPositiveLong("id") }.getOrNull() ?: continue
            add(
                GitHubPullRequestComment(
                    id = id,
                    author = runCatching { item.optJSONObject("user")?.optionalString("login")?.takeIf(String::isNotBlank) }.getOrNull(),
                    body = runCatching { item.optionalString("body") }.getOrNull(),
                    createdAt = runCatching { item.optionalString("created_at")?.let(Instant::parse) }.getOrNull()
                )
            )
        }
    }.sortedWith(compareBy<GitHubPullRequestComment> { it.createdAt }.thenBy { it.id })
}

internal fun parseGitHubPullRequestReviews(json: String): List<GitHubPullRequestReview> {
    val array = JSONArray(json)
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = runCatching { item.requiredPositiveLong("id") }.getOrNull() ?: continue
            val state = runCatching { item.requiredNonBlankString("state") }.getOrNull()
                ?.takeIf { it in REVIEW_STATES } ?: continue
            add(
                GitHubPullRequestReview(
                    id = id,
                    author = runCatching { item.optJSONObject("user")?.optionalString("login")?.takeIf(String::isNotBlank) }.getOrNull(),
                    state = state,
                    body = runCatching { item.optionalString("body") }.getOrNull(),
                    submittedAt = runCatching { item.optionalString("submitted_at")?.let(Instant::parse) }.getOrNull()
                )
            )
        }
    }.sortedWith(compareBy<GitHubPullRequestReview> { it.submittedAt }.thenBy { it.id })
}

internal class GitHubPullRequestPager(
    client: GitHubHttpClient,
    accessToken: String,
    filter: GitHubPullRequestFilter
) {
    @Volatile private var metadata = GitHubPullRequestSearch(0, false, emptyList())
    @Volatile private var currentItems = emptyList<GitHubPullRequest>()
    private var loadedPages = 0
    private val pager = GitHubPaginator(
        client = client,
        firstPath = githubPullRequestsPath(filter),
        accessToken = accessToken,
        decode = { json ->
            parseGitHubPullRequestSearch(json, filter).also { metadata = it }.items
        },
        itemKey = GitHubPullRequest::identity
    )

    fun loadNext(): GitHubPullRequestPageResult {
        if (loadedPages >= 20) {
            val current = metadata
            return GitHubPullRequestPageResult.Loaded(
                GitHubPullRequestPage(
                    items = currentItems,
                    hasNext = false,
                    totalCount = current.totalCount,
                    incomplete = current.incomplete
                )
            )
        }
        val result = pager.loadNext()
        val current = metadata
        val hasNext = when (result) {
            is GitHubPageResult.Loaded -> {
                loadedPages++
                currentItems = result.items
                result.hasNext && result.items.size < 1_000 && loadedPages < 20
            }
            else -> false
        }
        val page = GitHubPullRequestPage(result.items, hasNext, current.totalCount, current.incomplete)
        return when (result) {
            is GitHubPageResult.Loaded -> GitHubPullRequestPageResult.Loaded(page)
            is GitHubPageResult.RateLimited -> GitHubPullRequestPageResult.RateLimited(page, result.retryAtEpochMillis)
            is GitHubPageResult.Failed -> GitHubPullRequestPageResult.Failed(page, result.message, result.error)
        }
    }

    fun cancel() = pager.cancel()

}

internal class GitHubPullRequestLoader(
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

    fun load(pullRequest: GitHubPullRequest): GitHubPullRequestDetailResult {
        val path = detailPath(pullRequest)
        val call = synchronized(lock) {
            if (cancelled) return GitHubPullRequestDetailResult.Failed(GitHubHttpError.CANCELLED)
            client.get(path, accessToken).also { activeCall = it }
        }
        return try {
            when (val result = call.execute()) {
                is GitHubHttpResult.Failure -> when (result.statusCode) {
                    404, 410 -> GitHubPullRequestDetailResult.Unavailable
                    else -> GitHubPullRequestDetailResult.Failed(result.error, result.retryAtEpochMillis)
                }
                is GitHubHttpResult.Success -> try {
                    GitHubPullRequestDetailResult.Loaded(
                        parseGitHubPullRequestDetail(result.body, pullRequest.repository, pullRequest.number)
                    )
                } catch (_: Exception) {
                    GitHubPullRequestDetailResult.Failed(GitHubHttpError.UNEXPECTED)
                }
            }
        } finally {
            synchronized(lock) { if (activeCall === call) activeCall = null }
        }
    }

    companion object {
        fun detailPath(pullRequest: GitHubPullRequest): String =
            "/repos/${encodePullRequestSegment(pullRequest.repositoryPart(0))}/${encodePullRequestSegment(pullRequest.repositoryPart(1))}/pulls/${pullRequest.number}"

        fun commentsPath(pullRequest: GitHubPullRequest): String =
            "/repos/${encodePullRequestSegment(pullRequest.repositoryPart(0))}/${encodePullRequestSegment(pullRequest.repositoryPart(1))}/issues/${pullRequest.number}/comments?per_page=50"

        fun reviewsPath(pullRequest: GitHubPullRequest): String =
            "/repos/${encodePullRequestSegment(pullRequest.repositoryPart(0))}/${encodePullRequestSegment(pullRequest.repositoryPart(1))}/pulls/${pullRequest.number}/reviews?per_page=50"
    }
}

internal class GitHubPullRequestCommentPager(client: GitHubHttpClient, accessToken: String, pullRequest: GitHubPullRequest) {
    private val pager = GitHubPaginator(client, GitHubPullRequestLoader.commentsPath(pullRequest), accessToken, ::parseGitHubPullRequestComments) { it.id.toString() }
    fun loadNext() = pager.loadNext()
    fun cancel() = pager.cancel()
}

internal class GitHubPullRequestReviewPager(client: GitHubHttpClient, accessToken: String, pullRequest: GitHubPullRequest) {
    private val pager = GitHubPaginator(client, GitHubPullRequestLoader.reviewsPath(pullRequest), accessToken, ::parseGitHubPullRequestReviews) { it.id.toString() }
    fun loadNext() = pager.loadNext()
    fun cancel() = pager.cancel()
}

private fun JSONObject.toGitHubPullRequest(filter: GitHubPullRequestFilter): GitHubPullRequest {
    if (optJSONObject("pull_request") == null) throw IOException("Item não é pull request")
    val number = requiredPositiveInt("number")
    val title = requiredNonBlankString("title")
    val rawState = requiredNonBlankString("state")
    val state = when (filter) {
        GitHubPullRequestFilter.OPEN -> {
            if (rawState != "open") throw IOException("Pull request inválido")
            GitHubPullRequestState.OPEN
        }
        GitHubPullRequestFilter.CLOSED -> {
            if (rawState != "closed") throw IOException("Pull request inválido")
            GitHubPullRequestState.CLOSED
        }
        GitHubPullRequestFilter.MERGED -> {
            if (rawState != "closed") throw IOException("Pull request inválido")
            GitHubPullRequestState.MERGED
        }
    }
    val repository = requiredRepositoryUrl("repository_url")
    return GitHubPullRequest(
        number = number,
        title = title,
        state = state,
        repository = repository,
        author = optJSONObject("user")?.optionalString("login")?.takeIf(String::isNotBlank),
        labels = optionalLabels("labels"),
        updatedAt = optionalString("updated_at")?.let { raw ->
            try { Instant.parse(raw) } catch (_: Exception) { null }
        }
    )
}

private fun JSONObject.requiredRepositoryUrl(key: String): String {
    val uri = try { URI(requiredNonBlankString(key)) } catch (_: Exception) { throw IOException("Pull request inválido") }
    if (uri.scheme != "https" || !uri.host.equals("api.github.com", ignoreCase = true) ||
        uri.port != -1 || uri.userInfo != null || uri.query != null || uri.fragment != null
    ) throw IOException("Pull request inválido")
    val segments = uri.path.split('/').drop(1)
    if (segments.size != 3 || segments[0] != "repos" ||
        segments[1].isBlank() || segments[2].isBlank() ||
        segments.drop(1).any { it == "." || it == ".." || it.contains('/') || it.any(Char::isWhitespace) }
    ) throw IOException("Pull request inválido")
    return "${segments[1]}/${segments[2]}"
}

private fun JSONObject.requiredRepositoryName(): String =
    optionalRepositoryName() ?: throw IOException("Pull request inválido")

private fun JSONObject.optionalRepositoryName(): String? =
    optionalString("full_name")?.takeIf { value ->
        val parts = value.split('/')
        parts.size == 2 && parts.none { it.isBlank() || it.any(Char::isWhitespace) }
    }

private fun JSONObject.requiredPositiveLong(key: String): Long =
    (opt(key) as? Number)?.let { raw -> raw.toLong().takeIf { it > 0 && raw.toDouble() == it.toDouble() } }
        ?: throw IOException("Pull request inválido")

private fun JSONObject.requiredPositiveInt(key: String): Int {
    val raw = opt(key) as? Number ?: throw IOException("Pull request inválido")
    val long = raw.toLong()
    if (raw.toDouble() != long.toDouble() || long !in 1..Int.MAX_VALUE) throw IOException("Pull request inválido")
    return long.toInt()
}

private fun JSONObject.optionalNonNegativeInt(key: String): Int? {
    val raw = opt(key) as? Number ?: return null
    val long = raw.toLong()
    if (raw.toDouble() != long.toDouble() || long !in 0..Int.MAX_VALUE) return null
    return long.toInt()
}

private fun JSONObject.requiredNonBlankString(key: String): String =
    optionalString(key)?.takeIf(String::isNotBlank) ?: throw IOException("Pull request inválido")

private fun JSONObject.optionalString(key: String): String? = when (val value = opt(key)) {
    null, JSONObject.NULL -> null
    is String -> value
    else -> throw IOException("Pull request inválido")
}

private fun JSONObject.optionalLabels(key: String): List<String> {
    val raw = opt(key)
    if (raw == null || raw == JSONObject.NULL) return emptyList()
    val array = raw as? JSONArray ?: throw IOException("Pull request inválido")
    return buildList {
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.optionalString("name")?.takeIf(String::isNotBlank)?.let(::add)
        }
    }
}

private fun GitHubPullRequest.repositoryPart(index: Int): String {
    val parts = repository.split('/')
    require(parts.size == 2 && parts.all(String::isNotBlank) && number > 0)
    return parts[index]
}

private fun encodePullRequestSegment(value: String) =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

private val REVIEW_STATES = setOf("APPROVED", "CHANGES_REQUESTED", "COMMENTED", "DISMISSED", "PENDING")
