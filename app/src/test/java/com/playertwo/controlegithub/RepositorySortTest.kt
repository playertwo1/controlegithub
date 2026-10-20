package com.playertwo.controlegithub

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class RepositorySortTest {
    private val now = Instant.now()
    private fun repo(id: Long, daysAgo: Long?, stars: Int = 0, archived: Boolean = false) = GitHubRepository(
        id, "r$id", "me/r$id", "me", false, null, null, stars,
        pushedAt = daysAgo?.let { now.minus(Duration.ofDays(it)) }, archived = archived
    )

    private val repos = listOf(repo(1, 5, stars = 1), repo(2, 400, stars = 9), repo(3, null), repo(4, 100, stars = 3))

    @Test
    fun nameKeepsApiOrder() =
        assertEquals(listOf(1L, 2L, 3L, 4L), sortGitHubRepositories(repos, RepositorySort.NAME).map { it.id })

    @Test
    fun mostStaleFirstWithUnknownLast() =
        assertEquals(listOf(2L, 4L, 1L, 3L), sortGitHubRepositories(repos, RepositorySort.MOST_STALE).map { it.id })

    @Test
    fun recentFirstWithUnknownLast() =
        assertEquals(listOf(1L, 4L, 2L, 3L), sortGitHubRepositories(repos, RepositorySort.RECENT).map { it.id })

    @Test
    fun starsDescending() =
        assertEquals(listOf(2L, 4L, 1L, 3L), sortGitHubRepositories(repos, RepositorySort.STARS).map { it.id })

    @Test
    fun filtersByHealth() {
        assertEquals(listOf(2L), filterByHealth(repos, RepositoryHealthStatus.ABANDONED).map { it.id })
        assertEquals(repos, filterByHealth(repos, null))
        assertEquals(listOf(5L), filterByHealth(listOf(repo(5, 1, archived = true)), RepositoryHealthStatus.ARCHIVED).map { it.id })
    }
}
