package com.smartspacecleaner.core.report

import com.smartspacecleaner.core.fakes.p
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FileDetailsTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant()

    @Test
    fun `photo in trash shows date taken, size with resolution and where it came from`() {
        val lines = FileDetails.lines(
            FileFacts(
                originalPath = p("DCIM/Screenshots/Screenshot_1.jpg"),
                size = 753_000, takenAt = at(2026, 10, 25, 12, 29), modifiedAt = at(2026, 10, 26, 8, 0),
                width = 1080, height = 2400, durationMs = null, inTrashUntil = at(2026, 11, 1, 12, 0),
            ),
            zone,
        )

        assertEquals(
            listOf(
                "Дата съёмки" to "25 октября 2026, воскресенье 12:29",
                "Файл" to "Screenshot_1.jpg\n753 КБ · 1080×2400px",
                "Расположение" to "Память устройства/DCIM/Screenshots/Screenshot_1.jpg\nсейчас в корзине до 1 ноября",
            ),
            lines,
        )
    }

    @Test
    fun `without a date taken the date changed is shown, video gets its length`() {
        val lines = FileDetails.lines(
            FileFacts(
                originalPath = p("Download/v.mp4"), size = 5_000_000, takenAt = null, modifiedAt = at(2026, 10, 20, 9, 5),
                width = 1280, height = 720, durationMs = 83_000, inTrashUntil = null,
            ),
            zone,
        )

        assertEquals("Дата изменения" to "20 октября 2026, вторник 09:05", lines[0])
        assertEquals("Файл" to "v.mp4\n5 МБ · 1280×720px · 1:23", lines[1])
        assertEquals("Расположение" to "Память устройства/Download/v.mp4", lines[2])
    }
}
