package com.playertwo.controlegithub

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import org.commonmark.Extension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Document
import org.commonmark.parser.Parser

internal const val MAX_GITHUB_MARKDOWN_CHARS = 256 * 1024
internal const val MAX_GITHUB_MARKDOWN_AST_NODES = 4_096
internal const val MAX_GITHUB_MARKDOWN_ROUTE_DEPTH = 10

internal data class GitHubMarkdownRepositoryImages(
    val owner: String,
    val repository: String,
    val branch: String
)

internal fun resolveGitHubMarkdownImage(
    rawSource: String,
    repository: GitHubMarkdownRepositoryImages?
): String? {
    val source = rawSource.trim()
    if (source.isEmpty() || source.length > 8_192 || source.any(Char::isISOControl)) return null
    val uri = runCatching { URI(source) }.getOrNull() ?: return null
    if (uri.isAbsolute) {
        if (uri.scheme.equals("https", true) && uri.rawUserInfo == null && uri.port in setOf(-1, 443) && !uri.host.isNullOrBlank()) {
            return uri.toASCIIString()
        }
        return null
    }
    val context = repository ?: return null
    if (uri.rawAuthority != null || source.startsWith('/') || source.contains('\\') || uri.isOpaque) return null
    if (!context.owner.matches(Regex("[A-Za-z0-9_.-]{1,100}")) ||
        !context.repository.matches(Regex("[A-Za-z0-9_.-]{1,100}")) ||
        context.branch.isBlank() || context.branch.length > 255 || context.branch.any(Char::isISOControl)
    ) return null

    fun encode(segment: String) = URLEncoder.encode(segment, StandardCharsets.UTF_8.name()).replace("+", "%20")
    val encodedBranch = context.branch.split('/').joinToString("/") { encode(it) }
    val repoPrefix = "/${encode(context.owner)}/${encode(context.repository)}/"
    val base = URI("https://raw.githubusercontent.com$repoPrefix$encodedBranch/")
    val resolved = runCatching { base.resolve(uri).normalize() }.getOrNull() ?: return null
    if (!resolved.scheme.equals("https", true) || resolved.host != "raw.githubusercontent.com" ||
        resolved.rawUserInfo != null || !resolved.rawPath.orEmpty().startsWith(repoPrefix)
    ) return null
    return resolved.toASCIIString()
}

