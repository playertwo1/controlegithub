package com.playertwo.controlegithub

import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal data class GitHubReleaseAsset(
    val id: Long,
    val name: String,
    val sizeBytes: Long,
    val downloadCount: Long,
    val contentType: String?,
    val browserDownloadUrl: String?
) {
    val isApk: Boolean get() = name.lowercase(Locale.ROOT).endsWith(".apk")
    val isChecksum: Boolean get() = name.lowercase(Locale.ROOT).let {
        it.startsWith("sha256sums") || it.endsWith(".sha256")
    }
}

internal data class GitHubRelease(
    val id: Long,
    val tagName: String,
    val name: String?,
    val body: String?,
    val isDraft: Boolean,
    val isPrerelease: Boolean,
    val author: String?,
    val publishedAt: Instant?,
    val htmlUrl: String?,
    val assets: List<GitHubReleaseAsset>
) {
    val title: String get() = name?.takeIf(String::isNotBlank) ?: tagName
    val apkAssets: List<GitHubReleaseAsset> get() = assets.filter(GitHubReleaseAsset::isApk)
}

internal fun parseGitHubReleases(json: String): List<GitHubRelease> {
    val array = JSONArray(json)
    return List(array.length()) { parseGitHubRelease(array.getJSONObject(it)) }
}

internal fun parseGitHubRelease(release: JSONObject): GitHubRelease {
    val assetsArray = release.optJSONArray("assets") ?: JSONArray()
    return GitHubRelease(
        id = release.requiredPositiveLong("id"),
        tagName = release.requiredString("tag_name"),
        name = release.optionalString("name"),
        body = release.optionalString("body"),
        isDraft = release.opt("draft") as? Boolean ?: false,
        isPrerelease = release.opt("prerelease") as? Boolean ?: false,
        author = release.optJSONObject("author")?.optionalString("login"),
        publishedAt = (release.optionalString("published_at") ?: release.optionalString("created_at"))
            ?.let { value ->
                try {
                    Instant.parse(value)
                } catch (_: DateTimeParseException) {
                    null
                }
            },
        htmlUrl = release.optionalString("html_url"),
        assets = List(assetsArray.length()) { index ->
            val asset = assetsArray.getJSONObject(index)
            GitHubReleaseAsset(
                id = asset.requiredPositiveLong("id"),
                name = asset.requiredString("name"),
                sizeBytes = asset.optionalNonNegativeLong("size") ?: 0,
                downloadCount = asset.optionalNonNegativeLong("download_count") ?: 0,
                contentType = asset.optionalString("content_type"),
                browserDownloadUrl = asset.optionalString("browser_download_url")
                    ?.takeIf { it.startsWith("https://github.com/") }
            )
        }
    )
}

/** Release mais recente publicada (não rascunho nem pré-release), como o selo "Latest" do GitHub. */
internal fun latestReleaseId(releases: List<GitHubRelease>): Long? = releases
    .filter { !it.isDraft && !it.isPrerelease }
    .maxByOrNull { it.publishedAt ?: Instant.EPOCH }
    ?.id

internal fun formatByteSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.forLanguageTag("pt-BR"), "%.1f %s", value, units[unit])
}

internal class GitHubReleasePager(
    client: GitHubHttpClient,
    accessToken: String,
    repository: GitHubRepository,
    cacheStore: GitHubCacheStore? = null,
    accountLogin: String? = null
) {
    private val pager = GitHubPaginator(
        client = client,
        firstPath = firstPagePath(repository),
        accessToken = accessToken,
        decode = ::parseGitHubReleases,
        itemKey = { it.id.toString() },
        cacheStore = cacheStore,
        accountLogin = accountLogin
    )

    fun loadNext(): GitHubPageResult<GitHubRelease> = pager.loadNext()

    fun cancel() = pager.cancel()

    internal companion object {
        fun firstPagePath(repository: GitHubRepository) =
            "/repos/${encode(repository.owner)}/${encode(repository.name)}/releases?per_page=30"

        private fun encode(value: String) =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }
}

private fun JSONObject.requiredString(key: String): String =
    (opt(key) as? String)?.takeIf(String::isNotBlank)
        ?: throw IOException("Resposta de releases inválida")

private fun JSONObject.optionalString(key: String): String? = when (val value = opt(key)) {
    null, JSONObject.NULL -> null
    is String -> value
    else -> throw IOException("Resposta de releases inválida")
}

private fun JSONObject.requiredPositiveLong(key: String): Long =
    optionalNonNegativeLong(key)?.takeIf { it > 0 } ?: throw IOException("Resposta de releases inválida")

private fun JSONObject.optionalNonNegativeLong(key: String): Long? = when (val value = opt(key)) {
    null, JSONObject.NULL -> null
    is Number -> value.toLong().takeIf { it >= 0 && value.toDouble() == it.toDouble() }
        ?: throw IOException("Resposta de releases inválida")
    else -> throw IOException("Resposta de releases inválida")
}
