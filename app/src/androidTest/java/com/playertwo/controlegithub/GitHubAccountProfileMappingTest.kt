package com.playertwo.controlegithub

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubAccountProfileMappingTest {
    @Test fun parsesAccountIdentityAndZeroCounts() {
        val profile = GitHubAccountProfile.parse(
            """{"login":"OctoCat","html_url":"https://github.com/OctoCat","name":" Octo Cat ","public_repos":0,"owned_private_repos":2,"followers":0}""",
            "octocat"
        )
        assertEquals("Octo Cat", profile.displayName)
        assertEquals(0L, profile.publicRepositories)
        assertEquals(2L, profile.ownedPrivateRepositories)
        assertEquals(0L, profile.followers)
    }

    @Test fun absentAndNullCountsStayUnavailableAndBlankNameFallsBackToLogin() {
        val profile = GitHubAccountProfile.parse(
            """{"login":"octocat","html_url":"https://github.com/octocat","name":" ","public_repos":null}""",
            "octocat"
        )
        assertNull(profile.displayName)
        assertNull(profile.publicRepositories)
        assertNull(profile.ownedPrivateRepositories)
        assertNull(profile.followers)
    }

    @Test fun rejectsAnotherIdentityAndMalformedCounts() {
        listOf(
            """{"login":"other","html_url":"https://github.com/other"}""",
            """{"login":"octocat","html_url":"https://github.com/octocat","public_repos":-1}""",
            """{"login":"octocat","html_url":"https://github.com/octocat","followers":"2"}""",
            """{"login":"octocat","html_url":"https://github.com/octocat","owned_private_repos":2.5}"""
        ).forEach { json ->
            try {
                GitHubAccountProfile.parse(json, "octocat")
                throw AssertionError("Expected invalid profile to be rejected")
            } catch (_: IOException) {
                // Malformed or mismatched profile data is never displayed as real.
            }
        }
    }
}
