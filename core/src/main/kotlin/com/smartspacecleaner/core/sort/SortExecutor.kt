package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.action.freeTarget
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.port.ActionLogStore
import com.smartspacecleaner.core.port.Clock
import com.smartspacecleaner.core.port.FileSystem
import com.smartspacecleaner.core.scan.StorageLayout
import java.time.Instant

data class SortResult(val records: List<ActionRecord>) {
    val moved: List<ActionRecord> get() = records.filter { it.status == ActionStatus.DONE }
    val failed: List<ActionRecord> get() = records.filter { it.status == ActionStatus.FAILED }

    /** Старые и новые пути перенесённых файлов — их показываем медиасканеру, чтобы галерея обновилась. */
    val touchedPaths: List<String> get() = moved.flatMap { listOfNotNull(it.originalPath, it.movedToPath) }
}

/** Переносит файлы по папкам категорий. Как и корзина: сначала запись в журнал, потом действие. */
class SortExecutor(
    private val fs: FileSystem,
    private val log: ActionLogStore,
    private val clock: Clock,
    private val layout: StorageLayout,
) {
    fun execute(moves: List<SortMove>, sessionId: String, onProgress: (Int) -> Unit = {}): SortResult {
        val now = clock.now()
        val records = moves.mapIndexed { i, move -> move(move, sessionId, now).also { onProgress(i + 1) } }
        return SortResult(records)
    }

    private fun move(m: SortMove, sessionId: String, now: Instant): ActionRecord {
        val target = freeTarget(fs, "${m.category.folder(layout)}/${m.path.substringAfterLast('/')}") { "$it" }
        val pending = ActionRecord(
            sessionId = sessionId,
            groupId = "sort:${m.category.name}",
            groupTitle = m.category.title,
            kind = ActionKind.SORT_MOVE,
            originalPath = m.path,
            size = m.size,
            movedToPath = target,
            status = ActionStatus.PENDING,
            createdAt = now,
        )
        val record = pending.copy(id = log.insert(pending))
        val finished = when {
            !fs.exists(m.path) -> record.copy(status = ActionStatus.FAILED, reason = "файл исчез")
            fs.mkdirs(target.substringBeforeLast('/')) && fs.move(m.path, target) -> record.copy(status = ActionStatus.DONE)
            else -> record.copy(status = ActionStatus.FAILED, reason = "не удалось перенести")
        }
        log.update(finished)
        return finished
    }
}
