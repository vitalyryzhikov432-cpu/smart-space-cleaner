package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.model.Group
import com.smartspacecleaner.core.model.GroupKind
import com.smartspacecleaner.core.model.Plan
import com.smartspacecleaner.core.model.PlannedItem
import com.smartspacecleaner.core.port.ActionLogStore
import com.smartspacecleaner.core.port.Clock
import com.smartspacecleaner.core.port.FileSystem
import com.smartspacecleaner.core.scan.Hasher
import com.smartspacecleaner.core.scan.StorageLayout
import java.io.IOException
import java.time.Duration
import java.util.UUID

enum class DuplicateStrategy { TRASH, DELETE_WITH_RESTORE_COPY }

data class ExecutionResult(val sessionId: String, val records: List<ActionRecord>) {
    val done: List<ActionRecord> get() = records.filter { it.status == ActionStatus.DONE }
    val failed: List<ActionRecord> get() = records.filter { it.status == ActionStatus.FAILED }
}

class Executor(
    private val fs: FileSystem,
    private val log: ActionLogStore,
    private val clock: Clock,
    private val hasher: Hasher,
    private val layout: StorageLayout,
    private val duplicateStrategy: DuplicateStrategy = DuplicateStrategy.TRASH,
    private val trashRetention: Duration = Duration.ofDays(7),
    private val newTrashName: () -> String = { UUID.randomUUID().toString() },
) {
    fun execute(plan: Plan, selectedPaths: Set<String>, sessionId: String): ExecutionResult {
        val records = plan.groups
            .filter { it.kind != GroupKind.APP_CACHE }
            .sortedBy { it.level.number }
            .flatMap { g -> g.items.filter { it.path in selectedPaths }.map { g to it } }
            .map { (group, item) -> executeOne(group, item, sessionId) }
        return ExecutionResult(sessionId, records)
    }

    private fun executeOne(group: Group, item: PlannedItem, sessionId: String): ActionRecord {
        val kind = when {
            group.kind == GroupKind.APK_INSTALLED -> ActionKind.DELETE_APK
            group.kind == GroupKind.DUPLICATES && duplicateStrategy == DuplicateStrategy.DELETE_WITH_RESTORE_COPY -> ActionKind.DELETE_DUPLICATE
            else -> ActionKind.TRASH
        }
        val now = clock.now()
        val pending = ActionRecord(
            sessionId = sessionId,
            groupId = group.id,
            groupTitle = group.title,
            kind = kind,
            originalPath = item.path,
            size = item.size,
            trashPath = if (kind == ActionKind.TRASH) "${layout.trashRoot}/${newTrashName()}" else null,
            keptOriginalPath = item.keptOriginalPath,
            sha256 = item.sha256,
            status = ActionStatus.PENDING,
            createdAt = now,
            expiresAt = if (kind == ActionKind.TRASH) now.plus(trashRetention) else null,
        )
        val record = pending.copy(id = log.insert(pending))

        precheck(group, item)?.let { return finish(record, ActionStatus.FAILED, it) }

        val ok = if (kind == ActionKind.TRASH) {
            ensureTrashRoot()
            fs.move(item.path, record.trashPath!!)
        } else {
            fs.delete(item.path)
        }
        return if (ok) {
            finish(record, ActionStatus.DONE, null)
        } else {
            val verb = if (kind == ActionKind.TRASH) "переместить в корзину" else "удалить"
            finish(record, ActionStatus.FAILED, "не удалось $verb: нет доступа или файл занят")
        }
    }

    private fun precheck(group: Group, item: PlannedItem): String? {
        if (!fs.exists(item.path)) return "файл уже отсутствует"
        if (group.kind != GroupKind.DUPLICATES) return null
        val keeper = item.keptOriginalPath ?: return "не указан оригинал — копию не трогаю"
        if (!fs.exists(keeper)) return "оригинал не найден — копию не трогаю"
        val copyHash = hashOrNull(item.path)
        val keeperHash = hashOrNull(keeper)
        if (copyHash == null || keeperHash == null) return "не удалось прочитать файл для проверки"
        if (copyHash != item.sha256 || keeperHash != item.sha256) return "файл изменился после сканирования — не трогаю"
        return null
    }

    private fun hashOrNull(path: String): String? =
        try { hasher.full(path) } catch (e: IOException) { null } catch (e: SecurityException) { null }

    private fun ensureTrashRoot() {
        fs.mkdirs(layout.trashRoot)
        fs.createEmptyFile("${layout.trashRoot}/.nomedia")
    }

    private fun finish(record: ActionRecord, status: ActionStatus, reason: String?): ActionRecord {
        val updated = record.copy(status = status, reason = reason)
        log.update(updated)
        return updated
    }
}
