package com.playertwo.controlegithub

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepositoryHealthTest {
    private val now = Instant.parse("2026-10-20T12:00:00Z")

    private fun daysAgo(days: Long) = now.minus(Duration.ofDays(days))

    @Test
    fun classifiesByDaysWithoutPush() {
        assertEquals(RepositoryHealthStatus.ACTIVE, repositoryHealth(daysAgo(0), false, now).status)
        assertEquals(RepositoryHealthStatus.ACTIVE, repositoryHealth(daysAgo(30), false, now).status)
        assertEquals(RepositoryHealthStatus.COOLING, repositoryHealth(daysAgo(31), false, now).status)
        assertEquals(RepositoryHealthStatus.STALE, repositoryHealth(daysAgo(200), false, now).status)
        assertEquals(RepositoryHealthStatus.ABANDONED, repositoryHealth(daysAgo(400), false, now).status)
    }

    @Test
    fun archivedAndUnknownAreExplicit() {
        assertEquals(RepositoryHealthStatus.ARCHIVED, repositoryHealth(daysAgo(1), true, now).status)
        val unknown = repositoryHealth(null, false, now)
        assertEquals(RepositoryHealthStatus.UNKNOWN, unknown.status)
        assertNull(unknown.daysWithoutPush)
        assertEquals("Sem push registrado", unknown.summary)
    }

    @Test
    fun summaryShowsDays() {
        assertEquals("Ativo · push hoje", repositoryHealth(now, false, now).summary)
        assertEquals("Ativo · 1 dia sem push", repositoryHealth(daysAgo(1), false, now).summary)
        assertEquals("Parado · 120 dias sem push", repositoryHealth(daysAgo(120), false, now).summary)
    }

    @Test
    fun futurePushIsClampedToZero() {
        assertEquals(0L, repositoryHealth(now.plusSeconds(3600), false, now).daysWithoutPush)
    }

    @Test
    fun repositoryListParsesHealthFields() {
        val repo = parseGitHubRepositories(
            """[{"id":1,"name":"app","full_name":"me/app","owner":{"login":"me"},"private":false,
            "stargazers_count":2,"pushed_at":"2026-10-01T00:00:00Z","archived":true,"open_issues_count":4}]"""
        ).single()
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), repo.pushedAt)
        assertEquals(true, repo.archived)
        assertEquals(4, repo.openIssues)
    }
}
