package com.playertwo.controlegithub

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class GitHubRepositoryDetailMappingTest {
    @Test fun mapsDetailCountsVisibilityBranchAndLastPush() {
        val detail = parseGitHubRepositoryDetail(
            """{
                "id": 9,
                "name": "Mobile",
                "full_name": "acme/Mobile",
                "owner": {"login": "acme"},
                "private": true,
                "visibility": "internal",
                "default_branch": "main",
                "description": "A real repo",
                "stargazers_count": 0,
                "forks_count": 2,
                "open_issues_count": 4,
                "pushed_at": "2026-09-30T10:20:30Z"
            }"""
        )

        assertEquals(9L, detail.id)
        assertEquals("acme/Mobile", detail.fullName)
        assertEquals("A real repo", detail.description)
        assertEquals("internal", detail.visibility)
        assertEquals("main", detail.defaultBranch)
        assertEquals(0L, detail.stars)
        assertEquals(2L, detail.forks)
        assertEquals(4L, detail.openIssuesAndPullRequests)
        assertEquals("2026-09-30T10:20:30Z", detail.pushedAt.toString())
    }

    @Test fun absentCountsAndInvalidTimestampStayUnavailable() {
        val detail = parseGitHubRepositoryDetail(
            """{"id":9,"name":"Mobile","full_name":"acme/Mobile","owner":{"login":"acme"},"private":false,"forks_count":null,"pushed_at":"not-a-date"}"""
        )

        assertEquals("public", detail.visibility)
        assertNull(detail.stars)
        assertNull(detail.forks)
        assertNull(detail.openIssuesAndPullRequests)
        assertNull(detail.pushedAt)
    }

    @Test fun rejectsMismatchedIdentityAndInvalidCounts() {
        val wrongIdentity = """{"id":9,"name":"Mobile","full_name":"other/Mobile","owner":{"login":"acme"},"private":false}"""
        val negativeCount = """{"id":9,"name":"Mobile","full_name":"acme/Mobile","owner":{"login":"acme"},"private":false,"forks_count":-1}"""

        assertThrows(Exception::class.java) { parseGitHubRepositoryDetail(wrongIdentity) }
        assertThrows(Exception::class.java) { parseGitHubRepositoryDetail(negativeCount) }
    }

    @Test fun decodesReadmeBase64AsPlainUtf8AndRejectsOtherEncodings() {
        val source = "# Intro\n<script>literal</script>\n[link](https://example.test)"
        val encoded = Base64.getEncoder().encodeToString(source.toByteArray(Charsets.UTF_8))

        assertEquals(source, parseGitHubReadme("""{"encoding":"base64","content":"$encoded"}"""))
        assertThrows(Exception::class.java) { parseGitHubReadme("""{"encoding":"utf-8","content":"$encoded"}""") }
    }
}
