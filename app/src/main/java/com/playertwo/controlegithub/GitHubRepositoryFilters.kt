package com.playertwo.controlegithub

internal enum class RepositoryVisibilityFilter {
    ALL,
    PUBLIC,
    PRIVATE
}

internal fun filterGitHubRepositories(
    repositories: List<GitHubRepository>,
    query: String,
    visibility: RepositoryVisibilityFilter,
    language: String?,
    favoriteIds: Set<Long> = emptySet(),
    favoritesOnly: Boolean = false
): List<GitHubRepository> {
    val normalizedQuery = query.trim()
    return repositories.filter { repository ->
        val matchesQuery = normalizedQuery.isEmpty() || listOfNotNull(
            repository.name,
            repository.fullName,
            repository.language
        ).any { it.contains(normalizedQuery, ignoreCase = true) }
        val matchesVisibility = when (visibility) {
            RepositoryVisibilityFilter.ALL -> true
            RepositoryVisibilityFilter.PUBLIC -> !repository.isPrivate
            RepositoryVisibilityFilter.PRIVATE -> repository.isPrivate
        }
        val matchesLanguage = language == null || repository.language?.equals(language, ignoreCase = true) == true
        val matchesFavorite = !favoritesOnly || repository.id in favoriteIds
        matchesQuery && matchesVisibility && matchesLanguage && matchesFavorite
    }
}

internal enum class RepositorySort(val label: String) {
    NAME("Nome"),
    MOST_STALE("Mais parados"),
    RECENT("Push recente"),
    STARS("Estrelas")
}

internal fun filterByHealth(
    repositories: List<GitHubRepository>,
    health: RepositoryHealthStatus?
): List<GitHubRepository> =
    if (health == null) repositories else repositories.filter { it.health.status == health }

/** NAME preserva a ordem da API; sem push registrado vai sempre para o fim. */
internal fun sortGitHubRepositories(
    repositories: List<GitHubRepository>,
    sort: RepositorySort
): List<GitHubRepository> = when (sort) {
    RepositorySort.NAME -> repositories
    RepositorySort.MOST_STALE -> repositories.sortedWith(compareBy(nullsLast()) { it.pushedAt })
    RepositorySort.RECENT -> repositories.sortedWith(compareByDescending(nullsFirst()) { it.pushedAt })
    RepositorySort.STARS -> repositories.sortedByDescending(GitHubRepository::stars)
}
