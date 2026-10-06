package com.playertwo.controlegithub

import java.io.IOException
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubNotificationsParserTest {
    @Test fun parsesThreadAndRepositoryFieldsWithoutLosingReasonOrState() {
        val item = parseGitHubNotifications(fixtureThreads(fixtureNotificationJson())).single()

        assertEquals("1", item.id)
        assertTrue(item.unread)
        assertEquals("assign", item.reason)
        assertEquals("Fixture title", item.title)
        assertEquals("Issue", item.subjectType)
        assertEquals("acme/app", item.repository.fullName)
        assertEquals(Instant.parse("2026-10-01T12:00:00Z"), item.updatedAt)
    }

    @Test fun acceptsValidEmptyResponse() {
        assertTrue(parseGitHubNotifications("[]").isEmpty())
    }

    @Test fun rejectsMalformedJsonAndIncompleteThread() {
        assertThrows(IOException::class.java) { parseGitHubNotifications("{") }
        assertThrows(IOException::class.java) {
            parseGitHubNotifications("""[{"id":"2","repository":{},"reason":"assign","unread":true}]""")
        }
    }

    @Test fun preservesUnknownTypeForFallbackAndMissingSubjectForRepositoryRouting() {
        val unknown = parseGitHubNotifications(fixtureThreads(fixtureNotificationJson(type = "FutureType"))).single()
        val missing = parseGitHubNotifications(fixtureThreads(fixtureNotificationJson(type = null, url = null))).single()

        assertEquals("FutureType", unknown.subjectType)
        assertEquals(GitHubNotificationDestination.Unavailable, notificationDestination(unknown))
        assertEquals(GitHubNotificationDestination.Repository("acme/app"), notificationDestination(missing))
    }
}

internal fun fixtureNotificationJson(
    id: String = "1",
    type: String? = "Issue",
    url: String? = "https://api.github.com/repos/acme/app/issues/12"
): String {
    val repository = JSONObject()
        .put("id", 1)
        .put("name", "app")
        .put("full_name", "acme/app")
        .put("owner", JSONObject().put("login", "acme"))
        .put("private", false)
        .put("description", JSONObject.NULL)
        .put("language", JSONObject.NULL)
        .put("stargazers_count", 0)
    val subject = JSONObject().put("title", "Fixture title")
    type?.let { subject.put("type", it) }
    url?.let { subject.put("url", it) }
    val notification = JSONObject()
        .put("id", id)
        .put("unread", true)
        .put("reason", "assign")
        .put("updated_at", "2026-10-01T12:00:00Z")
        .put("repository", repository)
    if (type != null || url != null) notification.put("subject", subject)
    return notification.toString()
}

internal fun fixtureThreads(vararg notificationJson: String): String = JSONArray().apply {
    notificationJson.forEach { put(JSONObject(it)) }
}.toString()
