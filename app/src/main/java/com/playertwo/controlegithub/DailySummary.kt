package com.playertwo.controlegithub

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal enum class DailySummaryDataSource(val label: String) {
    ONLINE("Ao vivo do GitHub"),
    CACHED("Cache local"),
    PARTIAL("Cobertura parcial")
}

internal data class DailySummaryMetrics(
    val totalNotifications: Int,
    val unreadNotifications: Int,
    val reviewRequests: Int,
    val mentions: Int,
    val successfulWorkflows: Int,
    val failedWorkflows: Int,
    val activeIssues: Int
)

internal data class DailySummaryItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val timestamp: Instant
)

internal data class DailySummary(
    val windowStart: Instant,
    val windowEnd: Instant,
    val zoneId: ZoneId,
    val metrics: DailySummaryMetrics,
    val items: List<DailySummaryItem>,
    val dataSource: DailySummaryDataSource,
    val cachedAtMillis: Long? = null
)

internal fun buildDailySummary(
    notifications: List<GitHubNotification> = emptyList(),
    workflowRuns: List<GitHubWorkflowRun> = emptyList(),
    issues: List<GitHubIssue> = emptyList(),
    windowStart: Instant,
    windowEnd: Instant,
    zoneId: ZoneId = ZoneId.systemDefault(),
    isOfflineCache: Boolean = false,
    cachedAtMillis: Long? = null,
    hasPartialFailure: Boolean = false
): DailySummary {
    val uniqueNotifications = notifications.distinctBy { it.id }
        .filter { it.updatedAt != null && it.updatedAt in windowStart..windowEnd }

    val uniqueWorkflowRuns = workflowRuns.distinctBy { it.id }
        .filter { it.createdAt != null && it.createdAt in windowStart..windowEnd }

    val uniqueIssues = issues.distinctBy { it.identity }
        .filter { it.updatedAt != null && it.updatedAt in windowStart..windowEnd }

    val unread = uniqueNotifications.count { it.unread }
    val reviewRequests = uniqueNotifications.count { it.reason.equals("review_requested", ignoreCase = true) }
    val mentions = uniqueNotifications.count {
        it.reason.equals("mention", ignoreCase = true) || it.reason.equals("team_mention", ignoreCase = true)
    }
    val successWorkflows = uniqueWorkflowRuns.count { it.conclusion.equals("success", ignoreCase = true) }
    val failWorkflows = uniqueWorkflowRuns.count {
        it.conclusion.equals("failure", ignoreCase = true) || it.conclusion.equals("timed_out", ignoreCase = true)
    }
    val activeIssues = uniqueIssues.size

    val metrics = DailySummaryMetrics(
        totalNotifications = uniqueNotifications.size,
        unreadNotifications = unread,
        reviewRequests = reviewRequests,
        mentions = mentions,
        successfulWorkflows = successWorkflows,
        failedWorkflows = failWorkflows,
        activeIssues = activeIssues
    )

    val items = buildList {
        uniqueNotifications.forEach { n ->
            add(
                DailySummaryItem(
                    id = "notif_${n.id}",
                    title = n.title ?: "Sem título",
                    subtitle = "${n.repositoryFullName} · ${n.reason}",
                    category = "Notificação",
                    timestamp = n.updatedAt ?: windowStart
                )
            )
        }
        uniqueWorkflowRuns.forEach { run ->
            add(
                DailySummaryItem(
                    id = "run_${run.id}",
                    title = run.workflowName ?: "Execução #${run.runNumber}",
                    subtitle = "Execução #${run.runNumber} · ${run.conclusion ?: run.status}",
                    category = "CI/CD",
                    timestamp = run.createdAt ?: windowStart
                )
            )
        }
        uniqueIssues.forEach { issue ->
            add(
                DailySummaryItem(
                    id = "issue_${issue.identity}",
                    title = issue.title,
                    subtitle = "${issue.repository} · #${issue.number}",
                    category = "Issue",
                    timestamp = issue.updatedAt ?: windowStart
                )
            )
        }
    }.sortedByDescending { it.timestamp }

    val dataSource = when {
        hasPartialFailure -> DailySummaryDataSource.PARTIAL
        isOfflineCache -> DailySummaryDataSource.CACHED
        else -> DailySummaryDataSource.ONLINE
    }

    return DailySummary(
        windowStart = windowStart,
        windowEnd = windowEnd,
        zoneId = zoneId,
        metrics = metrics,
        items = items,
        dataSource = dataSource,
        cachedAtMillis = cachedAtMillis
    )
}

internal fun exportDailySummaryToMarkdown(summary: DailySummary): String {
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(summary.zoneId)
    val startStr = dateFormatter.format(summary.windowStart)
    val endStr = dateFormatter.format(summary.windowEnd)
    val zoneStr = summary.zoneId.id

    val cacheNote = when (summary.dataSource) {
        DailySummaryDataSource.ONLINE -> "Fonte: Dados ao vivo do GitHub"
        DailySummaryDataSource.CACHED -> {
            val cacheDate = summary.cachedAtMillis?.let {
                dateFormatter.format(Instant.ofEpochMilli(it))
            } ?: "Data não disponível"
            "Fonte: Cache local (atualizado em $cacheDate)"
        }
        DailySummaryDataSource.PARTIAL -> "Fonte: Cobertura parcial (algumas fontes offline ou indisponíveis)"
    }

    return buildString {
        appendLine("# Resumo Diário de Atividades")
        appendLine()
        appendLine("- **Período:** $startStr até $endStr")
        appendLine("- **Fuso horário:** $zoneStr")
        appendLine("- **$cacheNote**")
        appendLine()
        appendLine("## Métricas Consolidadas")
        appendLine()
        appendLine("| Métrica | Total |")
        appendLine("| :--- | :---: |")
        appendLine("| Notificações recebidas | ${summary.metrics.totalNotifications} |")
        appendLine("| Notificações não lidas | ${summary.metrics.unreadNotifications} |")
        appendLine("| Solicitações de revisão (PR) | ${summary.metrics.reviewRequests} |")
        appendLine("| Menções diretas / time | ${summary.metrics.mentions} |")
        appendLine("| Workflows de CI com sucesso | ${summary.metrics.successfulWorkflows} |")
        appendLine("| Workflows de CI com falha | ${summary.metrics.failedWorkflows} |")
        appendLine("| Issues ativas no período | ${summary.metrics.activeIssues} |")
        appendLine()
        if (summary.items.isNotEmpty()) {
            appendLine("## Principais Eventos")
            appendLine()
            summary.items.take(20).forEach { item ->
                val time = dateFormatter.format(item.timestamp)
                appendLine("- `[$time]` **[${item.category}]** ${item.title} — *${item.subtitle}*")
            }
        }
    }
}
