package com.playertwo.controlegithub

internal sealed interface SessionRestoreResult {
    data class Restored(val session: GitHubSession) : SessionRestoreResult
    data object SignedOut : SessionRestoreResult
    data object Retry : SessionRestoreResult
    data object CleanupFailed : SessionRestoreResult
}

internal class GitHubSessionManager(
    private val store: SessionCredentialStore,
    private val refreshToken: suspend (String) -> OAuthTokenResult,
    private val loadUser: suspend (String) -> GitHubHttpResult,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val parseUser: (String) -> GitHubUser = GitHubUser::parse
) {
    constructor(
        store: SessionCredentialStore,
        oauth: GitHubOAuthClient,
        nowMillis: () -> Long = System::currentTimeMillis,
        parseUser: (String) -> GitHubUser = GitHubUser::parse
    ) : this(
        store,
        { token -> oauth.refresh(token).await() },
        { token -> oauth.user(token).await() },
        nowMillis,
        parseUser
    )

    suspend fun persist(session: GitHubSession) = store.save(session.credentials)

    suspend fun logout() = store.clear()

    suspend fun restore(): SessionRestoreResult {
        var credentials = try {
            store.load()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return SessionRestoreResult.Retry
        } ?: return SessionRestoreResult.SignedOut

        val now = nowMillis()
        var refreshAttempted = false
        if (credentials.accessExpiresAtMillis?.let { it <= now + REFRESH_EARLY_MILLIS } == true) {
            when (val result = refresh(credentials)) {
                is RefreshOutcome.Success -> {
                    credentials = result.credentials
                    if (!saveAfterRefresh(credentials)) return SessionRestoreResult.Retry
                    refreshAttempted = true
                }
                RefreshOutcome.Reauthenticate -> {
                    return if (clearAndSignOut()) SessionRestoreResult.SignedOut else SessionRestoreResult.CleanupFailed
                }
                RefreshOutcome.Retry -> return SessionRestoreResult.Retry
            }
        }

        when (val profile = loadProfile(credentials.accessToken)) {
            is ProfileOutcome.Valid -> return SessionRestoreResult.Restored(GitHubSession(profile.user, credentials))
            ProfileOutcome.Unavailable -> return SessionRestoreResult.Retry
            ProfileOutcome.Unauthorized -> {
                if (refreshAttempted) {
                    return if (clearAndSignOut()) SessionRestoreResult.SignedOut else SessionRestoreResult.CleanupFailed
                }
            }
        }

        return when (val result = refresh(credentials)) {
            is RefreshOutcome.Success -> {
                credentials = result.credentials
                if (!saveAfterRefresh(credentials)) return SessionRestoreResult.Retry
                when (val profile = loadProfile(credentials.accessToken)) {
                    is ProfileOutcome.Valid -> SessionRestoreResult.Restored(GitHubSession(profile.user, credentials))
                    ProfileOutcome.Unauthorized -> {
                        if (clearAndSignOut()) SessionRestoreResult.SignedOut else SessionRestoreResult.CleanupFailed
                    }
                    ProfileOutcome.Unavailable -> SessionRestoreResult.Retry
                }
            }
            RefreshOutcome.Reauthenticate -> {
                if (clearAndSignOut()) SessionRestoreResult.SignedOut else SessionRestoreResult.CleanupFailed
            }
            RefreshOutcome.Retry -> SessionRestoreResult.Retry
        }
    }

    private suspend fun refresh(current: SessionCredentials): RefreshOutcome {
        val refreshToken = current.refreshToken ?: return RefreshOutcome.Reauthenticate
        if (current.refreshExpiresAtMillis?.let { it <= nowMillis() } == true) {
            return RefreshOutcome.Reauthenticate
        }
        val result = try {
            refreshToken(refreshToken)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return RefreshOutcome.Retry
        }
        return when (result) {
            is OAuthTokenResult.Success -> {
                val rotated = result.credentials
                if (rotated.accessExpiresAtMillis != null && rotated.refreshToken == null) {
                    RefreshOutcome.Reauthenticate
                } else {
                    RefreshOutcome.Success(rotated)
                }
            }
            is OAuthTokenResult.Failure -> if (result.reason == BAD_REFRESH_TOKEN) {
                RefreshOutcome.Reauthenticate
            } else {
                RefreshOutcome.Retry
            }
        }
    }

    private suspend fun saveAfterRefresh(credentials: SessionCredentials): Boolean = try {
        store.save(credentials)
        true
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private suspend fun clearAndSignOut(): Boolean = try {
        store.clear()
        true
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private suspend fun loadProfile(accessToken: String): ProfileOutcome = try {
        when (val response = loadUser(accessToken)) {
            is GitHubHttpResult.Success -> if (response.statusCode == 200) {
                val user = runCatching { parseUser(response.body) }.getOrNull()
                if (user == null) ProfileOutcome.Unavailable else ProfileOutcome.Valid(user)
            } else ProfileOutcome.Unavailable
            is GitHubHttpResult.Failure -> if (response.error == GitHubHttpError.UNAUTHORIZED) {
                ProfileOutcome.Unauthorized
            } else {
                ProfileOutcome.Unavailable
            }
        }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ProfileOutcome.Unavailable
    }

    private sealed interface RefreshOutcome {
        data class Success(val credentials: SessionCredentials) : RefreshOutcome
        data object Reauthenticate : RefreshOutcome
        data object Retry : RefreshOutcome
    }

    private sealed interface ProfileOutcome {
        data class Valid(val user: GitHubUser) : ProfileOutcome
        data object Unauthorized : ProfileOutcome
        data object Unavailable : ProfileOutcome
    }

    private companion object {
        const val REFRESH_EARLY_MILLIS = 60_000L
    }
}
