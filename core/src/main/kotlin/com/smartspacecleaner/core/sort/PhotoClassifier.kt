package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.scan.StorageLayout
import com.smartspacecleaner.core.text.plural
import kotlin.math.roundToInt

/** Решает, в какую папку положить файл. Правила идут сверху вниз, побеждает первое сработавшее. */
class PhotoClassifier(private val layout: StorageLayout, private val analyzer: PhotoAnalyzer) {
    fun classify(path: String): SortDecision {
        if (SortMedia.isVideo(path)) return SortDecision(SortCategory.VIDEO, "видео, без анализа")
        if (isScreenshot(path)) return SortDecision(SortCategory.SCREENSHOTS, "имя или папка скриншота")
        val signals = when (val analysis = analyzer.analyze(path)) {
            is Analysis.Ok -> analysis.signals
            Analysis.Unreadable -> return SortDecision(null, "не удалось прочитать фото")
        }
        return decide(path, signals)
    }

    private fun isScreenshot(path: String): Boolean =
        path.substringAfterLast('/').startsWith("screenshot", ignoreCase = true) ||
            layout.relative(path).split('/').dropLast(1).any { it.equals("Screenshots", ignoreCase = true) }

    private fun decide(path: String, s: PhotoSignals): SortDecision {
        if (s.textAreaRatio >= TEXT_AREA && s.textChars >= TEXT_CHARS) {
            return SortDecision(SortCategory.DOCUMENTS, "найден текст: ${s.textChars} ${plural(s.textChars, "символ", "символа", "символов")}")
        }
        if (s.largestFaceRatio >= FACE) return SortDecision(SortCategory.PEOPLE, "найдено лицо")

        val ranked = LabelTable.best(s.labels).filter { it.second.confidence >= WEAK }
        val top = ranked.getOrNull(0)
        if (top != null) {
            val (category, label) = top
            val rival = ranked.getOrNull(1)?.takeIf { label.confidence - it.second.confidence < GAP }
            return when {
                rival != null -> SortDecision(null, "«${category.title}» или «${rival.first.title}» — не уверен")
                label.confidence >= SURE -> SortDecision(category, "метка ${label.name}, ${percent(label.confidence)}")
                else -> SortDecision(null, "похоже на «${category.title}», ${percent(label.confidence)}")
            }
        }
        if (s.textChars in WEAK_TEXT_CHARS until TEXT_CHARS) {
            return SortDecision(null, "похоже на документ: ${s.textChars} ${plural(s.textChars, "символ", "символа", "символов")} текста")
        }
        layout.messengerOf(path)?.let { return SortDecision(SortCategory.MESSENGERS, "из ${it.title}") }
        return SortDecision(SortCategory.OTHER, "ничего не распознано")
    }

    private fun percent(confidence: Float) = "${(confidence * 100).roundToInt()} %"

    companion object {
        // Стартовые пороги из спецификации; подстраиваются по реальным фото после первой установки.
        const val TEXT_AREA = 0.25f
        const val TEXT_CHARS = 80
        const val WEAK_TEXT_CHARS = 30
        const val FACE = 0.03f
        const val SURE = 0.75f
        const val WEAK = 0.5f
        const val GAP = 0.10f
    }
}
