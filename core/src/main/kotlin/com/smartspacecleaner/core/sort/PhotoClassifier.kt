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
        // Человек на фото важнее пейзажа вокруг него, поэтому метка человека побеждает и без запаса уверенности.
        ranked.firstOrNull { it.first == SortCategory.PEOPLE }?.let { (category, label) ->
            return SortDecision(category, "метка ${label.name}, ${percent(label.confidence)}")
        }
        val top = ranked.getOrNull(0)
        if (top != null) {
            val (category, label) = top
            val rival = ranked.getOrNull(1)?.takeIf { label.confidence - it.second.confidence < GAP }
            // Животное почти всегда снято на природе, поэтому уверенное животное побеждает природу.
            rival?.let { animalOverNature(top, it) }?.let { return it }
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

    private fun animalOverNature(a: Pair<SortCategory, Label>, b: Pair<SortCategory, Label>): SortDecision? {
        val animal = listOf(a, b).singleOrNull { it.first == SortCategory.ANIMALS } ?: return null
        if (listOf(a, b).none { it.first == SortCategory.NATURE } || animal.second.confidence < SURE) return null
        return SortDecision(animal.first, "метка ${animal.second.name}, ${percent(animal.second.confidence)}")
    }

    /**
     * Отбрасываем дробную часть: 74,96 % не должно выглядеть как «75 %» ниже порога.
     * Сначала округляем до сотых процента: 0,59f хранится как 0,58999… и иначе показалось бы «58 %».
     */
    private fun percent(confidence: Float) = "${(confidence * 10_000).roundToInt() / 100} %"

    companion object {
        // Пороги подобраны по фото с телефона автора на приёмке: лист, снятый целиком, даёт 2–20 % текста по площади,
        // лицо человека в полный рост — около 0,1 % кадра.
        const val TEXT_AREA = 0.02f
        const val TEXT_CHARS = 80
        const val WEAK_TEXT_CHARS = 30
        const val FACE = 0.001f
        const val SURE = 0.75f
        const val WEAK = 0.5f
        const val GAP = 0.10f
    }
}
