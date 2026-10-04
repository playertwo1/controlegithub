package com.playertwo.controlegithub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubRepositoryMappingTest {
    @Test fun mapsRepositoryAndAcceptsNullOptionalFields() {
        val repositories = parseGitHubRepositories(
            """[
                {"id":42,"name":"controlegithub","full_name":"playertwo1/controlegithub",
                "owner":{"login":"playertwo1"},"private":true,"description":null,
                "language":null,"stargazers_count":3,"ignored":"value"},
                {"id":43,"name":"public","full_name":"octocat/public",
                "owner":{"login":"octocat"},"private":false,"description":"Sample",
                "language":"Kotlin","stargazers_count":0}
            ]"""
        )

        assertEquals(2, repositories.size)
        assertEquals(42L, repositories[0].id)
        assertEquals("playertwo1/controlegithub", repositories[0].fullName)
        assertTrue(repositories[0].isPrivate)
        assertNull(repositories[0].description)
        assertNull(repositories[0].language)
        assertFalse(repositories[1].isPrivate)
        assertEquals("Sample", repositories[1].description)
        assertEquals("Kotlin", repositories[1].language)
    }

    @Test fun acceptsEmptyListAndRejectsMalformedRequiredFields() {
        assertTrue(parseGitHubRepositories("[]").isEmpty())

        listOf(
            """[{"name":"missing-id","full_name":"owner/repo","owner":{"login":"owner"},"private":false,"stargazers_count":0}]""",
            """[{"id":1,"name":"repo","full_name":"other/repo","owner":{"login":"owner"},"private":false,"stargazers_count":0}]""",
            """[{"id":1,"name":"repo","full_name":"owner/repo","owner":{"login":"owner"},"private":false,"stargazers_count":-1}]""",
            """[{"id":1,"name":"repo","full_name":"owner/repo","owner":{"login":"owner"},"private":"false","stargazers_count":0}]"""
        ).forEach { malformed ->
            assertThrows(Exception::class.java) { parseGitHubRepositories(malformed) }
        }
    }
}
