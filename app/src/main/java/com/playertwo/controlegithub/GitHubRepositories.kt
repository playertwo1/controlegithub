package com.playertwo.controlegithub

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

internal data class GitHubRepository(
    val id: Long,
    val name: String,
    val fullName: String,
    val owner: String,
    val isPrivate: Boolean,
    val description: String?,
    val language: String?,
    val stars: Int
) {
    override fun toString() = "GitHubRepository(id=$id, isPrivate=$isPrivate)"
}

internal fun parseGitHubRepositories(json: String): List<GitHubRepository> {
    val array = JSONArray(json)
    return List(array.length()) { index ->
        val repository = array.getJSONObject(index)
        val id = repository.opt("id") as? Number
            ?: throw IOException("Resposta de repositórios inválida")
        val name = repository.requiredString("name")
        val fullName = repository.requiredString("full_name")
        val owner = repository.optJSONObject("owner")?.requiredString("login")
            ?: throw IOException("Resposta de repositórios inválida")
        val isPrivate = repository.opt("private") as? Boolean
            ?: throw IOException("Resposta de repositórios inválida")
        val stars = repository.opt("stargazers_count") as? Number
            ?: throw IOException("Resposta de repositórios inválida")
        val description = repository.optionalString("description")
        val language = repository.optionalString("language")
        val idValue = id.toLong()
        val starsValue = stars.toInt()
        if (idValue <= 0 || id.toDouble() != idValue.toDouble() || starsValue < 0 ||
            stars.toDouble() != starsValue.toDouble() ||
            !fullName.equals("$owner/$name", ignoreCase = true)
        ) throw IOException("Resposta de repositórios inválida")
        GitHubRepository(idValue, name, fullName, owner, isPrivate, description, language, starsValue)
    }
}

private fun JSONObject.requiredString(key: String): String =
    (opt(key) as? String)?.takeIf(String::isNotBlank)
        ?: throw IOException("Resposta de repositórios inválida")

private fun JSONObject.optionalString(key: String): String? = when (val value = opt(key)) {
    null, JSONObject.NULL -> null
    is String -> value
    else -> throw IOException("Resposta de repositórios inválida")
}

internal class GitHubRepositoryPager(
    client: GitHubHttpClient,
    accessToken: String,
    decode: (String) -> List<GitHubRepository> = ::parseGitHubRepositories
) {
    private val pager = GitHubPaginator(
        client = client,
        firstPath = FIRST_PAGE_PATH,
        accessToken = accessToken,
        decode = decode,
        itemKey = { it.id.toString() }
    )

    fun loadNext(): GitHubPageResult<GitHubRepository> = pager.loadNext()

    fun cancel() = pager.cancel()

    internal companion object {
        const val FIRST_PAGE_PATH = "/user/repos?visibility=all&affiliation=owner%2Ccollaborator%2Corganization_member&sort=full_name&direction=asc&per_page=100"
    }
}
