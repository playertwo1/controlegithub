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
            "stargazers_count":2,"pushed_at":"2026-10-01T00:00:00Z","archived":true,"open_issues_count":4,"size":0}]"""
        ).single()
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), repo.pushedAt)
        assertEquals(true, repo.archived)
        assertEquals(4, repo.openIssues)
        assertEquals(0L, repo.sizeKb)
    }

    @Test
    fun zeroSizeUnarchivedRepositoryIsEmptyRegardlessOfPushDate() {
        val freshEmpty = repositoryHealth(now, archived = false, now = now, sizeKb = 0L)
        assertEquals(RepositoryHealthStatus.EMPTY, freshEmpty.status)
        assertEquals("Vazio · sem arquivos", freshEmpty.summary)
        assertEquals("Vazio", freshEmpty.compactSummary)

        val neverPushedEmpty = repositoryHealth(null, archived = false, now = now, sizeKb = 0L)
        assertEquals(RepositoryHealthStatus.EMPTY, neverPushedEmpty.status)

        val archivedEmpty = repositoryHealth(null, archived = true, now = now, sizeKb = 0L)
        assertEquals(RepositoryHealthStatus.ARCHIVED, archivedEmpty.status)

        val unknownSize = repositoryHealth(null, archived = false, now = now, sizeKb = null)
        assertEquals(RepositoryHealthStatus.UNKNOWN, unknownSize.status)

        val nonEmpty = repositoryHealth(daysAgo(5), archived = false, now = now, sizeKb = 12L)
        assertEquals(RepositoryHealthStatus.ACTIVE, nonEmpty.status)
    }

    @Test
    fun severityOrderPutsEmptyFirstAndArchivedAndUnknownLast() {
        val ordered = RepositoryHealthStatus.entries.sortedBy { it.severityRank }
        assertEquals(
            listOf(
                RepositoryHealthStatus.EMPTY,
                RepositoryHealthStatus.ABANDONED,
                RepositoryHealthStatus.STALE,
                RepositoryHealthStatus.COOLING,
                RepositoryHealthStatus.ACTIVE,
                RepositoryHealthStatus.ARCHIVED,
                RepositoryHealthStatus.UNKNOWN
            ),
            ordered
        )
        assertEquals(ordered, RepositoryHealthStatus.entries)
    }

    @Test
    fun repositoryParsersHandleMissingAndInvalidSize() {
        val listRepos = parseGitHubRepositories(
            """[
                {"id":1,"name":"empty","full_name":"me/empty","owner":{"login":"me"},"private":false,"stargazers_count":0,"size":0},
                {"id":2,"name":"no-size","full_name":"me/no-size","owner":{"login":"me"},"private":false,"stargazers_count":0},
                {"id":3,"name":"neg-size","full_name":"me/neg-size","owner":{"login":"me"},"private":false,"stargazers_count":0,"size":-5}
            ]"""
        )
        assertEquals(0L, listRepos[0].sizeKb)
        assertEquals(RepositoryHealthStatus.EMPTY, listRepos[0].health.status)
        assertNull(listRepos[1].sizeKb)
        assertEquals(RepositoryHealthStatus.UNKNOWN, listRepos[1].health.status)
        assertNull(listRepos[2].sizeKb)

        val detail = parseGitHubRepositoryDetail(
            """{"id":1,"name":"empty","full_name":"me/empty","owner":{"login":"me"},"private":false,"size":0}"""
        )
        assertEquals(0L, detail.sizeKb)
    }
}