internal fun githubHtmlImageSources(html: String): List<Pair<String, String>> {
    val tags = Regex("(?is)<img\\b([^>]*)>")
    val attribute = Regex("(?is)([a-z_:][-a-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
    return tags.findAll(html).mapNotNull { tag ->
        val attributes = attribute.findAll(tag.groupValues[1]).associate { match ->
            match.groupValues[1].lowercase() to (match.groups[2]?.value ?: match.groups[3]?.value ?: match.groups[4]?.value.orEmpty())
        }
        attributes["src"]?.takeIf(String::isNotBlank)?.let { it to attributes["alt"].orEmpty().ifBlank { "Imagem do README" } }
    }.toList()
}

internal sealed interface GitHubMarkdownDestination {
    data class Repository(val owner: String, val repository: String) : GitHubMarkdownDestination
    data class Issue(val owner: String, val repository: String, val number: Int) : GitHubMarkdownDestination
    data class PullRequest(val owner: String, val repository: String, val number: Int) : GitHubMarkdownDestination
}

/** Reuses an existing destination or appends one until the bounded route depth is reached. */
internal fun appendMarkdownRoute(
    stack: List<GitHubMarkdownDestination>,
    destination: GitHubMarkdownDestination
): List<GitHubMarkdownDestination>? {
    val existingIndex = stack.indexOf(destination)
    if (existingIndex >= 0) return stack.take(existingIndex + 1)
    if (stack.size >= MAX_GITHUB_MARKDOWN_ROUTE_DEPTH) return null
    return stack + destination
}

internal sealed interface GitHubMarkdownLinkTarget {
    data class InApp(val destination: GitHubMarkdownDestination) : GitHubMarkdownLinkTarget
    data class External(val url: String, val host: String) : GitHubMarkdownLinkTarget
}

private val markdownParser: Parser by lazy {
    val tables = TablesExtension.builder().maxCells(10_000).build()
    Parser.builder()
        .extensions(listOf(tables as Extension))
        .maxOpenBlockParsers(100)
        .maxInlineNesting(100)
        .build()
}

internal fun parseGitHubMarkdown(markdown: String): Document? {
    if (markdown.length > MAX_GITHUB_MARKDOWN_CHARS) return null
    return try {
        (markdownParser.parse(markdown) as? Document)?.takeIf(::hasAcceptableNodeCount)
    } catch (_: RuntimeException) {
        null
    }
}

private fun hasAcceptableNodeCount(document: Document): Boolean {
    val pending = ArrayDeque<org.commonmark.node.Node>()
    pending.addLast(document)
    var nodeCount = 0
    while (pending.isNotEmpty()) {
        val current = pending.removeLast()
        nodeCount++
        if (nodeCount > MAX_GITHUB_MARKDOWN_AST_NODES) return false
        var child = current.firstChild
        while (child != null) {
            pending.addLast(child)
            child = child.next
        }
    }
    return true
}

/** Returns null for malformed, unsafe, relative, or unexpectedly ported links. */
internal fun classifyGitHubMarkdownLink(rawDestination: String): GitHubMarkdownLinkTarget? {
    if (rawDestination.isBlank() || rawDestination.length > 8_192 || rawDestination.any(Char::isISOControl)) {
        return null
    }

    val uri = try {
        URI(rawDestination)
    } catch (_: Exception) {
        return null
    }
    if (!uri.isAbsolute || uri.isOpaque || !uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.rawUserInfo != null || uri.port !in setOf(-1, 443) || uri.host.isNullOrBlank()) return null
    if (uri.rawAuthority.orEmpty().endsWith(':')) return null

    val host = uri.host
    if (host.equals("github.com", ignoreCase = true)) {
        val path = uri.rawPath.orEmpty()
        val destination = parseGitHubPath(path)
        if (destination != null) return GitHubMarkdownLinkTarget.InApp(destination)
        if (isMalformedIssueOrPullPath(path)) return null
    } else if (host.startsWith("github.com", ignoreCase = true)) {
        // Reject lookalike hosts such as github.com.evil.test instead of treating them as ordinary externals.
        return null
    }

    return GitHubMarkdownLinkTarget.External(uri.toASCIIString(), host)
}

private fun parseGitHubPath(rawPath: String): GitHubMarkdownDestination? {
    // Percent-encoded segments are intentionally left as browser fallbacks; no path decoding is guessed.
    if ('%' in rawPath || '\\' in rawPath || rawPath.any(Char::isISOControl)) return null
    val parts = rawPath.split('/').filter(String::isNotEmpty)
    if (parts.size < 2) return null
    val owner = parts[0]
    val repository = parts[1]
    if (!owner.matches(Regex("[A-Za-z0-9-]{1,39}"))) return null
    if (!repository.matches(Regex("[A-Za-z0-9._-]{1,100}")) || repository == "." || repository == "..") return null

    if (parts.size == 2) return GitHubMarkdownDestination.Repository(owner, repository)
    if (parts.size != 4) return null
    val number = parts[3].toIntOrNull()?.takeIf { it > 0 } ?: return null
    return when (parts[2]) {
        "issues" -> GitHubMarkdownDestination.Issue(owner, repository, number)
        "pull" -> GitHubMarkdownDestination.PullRequest(owner, repository, number)
        else -> null
    }
}

private fun isMalformedIssueOrPullPath(rawPath: String): Boolean {
    val parts = rawPath.split('/').filter(String::isNotEmpty)
    if (parts.size < 4 || parts[2] !in setOf("issues", "pull")) return false
    if (!parts[0].matches(Regex("[A-Za-z0-9-]{1,39}"))) return true
    if (!parts[1].matches(Regex("[A-Za-z0-9._-]{1,100}"))) return true
    return parts[3].toIntOrNull()?.let { it <= 0 } ?: true
}
