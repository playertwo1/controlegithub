package com.playertwo.controlegithub

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.node.Document
import org.commonmark.node.Heading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubMarkdownTest {
    @Test fun parsesSupportedCommonMarkAndGfmTablesWithoutProducingHtml() {
        val document = parseGitHubMarkdown(
            """# Título
               |**ênfase** `código` e [issue](https://github.com/acme/mobile/issues/7)
               |
               || Nome | Valor |
               || --- | --- |
               || Unicode | ação |
               |
               |<script>literal</script>
            """.trimMargin()
        )

        assertTrue(document is Document)
        val topLevel = document!!.children().toList()
        assertTrue(topLevel[0] is Heading)
        assertTrue(topLevel.any { it is TableBlock })
        assertTrue(topLevel.any { it is org.commonmark.node.HtmlBlock })
    }

    @Test fun oversizedInputFallsBackWithoutTruncatingOrThrowing() {
        val oversized = "á".repeat(MAX_GITHUB_MARKDOWN_CHARS + 1)

        assertNull(parseGitHubMarkdown(oversized))
        assertEquals(MAX_GITHUB_MARKDOWN_CHARS + 1, oversized.length)
    }

    @Test fun excessiveAstNodeCountFallsBackInsteadOfCreatingAnUnboundedComposeTree() {
        val manyBlocks = buildString {
            repeat(MAX_GITHUB_MARKDOWN_AST_NODES + 1) { append("block\n\n") }
        }
        assertTrue(manyBlocks.length < MAX_GITHUB_MARKDOWN_CHARS)

        assertNull(parseGitHubMarkdown(manyBlocks))
    }

    @Test fun markdownRouteReusesExistingDestinationInsteadOfGrowingCycles() {
        val first = GitHubMarkdownDestination.Repository("acme", "mobile")
        val second = GitHubMarkdownDestination.Repository("acme", "docs")
        val third = GitHubMarkdownDestination.Issue("acme", "mobile", 7)
        val stack = listOf(first, second, third)

        assertEquals(listOf(first, second), appendMarkdownRoute(stack, second))
        assertEquals(stack, appendMarkdownRoute(stack, third))
    }

    @Test fun markdownRouteDepthIsBounded() {
        val stack = (0 until MAX_GITHUB_MARKDOWN_ROUTE_DEPTH).map { index ->
            GitHubMarkdownDestination.Repository("acme", "repo-$index")
        }
        val next = GitHubMarkdownDestination.Repository("acme", "one-too-many")

        assertEquals(MAX_GITHUB_MARKDOWN_ROUTE_DEPTH, stack.size)
        assertNull(appendMarkdownRoute(stack, next))
    }

    @Test fun recognizedGithubUrlsMapOnlyToUnambiguousInAppRoutes() {
        assertEquals(
            GitHubMarkdownLinkTarget.InApp(GitHubMarkdownDestination.Repository("acme", "mobile")),
            classifyGitHubMarkdownLink("https://github.com/acme/mobile")
        )
        assertEquals(
            GitHubMarkdownLinkTarget.InApp(GitHubMarkdownDestination.Issue("acme", "mobile", 7)),
            classifyGitHubMarkdownLink("https://github.com/acme/mobile/issues/7#issuecomment-99")
        )
        assertEquals(
            GitHubMarkdownLinkTarget.InApp(GitHubMarkdownDestination.PullRequest("acme", "mobile", 12)),
            classifyGitHubMarkdownLink("https://github.com/acme/mobile/pull/12")
        )
    }

    @Test fun malformedAndUnsafeLinksRemainInert() {
        listOf(
            "javascript:alert(1)",
            "http://github.com/acme/mobile",
            "file:///etc/passwd",
            "content://provider/item",
            "data:text/html,hello",
            "../relative.md",
            "https://github.com.evil.test/acme/mobile",
            "https://github.com@evil.test/acme/mobile",
            "https://github.com:444/acme/mobile",
            "https://github.com/acme/mobile/issues/nope",
            "https://github.com/acme/mobile/issues/0",
            "https://github.com/acme/mobile/issues/nope/extra",
            "https://github.com/acme/mobile/pull/0/files",
            "https://example.test/%zz"
        ).forEach { unsafe -> assertNull("Unexpected active link: $unsafe", classifyGitHubMarkdownLink(unsafe)) }

        assertFalse(classifyGitHubMarkdownLink("https://github.com.evil.test/acme/mobile") is GitHubMarkdownLinkTarget.InApp)
    }

    @Test fun externalHttpsIsReturnedForExplicitConfirmation() {
        assertEquals(
            GitHubMarkdownLinkTarget.External("https://example.test/docs?q=1", "example.test"),
            classifyGitHubMarkdownLink("https://example.test/docs?q=1")
        )
        assertEquals(
            GitHubMarkdownLinkTarget.External("https://github.com/acme/mobile/issues", "github.com"),
            classifyGitHubMarkdownLink("https://github.com/acme/mobile/issues")
        )
    }
}

private fun org.commonmark.node.Node.children(): Sequence<org.commonmark.node.Node> = sequence {
    var child = firstChild
    while (child != null) {
        yield(child)
        child = child.next
    }
}
