package com.smartspacecleaner.core.feedback

import com.smartspacecleaner.core.report.FailedLine
import com.smartspacecleaner.core.report.Report
import com.smartspacecleaner.core.report.ReportLine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeedbackPromptPolicyTest {
    private fun report(done: List<ReportLine>, failed: List<FailedLine> = emptyList()) = Report(
        freeBefore = 0, freeAfter = 0, done = done, failed = failed, notSelected = emptyList(),
        inTrashBytes = 0, trashFreesOn = null, cacheNote = "Кэш очищен системой",
    )

    @Test
    fun `something was cleaned only when the report has done lines`() {
        assertTrue(FeedbackPromptPolicy.cleanedSomething(report(listOf(ReportLine("Дубли", 2, 100)))))
        assertFalse(FeedbackPromptPolicy.cleanedSomething(report(emptyList())))
        assertFalse(FeedbackPromptPolicy.cleanedSomething(report(emptyList(), listOf(FailedLine("/a", "нет доступа", "удалите вручную")))))
    }

    @Test
    fun `asks only after a cleanup, until sent or opted out`() {
        val cases = mapOf(
            Triple(true, false, false) to true,
            Triple(false, false, false) to false,
            Triple(true, true, false) to false,
            Triple(true, false, true) to false,
            Triple(true, true, true) to false,
        )
        cases.forEach { (input, expected) ->
            assertEquals(expected, FeedbackPromptPolicy.shouldPrompt(input.first, input.second, input.third), "$input")
        }
    }
}
