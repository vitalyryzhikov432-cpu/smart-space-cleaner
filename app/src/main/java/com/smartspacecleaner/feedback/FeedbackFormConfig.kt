package com.smartspacecleaner.feedback

import com.smartspacecleaner.core.feedback.FormFields

/**
 * Куда отправлять отзывы. Значения берутся из «предварительно заполненной ссылки» Google Формы:
 * …/forms/d/e/<FORM_ID>/viewform?entry.N=… Пока FORM_ID пустой, окно отзыва и пункт меню скрыты.
 */
object FeedbackFormConfig {
    const val FORM_ID = ""

    val fields = FormFields(
        rating = "entry.0",
        text = "entry.0",
        name = "entry.0",
        appVersion = "entry.0",
        phone = "entry.0",
        android = "entry.0",
        source = "entry.0",
    )

    val isConfigured: Boolean get() = FORM_ID.isNotEmpty()
    val submitUrl: String get() = "https://docs.google.com/forms/d/e/$FORM_ID/formResponse"
}
