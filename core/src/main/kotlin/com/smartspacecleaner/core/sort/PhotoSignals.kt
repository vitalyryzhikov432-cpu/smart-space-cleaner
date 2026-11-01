package com.smartspacecleaner.core.sort

data class Label(val name: String, val confidence: Float)

/** Что анализатор нашёл на фото. Доли — от площади кадра, 0..1. */
data class PhotoSignals(
    val textAreaRatio: Float = 0f,
    val textChars: Int = 0,
    val largestFaceRatio: Float = 0f,
    val labels: List<Label> = emptyList(),
)

sealed interface Analysis {
    data class Ok(val signals: PhotoSignals) : Analysis
    data object Unreadable : Analysis
}

/** Смотрит фото на телефоне. Сейчас это ML Kit; позже сюда можно подключить облачного агента. */
interface PhotoAnalyzer {
    fun analyze(path: String): Analysis
}

/** [category] = null — раздел «Проверьте»: файл остаётся на месте, пока ему не выберут категорию. */
data class SortDecision(val category: SortCategory?, val reason: String)
