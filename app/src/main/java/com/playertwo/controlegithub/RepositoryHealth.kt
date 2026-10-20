package com.playertwo.controlegithub

import java.time.Duration
import java.time.Instant

internal enum class RepositoryHealthStatus(val label: String) {
    ACTIVE("Ativo"),
    COOLING("Pouco movimento"),
    STALE("Parado"),
    ABANDONED("Abandonado"),
    ARCHIVED("Arquivado"),
    UNKNOWN("Sem dados")
}

internal data class RepositoryHealth(
    val status: RepositoryHealthStatus,
    val daysWithoutPush: Long?
) {
    val summary: String
        get() = when {
            status == RepositoryHealthStatus.ARCHIVED -> status.label
            daysWithoutPush == null -> "Sem push registrado"
            daysWithoutPush == 0L -> "${status.label} · push hoje"
            daysWithoutPush == 1L -> "${status.label} · 1 dia sem push"
            else -> "${status.label} · $daysWithoutPush dias sem push"
        }
}

internal fun repositoryHealth(
    pushedAt: Instant?,
    archived: Boolean,
    now: Instant = Instant.now()
): RepositoryHealth {
    val days = pushedAt?.let { Duration.between(it, now).toDays().coerceAtLeast(0) }
    val status = when {
        archived -> RepositoryHealthStatus.ARCHIVED
        days == null -> RepositoryHealthStatus.UNKNOWN
        days <= ACTIVE_DAYS -> RepositoryHealthStatus.ACTIVE
        days <= COOLING_DAYS -> RepositoryHealthStatus.COOLING
        days <= STALE_DAYS -> RepositoryHealthStatus.STALE
        else -> RepositoryHealthStatus.ABANDONED
    }
    return RepositoryHealth(status, days)
}

private const val ACTIVE_DAYS = 30L
private const val COOLING_DAYS = 90L
private const val STALE_DAYS = 365L

@androidx.compose.runtime.Composable
internal fun RepositoryHealthStatus.color(): androidx.compose.ui.graphics.Color {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    return when (this) {
        RepositoryHealthStatus.ACTIVE -> scheme.primary
        RepositoryHealthStatus.COOLING -> scheme.tertiary
        RepositoryHealthStatus.STALE, RepositoryHealthStatus.ABANDONED -> scheme.error
        RepositoryHealthStatus.ARCHIVED, RepositoryHealthStatus.UNKNOWN -> scheme.onSurfaceVariant
    }
}
