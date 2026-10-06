package com.playertwo.controlegithub

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubNotificationsTest {
    @Test fun mapsKnownAndUnknownSubjectTypesAndReasons() {
        assertEquals("Issue", notificationTypeLabel(fixtureNotification(type = "Issue")))
        assertEquals("Pull request", notificationTypeLabel(fixtureNotification(type = "PullRequest")))
        assertEquals("Repositório", notificationTypeLabel(fixtureNotification(type = "Repository")))
        assertEquals("Outro", notificationTypeLabel(fixtureNotification(type = "FutureType")))
        assertEquals("Atribuída a você", notificationReasonLabel("assign"))
        assertEquals("reason_future", notificationReasonLabel("reason_future"))
    }

    @Test fun repositoryAndAbsentSubjectNavigateToRepository() {
        assertEquals(
            GitHubNotificationDestination.Repository("acme/app"),
            notificationDestination(fixtureNotification(type = "Repository", url = null))
        )
        assertEquals(
            GitHubNotificationDestination.Repository("acme/app"),
            notificationDestination(fixtureNotification(type = null, url = null))
        )
    }

    @Test fun pullRequestAcceptsValidatedIssueAndPullRoutes() {
        listOf("issues", "pulls").forEach { collection ->
            assertEquals(
                GitHubNotificationDestination.PullRequest("acme/app", 12),
                notificationDestination(fixtureNotification(type = "PullRequest", url = "https://api.github.com/repos/acme/app/$collection/12"))
            )
        }
    }

    @Test fun rejectsUntrustedOrInconsistentSubjectUrls() {
        val invalidUrls = listOf(
            "https://example.com/repos/acme/app/issues/12",
            "http://api.github.com/repos/acme/app/issues/12",
            "https://api.github.com:443/repos/acme/app/issues/12",
            "https://user@api.github.com/repos/acme/app/issues/12",
            "https://api.github.com/repos/acme/app/issues/12?query=1",
            "https://api.github.com/repos/acme/app/issues/12#fragment",
            "https://api.github.com/repos/other/app/issues/12",
            "https://api.github.com/repos/acme/app/issues/0",
            "https://api.github.com/repos/acme/app/issues/12/comments"
        )

        invalidUrls.forEach { url ->
            assertEquals(url, GitHubNotificationDestination.Unavailable, notificationDestination(fixtureNotification(url = url)))
        }
        assertEquals(
            GitHubNotificationDestination.Unavailable,
            notificationDestination(fixtureNotification(type = "Issue", url = "https://api.github.com/repos/acme/app/pulls/12"))
        )
        assertEquals(
            GitHubNotificationDestination.Unavailable,
            notificationDestination(fixtureNotification(type = "FutureType", url = "https://api.github.com/repos/acme/app/issues/12"))
        )
    }
}

internal fun fixtureNotification(
    id: String = "1",
    type: String? = "Issue",
    url: String? = "https://api.github.com/repos/acme/app/issues/12"
): GitHubNotification = GitHubNotification(
    id = id,
    unread = true,
    reason = "assign",
    title = "Fixture title",
    subjectType = type,
    repository = GitHubRepository(1, "app", "acme/app", "acme", false, null, null, 0),
    updatedAt = Instant.parse("2026-10-01T12:00:00Z"),
    subjectApiUrl = url
)
