package com.smartspacecleaner.core.report

import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus

/** Экран «Корзина»: сверху то, что лежит в корзине сейчас, ниже — журнал уже завершённого. */
data class HistorySections(val inTrash: List<ActionRecord>, val earlier: List<ActionRecord>) {
    companion object {
        fun of(records: List<ActionRecord>): HistorySections {
            val (inTrash, earlier) = records.partition { it.status == ActionStatus.DONE && it.kind == ActionKind.TRASH }
            return HistorySections(inTrash, earlier)
        }
    }
}
