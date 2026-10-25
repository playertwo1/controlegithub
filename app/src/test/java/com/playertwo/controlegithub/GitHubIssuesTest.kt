package com.playertwo.controlegithub

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubIssuesTest {
    @Test fun repositoryIssuesPathEncodesOwnerAndRepoAndState() {
        assertEquals(
            "/repos/acme/mobile%20app/issues?state=open&per_page=50",
            githubRepositoryIssuesPath("acme/mobile app", GitHubIssueState.OPEN)
        )
        assertEquals(
            "/repos/acme/Mobile/issues?state=closed&per_page=50",
            githubRepositoryIssuesPath("acme/Mobile", GitHubIssueState.CLOSED)
        )
        assertEquals(
            "/repos/acme/Mobile/issues?state=all&per_page=50",
            githubRepositoryIssuesPath("acme/Mobile", GitHubIssueState.ALL)
        )
    }

    @Test fun parseGitHubIssuesSupportsFallbackRepositoryAndSkipsPullRequests() {
        val json = """[
            {
                "number": 14,
                "title": "Falha ao sincronizar",
                "state": "open",
                "html_url": "https://github.com/acme/Mobile/issues/14",
                "user": { "login": "alice" },
                "assignees": [{ "login": "bob" }],
                "labels": [{ "name": "bug" }],
                "updated_at": "2026-10-20T12:00:00Z"
            },
            {
                "number": 15,
                "title": "PR infiltrado no endpoint de issues",
                "state": "open",
                "html_url": "https://github.com/acme/Mobile/pull/15",
                "pull_request": { "url": "https://api.github.com/repos/acme/Mobile/pulls/15" },
                "user": { "login": "carol" }
            }
        ]"""

        val parsed = parseGitHubIssues(json, fallbackRepository = "acme/Mobile")
        assertEquals(
            listOf(
                GitHubIssue(
                    number = 14,
                    title = "Falha ao sincronizar",
                    state = GitHubIssueState.OPEN,
                    repository = "acme/Mobile",
                    author = "alice",
                    assignees = listOf("bob"),
                    labels = listOf("bug"),
                    updatedAt = Instant.parse("2026-10-20T12:00:00Z")
                )
            ),
            parsed
        )
    }
}
