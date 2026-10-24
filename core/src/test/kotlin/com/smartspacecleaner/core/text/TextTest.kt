package com.smartspacecleaner.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextTest {
    @Test
    fun `formats bytes in russian units`() {
        assertEquals("0 Б", formatBytes(0))
        assertEquals("512 Б", formatBytes(512))
        assertEquals("2 КБ", formatBytes(1536))
        assertEquals("5 МБ", formatBytes(5L * 1024 * 1024))
        assertEquals("100 МБ", formatBytes(100L * 1024 * 1024))
        assertEquals("1,8 ГБ", formatBytes(1_932_735_283))
        assertEquals("16,5 ГБ", formatBytes(17_716_740_096))
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
}
