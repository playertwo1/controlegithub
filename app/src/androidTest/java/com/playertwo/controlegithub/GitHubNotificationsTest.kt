package com.playertwo.controlegithub

import java.io.IOException
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GitHubNotificationsTest {
    @Test
    fun parsesRequiredFieldsAndPreservesUnknownValues() {
        val notification = parseGitHubNotifications(fixtureThreads(fixtureNotification(
            type = "CustomSubject",
            reason = "custom_reason"
        ))).single()

        assertEquals("thread-1", notification.id)
        assertEquals(true, notification.unread)
        assertEquals("custom_reason", notification.reason)
        assertEquals("Fixture title", notification.title)
        assertEquals("CustomSubject", notification.subjectType)
        assertEquals(1L, notification.repositoryId)
        assertEquals("acme/app", notification.repositoryFullName)
        assertEquals(Instant.parse("2026-10-01T12:00:00Z"), notification.updatedAt)
        assertEquals("https://api.github.com/repos/acme/app/issues/12", notification.subjectApiUrl)
        assertEquals(GitHubNotificationDestination.Unavailable, notificationDestination(notification))
    }

    @Test
    fun acceptsOptionalTitleTypeUrlAndDate() {
        val raw = fixtureNotification().also {
            it.getJSONObject("subject").put("title", JSONObject.NULL)
                .put("type", JSONObject.NULL)
                .put("url", JSONObject.NULL)
            it.put("updated_at", "not-a-date")
        }

        val notification = parseGitHubNotifications(fixtureThreads(raw)).single()

        assertEquals(null, notification.title)
        assertEquals(null, notification.subjectType)
        assertEquals(null, notification.subjectApiUrl)
        assertEquals(null, notification.updatedAt)
        assertEquals(GitHubNotificationDestination.Repository("acme/app"), notificationDestination(notification))
    }

    @Test
    fun rejectsMalformedCollectionOrThreadInsteadOfReturningPartialItems() {
        assertThrows(IOException::class.java) { parseGitHubNotifications("{") }
        assertThrows(IOException::class.java) { parseGitHubNotifications("{}") }
        assertThrows(IOException::class.java) {
            parseGitHubNotifications(JSONArray().put(fixtureNotification().put("unread", "yes")).toString())
        }
        assertThrows(IOException::class.java) {
            parseGitHubNotifications(JSONArray().put(fixtureNotification()).put(JSONObject()).toString())
        }
        assertThrows(IOException::class.java) {
            parseGitHubNotifications(JSONArray().put(fixtureNotification().put("subject", "invalid")).toString())
        }
    }

    @Test
    fun mapsOnlyValidatedIssuePullRequestAndRepositoryDestinations() {
        assertEquals(
            GitHubNotificationDestination.Issue("acme/app", 12),
            notificationDestination(fixtureNotification(type = "Issue").toModel())
        )
        assertEquals(
            GitHubNotificationDestination.PullRequest("acme/app", 12),
            notificationDestination(fixtureNotification(
                type = "PullRequest",
                url = "https://api.github.com/repos/acme/app/pulls/12"
            ).toModel())
        )
        assertEquals(
            GitHubNotificationDestination.Repository("acme/app"),
            notificationDestination(fixtureNotification(type = "Repository", url = null).toModel())
        )
    }

    @Test
    fun rejectsUntrustedOrInconsistentSubjectUrls() {
        val invalidUrls = listOf(
            "https://example.com/repos/acme/app/issues/12",
            "http://api.github.com/repos/acme/app/issues/12",
            "https://user@api.github.com/repos/acme/app/issues/12",
            "https://api.github.com:443/repos/acme/app/issues/12",
            "https://api.github.com/repos/acme/app/issues/12?redirect=1",
            "https://api.github.com/repos/acme/app/issues/12#fragment",
            "https://api.github.com/repos/other/app/issues/12",
            "https://api.github.com/repos/acme/app/issues/0",
            "https://api.github.com/repos/acme/app/issues/2147483648",
            "https://api.github.com/repos/acme/app/issues/12/comments"
        )

        invalidUrls.forEach { url ->
            assertEquals(url, GitHubNotificationDestination.Unavailable,
                notificationDestination(fixtureNotification(url = url).toModel()))
        }
    }

    private fun JSONObject.toModel() =
        parseGitHubNotifications(fixtureThreads(this)).single()
}

internal fun fixtureNotification(
    id: String = "thread-1",
    type: String? = "Issue",
    url: String? = "https://api.github.com/repos/acme/app/issues/12",
    reason: String = "assign"
): JSONObject = JSONObject()
    .put("id", id)
    .put("unread", true)
    .put("reason", reason)
    .put("updated_at", "2026-10-01T12:00:00Z")
    .put("subject", JSONObject()
        .put("title", "Fixture title")
        .put("type", type ?: JSONObject.NULL)
        .put("url", url ?: JSONObject.NULL))
    .put("repository", JSONObject()
        .put("id", 1)
        .put("full_name", "acme/app"))

internal fun fixtureThreads(vararg items: JSONObject): String = JSONArray().apply {
    items.forEach(::put)
}.toString()
