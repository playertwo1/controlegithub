package com.playertwo.controlegithub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubRepositoryFiltersTest {
    private val repositories = listOf(
        repository(1, "Mobile", "acme/Mobile", private = true, language = "Kotlin"),
        repository(2, "docs", "acme/docs", private = false, language = "Markdown"),
        repository(3, "tools", "acme/tools", private = false, language = null),
        repository(4, "kotlin-kit", "other/kotlin-kit", private = true, language = "kotlin")
    )

    @Test fun queryMatchesNameFullNameOrLanguageIgnoringCaseAndOuterWhitespace() {
        assertEquals(listOf(1L), filter(" MOBILE ").map(GitHubRepository::id))
        assertEquals(listOf(2L), filter("ACME/DOCS").map(GitHubRepository::id))
        assertEquals(listOf(1L, 4L), filter("kOtLiN").map(GitHubRepository::id))
    }

    @Test fun visibilityAndLanguageAreCombinedAndLanguageIsExact() {
        assertEquals(
            listOf(1L, 4L),
            filter(visibility = RepositoryVisibilityFilter.PRIVATE, language = "KOTLIN")
                .map(GitHubRepository::id)
        )
        assertEquals(listOf(2L, 3L), filter(visibility = RepositoryVisibilityFilter.PUBLIC).map(GitHubRepository::id))
        assertTrue(filter(language = "Kotlin extra").isEmpty())
    }

    @Test fun noCriteriaAndClearedCriteriaPreserveInputOrderAndNullLanguage() {
        assertEquals(repositories, filter())
        assertEquals(listOf(1L, 2L, 3L, 4L), filter(language = null).map(GitHubRepository::id))
    }

    @Test fun filteringNeverAddsItemsToAnEmptyLoadedPage() {
        assertTrue(filterGitHubRepositories(emptyList(), "", RepositoryVisibilityFilter.ALL, null).isEmpty())
    }

    private fun filter(
        query: String = "",
        visibility: RepositoryVisibilityFilter = RepositoryVisibilityFilter.ALL,
        language: String? = null
    ) = filterGitHubRepositories(repositories, query, visibility, language)

    private fun repository(
        id: Long,
        name: String,
        fullName: String,
        private: Boolean,
        language: String?
    ) = GitHubRepository(id, name, fullName, fullName.substringBefore('/'), private, null, language, 0)
}
