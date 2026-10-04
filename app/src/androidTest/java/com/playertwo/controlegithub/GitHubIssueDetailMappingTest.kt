package com.playertwo.controlegithub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubIssueDetailMappingTest {
    private val issue = GitHubIssue(
        number = 18,
        title = "Título da lista",
        state = GitHubIssueState.OPEN,
        repository = "fixture-owner/sample-repo",
        author = "old-author",
        assignees = emptyList(),
        labels = emptyList(),
        updatedAt = null
    )

    @Test fun buildsIssueAndCommentRoutesFromValidatedIdentity() {
        assertEquals(
            "/repos/fixture-owner/sample-repo/issues/18",
            GitHubIssueDetailLoader.issuePath(issue)
        )
        assertEquals(
            "/repos/fixture-owner/sample-repo/issues/18/comments?per_page=50",
            GitHubIssueDetailLoader.commentsPath(issue)
        )
    }

    @Test fun mapsSelectedIssueBodyLabelsAndInertMarkdownWithoutChangingIdentity() {
        val detail = parseGitHubIssueDetail(
            """{"number":18,"title":"Título atual","state":"closed","html_url":"https://github.com/fixture-owner/sample-repo/issues/18","body":"<script>bad()</script> **texto** https://evil.example","user":{"login":"fixture-author"},"labels":[{"name":"bug"}],"updated_at":"2026-10-04T12:30:00Z"}""",
            issue
        )

        assertEquals(issue.identity, detail.issue.identity)
        assertEquals("Título atual", detail.issue.title)
        assertEquals(GitHubIssueState.CLOSED, detail.issue.state)
        assertEquals("fixture-author", detail.issue.author)
        assertEquals(listOf("bug"), detail.issue.labels)
        assertEquals("<script>bad()</script> **texto** https://evil.example", detail.body)
    }

    @Test fun absentBodyAndAuthorRemainEmptyAndPullRequestOrMismatchedIdentityAreRejected() {
        val detail = parseGitHubIssueDetail(
            """{"number":18,"title":"Atual","state":"open","html_url":"https://github.com/fixture-owner/sample-repo/issues/18","body":null,"user":null}""",
            issue
        )
        assertNull(detail.body)
        assertNull(detail.issue.author)
        assertTrue(detail.issue.labels.isEmpty())

        listOf(
            """{"number":19,"title":"Outro","state":"open","html_url":"https://github.com/fixture-owner/sample-repo/issues/19"}""",
            """{"number":18,"title":"PR","state":"open","html_url":"https://github.com/fixture-owner/sample-repo/issues/18","pull_request":{}}""",
            """{"number":18,"title":"Host externo","state":"open","html_url":"https://evil.example/fixture-owner/sample-repo/issues/18"}"""
        ).forEach { invalid ->
            assertFalse(runCatching { parseGitHubIssueDetail(invalid, issue) }.isSuccess)
        }
    }

    @Test fun mapsCommentsInResponseOrderAndSkipsMalformedEntries() {
        val comments = parseGitHubIssueComments(
            """[
                {"id":20,"body":"**first**","user":{"login":"fixture-a"},"created_at":"2026-10-04T12:00:00Z"},
                {"id":"bad","body":"invalid"},
                {"id":21,"body":"<b>second</b>","user":null,"created_at":"not-a-date"},
                {"id":22,"body":"valid malgré author inválido","user":{"login":7},"created_at":false}
            ]"""
        )

        assertEquals(listOf(20L, 21L, 22L), comments.map { it.id })
        assertEquals("fixture-a", comments.first().author)
        assertEquals("**first**", comments.first().body)
        assertEquals("<b>second</b>", comments[1].body)
        assertNull(comments[1].author)
        assertNull(comments[1].createdAt)
        assertEquals("valid malgré author inválido", comments.last().body)
        assertNull(comments.last().author)
        assertNull(comments.last().createdAt)
        assertTrue(parseGitHubIssueComments("[]").isEmpty())
    }
}
