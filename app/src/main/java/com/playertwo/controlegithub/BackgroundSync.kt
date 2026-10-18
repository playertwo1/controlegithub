package com.playertwo.controlegithub

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class SyncPreferences(context: Context) {
    private val dataStore = context.applicationContext.preferencesDataStore

    val syncEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_SYNC_ENABLED] ?: true
    }

    val syncWifiOnly: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_SYNC_WIFI_ONLY] ?: false
    }

    val syncRequiresBatteryNotLow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_SYNC_BATTERY_NOT_LOW] ?: true
    }

    val syncIntervalMinutes: Flow<Long> = dataStore.data.map { preferences ->
        preferences[KEY_SYNC_INTERVAL_MINUTES] ?: 15L
    }

    suspend fun setSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SYNC_ENABLED] = enabled }
    }

    suspend fun setSyncWifiOnly(wifiOnly: Boolean) {
        dataStore.edit { it[KEY_SYNC_WIFI_ONLY] = wifiOnly }
    }

    suspend fun setSyncRequiresBatteryNotLow(requiresBatteryNotLow: Boolean) {
        dataStore.edit { it[KEY_SYNC_BATTERY_NOT_LOW] = requiresBatteryNotLow }
    }

    suspend fun setSyncIntervalMinutes(minutes: Long) {
        dataStore.edit { it[KEY_SYNC_INTERVAL_MINUTES] = maxOf(15L, minutes) }
    }

    companion object {
        private val KEY_SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        private val KEY_SYNC_WIFI_ONLY = booleanPreferencesKey("sync_wifi_only")
        private val KEY_SYNC_BATTERY_NOT_LOW = booleanPreferencesKey("sync_requires_battery_not_low")
        private val KEY_SYNC_INTERVAL_MINUTES = longPreferencesKey("sync_interval_minutes")
    }
}

internal object SyncScheduler {
    const val JOB_ID = 2026
    const val MIN_INTERVAL_MINUTES = 15L

    fun calculatePeriodMillis(intervalMinutes: Long): Long {
        return maxOf(intervalMinutes, MIN_INTERVAL_MINUTES) * 60 * 1000L
    }

    fun resolveRequiredNetworkType(wifiOnly: Boolean): Int {
        return if (wifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY
    }

    fun buildJobInfo(
        context: Context,
        enabled: Boolean,
        intervalMinutes: Long,
        wifiOnly: Boolean,
        requiresBatteryNotLow: Boolean
    ): JobInfo? {
        if (!enabled) return null
        val component = ComponentName(context, BackgroundSyncJobService::class.java)
        val periodMillis = calculatePeriodMillis(intervalMinutes)
        val networkType = resolveRequiredNetworkType(wifiOnly)

        return JobInfo.Builder(JOB_ID, component)
            .setPeriodic(periodMillis)
            .setRequiredNetworkType(networkType)
            .setRequiresBatteryNotLow(requiresBatteryNotLow)
            .setPersisted(false)
            .build()
    }

    fun schedule(
        context: Context,
        enabled: Boolean,
        intervalMinutes: Long,
        wifiOnly: Boolean,
        requiresBatteryNotLow: Boolean
    ): Boolean {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return false
        if (!enabled) {
            scheduler.cancel(JOB_ID)
            return true
        }
        val jobInfo = buildJobInfo(context, enabled, intervalMinutes, wifiOnly, requiresBatteryNotLow) ?: return false
        return scheduler.schedule(jobInfo) == JobScheduler.RESULT_SUCCESS
    }

    suspend fun schedule(
        context: Context,
        preferences: SyncPreferences = SyncPreferences(context)
    ): Boolean {
        val enabled = preferences.syncEnabled.first()
        val interval = preferences.syncIntervalMinutes.first()
        val wifiOnly = preferences.syncWifiOnly.first()
        val batteryNotLow = preferences.syncRequiresBatteryNotLow.first()
        return schedule(context, enabled, interval, wifiOnly, batteryNotLow)
    }

    fun cancel(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
        scheduler?.cancel(JOB_ID)
    }

    fun isScheduled(context: Context): Boolean {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return false
        return scheduler.allPendingJobs.any { it.id == JOB_ID }
    }
}

class BackgroundSyncJobService : JobService() {
    private var jobScope: CoroutineScope? = null
    private var activeCall: GitHubHttpCall? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        jobScope = scope
        scope.launch {
            try {
                val sessionStore = SecureSessionStore(applicationContext)
                val credentials = sessionStore.load()
                val userLogin = credentials?.userLogin
                if (credentials == null || userLogin == null) {
                    jobFinished(params, false)
                    return@launch
                }
                if (credentials.accessExpiresAtMillis != null && System.currentTimeMillis() >= credentials.accessExpiresAtMillis) {
                    jobFinished(params, false)
                    return@launch
                }
                val cacheStore = SQLiteGitHubCacheStore(applicationContext)
                val client = GitHubHttpClient()
                val cached = cacheStore.get(userLogin, "/notifications")
                val call = client.get(
                    path = "/notifications",
                    accessToken = credentials.accessToken,
                    ifNoneMatch = cached?.etag
                )
                activeCall = call
                val response = call.execute()

                when (response) {
                    is GitHubHttpResult.Success -> {
                        if (response.statusCode == 304) {
                            cacheStore.updateTimestamp(
                                accountLogin = userLogin,
                                resourceKey = "/notifications",
                                revalidatedAtMillis = System.currentTimeMillis(),
                                etag = response.headers.etag ?: cached?.etag
                            )
                        } else {
                            cacheStore.put(
                                accountLogin = userLogin,
                                resourceKey = "/notifications",
                                payload = response.body,
                                statusCode = response.statusCode,
                                etag = response.headers.etag
                            )
                        }
                    }
                    else -> Unit
                }
            } catch (_: Exception) {
                // Silently handle exceptions in background to prevent process crash
            } finally {
                activeCall = null
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        activeCall?.cancel()
        activeCall = null
        jobScope?.cancel()
        jobScope = null
        return false // Don't force reschedule if cancelled by OS
    }
}

