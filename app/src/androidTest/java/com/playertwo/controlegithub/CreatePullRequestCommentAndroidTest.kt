package com.playertwo.controlegithub

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CreatePullRequestCommentAndroidTest {

    @Test
    fun parseCreatedPullRequestCommentMapsFieldsCorrectly() {
        val json = """
            {
                "id": 2002,
                "user": {"login": "playertwo1"},
                "body": "Comentário no PR de teste",
                "created_at": "2026-10-17T20:15:00Z"
            }
        """.trimIndent()

        val comment = parseCreatedPullRequestComment(json)
        assertEquals(2002L, comment.id)
        assertEquals("playertwo1", comment.author)
        assertEquals("Comentário no PR de teste", comment.body)
        assertNotNull(comment.createdAt)
    }

    @Test
    fun parseCreatedPullRequestCommentRejectsMissingId() {
        assertThrows(IOException::class.java) {
            parseCreatedPullRequestComment("""{"body": "Sem ID"}""")
        }
    }
}
