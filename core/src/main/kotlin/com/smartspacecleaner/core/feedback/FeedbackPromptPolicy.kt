package com.smartspacecleaner.core.feedback

import com.smartspacecleaner.core.report.Report

/** Окно отзыва появляется само только после очистки, пока отзыв не отправлен и человек не отказался. */
object FeedbackPromptPolicy {
    fun cleanedSomething(report: Report): Boolean = report.done.isNotEmpty()

    fun shouldPrompt(cleanedSomething: Boolean, alreadySent: Boolean, optedOut: Boolean): Boolean =
        cleanedSomething && !alreadySent && !optedOut
}
