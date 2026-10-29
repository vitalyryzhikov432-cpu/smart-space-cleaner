package com.smartspacecleaner.core.feedback

/** То, что человек ввёл в окне отзыва, как есть. */
data class FeedbackDraft(val rating: Int, val text: String, val name: String)

/** Проверенный отзыв: пробелы по краям убраны, имя не длиннее [FeedbackValidator.MAX_NAME]. */
data class Feedback(val rating: Int, val text: String, val name: String)

sealed interface FeedbackCheck {
    data class Ok(val feedback: Feedback) : FeedbackCheck
    data class Invalid(val reason: String) : FeedbackCheck
}

object FeedbackValidator {
    const val MAX_TEXT = 2000
    const val MAX_NAME = 50

    fun check(draft: FeedbackDraft): FeedbackCheck {
        val text = draft.text.trim()
        val name = draft.name.trim().take(MAX_NAME)
        return when {
            draft.rating !in 1..5 -> FeedbackCheck.Invalid("Поставьте оценку")
            text.length > MAX_TEXT -> FeedbackCheck.Invalid("Текст длиннее $MAX_TEXT символов")
            draft.rating <= 3 && text.isEmpty() -> FeedbackCheck.Invalid("Напишите, что мешало — так будет понятно, что исправить")
            else -> FeedbackCheck.Ok(Feedback(draft.rating, text, name))
        }
    }
}
