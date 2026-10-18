package com.playertwo.controlegithub

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncSchedulerTest {

    @Test
    fun calculatePeriodMillis_enforcesMinimum15Minutes() {
        val belowMinimum = SyncScheduler.calculatePeriodMillis(5L)
        assertEquals(15 * 60 * 1000L, belowMinimum)

        val exactMinimum = SyncScheduler.calculatePeriodMillis(15L)
        assertEquals(15 * 60 * 1000L, exactMinimum)
    }

    @Test
    fun calculatePeriodMillis_calculatesCorrectlyForLargerIntervals() {
        val thirtyMinutes = SyncScheduler.calculatePeriodMillis(30L)
        assertEquals(30 * 60 * 1000L, thirtyMinutes)

        val oneHour = SyncScheduler.calculatePeriodMillis(60L)
        assertEquals(60 * 60 * 1000L, oneHour)

        val sixHours = SyncScheduler.calculatePeriodMillis(360L)
        assertEquals(360 * 60 * 1000L, sixHours)
    }

    @Test
    fun resolveRequiredNetworkType_mapsCorrectly() {
        // JobInfo.NETWORK_TYPE_UNMETERED is 2
        // JobInfo.NETWORK_TYPE_ANY is 1
        val wifiOnly = SyncScheduler.resolveRequiredNetworkType(true)
        assertEquals(2, wifiOnly)

        val anyNetwork = SyncScheduler.resolveRequiredNetworkType(false)
        assertEquals(1, anyNetwork)
    }
}
