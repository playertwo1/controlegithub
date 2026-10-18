package com.playertwo.controlegithub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DailySummaryUiTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-10-17T15:00:00Z")
    private val zone = ZoneId.of("America/Sao_Paulo")

    private fun fixtureSummary(
        source: DailySummaryDataSource = DailySummaryDataSource.ONLINE,
        cachedAt: Long? = null
    ): DailySummary {
        val windowStart = now.minusSeconds(86400)
        val windowEnd = now
        val metrics = DailySummaryMetrics(
            totalNotifications = 5,
            unreadNotifications = 2,
            reviewRequests = 1,
            mentions = 2,
            successfulWorkflows = 3,
            failedWorkflows = 1,
            activeIssues = 4
        )
        val items = listOf(
            DailySummaryItem("1", "Pull Request Review", "owner/repo · review_requested", "Notificação", now.minusSeconds(3600)),
            DailySummaryItem("2", "CI Build", "owner/repo · success", "CI/CD", now.minusSeconds(7200))
        )
        return DailySummary(
            windowStart = windowStart,
            windowEnd = windowEnd,
            zoneId = zone,
            metrics = metrics,
            items = items,
            dataSource = source,
            cachedAtMillis = cachedAt
        )
    }

    @Test
    fun dailySummaryDisplaysPeriodTimezoneAndMetrics() {
        val summary = fixtureSummary(DailySummaryDataSource.ONLINE)
        compose.setContent {
            ControleTheme(AppThemeMode.LIGHT) {
                DailySummaryCard(
                    summary = summary
                )
            }
        }

        // Verify period & timezone
        compose.onNodeWithTag("daily_summary_period").assertIsDisplayed()
        compose.onNodeWithTag("daily_summary_timezone").assertIsDisplayed()
        compose.onNodeWithText("Fuso horário: America/Sao_Paulo").assertIsDisplayed()

        // Verify source badge
        compose.onNodeWithTag("daily_summary_source").assertIsDisplayed()
        compose.onNodeWithText("Ao vivo do GitHub").assertIsDisplayed()

        // Verify metrics
        compose.onNodeWithText("5").assertIsDisplayed() // Notificações
        compose.onNodeWithText("2 não lidas").assertIsDisplayed()
        compose.onNodeWithText("1").assertIsDisplayed() // Revisões PR
        compose.onNodeWithText("3 / 1").assertIsDisplayed() // CI / Workflows
        compose.onNodeWithText("4").assertIsDisplayed() // Issues
    }

    @Test
    fun dailySummaryExportMarkdownCopiesToClipboardAndShowsFeedback() {
        val summary = fixtureSummary(DailySummaryDataSource.ONLINE)
        compose.setContent {
            ControleTheme(AppThemeMode.LIGHT) {
                DailySummaryCard(
                    summary = summary
                )
            }
        }

        // Export button is displayed
        compose.onNodeWithTag("daily_summary_export_button").assertIsDisplayed()
        compose.onNodeWithText("Exportar Markdown").performClick()

        // Feedback is displayed
        compose.onNodeWithTag("daily_summary_export_feedback").assertIsDisplayed()
        compose.onNodeWithText("Resumo copiado em formato Markdown!").assertIsDisplayed()
    }

    @Test
    fun dailySummaryDisplaysCachedSourceWhenOffline() {
        val cachedSummary = fixtureSummary(
            source = DailySummaryDataSource.CACHED,
            cachedAt = 1729166400000L
        )
        compose.setContent {
            ControleTheme(AppThemeMode.LIGHT) {
                DailySummaryCard(
                    summary = cachedSummary
                )
            }
        }

        compose.onNodeWithTag("daily_summary_source").assertIsDisplayed()
        compose.onNodeWithText("Cache local").assertIsDisplayed()
        compose.onNodeWithTag("daily_summary_cached_timestamp").assertIsDisplayed()
    }

    @Test
    fun dailySummaryDisplaysPartialCoverageSource() {
        val partialSummary = fixtureSummary(
            source = DailySummaryDataSource.PARTIAL
        )
        compose.setContent {
            ControleTheme(AppThemeMode.LIGHT) {
                DailySummaryCard(
                    summary = partialSummary
                )
            }
        }

        compose.onNodeWithTag("daily_summary_source").assertIsDisplayed()
        compose.onNodeWithText("Cobertura parcial").assertIsDisplayed()
    }
}
