package com.smartspacecleaner.core.report

import com.smartspacecleaner.core.text.displayPath
import com.smartspacecleaner.core.text.formatBytes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** То, что удалось прочитать о файле: дата съёмки из EXIF/видео, размеры кадра, длительность. */
data class FileFacts(
    val originalPath: String,
    val size: Long,
    val takenAt: Instant?,
    val modifiedAt: Instant,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val inTrashUntil: Instant?,
)

/** Окно «Сведения» в том же виде, что у Галереи MIUI. */
object FileDetails {
    private val RU = Locale.forLanguageTag("ru")
    private val DATE_TIME = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE HH:mm", RU)
    private val DAY = DateTimeFormatter.ofPattern("d MMMM", RU)

    fun lines(f: FileFacts, zone: ZoneId): List<Pair<String, String>> {
        val date = if (f.takenAt != null) "Дата съёмки" to f.takenAt else "Дата изменения" to f.modifiedAt
        val file = buildList {
            add(formatBytes(f.size))
            if (f.width != null && f.height != null) add("${f.width}×${f.height}px")
            f.durationMs?.let { add(duration(it)) }
        }.joinToString(" · ")
        val location = "Память устройства/" + displayPath(f.originalPath) +
            (f.inTrashUntil?.let { "\nсейчас в корзине до " + DAY.format(it.atZone(zone)) } ?: "")
        return listOf(
            date.first to DATE_TIME.format(date.second.atZone(zone)),
            "Файл" to f.originalPath.substringAfterLast('/') + "\n" + file,
            "Расположение" to location,
        )
    }

    private fun duration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
