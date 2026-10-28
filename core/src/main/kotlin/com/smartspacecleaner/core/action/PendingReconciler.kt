package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.port.ActionLogStore
import com.smartspacecleaner.core.port.FileSystem

/** Сверяет записи, оставшиеся в PENDING после сбоя, с реальным состоянием файлов. */
class PendingReconciler(private val fs: FileSystem, private val log: ActionLogStore) {
    fun reconcile(): Int {
        val pending = log.byStatus(ActionStatus.PENDING)
        pending.forEach { log.update(resolve(it)) }
        return pending.size
    }

    private fun resolve(r: ActionRecord): ActionRecord = when {
        fs.exists(r.originalPath) ->
            r.copy(status = ActionStatus.FAILED, reason = "прервано: действие не завершилось, файл на месте")
        r.kind == ActionKind.TRASH && r.trashPath != null && fs.exists(r.trashPath) ->
            r.copy(status = ActionStatus.DONE)
        r.kind == ActionKind.TRASH ->
            r.copy(status = ActionStatus.FAILED, reason = "файл потерян при сбое: его нет ни на месте, ни в корзине")
        else ->
            r.copy(status = ActionStatus.DONE)
    }
}
