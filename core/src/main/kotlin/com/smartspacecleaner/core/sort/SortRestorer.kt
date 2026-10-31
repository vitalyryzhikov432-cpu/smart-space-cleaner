package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.action.freeTarget
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.port.ActionLogStore
import com.smartspacecleaner.core.port.FileSystem
import com.smartspacecleaner.core.scan.StorageLayout

data class SortRestore(val restored: Int, val skipped: Int, val touchedPaths: List<String>)

/** «Вернуть всё на место»: переносит файлы одной сортировки обратно; один пропавший файл не мешает остальным. */
class SortRestorer(
    private val fs: FileSystem,
    private val log: ActionLogStore,
    private val layout: StorageLayout,
) {
    fun restoreSession(sessionId: String): SortRestore {
        val records = log.all().filter { it.sessionId == sessionId && it.kind == ActionKind.SORT_MOVE && it.status == ActionStatus.DONE }
        var restored = 0
        var skipped = 0
        val touched = mutableListOf<String>()
        records.forEach { r ->
            val moved = r.movedToPath
            when {
                moved != null && fs.exists(moved) -> {
                    val target = freeTarget(fs, r.originalPath) { "$it" }
                    if (fs.mkdirs(target.substringBeforeLast('/')) && fs.move(moved, target)) {
                        val note = if (target != r.originalPath) "возвращён как ${target.substringAfterLast('/')}" else null
                        log.update(r.copy(status = ActionStatus.RESTORED, reason = note))
                        touched += moved
                        touched += target
                        restored++
                    } else {
                        // Файл остался в папке сортировки: запись не трогаем, чтобы повторное «Вернуть всё» попробовало снова.
                        skipped++
                    }
                }
                // Возврат прервался в прошлый раз уже после переноса.
                fs.exists(r.originalPath) -> {
                    log.update(r.copy(status = ActionStatus.RESTORED))
                    restored++
                }
                else -> {
                    fail(r, "уже не в папке сортировки")
                    skipped++
                }
            }
        }
        SortCategory.entries.forEach { fs.deleteEmptyDir(it.folder(layout)) }
        fs.deleteEmptyDir(layout.sortRoot)
        return SortRestore(restored, skipped, touched)
    }

    private fun fail(r: ActionRecord, reason: String) = log.update(r.copy(status = ActionStatus.FAILED, reason = reason))
}
