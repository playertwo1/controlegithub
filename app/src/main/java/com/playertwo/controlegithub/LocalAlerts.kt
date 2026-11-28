package com.playertwo.controlegithub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class AlertPreferences(context: Context) {
    private val dataStore = context.applicationContext.preferencesDataStore

    val alertsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_ALERTS_ENABLED] ?: true
    }

    val alertMentionsOnly: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_ALERT_MENTIONS_ONLY] ?: false
    }

    val notifiedThreadIds: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[KEY_NOTIFIED_THREAD_IDS] ?: emptySet()
    }

    suspend fun setAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ALERTS_ENABLED] = enabled }
    }

    suspend fun setAlertMentionsOnly(mentionsOnly: Boolean) {
        dataStore.edit { it[KEY_ALERT_MENTIONS_ONLY] = mentionsOnly }
    }

    suspend fun markNotified(threadIds: Set<String>) {
        if (threadIds.isEmpty()) return
        dataStore.edit { preferences ->
            val existing = preferences[KEY_NOTIFIED_THREAD_IDS] ?: emptySet()
            // Keep recent 100 IDs to avoid unbounded storage growth
            val combined = (existing + threadIds).toList()
            val trimmed = if (combined.size > 100) combined.takeLast(100) else combined
            preferences[KEY_NOTIFIED_THREAD_IDS] = trimmed.toSet()
        }
    }

    suspend fun clearNotified() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_NOTIFIED_THREAD_IDS)
        }
    }

    companion object {
        private val KEY_ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        private val KEY_ALERT_MENTIONS_ONLY = booleanPreferencesKey("alert_mentions_only")
        private val KEY_NOTIFIED_THREAD_IDS = stringSetPreferencesKey("notified_thread_ids")
    }
}

internal fun isMentionOrAssignment(reason: String): Boolean {
    val normalized = reason.trim().lowercase()
    return normalized in setOf("mention", "team_mention", "assign", "author", "review_requested")
}

internal fun filterAlertCandidates(
    notifications: List<GitHubNotification>,
    notifiedIds: Set<String>,
    mentionsOnly: Boolean
): List<GitHubNotification> {
    return notifications.filter { item ->
        item.unread &&
            item.id !in notifiedIds &&
            (!mentionsOnly || isMentionOrAssignment(item.reason))
    }
}

internal data class PlannedLocalAlert(
    val notificationId: Int,
    val threadId: String?,
    val title: String,
    val text: String,
    val summary: Boolean
)

internal fun planLocalAlerts(items: List<GitHubNotification>): List<PlannedLocalAlert> {
    val unique = items.distinctBy { it.id }
    if (unique.isEmpty()) return emptyList()
    if (unique.size == 1) {
        val item = unique.first()
        return listOf(plannedIndividualAlert(item, usedIds = mutableSetOf()))
    }
    val usedIds = mutableSetOf<Int>()
    val individuals = unique.map { plannedIndividualAlert(it, usedIds) }
    return individuals + PlannedLocalAlert(
        notificationId = LocalNotificationManager.NOTIFICATION_ID,
        threadId = null,
        title = "ControleGitHub (${unique.size} novas notificações)",
        text = unique.take(3).joinToString("; ") { it.title ?: it.repositoryFullName },
        summary = true
    )
}

private fun plannedIndividualAlert(
    item: GitHubNotification,
    usedIds: MutableSet<Int>
): PlannedLocalAlert {
    var id = item.id.hashCode() and 0x7fffffff
    if (id == 0) id = 1
    while (id == LocalNotificationManager.NOTIFICATION_ID || id in usedIds) {
        id = if (id == Int.MAX_VALUE) 1 else id + 1
    }
    usedIds.add(id)
    return PlannedLocalAlert(
        notificationId = id,
        threadId = item.id,
        title = item.repositoryFullName,
        text = item.title ?: "Nova atividade",
        summary = false
    )
}

internal object LocalNotificationManager {
    const val CHANNEL_ID = "controlegithub_alerts"
    const val CHANNEL_NAME = "Alertas do GitHub"
    const val NOTIFICATION_ID = 20261
    const val ALERT_GROUP = "controlegithub_alerts"
    const val ALERT_TAG = "controlegithub_alert"
    const val OPEN_ALERT = "com.playertwo.controlegithub.OPEN_ALERT"
    const val EXTRA_THREAD_ID = "thread_id"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificações de novas atividades e menções no GitHub"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return manager.areNotificationsEnabled()
    }

    fun notifyNewEvents(context: Context, items: List<GitHubNotification>): Int {
        val posts = planLocalAlerts(items)
        if (posts.isEmpty()) return 0
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return 0
        if (!manager.areNotificationsEnabled()) return 0

        ensureChannel(context)
        return try {
            cancelPostedAlerts(manager)
            val grouped = posts.size > 1
            for (post in posts) {
                val notification = Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_notification)
                    .setContentTitle(post.title)
                    .setContentText(post.text)
                    .setContentIntent(alertContentIntent(context, post))
                    .setAutoCancel(true)
                    .apply {
                        if (grouped) {
                            setGroup(ALERT_GROUP)
                            setGroupSummary(post.summary)
                        }
                    }
                    .build()
                manager.notify(ALERT_TAG, post.notificationId, notification)
            }
            posts.count { !it.summary }
        } catch (_: SecurityException) {
            0
        }
    }

    private fun alertContentIntent(context: Context, post: PlannedLocalAlert): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = OPEN_ALERT
            if (post.threadId != null) putExtra(EXTRA_THREAD_ID, post.threadId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            post.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelPostedAlerts(manager: NotificationManager) {
        manager.activeNotifications
            .filter { it.tag == ALERT_TAG }
            .forEach { manager.cancel(ALERT_TAG, it.id) }
    }

    fun cancelAll(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancelAll()
    }
}
