package com.smartspacecleaner.core.feedback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeedbackValidatorTest {
    private fun check(rating: Int, text: String = "", name: String = "") =
        FeedbackValidator.check(FeedbackDraft(rating, text, name))

    @Test
    fun `rating outside 1-5 is rejected`() {
        listOf(0, 6, -1).forEach { assertEquals(FeedbackCheck.Invalid("Поставьте оценку"), check(it, "текст")) }
    }

    @Test
    fun `4 and 5 stars may come without text`() {
        assertEquals(FeedbackCheck.Ok(Feedback(4, "", "")), check(4))
        assertEquals(FeedbackCheck.Ok(Feedback(5, "", "")), check(5))
    }

    @Test
    fun `3 stars and below need text, and spaces do not count as text`() {
        listOf(1, 3).forEach { assertTrue(check(it) is FeedbackCheck.Invalid) }
        assertTrue(check(2, "   \n ") is FeedbackCheck.Invalid)
        assertEquals(FeedbackCheck.Ok(Feedback(3, "мешает реклама", "")), check(3, "  мешает реклама "))
    }

    @Test
    fun `text is limited to 2000 characters`() {
        assertTrue(check(5, "а".repeat(2000)) is FeedbackCheck.Ok)
        assertEquals(FeedbackCheck.Invalid("Текст длиннее 2000 символов"), check(5, "а".repeat(2001)))
    }

    @Test
    fun `name is trimmed and cut to 50 characters, blank name becomes empty`() {
        assertEquals("Аня", (check(5, name = "  Аня ") as FeedbackCheck.Ok).feedback.name)
        assertEquals("", (check(5, name = "   ") as FeedbackCheck.Ok).feedback.name)
        assertEquals(50, (check(5, name = "я".repeat(80)) as FeedbackCheck.Ok).feedback.name.length)
    }
}
