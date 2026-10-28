package com.smartspacecleaner.core.report

import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration

class TrashContentsTest {
    private fun record(id: Long, status: ActionStatus, kind: ActionKind = ActionKind.TRASH, size: Long = 100, days: Long = 7) =
        ActionRecord(
            id = id, sessionId = "s", groupId = "g", groupTitle = "t", kind = kind,
            originalPath = p("Download/f$id.jpg"), size = size, trashPath = p(".SmartSpaceCleaner/trash/$id"),
            status = status, createdAt = T0, expiresAt = T0.plus(Duration.ofDays(days)),
        )

    @Test
    fun `counts only files that are in the trash right now`() {
        val trash = TrashContents.of(
            listOf(
                record(1, ActionStatus.DONE, size = 100, days = 7),
                record(2, ActionStatus.DONE, size = 250, days = 9),
                record(3, ActionStatus.RESTORED),
                record(4, ActionStatus.PURGED),
                record(5, ActionStatus.DONE, kind = ActionKind.DELETE_DUPLICATE),
                record(6, ActionStatus.FAILED),
            ),
        )

        assertEquals(listOf(1L, 2L), trash.ids)
        assertEquals(350L, trash.bytes)
        assertEquals(T0.plus(Duration.ofDays(9)), trash.freesBy)
    }

    @Test
    fun `empty trash has nothing to free`() {
        val trash = TrashContents.of(listOf(record(3, ActionStatus.RESTORED)))
        assertEquals(emptyList<Long>(), trash.ids)
        assertNull(trash.freesBy)
    }
}
