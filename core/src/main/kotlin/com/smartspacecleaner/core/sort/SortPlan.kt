package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.model.FileEntry

data class SortItem(val path: String, val size: Long, val decision: SortDecision)

/** [category] = null — раздел «Проверьте». */
data class SortSection(val category: SortCategory?, val items: List<SortItem>) {
    val bytes: Long get() = items.sumOf { it.size }
}

data class SortMove(val path: String, val size: Long, val category: SortCategory)

data class SortPlan(val items: List<SortItem>) {
    val videos: Int get() = items.count { it.decision.category == SortCategory.VIDEO }

    /** «Проверьте» первым, дальше категории в порядке [SortCategory]; пустые разделы не показываем. */
    fun sections(): List<SortSection> {
        val byCategory = items.groupBy { it.decision.category }
        return (listOf<SortCategory?>(null) + SortCategory.entries).mapNotNull { c -> byCategory[c]?.let { SortSection(c, it) } }
    }

    /** Заранее отмечено всё, чему выбрана категория; «Проверьте» — нет. */
    fun defaultSelection(): Set<String> = items.filter { it.decision.category != null }.map { it.path }.toSet()

    fun reassign(path: String, category: SortCategory): SortPlan = copy(
        items = items.map { if (it.path == path) it.copy(decision = SortDecision(category, "вы выбрали «${category.title}»")) else it },
    )

    fun moves(selected: Set<String>): List<SortMove> = items.mapNotNull { item ->
        item.decision.category?.takeIf { item.path in selected }?.let { SortMove(item.path, item.size, it) }
    }

    fun reviewOnly(): SortPlan = SortPlan(items.filter { it.decision.category == null })

    fun without(paths: Set<String>): SortPlan = SortPlan(items.filter { it.path !in paths })
}

class SortAnalysis(private val classifier: PhotoClassifier) {
    /** null — анализ остановили. Ничего ещё не перенесено, поэтому остановка безопасна. */
    fun run(files: List<FileEntry>, onProgress: (Int) -> Unit = {}, shouldStop: () -> Boolean = { false }): SortPlan? {
        val items = ArrayList<SortItem>(files.size)
        files.forEachIndexed { i, file ->
            if (shouldStop()) return null
            items += SortItem(file.path, file.size, classifier.classify(file.path))
            onProgress(i + 1)
        }
        return SortPlan(items)
    }
}
