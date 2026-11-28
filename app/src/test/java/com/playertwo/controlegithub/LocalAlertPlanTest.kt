package com.playertwo.controlegithub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAlertPlanTest {
    @Test fun oneAlertKeepsItsOwnThreadAndIsNotASummary() {
        val planned = planLocalAlerts(listOf(alert("issue-4", "acme/android", "Corrigir login")))

        assertEquals(1, planned.size)
        val post = planned.single()
        assertEquals("issue-4", post.threadId)
        assertEquals("acme/android", post.title)
        assertEquals("Corrigir login", post.text)
        assertFalse(post.summary)
        assertTrue(post.notificationId != LocalNotificationManager.NOTIFICATION_ID)
    }

    @Test fun severalAlertsKeepSeparateDestinationsAndTheSummaryOpensTheInbox() {
        val planned = planLocalAlerts(listOf(
            alert("pr-9", "acme/android", "Abrir pull request"),
            alert("issue-3", "other/repo", "Atualizar texto")
        ))

        assertEquals(listOf("pr-9", "issue-3", null), planned.map { it.threadId })
        assertEquals(listOf(false, false, true), planned.map { it.summary })
        assertEquals(listOf("acme/android", "other/repo"), planned.take(2).map { it.title })
        assertEquals(LocalNotificationManager.NOTIFICATION_ID, planned.last().notificationId)
        assertNull(planned.last().threadId)
        assertEquals(3, planned.map { it.notificationId }.toSet().size)
        assertTrue(planned.last().title.contains("2"))
    }

    @Test fun emptyAlertListPostsNothing() {
        assertTrue(planLocalAlerts(emptyList()).isEmpty())
    }

    private fun alert(id: String, repository: String, title: String) = GitHubNotification(
        id = id,
        unread = true,
        reason = "mention",
        title = title,
        subjectType = "Issue",
        repositoryId = 1L,
        repositoryFullName = repository,
        updatedAt = null,
        subjectApiUrl = null
    )
}
