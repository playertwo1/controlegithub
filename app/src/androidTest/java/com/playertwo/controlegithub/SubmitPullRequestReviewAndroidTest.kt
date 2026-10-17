package com.playertwo.controlegithub

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SubmitPullRequestReviewAndroidTest {

    @Test
    fun parseCreatedPullRequestReviewMapsFieldsCorrectly() {
        val json = """
            {
                "id": 8877,
                "user": {"login": "playertwo1"},
                "state": "APPROVED",
                "body": "Aprovado com sucesso!",
                "submitted_at": "2026-10-17T20:40:00Z"
            }
        """.trimIndent()

        val review = parseCreatedPullRequestReview(json)
        assertEquals(8877L, review.id)
        assertEquals("playertwo1", review.author)
        assertEquals("APPROVED", review.state)
        assertEquals("Aprovado com sucesso!", review.body)
        assertNotNull(review.submittedAt)
    }

    @Test
    fun parseCreatedPullRequestReviewRejectsMissingId() {
        assertThrows(IOException::class.java) {
            parseCreatedPullRequestReview("""{"state": "APPROVED"}""")
        }
    }
}
