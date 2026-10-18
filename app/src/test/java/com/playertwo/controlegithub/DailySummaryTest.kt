package com.playertwo.controlegithub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DailySummaryTest {

    private val now = Instant.parse("2026-10-17T12:00:00Z")
    private val windowStart = now.minusSeconds(86400) // 24h prior
    private val windowEnd = now
    private val zoneSaoPaulo = ZoneId.of("America/Sao_Paulo")

    @Test
    fun buildDailySummaryCalculatesMetricsAndDeduplicates() {
        val notif1 = GitHubNotification(
            id = "101",
            unread = true,
            reason = "review_requested",
            title = "PR review needed",
            subjectType = "PullRequest",
            repositoryId = 1L,
            repositoryFullName = "owner/repo",
            updatedAt = now.minusSeconds(3600),
            subjectApiUrl = null
        )
        // Duplicate of notif1 with same id
        val notif1Duplicate = notif1.copy(unread = false)

        val notif2 = GitHubNotification(
            id = "102",
            unread = true,
            reason = "mention",
            title = "Mentioned in issue",
            subjectType = "Issue",
            repositoryId = 1L,
            repositoryFullName = "owner/repo",
            updatedAt = now.minusSeconds(7200),
            subjectApiUrl = null
        )

        // Notification outside window
        val notifOutside = notif1.copy(id = "103", updatedAt = now.minusSeconds(100000))

        val runSuccess = GitHubWorkflowRun(
            id = 201L,
            workflowId = 1L,
            workflowName = "CI Test",
            runNumber = 42L,
            event = "push",
            status = "completed",
            conclusion = "success",
            branch = "main",
            createdAt = now.minusSeconds(1800)
        )
        // Duplicate run
        val runSuccessDuplicate = runSuccess.copy(workflowName = "Duplicate CI")

        val runFailure = runSuccess.copy(
            id = 202L,
            conclusion = "failure",
            createdAt = now.minusSeconds(900)
        )

        val issue = GitHubIssue(
            number = 15,
            title = "Fix crash",
            state = GitHubIssueState.OPEN,
            repository = "owner/repo",
            author = "dev",
            assignees = listOf("dev"),
            labels = listOf("bug"),
            updatedAt = now.minusSeconds(600)
        )

        val summary = buildDailySummary(
            notifications = listOf(notif1, notif1Duplicate, notif2, notifOutside),
            workflowRuns = listOf(runSuccess, runSuccessDuplicate, runFailure),
            issues = listOf(issue, issue), // duplicate issue
            windowStart = windowStart,
            windowEnd = windowEnd,
            zoneId = zoneSaoPaulo,
            isOfflineCache = false
        )

        // Metrics verification
        assertEquals(2, summary.metrics.totalNotifications) // notif1 + notif2 (notifOutside excluded, duplicate deduplicated)
        assertEquals(2, summary.metrics.unreadNotifications)
        assertEquals(1, summary.metrics.reviewRequests)
        assertEquals(1, summary.metrics.mentions)
        assertEquals(1, summary.metrics.successfulWorkflows)
        assertEquals(1, summary.metrics.failedWorkflows)
        assertEquals(1, summary.metrics.activeIssues)

        // Total deduplicated items = 2 notifications + 2 runs + 1 issue = 5 items
        assertEquals(5, summary.items.size)
        assertEquals(DailySummaryDataSource.ONLINE, summary.dataSource)
        assertEquals(zoneSaoPaulo, summary.zoneId)
    }

    @Test
    fun buildDailySummaryHandlesOfflineAndPartialSources() {
        val summaryCached = buildDailySummary(
            windowStart = windowStart,
            windowEnd = windowEnd,
            zoneId = zoneSaoPaulo,
            isOfflineCache = true,
            cachedAtMillis = 1729166400000L
        )
        assertEquals(DailySummaryDataSource.CACHED, summaryCached.dataSource)
        assertEquals(1729166400000L, summaryCached.cachedAtMillis)

        val summaryPartial = buildDailySummary(
            windowStart = windowStart,
            windowEnd = windowEnd,
            zoneId = zoneSaoPaulo,
            hasPartialFailure = true
        )
        assertEquals(DailySummaryDataSource.PARTIAL, summaryPartial.dataSource)
    }

    @Test
    fun exportDailySummaryToMarkdownProducesStructuredDocument() {
        val summary = buildDailySummary(
            notifications = listOf(
                GitHubNotification(
                    id = "1",
                    unread = true,
                    reason = "review_requested",
                    title = "PR Ready for Review",
                    subjectType = "PullRequest",
                    repositoryId = 1L,
                    repositoryFullName = "player/app",
                    updatedAt = now.minusSeconds(1800),
                    subjectApiUrl = null
                )
            ),
            windowStart = windowStart,
            windowEnd = windowEnd,
            zoneId = zoneSaoPaulo,
            isOfflineCache = false
        )

        val markdown = exportDailySummaryToMarkdown(summary)

        assertTrue(markdown.contains("# Resumo Diário de Atividades"))
        assertTrue(markdown.contains("America/Sao_Paulo"))
        assertTrue(markdown.contains("Fonte: Dados ao vivo do GitHub"))
        assertTrue(markdown.contains("| Notificações recebidas | 1 |"))
        assertTrue(markdown.contains("| Solicitações de revisão (PR) | 1 |"))
        assertTrue(markdown.contains("## Principais Eventos"))
        assertTrue(markdown.contains("PR Ready for Review"))
        assertTrue(markdown.contains("player/app · review_requested"))
    }
}
