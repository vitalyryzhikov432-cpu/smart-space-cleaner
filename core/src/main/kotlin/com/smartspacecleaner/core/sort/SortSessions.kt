package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import java.time.Instant

/** Одна сортировка в Истории: [moved] лежат в папках сортировки, [restored] возвращены, [failed] не получились. */
data class SortSession(val sessionId: String, val createdAt: Instant, val moved: Int, val restored: Int, val failed: Int) {
    val canRestore: Boolean get() = moved > 0
}

object SortSessions {
    fun of(records: List<ActionRecord>): List<SortSession> =
        records.filter { it.kind == ActionKind.SORT_MOVE }
            .groupBy { it.sessionId }
            .map { (id, rs) ->
                SortSession(
                    sessionId = id,
                    createdAt = rs.minOf { it.createdAt },
                    moved = rs.count { it.status == ActionStatus.DONE },
                    restored = rs.count { it.status == ActionStatus.RESTORED },
                    failed = rs.count { it.status == ActionStatus.FAILED },
                )
            }
            .sortedByDescending { it.createdAt }
}
