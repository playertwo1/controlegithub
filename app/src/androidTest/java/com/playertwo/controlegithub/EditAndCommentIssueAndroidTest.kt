package com.playertwo.controlegithub

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class EditAndCommentIssueAndroidTest {

    @Test
    fun parseCreatedIssueCommentMapsFieldsCorrectly() {
        val json = """
            {
                "id": 1001,
                "user": {"login": "playertwo1"},
                "body": "Este é um comentário de teste",
                "created_at": "2026-10-17T19:30:00Z"
            }
        """.trimIndent()

        val comment = parseCreatedIssueComment(json)
        assertEquals(1001L, comment.id)
        assertEquals("playertwo1", comment.author)
        assertEquals("Este é um comentário de teste", comment.body)
        assertNotNull(comment.createdAt)
    }

    @Test
    fun parseCreatedIssueCommentRejectsMissingId() {
        assertThrows(IOException::class.java) {
            parseCreatedIssueComment("""{"body": "Sem ID"}""")
        }
    }
}
