package com.playertwo.controlegithub

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal fun githubGlobalRepositorySearchPath(
    query: String,
    page: Int = 1,
    perPage: Int = 30
): String {
    val trimmed = query.trim()
    require(trimmed.isNotEmpty()) { "O termo de busca não pode ser vazio." }
    require(page >= 1) { "A página deve ser maior ou igual a 1." }
    require(perPage in 1..100) { "O limite por página deve estar entre 1 e 100." }
    val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.name())
    return "/search/repositories?q=$encoded&sort=stars&order=desc&per_page=$perPage&page=$page"
}

internal fun parseGitHubRepositorySearch(jsonBody: String): List<GitHubRepository> {
    val root = try {
        JSONObject(jsonBody)
    } catch (exception: JSONException) {
        throw IOException("Resposta de repositórios inválida", exception)
    }
    if (root.has("items") && !root.isNull("items") && root.optJSONArray("items") == null) {
        throw IOException("Resposta de repositórios inválida")
    }
    val itemsArray = root.optJSONArray("items") ?: JSONArray()
    return parseGitHubRepositories(itemsArray.toString())
}

internal class GitHubGlobalRepositorySearchPager(
    private val client: GitHubHttpClient,
    private val accessToken: String,
    private val query: String,
    private val perPage: Int = 30
) {
    private val pager = GitHubPaginator(
        client = client,
        firstPath = githubGlobalRepositorySearchPath(query = query, page = 1, perPage = perPage),
        accessToken = accessToken,
        decode = ::parseGitHubRepositorySearch,
        itemKey = { it.id.toString() }
    )

    fun loadNext(): GitHubPageResult<GitHubRepository> = pager.loadNext()

    fun cancel() = pager.cancel()
}
