package com.playertwo.controlegithub

import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSignInFlowTest {
    @Test
    fun createsSessionOnlyAfterAuthorizedProfileAndHonorsSlowDown() = runBlocking {
        var now = 0L
        val waits = mutableListOf<Long>()
        val polls = mutableListOf<Long>()
        var loadedToken: String? = null
        var shownCode: DeviceAuthorization? = null
        val user = GitHubUser("octocat", "https://github.com/octocat")

        val session = authorizeDeviceFlow(
            requestCode = { device() },
            poll = { _, interval ->
                polls += interval
                if (polls.size == 1) DevicePoll.Pending(10) else DevicePoll.Authorized(SessionCredentials("private-token"))
            },
            loadProfile = { loadedToken = it; user },
            onAuthorization = { shownCode = it },
            nowMillis = { now },
            waitMillis = { waits += it; now += it }
        )

        assertSame(user, session.user)
        assertEquals("private-token", session.accessToken)
        assertTrue("Session string must not reveal the access token", !session.toString().contains("private-token"))
        assertEquals(listOf(5_000L, 10_000L), waits)
        assertEquals(listOf(5L, 10L), polls)
        assertEquals("user-code", shownCode?.userCode)
        assertEquals("private-token", loadedToken)
    }

    @Test
    fun neverPollsAfterTheNextIntervalWouldReachExpiry() = runBlocking {
        var now = 0L
        var polls = 0
        val waits = mutableListOf<Long>()
        val error = assertThrows(SignInException::class.java) {
            runBlocking {
                authorizeDeviceFlow(
                    requestCode = { device(expires = 11, interval = 6) },
                    poll = { _, interval -> polls++; DevicePoll.Pending(interval) },
                    loadProfile = { null },
                    onAuthorization = {},
                    nowMillis = { now },
                    waitMillis = { waits += it; now += it }
                )
            }
        }
        assertEquals(SignInFailure.EXPIRED, error.reason)
        assertEquals(1, polls)
        assertEquals(listOf(6_000L), waits)
    }

    @Test
    fun cancellationAndFailedProfileCannotCreateASession() {
        var polls = 0
        assertThrows(CancellationException::class.java) {
            runBlocking {
                authorizeDeviceFlow(
                    requestCode = { device() },
                    poll = { _, _ -> polls++; DevicePoll.Authorized(SessionCredentials("private-token")) },
                    loadProfile = { null },
                    onAuthorization = {},
                    nowMillis = { 0L },
                    waitMillis = { throw CancellationException() }
                )
            }
        }
        assertEquals(0, polls)

        val error = assertThrows(SignInException::class.java) {
            runBlocking {
                authorizeDeviceFlow(
                    requestCode = { device() },
                    poll = { _, _ -> DevicePoll.Authorized(SessionCredentials("private-token")) },
                    loadProfile = { null },
                    onAuthorization = {},
                    nowMillis = { 0L },
                    waitMillis = {}
                )
            }
        }
        assertEquals(SignInFailure.INVALID_PROFILE, error.reason)
        assertTrue(error.message.isNullOrEmpty())

        val denied = assertThrows(SignInException::class.java) {
            runBlocking {
                authorizeDeviceFlow(
                    requestCode = { device() },
                    poll = { _, _ -> DevicePoll.Denied },
                    loadProfile = { error("denied flow must not request a profile") },
                    onAuthorization = {},
                    nowMillis = { 0L },
                    waitMillis = {}
                )
            }
        }
        assertEquals(SignInFailure.DENIED, denied.reason)
    }

    private fun device(expires: Long = 60, interval: Long = 5) = DeviceAuthorization(
        "device-secret", "user-code", "https://github.com/login/device", expires, interval
    )
}
