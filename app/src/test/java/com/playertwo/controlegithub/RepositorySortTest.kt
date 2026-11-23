package com.playertwo.controlegithub

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class RepositorySortTest {
    private val now = Instant.parse("2026-10-20T12:00:00Z")
    private fun repo(
        id: Long,
        daysAgo: Long?,
        stars: Int = 0,
        archived: Boolean = false,
        sizeKb: Long? = 10L,
        confirmedEmpty: Boolean = false
    ) = GitHubRepository(
        id, "r$id", "me/r$id", "me", false, null, null, stars,
        pushedAt = daysAgo?.let { now.minus(Duration.ofDays(it)) },
        archived = archived,
        sizeKb = sizeKb,
        confirmedEmpty = confirmedEmpty
    )

    private val repos = listOf(repo(1, 5, stars = 1), repo(2, 400, stars = 9), repo(3, null), repo(4, 100, stars = 3))

    @Test
    fun nameKeepsApiOrder() =
        assertEquals(listOf(1L, 2L, 3L, 4L), sortGitHubRepositories(repos, RepositorySort.NAME, now).map { it.id })

    @Test
    fun mostStaleFirstWithUnknownLast() =
        assertEquals(listOf(2L, 4L, 1L, 3L), sortGitHubRepositories(repos, RepositorySort.MOST_STALE, now).map { it.id })

    @Test
    fun recentFirstWithUnknownLast() =
        assertEquals(listOf(1L, 4L, 2L, 3L), sortGitHubRepositories(repos, RepositorySort.RECENT, now).map { it.id })

    @Test
    fun starsDescending() =
        assertEquals(listOf(2L, 4L, 1L, 3L), sortGitHubRepositories(repos, RepositorySort.STARS, now).map { it.id })

    @Test
    fun healthOrdersBySeverityAndMoreDaysWithoutPushFirst() {
        val mixed = listOf(
            repo(1, daysAgo = null, sizeKb = 10L), // UNKNOWN
            repo(2, daysAgo = 5, sizeKb = 10L), // ACTIVE (5d)
            repo(3, daysAgo = 25, sizeKb = 10L), // ACTIVE (25d)
            repo(4, daysAgo = 500, archived = true, sizeKb = 10L), // ARCHIVED (500d)
            repo(5, daysAgo = 10, archived = true, sizeKb = 10L), // ARCHIVED (10d)
            repo(6, daysAgo = 40, sizeKb = 10L), // COOLING (40d)
            repo(7, daysAgo = 85, sizeKb = 10L), // COOLING (85d)
            repo(8, daysAgo = 120, sizeKb = 10L), // STALE (120d)
            repo(9, daysAgo = 300, sizeKb = 10L), // STALE (300d)
            repo(10, daysAgo = 400, sizeKb = 10L), // ABANDONED (400d)
            repo(11, daysAgo = 700, sizeKb = 10L), // ABANDONED (700d)
            repo(12, daysAgo = null, sizeKb = 0L, confirmedEmpty = true), // EMPTY (null push)
            repo(13, daysAgo = 1, sizeKb = 0L, confirmedEmpty = true), // EMPTY (1d)
            repo(14, daysAgo = 45, sizeKb = 0L, confirmedEmpty = true), // EMPTY (45d)
            repo(15, daysAgo = 3, sizeKb = 0L, confirmedEmpty = false) // size=0 NOT confirmed -> ACTIVE (3d)
        )
        assertEquals(
            listOf(14L, 13L, 12L, 11L, 10L, 9L, 8L, 7L, 6L, 3L, 2L, 15L, 4L, 5L, 1L),
            sortGitHubRepositories(mixed, RepositorySort.HEALTH, now).map { it.id }
        )
    }

    @Test
    fun filtersByHealth() {
        val withEmpty = repos + listOf(
            repo(5, 1, archived = true),
            repo(6, 0, sizeKb = 0L, confirmedEmpty = true),
            repo(7, 2, sizeKb = 0L, confirmedEmpty = false)
        )
        assertEquals(listOf(6L), filterByHealth(withEmpty, RepositoryHealthStatus.EMPTY, now).map { it.id })
        assertEquals(listOf(2L), filterByHealth(withEmpty, RepositoryHealthStatus.ABANDONED, now).map { it.id })
        assertEquals(listOf(4L), filterByHealth(withEmpty, RepositoryHealthStatus.STALE, now).map { it.id })
        assertEquals(listOf(1L, 7L), filterByHealth(withEmpty, RepositoryHealthStatus.ACTIVE, now).map { it.id })
        assertEquals(listOf(5L), filterByHealth(withEmpty, RepositoryHealthStatus.ARCHIVED, now).map { it.id })
        assertEquals(listOf(3L), filterByHealth(withEmpty, RepositoryHealthStatus.UNKNOWN, now).map { it.id })
        assertEquals(withEmpty, filterByHealth(withEmpty, null, now))
    }
}
