package com.smartspacecleaner.core.feedback

import java.net.URLEncoder

/** Где человек оставил отзыв; значение уходит в таблицу как есть. */
enum class FeedbackSource(val code: String) { AFTER_CLEANUP("after_cleanup"), MENU("menu") }

data class DeviceInfo(val appVersion: String, val phone: String, val android: String)

/** Имена полей Google Формы, вида `entry.123456`. */
data class FormFields(
    val rating: String,
    val text: String,
    val name: String,
    val appVersion: String,
    val phone: String,
    val android: String,
    val source: String,
)

/** Тело POST-запроса `application/x-www-form-urlencoded` для `…/formResponse`. */
object FeedbackFormEncoder {
    fun encode(feedback: Feedback, source: FeedbackSource, device: DeviceInfo, fields: FormFields): String =
        listOf(
            fields.rating to feedback.rating.toString(),
            fields.text to feedback.text,
            fields.name to feedback.name,
            fields.appVersion to device.appVersion,
            fields.phone to device.phone,
            fields.android to device.android,
            fields.source to source.code,
        ).joinToString("&") { (key, value) -> "${enc(key)}=${enc(value)}" }

    // Вариант с Charset появился только в Android API 33, а minSdk у нас 31.
    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
