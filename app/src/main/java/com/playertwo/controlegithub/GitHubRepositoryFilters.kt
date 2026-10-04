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
    language: String?
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
        matchesQuery && matchesVisibility && matchesLanguage
    }
}
