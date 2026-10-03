package com.playertwo.controlegithub

import kotlinx.coroutines.delay

internal enum class SignInFailure { DENIED, EXPIRED, INVALID_PROFILE, FAILED }

internal class SignInException(val reason: SignInFailure) : Exception()

internal suspend fun authorizeDeviceFlow(
    requestCode: suspend () -> DeviceAuthorization,
    poll: suspend (deviceCode: String, intervalSeconds: Long) -> DevicePoll,
    loadProfile: suspend (accessToken: String) -> GitHubUser?,
    onAuthorization: (DeviceAuthorization) -> Unit,
    nowMillis: () -> Long = System::currentTimeMillis,
    waitMillis: suspend (Long) -> Unit = { delay(it) }
): GitHubSession {
    val device = requestCode()
    onAuthorization(device)
    val expiresAt = nowMillis() + device.expiresInSeconds * 1_000
    var interval = device.intervalSeconds
    while (true) {
        val wait = interval * 1_000
        if (nowMillis() + wait >= expiresAt) throw SignInException(SignInFailure.EXPIRED)
        waitMillis(wait)
        if (nowMillis() >= expiresAt) throw SignInException(SignInFailure.EXPIRED)
        when (val result = poll(device.deviceCode, interval)) {
            is DevicePoll.Pending -> interval = result.intervalSeconds
            DevicePoll.Denied -> throw SignInException(SignInFailure.DENIED)
            DevicePoll.Expired -> throw SignInException(SignInFailure.EXPIRED)
            DevicePoll.Failed -> throw SignInException(SignInFailure.FAILED)
            is DevicePoll.Authorized -> {
                val user = loadProfile(result.accessToken)
                    ?: throw SignInException(SignInFailure.INVALID_PROFILE)
                return GitHubSession(user, result.accessToken)
            }
        }
    }
}
