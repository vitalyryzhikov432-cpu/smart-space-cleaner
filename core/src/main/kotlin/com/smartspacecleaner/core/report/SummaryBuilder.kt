package com.smartspacecleaner.core.report

import com.smartspacecleaner.core.model.Plan
import com.smartspacecleaner.core.text.formatBytes

enum class Severity(val emoji: String, val label: String) {
    CRITICAL("🔴", "критично"),
    ATTENTION("🟡", "стоит заняться"),
    OK("🟢", "в порядке"),
}

data class Summary(val severity: Severity, val freeBytes: Long, val totalBytes: Long, val lines: List<String>)

object SummaryBuilder {
    fun build(freeBytes: Long, totalBytes: Long, plan: Plan): Summary {
        val freePercent = if (totalBytes == 0L) 0.0 else freeBytes * 100.0 / totalBytes
        val severity = when {
            freePercent < 5 -> Severity.CRITICAL
            freePercent <= 15 -> Severity.ATTENTION
            else -> Severity.OK
        }
        val lines = mutableListOf("Свободно ${formatBytes(freeBytes)} из ${formatBytes(totalBytes)}")
        val biggest = plan.groups.filter { it.items.isNotEmpty() }.sortedByDescending { it.totalBytes }.take(3)
        biggest.forEach { lines += "${it.title} — ${formatBytes(it.totalBytes)}" }
        if (biggest.isEmpty()) lines += "Ничего заметного для уборки не нашлось — можно только очистить кэш приложений"
        return Summary(severity, freeBytes, totalBytes, lines)
    }
}
