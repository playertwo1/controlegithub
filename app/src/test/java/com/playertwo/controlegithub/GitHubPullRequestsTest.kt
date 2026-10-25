package com.playertwo.controlegithub

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubPullRequestsTest {
    @Test fun filterBuildsEncodedGlobalSearchQueryForEachState() {
        assertEquals(
            "/search/issues?q=is%3Apr+involves%3A%40me+state%3Aopen&sort=updated&order=desc&per_page=50",
            githubPullRequestsPath(GitHubPullRequestFilter.OPEN)
        )
        assertEquals(
            "/search/issues?q=is%3Apr+involves%3A%40me+state%3Aclosed+-is%3Amerged&sort=updated&order=desc&per_page=50",
            githubPullRequestsPath(GitHubPullRequestFilter.CLOSED)
        )
        assertEquals(
            "/search/issues?q=is%3Apr+involves%3A%40me+is%3Amerged&sort=updated&order=desc&per_page=50",
            githubPullRequestsPath(GitHubPullRequestFilter.MERGED)
        )
    }

    @Test fun detailPathsUseOnlyEncodedRepositorySegmentsAndSelectedNumber() {
        val pullRequest = GitHubPullRequest(
            number = 12,
            title = "PR",
            state = GitHubPullRequestState.OPEN,
            repository = "owner/mobile app",
            author = null,
            labels = emptyList(),
            updatedAt = null
        )

        assertEquals(
            "/repos/owner/mobile%20app/pulls/12",
            GitHubPullRequestLoader.detailPath(pullRequest)
        )
        assertEquals(
            "/repos/owner/mobile%20app/issues/12/comments?per_page=50",
            GitHubPullRequestLoader.commentsPath(pullRequest)
        )
        assertEquals(
            "/repos/owner/mobile%20app/pulls/12/reviews?per_page=50",
            GitHubPullRequestLoader.reviewsPath(pullRequest)
        )
    }

    @Test fun repositoryPullRequestsPathUsesEncodedSegmentsAndState() {
        assertEquals(
            "/repos/acme/mobile%20app/pulls?state=open&per_page=50",
            githubRepositoryPullRequestsPath("acme/mobile app", GitHubRepositoryPullRequestState.OPEN)
        )
        assertEquals(
            "/repos/acme/Mobile/pulls?state=closed&per_page=50",
            githubRepositoryPullRequestsPath("acme/Mobile", GitHubRepositoryPullRequestState.CLOSED)
        )
        assertEquals(
            "/repos/acme/Mobile/pulls?state=all&per_page=50",
            githubRepositoryPullRequestsPath("acme/Mobile", GitHubRepositoryPullRequestState.ALL)
        )
    }

    @Test fun parseGitHubRepositoryPullRequestsClassifiesOpenClosedAndMergedStates() {
        val json = """[
            {
                "number": 21,
                "title": "Adiciona filtro por repositório",
                "state": "open",
                "merged_at": null,
                "html_url": "https://github.com/acme/Mobile/pull/21",
                "user": { "login": "alice" },
                "labels": [{ "name": "enhancement" }],
                "updated_at": "2026-10-21T10:00:00Z"
            },
            {
                "number": 22,
                "title": "Refatora ícone adaptativo",
                "state": "closed",
                "merged_at": "2026-10-22T15:30:00Z",
                "html_url": "https://github.com/acme/Mobile/pull/22",
                "user": { "login": "bob" },
                "labels": [],
                "updated_at": "2026-10-22T15:30:00Z"
            },
            {
                "number": 23,
                "title": "Rascunho descartado",
                "state": "closed",
                "merged_at": null,
                "html_url": "https://github.com/acme/Mobile/pull/23",
                "user": { "login": "carol" },
                "labels": []
            }
        ]"""

        val parsed = parseGitHubRepositoryPullRequests(json, "acme/Mobile")
        assertEquals(
            listOf(
                GitHubPullRequest(
                    number = 21,
                    title = "Adiciona filtro por repositório",
                    state = GitHubPullRequestState.OPEN,
                    repository = "acme/Mobile",
                    author = "alice",
                    labels = listOf("enhancement"),
                    updatedAt = Instant.parse("2026-10-21T10:00:00Z")
                ),
                GitHubPullRequest(
                    number = 22,
                    title = "Refatora ícone adaptativo",
                    state = GitHubPullRequestState.MERGED,
                    repository = "acme/Mobile",
                    author = "bob",
                    labels = emptyList(),
                    updatedAt = Instant.parse("2026-10-22T15:30:00Z")
                ),
                GitHubPullRequest(
                    number = 23,
                    title = "Rascunho descartado",
                    state = GitHubPullRequestState.CLOSED,
                    repository = "acme/Mobile",
                    author = "carol",
                    labels = emptyList(),
                    updatedAt = null
                )
            ),
            parsed
        )
    }
}
