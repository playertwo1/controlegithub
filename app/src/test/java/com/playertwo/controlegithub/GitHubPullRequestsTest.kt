package com.playertwo.controlegithub

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
}
