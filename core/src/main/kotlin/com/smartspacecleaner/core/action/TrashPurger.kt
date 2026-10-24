package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.port.ActionLogStore
import com.smartspacecleaner.core.port.Clock
import com.smartspacecleaner.core.port.FileSystem

class TrashPurger(
    private val fs: FileSystem,
    private val log: ActionLogStore,
    private val clock: Clock,
) {
    fun purgeExpired(): Int {
        val now = clock.now()
        return purge(
            log.byStatus(ActionStatus.DONE).filter {
                it.kind == ActionKind.TRASH && it.expiresAt != null && !it.expiresAt.isAfter(now)
            },
        )
    }

    fun purgeNow(recordIds: Collection<Long>): Int =
        purge(recordIds.mapNotNull { log.get(it) }.filter { it.status == ActionStatus.DONE && it.kind == ActionKind.TRASH })

    private fun purge(records: List<ActionRecord>): Int {
        var purged = 0
        records.forEach { r ->
            val trash = r.trashPath
            if (trash == null || !fs.exists(trash) || fs.delete(trash)) {
                log.update(r.copy(status = ActionStatus.PURGED))
                purged++
            }
        }
        return purged
    }
}
