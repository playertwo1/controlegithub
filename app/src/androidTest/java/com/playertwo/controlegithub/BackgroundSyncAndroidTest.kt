package com.playertwo.controlegithub

import android.app.job.JobInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundSyncAndroidTest {

    private lateinit var context: Context
    private lateinit var syncPreferences: SyncPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        syncPreferences = SyncPreferences(context)
        SyncScheduler.cancel(context)
    }

    @After
    fun tearDown() {
        SyncScheduler.cancel(context)
    }

    @Test
    fun testBuildJobInfo_wifiOnlyAndBatteryConstraints() {
        val jobInfo = SyncScheduler.buildJobInfo(
            context = context,
            enabled = true,
            intervalMinutes = 30L,
            wifiOnly = true,
            requiresBatteryNotLow = true
        )
        assertNotNull(jobInfo)
        jobInfo?.let {
            assertEquals(SyncScheduler.JOB_ID, it.id)
            assertTrue(it.isPeriodic)
            assertEquals(JobInfo.NETWORK_TYPE_UNMETERED, it.networkType)
            assertTrue(it.isRequireBatteryNotLow)
            assertEquals(30 * 60 * 1000L, it.intervalMillis)
        }
    }

    @Test
    fun testBuildJobInfo_anyNetworkAndDisabled() {
        val jobInfoAny = SyncScheduler.buildJobInfo(
            context = context,
            enabled = true,
            intervalMinutes = 15L,
            wifiOnly = false,
            requiresBatteryNotLow = false
        )
        assertNotNull(jobInfoAny)
        jobInfoAny?.let {
            assertEquals(JobInfo.NETWORK_TYPE_ANY, it.networkType)
            assertFalse(it.isRequireBatteryNotLow)
        }

        val jobInfoDisabled = SyncScheduler.buildJobInfo(
            context = context,
            enabled = false,
            intervalMinutes = 15L,
            wifiOnly = false,
            requiresBatteryNotLow = true
        )
        assertNull(jobInfoDisabled)
    }

    @Test
    fun testScheduleAndCancel() {
        assertFalse(SyncScheduler.isScheduled(context))

        val scheduled = SyncScheduler.schedule(
            context = context,
            enabled = true,
            intervalMinutes = 15L,
            wifiOnly = false,
            requiresBatteryNotLow = true
        )
        assertTrue(scheduled)
        assertTrue(SyncScheduler.isScheduled(context))

        SyncScheduler.cancel(context)
        assertFalse(SyncScheduler.isScheduled(context))
    }

    @Test
    fun testScheduleDisabledCancelsJob() {
        SyncScheduler.schedule(
            context = context,
            enabled = true,
            intervalMinutes = 15L,
            wifiOnly = false,
            requiresBatteryNotLow = true
        )
        assertTrue(SyncScheduler.isScheduled(context))

        val scheduled = SyncScheduler.schedule(
            context = context,
            enabled = false,
            intervalMinutes = 15L,
            wifiOnly = false,
            requiresBatteryNotLow = true
        )
        assertTrue(scheduled)
        assertFalse(SyncScheduler.isScheduled(context))
    }

    @Test
    fun testSyncPreferencesPersistence() = runBlocking {
        syncPreferences.setSyncEnabled(false)
        assertEquals(false, syncPreferences.syncEnabled.first())

        syncPreferences.setSyncWifiOnly(true)
        assertEquals(true, syncPreferences.syncWifiOnly.first())

        syncPreferences.setSyncRequiresBatteryNotLow(false)
        assertEquals(false, syncPreferences.syncRequiresBatteryNotLow.first())

        syncPreferences.setSyncIntervalMinutes(45L)
        assertEquals(45L, syncPreferences.syncIntervalMinutes.first())

        // Reset
        syncPreferences.setSyncEnabled(true)
        syncPreferences.setSyncWifiOnly(false)
        syncPreferences.setSyncRequiresBatteryNotLow(true)
        syncPreferences.setSyncIntervalMinutes(15L)
    }

    @Test
    fun testScheduleWithPreferencesReflectsConfiguredSettings() = runBlocking {
        // Configure preferences to disabled
        syncPreferences.setSyncEnabled(false)
        val scheduledDisabled = SyncScheduler.schedule(context, syncPreferences)
        assertTrue(scheduledDisabled)
        assertFalse(SyncScheduler.isScheduled(context))

        // Configure preferences to enabled, 60 minutes, wifi only
        syncPreferences.setSyncEnabled(true)
        syncPreferences.setSyncIntervalMinutes(60L)
        syncPreferences.setSyncWifiOnly(true)
        syncPreferences.setSyncRequiresBatteryNotLow(true)

        val scheduledEnabled = SyncScheduler.schedule(context, syncPreferences)
        assertTrue(scheduledEnabled)
        assertTrue(SyncScheduler.isScheduled(context))

        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as android.app.job.JobScheduler
        val pendingJob = scheduler.allPendingJobs.firstOrNull { it.id == SyncScheduler.JOB_ID }
        assertNotNull(pendingJob)
        pendingJob?.let {
            assertTrue(it.isPeriodic)
            assertEquals(60 * 60 * 1000L, it.intervalMillis)
            assertEquals(JobInfo.NETWORK_TYPE_UNMETERED, it.networkType)
            assertTrue(it.isRequireBatteryNotLow)
        }

        // Reset
        SyncScheduler.cancel(context)
        syncPreferences.setSyncEnabled(true)
        syncPreferences.setSyncWifiOnly(false)
        syncPreferences.setSyncRequiresBatteryNotLow(true)
        syncPreferences.setSyncIntervalMinutes(15L)
    }
}
