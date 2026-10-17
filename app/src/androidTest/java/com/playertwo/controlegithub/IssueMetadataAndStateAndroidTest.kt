package com.playertwo.controlegithub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class IssueMetadataAndStateAndroidTest {

    private fun fixtureExpectedIssue(state: GitHubIssueState = GitHubIssueState.OPEN) = GitHubIssue(
        number = 42,
        title = "Titulo Antigo",
        state = state,
        repository = "acme/app",
        author = "author",
        assignees = emptyList(),
        labels = emptyList(),
        updatedAt = null
    )

    @Test
    fun parseGitHubIssueDetailMapsAssigneesAndLabelsCorrectly() {
        val json = """
            {
                "number": 42,
                "title": "Issue com metadados",
                "state": "closed",
                "html_url": "https://github.com/acme/app/issues/42",
                "user": {"login": "author1"},
                "assignees": [{"login": "dev1"}, {"login": "dev2"}],
                "labels": [{"name": "bug"}, {"name": "frontend"}],
                "body": "Descricao completa",
                "updated_at": "2026-10-17T20:00:00Z"
            }
        """.trimIndent()

        val detail = parseGitHubIssueDetail(json, fixtureExpectedIssue())
        assertEquals(42, detail.issue.number)
        assertEquals("Issue com metadados", detail.issue.title)
        assertEquals(GitHubIssueState.CLOSED, detail.issue.state)
        assertEquals("author1", detail.issue.author)
        assertEquals(listOf("dev1", "dev2"), detail.issue.assignees)
        assertEquals(listOf("bug", "frontend"), detail.issue.labels)
        assertEquals("Descricao completa", detail.body)
        assertNotNull(detail.issue.updatedAt)
    }

    @Test
    fun parseGitHubIssueDetailHandlesEmptyAssigneesAndLabels() {
        val json = """
            {
                "number": 42,
                "title": "Issue simples",
                "state": "open",
                "html_url": "https://github.com/acme/app/issues/42",
                "user": {"login": "author1"},
                "assignees": [],
                "labels": [],
                "body": null
            }
        """.trimIndent()

        val detail = parseGitHubIssueDetail(json, fixtureExpectedIssue())
        assertEquals(emptyList<String>(), detail.issue.assignees)
        assertEquals(emptyList<String>(), detail.issue.labels)
        assertEquals(null, detail.body)
    }
}
