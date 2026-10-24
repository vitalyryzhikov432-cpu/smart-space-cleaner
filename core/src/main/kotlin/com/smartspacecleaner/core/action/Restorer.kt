package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.port.ActionLogStore
import com.smartspacecleaner.core.port.FileSystem
import com.smartspacecleaner.core.scan.Hasher
import java.io.IOException

sealed interface RestoreResult {
    data class Restored(val path: String) : RestoreResult
    data class Failed(val reason: String) : RestoreResult
}

class Restorer(
    private val fs: FileSystem,
    private val log: ActionLogStore,
    private val hasher: Hasher,
) {
    fun restore(recordId: Long): RestoreResult {
        val r = log.get(recordId) ?: return RestoreResult.Failed("запись не найдена")
        if (r.status != ActionStatus.DONE) return RestoreResult.Failed("это действие нельзя отменить")
        return when (r.kind) {
            ActionKind.TRASH -> fromTrash(r)
            ActionKind.DELETE_DUPLICATE -> fromOriginal(r)
            ActionKind.DELETE_APK -> RestoreResult.Failed("установщик удалён окончательно — при необходимости скачайте его заново")
        }
    }

    private fun fromTrash(r: ActionRecord): RestoreResult {
        val trash = r.trashPath ?: return RestoreResult.Failed("в записи нет пути корзины")
        if (!fs.exists(trash)) return RestoreResult.Failed("файл уже удалён из корзины окончательно")
        val target = freeTarget(r.originalPath)
        fs.mkdirs(target.substringBeforeLast('/'))
        if (!fs.move(trash, target)) return RestoreResult.Failed("не удалось вернуть файл: нет доступа")
        return markRestored(r, target)
    }

    private fun fromOriginal(r: ActionRecord): RestoreResult {
        val kept = r.keptOriginalPath ?: return RestoreResult.Failed("в записи нет пути к оригиналу")
        if (!fs.exists(kept)) return RestoreResult.Failed("оригинал больше не существует — копию вернуть нельзя")
        if (hashOrNull(kept) != r.sha256) return RestoreResult.Failed("оригинал изменился — точную копию вернуть нельзя")
        if (fs.freeBytes() < r.size) return RestoreResult.Failed("недостаточно места для восстановления")
        val target = freeTarget(r.originalPath)
        fs.mkdirs(target.substringBeforeLast('/'))
        if (!fs.copy(kept, target)) return RestoreResult.Failed("не удалось скопировать оригинал")
        if (hashOrNull(target) != r.sha256) {
            fs.delete(target)
            return RestoreResult.Failed("копия повреждена при восстановлении")
        }
        return markRestored(r, target)
    }

    private fun markRestored(r: ActionRecord, target: String): RestoreResult {
        val note = if (target != r.originalPath) "возвращён как ${target.substringAfterLast('/')}" else null
        log.update(r.copy(status = ActionStatus.RESTORED, reason = note))
        return RestoreResult.Restored(target)
    }

    private fun freeTarget(path: String): String {
        if (!fs.exists(path)) return path
        val dir = path.substringBeforeLast('/')
        val name = path.substringAfterLast('/')
        val base = name.substringBeforeLast('.')
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var candidate = "$dir/$base (восстановлено)$ext"
        var i = 2
        while (fs.exists(candidate)) {
            candidate = "$dir/$base (восстановлено $i)$ext"
            i++
        }
        return candidate
    }

    private fun hashOrNull(path: String): String? =
        try { hasher.full(path) } catch (e: IOException) { null } catch (e: SecurityException) { null }
}
