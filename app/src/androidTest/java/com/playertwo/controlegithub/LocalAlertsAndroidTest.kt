package com.playertwo.controlegithub

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAlertsAndroidTest {

    private lateinit var context: Context
    private lateinit var alertPreferences: AlertPreferences

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        alertPreferences = AlertPreferences(context)
        alertPreferences.clearNotified()
        LocalNotificationManager.cancelAll(context)
    }

    @After
    fun tearDown() = runBlocking {
        alertPreferences.clearNotified()
        LocalNotificationManager.cancelAll(context)
    }

    @Test
    fun testEnsureNotificationChannel() {
        LocalNotificationManager.ensureChannel(context)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(LocalNotificationManager.CHANNEL_ID)

        assertNotNull(channel)
        channel?.let {
            assertEquals(LocalNotificationManager.CHANNEL_NAME, it.name)
            assertEquals(NotificationManager.IMPORTANCE_DEFAULT, it.importance)
        }
    }

    @Test
    fun testAlertPreferencesPersistence() = runBlocking {
        alertPreferences.setAlertsEnabled(false)
        assertEquals(false, alertPreferences.alertsEnabled.first())

        alertPreferences.setAlertMentionsOnly(true)
        assertEquals(true, alertPreferences.alertMentionsOnly.first())

        alertPreferences.markNotified(setOf("101", "102"))
        assertEquals(setOf("101", "102"), alertPreferences.notifiedThreadIds.first())

        alertPreferences.markNotified(setOf("103"))
        assertEquals(setOf("101", "102", "103"), alertPreferences.notifiedThreadIds.first())

        alertPreferences.clearNotified()
        assertEquals(emptySet<String>(), alertPreferences.notifiedThreadIds.first())

        // Reset
        alertPreferences.setAlertsEnabled(true)
        alertPreferences.setAlertMentionsOnly(false)
    }

    @Test
    fun testAreNotificationsEnabledResilience() {
        // Must return boolean without throwing any exception
        val enabled = LocalNotificationManager.areNotificationsEnabled(context)
        assertTrue(enabled || !enabled)
    }

    @Test
    fun testNotifyNewEventsAndCancelAll() {
        val notificationItem = GitHubNotification(
            id = "alert-test-1",
            unread = true,
            reason = "mention",
            title = "Test Mention Issue",
            subjectType = "Issue",
            repositoryId = 42L,
            repositoryFullName = "test-owner/test-repo",
            updatedAt = null,
            subjectApiUrl = null
        )

        // Must execute cleanly without crashing
        LocalNotificationManager.notifyNewEvents(context, listOf(notificationItem))

        // Cancel all
        LocalNotificationManager.cancelAll(context)
    }
}
