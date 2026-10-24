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

internal object LocalNotificationManager {
    const val CHANNEL_ID = "controlegithub_alerts"
    const val CHANNEL_NAME = "Alertas do GitHub"
    const val NOTIFICATION_ID = 20261

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
        if (items.isEmpty()) return 0
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return 0
        if (!manager.areNotificationsEnabled()) return 0

        ensureChannel(context)

        val title = if (items.size == 1) {
            val first = items.first()
            first.repositoryFullName
        } else {
            "ControleGitHub (${items.size} novas notificações)"
        }

        val text = if (items.size == 1) {
            val first = items.first()
            first.title ?: "Nova atividade"
        } else {
            items.take(3).joinToString("; ") { it.title ?: it.repositoryFullName }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        return try {
            manager.notify(NOTIFICATION_ID, notification)
            items.size
        } catch (_: SecurityException) {
            0
        }
    }

    fun cancelAll(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancelAll()
    }
}
