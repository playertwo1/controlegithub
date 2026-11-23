package com.playertwo.controlegithub

import java.time.Instant

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
    HEALTH("Saúde"),
    MOST_STALE("Mais parados"),
    RECENT("Push recente"),
    STARS("Estrelas")
}

internal fun filterByHealth(
    repositories: List<GitHubRepository>,
    health: RepositoryHealthStatus?,
    now: Instant = Instant.now()
): List<GitHubRepository> =
    if (health == null) repositories else repositories.filter {
        repositoryHealth(it.pushedAt, it.archived, now, it.sizeKb).status == health
    }

/** NAME preserva a ordem da API; HEALTH ordena mais doentes primeiro e mais dias sem push dentro do mesmo estado. */
internal fun sortGitHubRepositories(
    repositories: List<GitHubRepository>,
    sort: RepositorySort,
    now: Instant = Instant.now()
): List<GitHubRepository> = when (sort) {
    RepositorySort.NAME -> repositories
    RepositorySort.HEALTH -> repositories
        .map { it to repositoryHealth(it.pushedAt, it.archived, now, it.sizeKb) }
        .sortedWith(
            compareBy<Pair<GitHubRepository, RepositoryHealth>> { it.second.status.severityRank }
                .thenBy(nullsLast(reverseOrder())) { it.second.daysWithoutPush }
                .thenBy(nullsLast(naturalOrder())) { it.first.pushedAt }
        )
        .map { it.first }
    RepositorySort.MOST_STALE -> repositories.sortedWith(compareBy(nullsLast()) { it.pushedAt })
    RepositorySort.RECENT -> repositories.sortedWith(compareByDescending(nullsFirst()) { it.pushedAt })
    RepositorySort.STARS -> repositories.sortedByDescending(GitHubRepository::stars)
}
