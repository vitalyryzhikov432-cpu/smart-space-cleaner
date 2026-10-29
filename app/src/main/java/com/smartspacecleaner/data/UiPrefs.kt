package com.smartspacecleaner.data

import android.content.Context
import com.smartspacecleaner.core.help.HintId

/** Мелкие флаги интерфейса: что уже показано и что человек выбрал в окне отзыва. */
class UiPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = prefs.getBoolean(ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(ONBOARDING_DONE, value).apply()

    var dismissedHints: Set<HintId>
        get() = prefs.getStringSet(DISMISSED_HINTS, emptySet()).orEmpty()
            .mapNotNull { name -> HintId.entries.firstOrNull { it.name == name } }
            .toSet()
        set(value) = prefs.edit().putStringSet(DISMISSED_HINTS, value.map { it.name }.toSet()).apply()

    var feedbackSent: Boolean
        get() = prefs.getBoolean(FEEDBACK_SENT, false)
        set(value) = prefs.edit().putBoolean(FEEDBACK_SENT, value).apply()

    var feedbackOptOut: Boolean
        get() = prefs.getBoolean(FEEDBACK_OPT_OUT, false)
        set(value) = prefs.edit().putBoolean(FEEDBACK_OPT_OUT, value).apply()

    var feedbackName: String
        get() = prefs.getString(FEEDBACK_NAME, "").orEmpty()
        set(value) = prefs.edit().putString(FEEDBACK_NAME, value).apply()

    private companion object {
        const val ONBOARDING_DONE = "onboarding_done"
        const val DISMISSED_HINTS = "dismissed_hints"
        const val FEEDBACK_SENT = "feedback_sent"
        const val FEEDBACK_OPT_OUT = "feedback_opt_out"
        const val FEEDBACK_NAME = "feedback_name"
    }
}
