package com.smartspacecleaner.core.report

import com.smartspacecleaner.core.model.PlannedItem

enum class CompareRole(val title: String) {
    REMOVED_COPY("Удалится (копия)"),
    KEPT_ORIGINAL("Останется (оригинал)"),
}

data class CompareSide(val role: CompareRole, val path: String)

/** Окно «Сравнение» в плане: что удаляется и что остаётся. */
object DuplicateComparison {
    /** null — у файла нет оригинала, сравнивать не с чем. */
    fun of(item: PlannedItem): List<CompareSide>? {
        val kept = item.keptOriginalPath ?: return null
        return listOf(CompareSide(CompareRole.REMOVED_COPY, item.path), CompareSide(CompareRole.KEPT_ORIGINAL, kept))
    }
}
