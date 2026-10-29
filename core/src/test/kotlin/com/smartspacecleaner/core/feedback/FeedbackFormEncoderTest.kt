package com.smartspacecleaner.core.feedback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.URLDecoder

class FeedbackFormEncoderTest {
    private val fields = FormFields("entry.1", "entry.2", "entry.3", "entry.4", "entry.5", "entry.6", "entry.7")
    private val device = DeviceInfo("1.2 (3)", "Xiaomi Redmi Note 9 Pro", "12 (API 31)")

    private fun encode(text: String, name: String = "") =
        FeedbackFormEncoder.encode(Feedback(4, text, name), FeedbackSource.AFTER_CLEANUP, device, fields)

    private fun decode(body: String): Map<String, String> = body.split("&").associate {
        val (k, v) = it.split("=", limit = 2)
        URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
    }

    @Test
    fun `every field goes to its entry`() {
        assertEquals(
            mapOf(
                "entry.1" to "4", "entry.2" to "Всё понятно", "entry.3" to "Аня", "entry.4" to "1.2 (3)",
                "entry.5" to "Xiaomi Redmi Note 9 Pro", "entry.6" to "12 (API 31)", "entry.7" to "after_cleanup",
            ),
            decode(encode("Всё понятно", "Аня")),
        )
    }

    @Test
    fun `special characters and line breaks survive the round trip`() {
        val tricky = "a&b=c+d %20\nвторая строка 😀"
        assertEquals(tricky, decode(encode(tricky))["entry.2"])
        assertTrue(encode(tricky).contains("entry.2=a%26b%3Dc%2Bd+%2520%0A"))
    }

    @Test
    fun `empty name is still sent as an empty field`() {
        assertTrue(encode("ок").contains("entry.3=&"))
    }

    @Test
    fun `2000 cyrillic characters fit into WorkManager input data as raw text`() {
        // Data хранит строки через writeUTF: 2 байта на кириллический символ. Лимит Data — 10 240 байт.
        val bytes = ByteArrayOutputStream().also { DataOutputStream(it).writeUTF("я".repeat(2000)) }.size()
        assertTrue(bytes < 10_240 - 1024, "сырые поля: $bytes байт")
        // А закодированное тело в лимит не помещается — поэтому кодируем в воркере, а не заранее.
        assertTrue(encode("я".repeat(2000)).length > 10_240)
    }
}
