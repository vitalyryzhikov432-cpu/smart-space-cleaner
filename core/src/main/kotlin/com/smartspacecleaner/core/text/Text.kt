package com.smartspacecleaner.core.text

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
