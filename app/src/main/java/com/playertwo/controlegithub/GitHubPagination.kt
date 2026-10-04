package com.playertwo.controlegithub

import java.util.concurrent.atomic.AtomicBoolean

internal sealed interface GitHubPageResult<out T> {
    val items: List<T>

    data class Loaded<T>(override val items: List<T>, val hasNext: Boolean) : GitHubPageResult<T>
    data class RateLimited<T>(override val items: List<T>, val retryAtEpochMillis: Long) : GitHubPageResult<T>
    data class Failed<T>(
        override val items: List<T>,
        val message: String,
        val error: GitHubHttpError? = null
    ) : GitHubPageResult<T>
}

internal class GitHubPaginator<T>(
    private val client: GitHubHttpClient,
    firstPath: String,
    private val accessToken: String?,
    private val decode: (String) -> List<T>,
    private val itemKey: (T) -> String
) {
    private var nextPath: String? = firstPath
    private val visitedPaths = mutableSetOf<String>()
    private val items = linkedMapOf<String, T>()
    @Volatile
    private var activeCall: GitHubHttpCall? = null

    fun cancel() {
        activeCall?.cancel()
    }

    @Synchronized
    fun loadNext(): GitHubPageResult<T> {
        val path = nextPath ?: return GitHubPageResult.Loaded(items.values.toList(), hasNext = false)

        if (!visitedPaths.add(path)) {
            nextPath = null
            return GitHubPageResult.Failed(items.values.toList(), PAGINATION_LOOP_MESSAGE)
        }

        val response = try {
            client.get(path, accessToken).also { activeCall = it }.execute()
        } finally {
            activeCall = null
        }

        return when (response) {
            is GitHubHttpResult.Success -> addPage(path, response)
            is GitHubHttpResult.Failure -> handleFailure(path, response)
        }
    }

    private fun addPage(path: String, response: GitHubHttpResult.Success): GitHubPageResult<T> {
        val pageItems = try {
            decode(response.body).map { item ->
                val key = itemKey(item)
                require(key.isNotBlank())
                key to item
            }
        } catch (_: Exception) {
            visitedPaths.remove(path)
            return GitHubPageResult.Failed(items.values.toList(), INVALID_RESPONSE_MESSAGE)
        }
        pageItems.forEach { (key, item) -> items.putIfAbsent(key, item) }

        when (val next = GitHubLinkHeader.next(response.headers.link)) {
            GitHubLinkHeader.NextPage.None -> {
                nextPath = null
                return GitHubPageResult.Loaded(items.values.toList(), hasNext = false)
            }
            is GitHubLinkHeader.NextPage.Invalid -> {
                nextPath = null
                return GitHubPageResult.Failed(items.values.toList(), next.message)
            }
            is GitHubLinkHeader.NextPage.Url -> {
                val nextPath = try {
                    client.pathFromLink(next.value)
                } catch (_: IllegalArgumentException) {
                    this.nextPath = null
                    return GitHubPageResult.Failed(items.values.toList(), INVALID_LINK_MESSAGE)
                }
                if (nextPath == path || nextPath in visitedPaths) {
                    this.nextPath = null
                    return GitHubPageResult.Failed(items.values.toList(), PAGINATION_LOOP_MESSAGE)
                }
                this.nextPath = nextPath
            }
        }

        return GitHubPageResult.Loaded(items.values.toList(), hasNext = true)
    }

    private fun handleFailure(
        path: String,
        failure: GitHubHttpResult.Failure
    ): GitHubPageResult<T> {
        if (failure.error == GitHubHttpError.RATE_LIMITED) {
            visitedPaths.remove(path)
            return GitHubPageResult.RateLimited(
                items.values.toList(),
                failure.retryAtEpochMillis ?: Long.MAX_VALUE
            )
        }

        visitedPaths.remove(path)
        return GitHubPageResult.Failed(items.values.toList(), failure.error.userMessage, failure.error)
    }

    private companion object {
        const val PAGINATION_LOOP_MESSAGE = "O GitHub repetiu o link de paginação. Atualize para tentar novamente."
        const val INVALID_LINK_MESSAGE = "O GitHub retornou um link de página inválido."
        const val INVALID_RESPONSE_MESSAGE = "Não foi possível interpretar a resposta do GitHub."
    }
}

private object GitHubLinkHeader {
    private val entry = Regex("<([^<>]+)>([^<]*)")
    private val relation = Regex("(?:^|;)\\s*rel\\s*=\\s*(?:\"([^\"]+)\"|([^;,\\s]+))", RegexOption.IGNORE_CASE)

    sealed interface NextPage {
        data object None : NextPage
        data class Url(val value: String) : NextPage
        data class Invalid(val message: String) : NextPage
    }

    fun next(header: String?): NextPage {
        if (header.isNullOrBlank()) return NextPage.None
        val entries = entry.findAll(header).toList()
        if (entries.isEmpty() || header.count { it == '<' } != entries.size || header.count { it == '>' } != entries.size) {
            return NextPage.Invalid("O GitHub retornou um cabeçalho de paginação inválido.")
        }

        for (match in entries) {
            val relations = relation.find(match.groupValues[2])
                ?: return NextPage.Invalid("O GitHub retornou um cabeçalho de paginação inválido.")
            val values = (relations.groups[1]?.value ?: relations.groups[2]?.value.orEmpty())
                .split(Regex("\\s+"))
            if (values.any { it.equals("next", ignoreCase = true) }) {
                return NextPage.Url(match.groupValues[1])
            }
        }
        return NextPage.None
    }
}
