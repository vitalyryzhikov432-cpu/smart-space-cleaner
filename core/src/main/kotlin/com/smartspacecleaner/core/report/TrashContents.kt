package com.smartspacecleaner.core.report

import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import java.time.Instant

/** Что сейчас лежит в корзине — для заголовка и кнопок «Вернуть все» / «Удалить все». */
data class TrashContents(val ids: List<Long>, val bytes: Long, val freesBy: Instant?) {
    companion object {
        fun of(records: List<ActionRecord>): TrashContents {
            val inTrash = records.filter { it.status == ActionStatus.DONE && it.kind == ActionKind.TRASH }
            return TrashContents(inTrash.map { it.id }, inTrash.sumOf { it.size }, inTrash.mapNotNull { it.expiresAt }.maxOrNull())
        }
    }
}
