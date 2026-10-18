package com.playertwo.controlegithub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class BetaRobustnessTest {

    @Test
    fun dailySummaryResilienceWithZeroEventsProducesCleanMarkdown() {
        val now = Instant.now()
        val zoneId = ZoneId.of("UTC")
        val emptySummary = buildDailySummary(
            notifications = emptyList(),
            workflowRuns = emptyList(),
            issues = emptyList(),
            windowStart = now.minusSeconds(86400),
            windowEnd = now,
            zoneId = zoneId,
            isOfflineCache = false,
            cachedAtMillis = null,
            hasPartialFailure = false
        )

        assertNotNull(emptySummary)
        assertEquals(0, emptySummary.metrics.totalNotifications)
        assertEquals(0, emptySummary.metrics.unreadNotifications)
        assertEquals(0, emptySummary.metrics.activeIssues)

        val markdown = exportDailySummaryToMarkdown(emptySummary)
        assertTrue(markdown.contains("Resumo Diário de Atividades"))
        assertTrue(markdown.contains("Métricas Consolidadas"))
        assertTrue(markdown.contains("| Notificações recebidas | 0 |"))
    }

    @Test
    fun dailySummaryWithEventsGeneratesFormattedMarkdown() {
        val now = Instant.now()
        val zoneId = ZoneId.of("UTC")
        val summary = DailySummary(
            windowStart = now.minusSeconds(86400),
            windowEnd = now,
            zoneId = zoneId,
            metrics = DailySummaryMetrics(
                totalNotifications = 5,
                unreadNotifications = 2,
                reviewRequests = 1,
                mentions = 1,
                successfulWorkflows = 3,
                failedWorkflows = 0,
                activeIssues = 2
            ),
            items = listOf(
                DailySummaryItem(
                    id = "item-1",
                    title = "Nova issue criada",
                    subtitle = "octocat/hello-world#42",
                    category = "Issue",
                    timestamp = now.minusSeconds(3600)
                )
            ),
            dataSource = DailySummaryDataSource.ONLINE
        )

        val markdown = exportDailySummaryToMarkdown(summary)
        assertTrue(markdown.contains("Fonte: Dados ao vivo do GitHub"))
        assertTrue(markdown.contains("Nova issue criada"))
        assertTrue(markdown.contains("Principais Eventos"))
    }
}
