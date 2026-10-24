package com.playertwo.controlegithub

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubNotificationDestinationTest {
    @Test
    fun issueAndPullRequestKeepTheirSpecificDestination() {
        assertEquals(
            GitHubNotificationDestination.Issue("acme/app", 12),
            notificationDestination(notification(type = "Issue"))
        )
        assertEquals(
            GitHubNotificationDestination.PullRequest("acme/app", 12),
            notificationDestination(notification(
                type = "PullRequest",
                url = "https://api.github.com/repos/acme/app/pulls/12"
            ))
        )
    }

    @Test
    fun releaseUnknownAndMissingTypesOpenOnlyAValidRepository() {
        listOf("Release", "CheckSuite", null).forEach { type ->
            assertEquals(
                type,
                GitHubNotificationDestination.Repository("other-owner/other-repo"),
                notificationDestination(notification(
                    type = type,
                    repository = "other-owner/other-repo",
                    url = "https://api.github.com/repos/captured/name/releases/1"
                ))
            )
        }
    }

    @Test
    fun invalidRepositoryIdentityDoesNotNavigate() {
        assertEquals(
            GitHubNotificationDestination.Unavailable,
            notificationDestination(notification(type = "Release", repositoryId = 0))
        )
        assertEquals(
            GitHubNotificationDestination.Unavailable,
            notificationDestination(notification(type = "Release", repository = "not a repo"))
        )
        assertEquals(
            GitHubNotificationDestination.Unavailable,
            notificationDestination(notification(type = "Release", repository = "only-owner"))
        )
    }

    @Test
    fun invalidIssueUrlStaysUnavailableInsteadOfFallingBackToTheRepository() {
        assertEquals(
            GitHubNotificationDestination.Unavailable,
            notificationDestination(notification(url = "https://example.com/repos/acme/app/issues/12"))
        )
    }

    private fun notification(
        type: String? = "Issue",
        repository: String = "acme/app",
        repositoryId: Long = 7,
        url: String? = "https://api.github.com/repos/acme/app/issues/12"
    ) = GitHubNotification(
        id = "thread-1",
        unread = true,
        reason = "assign",
        title = "Fixture",
        subjectType = type,
        repositoryId = repositoryId,
        repositoryFullName = repository,
        updatedAt = Instant.parse("2026-10-01T12:00:00Z"),
        subjectApiUrl = url
    )
}
