package com.smartspacecleaner.feedback

import com.smartspacecleaner.core.feedback.FormFields

/**
 * Куда отправлять отзывы. Значения берутся из «предварительно заполненной ссылки» Google Формы:
 * …/forms/d/e/<FORM_ID>/viewform?entry.N=… Пока FORM_ID пустой, окно отзыва и пункт меню скрыты.
 */
object FeedbackFormConfig {
    const val FORM_ID = "1FAIpQLSdK12FRgt3EN5dydjpa1XJxMElT1xPvh-SGIWQb1BODu1068w"

    val fields = FormFields(
        rating = "entry.655179792",
        text = "entry.840603498",
        name = "entry.1853667793",
        appVersion = "entry.1513899478",
        phone = "entry.1989510114",
        android = "entry.94136708",
        source = "entry.1311587837",
    )

    val isConfigured: Boolean get() = FORM_ID.isNotEmpty()
    val submitUrl: String get() = "https://docs.google.com/forms/d/e/$FORM_ID/formResponse"
}
