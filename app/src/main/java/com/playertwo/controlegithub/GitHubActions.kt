package com.playertwo.controlegithub

import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeParseException
import org.json.JSONArray
import org.json.JSONObject

internal data class GitHubWorkflow(
    val id: Long,
    val name: String,
    val path: String,
    val state: String
)

internal data class GitHubWorkflowRun(
    val id: Long,
    val workflowId: Long,
    val workflowName: String?,
    val runNumber: Long,
    val event: String,
    val status: String,
    val conclusion: String?,
    val branch: String?,
    val createdAt: Instant?
)

internal data class GitHubActionJob(
    val id: Long,
    val runId: Long,
    val name: String,
    val status: String,
    val conclusion: String?
)

internal fun parseGitHubWorkflows(json: String): List<GitHubWorkflow> =
    parseActionsCollection(json, "workflows") { item ->
        GitHubWorkflow(
            id = item.requiredPositiveLong("id"),
            name = item.requiredSafeText("name"),
            path = item.requiredSafeText("path"),
            state = item.requiredSafeCode("state")
        )
    }

internal fun parseGitHubWorkflowRuns(json: String): List<GitHubWorkflowRun> =
    parseActionsCollection(json, "workflow_runs") { item ->
        val createdAt = item.optionalSafeText("created_at")?.let { value ->
            try {
                Instant.parse(value)
            } catch (_: DateTimeParseException) {
                null
            }
        }
        GitHubWorkflowRun(
            id = item.requiredPositiveLong("id"),
            workflowId = item.requiredPositiveLong("workflow_id"),
            workflowName = item.optionalSafeText("name"),
            runNumber = item.requiredPositiveLong("run_number"),
            event = item.requiredSafeCode("event"),
            status = item.requiredSafeCode("status"),
            conclusion = item.optionalSafeCode("conclusion"),
            branch = item.optionalSafeText("head_branch"),
            createdAt = createdAt
        )
    }

internal fun parseGitHubActionJobs(json: String): List<GitHubActionJob> =
    parseActionsCollection(json, "jobs") { item ->
        GitHubActionJob(
            id = item.requiredPositiveLong("id"),
            runId = item.requiredPositiveLong("run_id"),
            name = item.requiredSafeText("name"),
            status = item.requiredSafeCode("status"),
            conclusion = item.optionalSafeCode("conclusion")
        )
    }

private inline fun <T> parseActionsCollection(
    json: String,
    key: String,
    map: (JSONObject) -> T
): List<T> {
    val items = JSONObject(json).opt(key) as? JSONArray
        ?: throw IOException("Resposta de Actions inválida")
    return List(items.length()) { index -> map(items.getJSONObject(index)) }
}

private fun JSONObject.requiredPositiveLong(key: String): Long =
    optionalPositiveLong(key) ?: throw IOException("Resposta de Actions inválida")

private fun JSONObject.optionalPositiveLong(key: String): Long? = when (val value = opt(key)) {
    null, JSONObject.NULL -> null
    is Number -> value.toLong().takeIf { it > 0 && value.toDouble() == it.toDouble() }
        ?: throw IOException("Resposta de Actions inválida")
    else -> throw IOException("Resposta de Actions inválida")
}

private fun JSONObject.requiredSafeText(key: String): String =
    optionalSafeText(key)?.takeIf(String::isNotBlank)
        ?: throw IOException("Resposta de Actions inválida")

private fun JSONObject.optionalSafeText(key: String): String? = when (val value = opt(key)) {
    null, JSONObject.NULL -> null
    is String -> value.filterNot(Char::isISOControl).trim().take(200).takeIf(String::isNotEmpty)
    else -> throw IOException("Resposta de Actions inválida")
}

private fun JSONObject.requiredSafeCode(key: String): String =
    optionalSafeCode(key)?.takeIf(String::isNotBlank)
        ?: throw IOException("Resposta de Actions inválida")

private fun JSONObject.optionalSafeCode(key: String): String? =
    optionalSafeText(key)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,80}")) }
        ?: if (isNull(key)) null else throw IOException("Resposta de Actions inválida")

internal class GitHubActionsRepository(
    private val client: GitHubHttpClient,
    private val accessToken: String,
    private val repository: GitHubRepository
) {
    fun workflows(): GitHubPaginator<GitHubWorkflow> = pager(
        "${repositoryRoute()}/actions/workflows?per_page=50",
        ::parseGitHubWorkflows,
        { it.id.toString() }
    )

    fun runs(workflowId: Long? = null): GitHubPaginator<GitHubWorkflowRun> {
        require(workflowId == null || workflowId > 0)
        val route = if (workflowId == null) "${repositoryRoute()}/actions/runs"
        else "${repositoryRoute()}/actions/workflows/$workflowId/runs"
        val decode: (String) -> List<GitHubWorkflowRun> = { json ->
            parseGitHubWorkflowRuns(json).also { runs ->
                require(workflowId == null || runs.all { it.workflowId == workflowId })
            }
        }
        return pager("$route?per_page=50", decode, { it.id.toString() })
    }

    fun jobs(runId: Long): GitHubPaginator<GitHubActionJob> {
        require(runId > 0)
        return pager(
            "${repositoryRoute()}/actions/runs/$runId/jobs?filter=latest&per_page=50",
            { json -> parseGitHubActionJobs(json).also { jobs -> require(jobs.all { it.runId == runId }) } },
            { it.id.toString() }
        )
    }

    private fun <T> pager(path: String, decode: (String) -> List<T>, key: (T) -> String) =
        GitHubPaginator(client, path, accessToken, decode, key)

    private fun repositoryRoute() =
        "/repos/${encodePathSegment(repository.owner)}/${encodePathSegment(repository.name)}"

    private fun encodePathSegment(value: String) =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}

internal fun workflowStateLabel(state: String): String = when (state.lowercase()) {
    "active" -> "Ativo"
    "disabled_manually" -> "Desativado manualmente"
    "disabled_inactivity" -> "Desativado por inatividade"
    else -> "Estado desconhecido: $state"
}

internal fun workflowRunStatusLabel(status: String): String = when (status.lowercase()) {
    "queued", "requested", "waiting", "pending" -> "Na fila"
    "in_progress" -> "Em andamento"
    "completed" -> "Concluída"
    else -> "Estado desconhecido: $status"
}

internal fun workflowConclusionLabel(conclusion: String?, status: String? = null): String = when (conclusion?.lowercase()) {
    null -> if (status.equals("completed", ignoreCase = true)) "Conclusão não informada" else "Aguardando conclusão"
    "success" -> "Sucesso"
    "failure" -> "Falhou"
    "neutral" -> "Neutro"
    "cancelled" -> "Cancelada"
    "skipped" -> "Ignorada"
    "timed_out" -> "Tempo esgotado"
    "action_required" -> "Ação necessária"
    "stale" -> "Desatualizada"
    "startup_failure" -> "Falha ao iniciar"
    else -> "Conclusão desconhecida: $conclusion"
}

internal fun actionJobStatusLabel(status: String): String = when (status.lowercase()) {
    "queued", "waiting", "pending" -> "Na fila"
    "in_progress" -> "Em andamento"
    "completed" -> "Concluído"
    else -> "Estado desconhecido: $status"
}
