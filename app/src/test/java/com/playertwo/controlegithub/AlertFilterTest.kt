package com.playertwo.controlegithub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertFilterTest {

    private fun fixtureNotification(
        id: String,
        unread: Boolean = true,
        reason: String = "subscribed",
        title: String = "Test Issue",
        repo: String = "octocat/Hello-World"
    ) = GitHubNotification(
        id = id,
        unread = unread,
        reason = reason,
        title = title,
        subjectType = "Issue",
        repositoryId = 1L,
        repositoryFullName = repo,
        updatedAt = null,
        subjectApiUrl = null
    )

    @Test
    fun isMentionOrAssignment_correctlyIdentifiesRelevantReasons() {
        assertTrue(isMentionOrAssignment("mention"))
        assertTrue(isMentionOrAssignment("team_mention"))
        assertTrue(isMentionOrAssignment("assign"))
        assertTrue(isMentionOrAssignment("author"))
        assertTrue(isMentionOrAssignment("review_requested"))

        assertFalse(isMentionOrAssignment("subscribed"))
        assertFalse(isMentionOrAssignment("comment"))
        assertFalse(isMentionOrAssignment("ci_activity"))
        assertFalse(isMentionOrAssignment("state_change"))
    }

    @Test
    fun filterAlertCandidates_filtersReadNotifications() {
        val notifications = listOf(
            fixtureNotification(id = "1", unread = false),
            fixtureNotification(id = "2", unread = true)
        )
        val candidates = filterAlertCandidates(notifications, notifiedIds = emptySet(), mentionsOnly = false)
        assertEquals(1, candidates.size)
        assertEquals("2", candidates.first().id)
    }

    @Test
    fun filterAlertCandidates_deduplicatesAlreadyNotifiedIds() {
        val notifications = listOf(
            fixtureNotification(id = "1", unread = true),
            fixtureNotification(id = "2", unread = true),
            fixtureNotification(id = "3", unread = true)
        )
        val candidates = filterAlertCandidates(notifications, notifiedIds = setOf("1", "3"), mentionsOnly = false)
        assertEquals(1, candidates.size)
        assertEquals("2", candidates.first().id)
    }

    @Test
    fun filterAlertCandidates_appliesMentionsOnlyFilter() {
        val notifications = listOf(
            fixtureNotification(id = "1", unread = true, reason = "subscribed"),
            fixtureNotification(id = "2", unread = true, reason = "mention"),
            fixtureNotification(id = "3", unread = true, reason = "comment"),
            fixtureNotification(id = "4", unread = true, reason = "review_requested")
        )
        val allCandidates = filterAlertCandidates(notifications, notifiedIds = emptySet(), mentionsOnly = false)
        assertEquals(4, allCandidates.size)

        val mentionsOnlyCandidates = filterAlertCandidates(notifications, notifiedIds = emptySet(), mentionsOnly = true)
        assertEquals(2, mentionsOnlyCandidates.size)
        assertEquals(listOf("2", "4"), mentionsOnlyCandidates.map { it.id })
    }
}
