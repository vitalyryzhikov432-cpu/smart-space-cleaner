package com.smartspacecleaner.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextTest {
    @Test
    fun `formats bytes in decimal russian units like android settings`() {
        assertEquals("0 Б", formatBytes(0))
        assertEquals("512 Б", formatBytes(512))
        assertEquals("2 КБ", formatBytes(1536))
        assertEquals("5 МБ", formatBytes(5_000_000))
        assertEquals("100 МБ", formatBytes(100_000_000))
        assertEquals("1,8 ГБ", formatBytes(1_800_000_000))
        assertEquals("128,0 ГБ", formatBytes(128_000_000_000))
    }

    @Test
    fun `picks russian plural form`() {
        assertEquals("файл", plural(1, "файл", "файла", "файлов"))
        assertEquals("файла", plural(3, "файл", "файла", "файлов"))
        assertEquals("файлов", plural(5, "файл", "файла", "файлов"))
        assertEquals("файлов", plural(11, "файл", "файла", "файлов"))
        assertEquals("файлов", plural(12, "файл", "файла", "файлов"))
        assertEquals("файл", plural(21, "файл", "файла", "файлов"))
        assertEquals("файла", plural(22, "файл", "файла", "файлов"))
        assertEquals("файлов", plural(0, "файл", "файла", "файлов"))
    }

    @Test
    fun `shows a path the way the phone's file manager does`() {
        assertEquals(
            "Pictures/Gallery/owner/кадет/IMG_1.jpg",
            displayPath("/storage/emulated/0/Pictures/Gallery/owner/кадет/IMG_1.jpg"),
        )
        assertEquals("/mnt/other/x.jpg", displayPath("/mnt/other/x.jpg"))
    }
}
