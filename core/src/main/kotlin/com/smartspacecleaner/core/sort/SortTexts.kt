package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.scan.StorageLayout
import com.smartspacecleaner.core.text.displayPath
import com.smartspacecleaner.core.text.formatBytes
import com.smartspacecleaner.core.text.plural

/** Все тексты сортировки в одном месте. */
object SortTexts {
    const val NOTHING_SURE = "Уверенно распознать ничего не удалось — выберите категории сами"

    private fun files(n: Int) = "$n ${plural(n, "файл", "файла", "файлов")}"

    /** Длинные пути медиа мессенджеров сокращаем, чтобы строка помещалась. */
    private fun shortPath(path: String): String = when {
        path.startsWith("Android/media/com.whatsapp/WhatsApp/Media/") -> "WhatsApp/" + path.removePrefix("Android/media/com.whatsapp/WhatsApp/Media/")
        path.startsWith("Android/media/org.telegram.messenger/Telegram/") -> "Telegram/" + path.removePrefix("Android/media/org.telegram.messenger/Telegram/")
        else -> path
    }

    fun folderLine(f: SourceFolder): String {
        val counts = buildList {
            if (f.photos > 0) add("${f.photos} фото")
            if (f.videos > 0) add("${f.videos} видео")
        }
        return "${shortPath(displayPath(f.path))} — ${counts.joinToString(", ")} · ${formatBytes(f.bytes)}"
    }

    fun sectionTitle(s: SortSection): String {
        val category = s.category ?: return "Проверьте — ${s.items.size} фото"
        val noun = if (category == SortCategory.VIDEO) "видео" else "фото"
        return "${category.title} — ${s.items.size} $noun · ${formatBytes(s.bytes)} → ${category.relativeFolder}"
    }

    fun confirmMove(count: Int): String = "Перенести ${files(count)} в ${StorageLayout.SORT_DIR}? Вернуть можно в Истории."

    fun quickConfirm(plan: SortPlan): String {
        val ready = plan.defaultSelection().size
        val total = plan.items.size
        val left = total - ready
        return buildString {
            // После «из» — родительный падеж: из 1 файла, из 3 файлов, из 21 файла.
            append("Готово к раскладке $ready из $total ${plural(total, "файла", "файлов", "файлов")}")
            if (plan.videos > 0) append(" (в том числе ${plan.videos} видео)")
            append(". Разложить их?")
            if (left > 0) append(" $left ${plural(left, "сомнительное фото останется", "сомнительных фото останутся", "сомнительных фото останутся")} на месте.")
            append(" Вернуть всё можно в Истории.")
        }
    }

    fun result(moved: Int, folders: Int, left: Int, failed: Int): String = buildString {
        append("${plural(moved, "Разложен", "Разложено", "Разложено")} ${files(moved)} по $folders ${plural(folders, "папке", "папкам", "папкам")}")
        if (left > 0) append(", ${files(left)} ${plural(left, "остался", "осталось", "осталось")} на месте")
        if (failed > 0) append(". Не удалось перенести: $failed")
    }

    fun restored(r: SortRestore): String =
        if (r.skipped == 0) "Возвращено на место: ${files(r.restored)}" else "Возвращено ${r.restored}, пропущено ${r.skipped}"

    fun sessionTitle(s: SortSession): String = "Сортировка фото и видео — ${files(s.moved + s.restored)}"

    fun sessionStatus(s: SortSession): String = buildList {
        if (s.moved > 0) add("в папках сортировки: ${s.moved}")
        if (s.restored > 0) add("возвращено на место: ${s.restored}")
        if (s.failed > 0) add("не выполнено: ${s.failed}")
    }.joinToString(" · ")
}
