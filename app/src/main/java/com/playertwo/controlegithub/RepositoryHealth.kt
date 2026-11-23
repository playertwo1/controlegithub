package com.playertwo.controlegithub

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant

internal enum class RepositoryHealthStatus(
    val label: String,
    val severityRank: Int
) {
    EMPTY("Vazio", 0),
    ABANDONED("Abandonado", 1),
    STALE("Parado", 2),
    COOLING("Pouco movimento", 3),
    ACTIVE("Ativo", 4),
    ARCHIVED("Arquivado", 5),
    UNKNOWN("Sem dados", 6)
}

internal enum class RepositoryEmptyStatus {
    EMPTY,
    NOT_EMPTY,
    UNCONFIRMED
}

internal data class RepositoryEmptyProbeResult(
    val status: RepositoryEmptyStatus,
    val stopBatch: Boolean = false
)

internal const val MAX_EMPTY_CANDIDATE_CHECKS_PER_BATCH = 10

internal fun isEmptyRepositoryCandidate(archived: Boolean, sizeKb: Long?): Boolean =
    !archived && sizeKb == 0L

internal fun repositoryCommitsProbePath(owner: String, name: String): String {
    fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
    return "/repos/${encode(owner)}/${encode(name)}/commits?per_page=1"
}

internal fun confirmRepositoryEmpty(
    client: GitHubHttpClient,
    accessToken: String,
    owner: String,
    name: String,
    onCallCreated: (GitHubHttpCall) -> Unit = {}
): RepositoryEmptyProbeResult {
    val call = client.get(repositoryCommitsProbePath(owner, name), accessToken)
    onCallCreated(call)
    return when (val response = call.execute()) {
        is GitHubHttpResult.Success -> RepositoryEmptyProbeResult(RepositoryEmptyStatus.NOT_EMPTY)
        is GitHubHttpResult.Failure -> when {
            response.statusCode == 409 -> RepositoryEmptyProbeResult(RepositoryEmptyStatus.EMPTY)
            response.error == GitHubHttpError.RATE_LIMITED ||
                response.error == GitHubHttpError.UNAUTHORIZED ||
                response.error == GitHubHttpError.NETWORK ||
                response.error == GitHubHttpError.TIMEOUT ||
                response.error == GitHubHttpError.CANCELLED ||
                response.error == GitHubHttpError.SERVER ->
                RepositoryEmptyProbeResult(RepositoryEmptyStatus.UNCONFIRMED, stopBatch = true)
            else -> RepositoryEmptyProbeResult(RepositoryEmptyStatus.UNCONFIRMED)
        }
    }
}

internal fun checkEmptyRepositoryCandidates(
    repositories: List<GitHubRepository>,
    existingResults: Map<Long, RepositoryEmptyStatus>,
    maxChecks: Int = MAX_EMPTY_CANDIDATE_CHECKS_PER_BATCH,
    isCancelled: () -> Boolean = { false },
    probe: (GitHubRepository) -> RepositoryEmptyProbeResult
): Map<Long, RepositoryEmptyStatus> {
    if (maxChecks <= 0) return existingResults
    val candidates = repositories
        .asSequence()
        .filter { it.isEmptyCandidate && it.id !in existingResults }
        .distinctBy(GitHubRepository::id)
        .take(maxChecks)
        .toList()
    if (candidates.isEmpty()) return existingResults

    val updated = existingResults.toMutableMap()
    for (candidate in candidates) {
        if (isCancelled()) break
        val result = probe(candidate)
        if (result.stopBatch) break
        updated[candidate.id] = result.status
    }
    return updated
}

internal data class RepositoryHealth(
    val status: RepositoryHealthStatus,
    val daysWithoutPush: Long?
) {
    val summary: String
        get() = when {
            status == RepositoryHealthStatus.EMPTY -> "${status.label} · sem arquivos"
            status == RepositoryHealthStatus.ARCHIVED -> status.label
            daysWithoutPush == null -> "Sem push registrado"
            daysWithoutPush == 0L -> "${status.label} · push hoje"
            daysWithoutPush == 1L -> "${status.label} · 1 dia sem push"
            else -> "${status.label} · $daysWithoutPush dias sem push"
        }

    val compactSummary: String
        get() = when {
            status == RepositoryHealthStatus.EMPTY -> status.label
            status == RepositoryHealthStatus.ARCHIVED -> status.label
            daysWithoutPush == null -> status.label
            daysWithoutPush == 0L -> "${status.label} · hoje"
            else -> "${status.label} · ${daysWithoutPush}d"
        }
}

internal fun repositoryHealth(
    pushedAt: Instant?,
    archived: Boolean,
    now: Instant = Instant.now(),
    confirmedEmpty: Boolean = false
): RepositoryHealth {
    val days = pushedAt?.let { Duration.between(it, now).toDays().coerceAtLeast(0) }
    val status = when {
        archived -> RepositoryHealthStatus.ARCHIVED
        confirmedEmpty -> RepositoryHealthStatus.EMPTY
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
        RepositoryHealthStatus.EMPTY,
        RepositoryHealthStatus.ABANDONED,
        RepositoryHealthStatus.STALE -> scheme.error
        RepositoryHealthStatus.COOLING -> scheme.tertiary
        RepositoryHealthStatus.ACTIVE -> scheme.primary
        RepositoryHealthStatus.ARCHIVED,
        RepositoryHealthStatus.UNKNOWN -> scheme.onSurfaceVariant
    }
}
