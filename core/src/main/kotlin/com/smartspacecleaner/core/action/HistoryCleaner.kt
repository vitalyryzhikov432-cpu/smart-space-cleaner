package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.port.ActionLogStore
import com.smartspacecleaner.core.port.Clock
import java.time.Duration

/** Чистит журнал «Ранее». Записи, по которым файл ещё можно вернуть или которые не завершены, не трогает. */
class HistoryCleaner(private val log: ActionLogStore, private val clock: Clock, private val keep: Duration = Duration.ofDays(15)) {
    fun forgetOld(): Int {
        val cutoff = clock.now().minus(keep)
        return forget(log.all().filter { isFinished(it) && it.createdAt.isBefore(cutoff) })
    }

    fun clearEarlier(): Int = forget(log.all().filter(::isFinished))

    private fun forget(records: List<ActionRecord>): Int {
        log.delete(records.map { it.id })
        return records.size
    }

    private fun isFinished(r: ActionRecord): Boolean = when (r.status) {
        ActionStatus.RESTORED, ActionStatus.PURGED, ActionStatus.FAILED -> true
        ActionStatus.DONE -> r.kind == ActionKind.DELETE_APK
        ActionStatus.PENDING -> false
    }
}
