package com.smartspacecleaner.core.text

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

// Десятичные единицы, как в «Настройки → Хранилище» Android/MIUI: иначе цифры расходятся с системными.
private const val KB = 1000.0
private const val MB = KB * 1000
private const val GB = MB * 1000

fun formatBytes(bytes: Long): String = when {
    bytes >= GB -> String.format(Locale.ROOT, "%.1f", bytes / GB).replace('.', ',') + " ГБ"
    bytes >= MB -> "${(bytes / MB).roundToLong()} МБ"
    bytes >= KB -> "${(bytes / KB).roundToLong()} КБ"
    else -> "$bytes Б"
}

private val DATE_WITH_YEAR = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))

/** «9 июля 2021» — с годом, иначе у старых файлов дата обманчиво выглядит свежей. */
fun formatDate(instant: Instant, zone: ZoneId): String = DATE_WITH_YEAR.format(instant.atZone(zone))

private const val SHARED_STORAGE = "/storage/emulated/0/"

/** Путь без системного префикса — так, как его показывает «Проводник». */
fun displayPath(path: String): String = path.removePrefix(SHARED_STORAGE)

fun plural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
