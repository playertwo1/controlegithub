package com.playertwo.controlegithub

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateIssueAndroidTest {

    @Test
    fun parseCreatedIssueMapsFieldsCorrectly() {
        val json = """
            {
                "id": 101,
                "number": 42,
                "title": "Nova falha encontrada",
                "state": "open",
                "html_url": "https://github.com/playertwo1/controlegithub/issues/42",
                "user": {"login": "playertwo1"},
                "assignees": [{"login": "playertwo1"}],
                "labels": [{"name": "bug"}],
                "body": "Descrição detalhada",
                "created_at": "2026-10-17T19:00:00Z",
                "updated_at": "2026-10-17T19:00:00Z"
            }
        """.trimIndent()

        val issue = parseCreatedIssue(json, fallbackRepository = "playertwo1/controlegithub")
        assertEquals(42, issue.number)
        assertEquals("Nova falha encontrada", issue.title)
        assertEquals("playertwo1/controlegithub", issue.repository)
        assertEquals("playertwo1", issue.author)
        assertEquals(GitHubIssueState.OPEN, issue.state)
        assertEquals(listOf("playertwo1"), issue.assignees)
        assertEquals(listOf("bug"), issue.labels)
        assertNotNull(issue.updatedAt)
    }

    @Test
    fun parseCreatedIssueHandlesMissingRepositoryByFallback() {
        val json = """
            {
                "number": 15,
                "title": "Bug sem objeto repository",
                "state": "open",
                "html_url": "https://github.com/octocat/Hello-World/issues/15",
                "user": {"login": "octocat"}
            }
        """.trimIndent()

        val issue = parseCreatedIssue(json, fallbackRepository = "octocat/Hello-World")
        assertEquals(15, issue.number)
        assertEquals("Bug sem objeto repository", issue.title)
        assertEquals("octocat/Hello-World", issue.repository)
        assertEquals("octocat", issue.author)
        assertEquals(GitHubIssueState.OPEN, issue.state)
    }

    @Test
    fun parseCreatedIssueRejectsMissingTitleOrNumber() {
        assertThrows(IOException::class.java) {
            parseCreatedIssue("""{"title": "Sem numero"}""", fallbackRepository = "repo/test")
        }
        assertThrows(IOException::class.java) {
            parseCreatedIssue("""{"number": 1}""", fallbackRepository = "repo/test")
        }
    }
}
