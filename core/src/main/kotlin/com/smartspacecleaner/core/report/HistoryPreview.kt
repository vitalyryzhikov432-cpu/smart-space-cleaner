package com.smartspacecleaner.core.report

import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.text.displayPath

/** Что показать в «Истории»: откуда брать картинку файла и из какой папки он был убран. */
object HistoryPreview {
    /** Путь к содержимому файла сейчас; null — смотреть уже нечего. */
    fun contentPath(r: ActionRecord): String? = when (r.status) {
        // В корзине файл лежит под служебным именем без расширения.
        ActionStatus.DONE -> if (r.kind == ActionKind.TRASH) r.trashPath else r.keptOriginalPath
        ActionStatus.RESTORED, ActionStatus.FAILED, ActionStatus.PENDING -> r.originalPath
        // Удалённый дубль: у оставленного оригинала то же содержимое.
        ActionStatus.PURGED -> r.keptOriginalPath
    }

    fun origin(r: ActionRecord): String =
        displayPath(r.originalPath).substringBeforeLast('/', "").ifEmpty { "корень памяти" }
}
