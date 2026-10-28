package com.smartspacecleaner.core.report

import com.smartspacecleaner.core.action.HistoryCleaner
import com.smartspacecleaner.core.fakes.FakeClock
import com.smartspacecleaner.core.fakes.InMemoryActionLogStore
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class HistorySectionsTest {
    private fun record(status: ActionStatus, kind: ActionKind = ActionKind.TRASH, daysAgo: Long = 0, now: java.time.Instant = T0) =
        ActionRecord(
            sessionId = "s", groupId = "g", groupTitle = "t", kind = kind,
            originalPath = p("Download/f.jpg"), size = 1, status = status,
            createdAt = now.minus(Duration.ofDays(daysAgo)),
        )

    @Test
    fun `files in the trash go first, everything already finished goes to earlier`() {
        val log = InMemoryActionLogStore()
        listOf(
            record(ActionStatus.DONE),
            record(ActionStatus.RESTORED),
            record(ActionStatus.PURGED),
            record(ActionStatus.FAILED),
            record(ActionStatus.DONE, kind = ActionKind.DELETE_APK),
        ).forEach { log.insert(it) }

        val sections = HistorySections.of(log.all())

        assertEquals(listOf(ActionStatus.DONE), sections.inTrash.map { it.status })
        assertEquals(4, sections.earlier.size)
    }

    @Test
    fun `history older than 15 days is forgotten, trash and unfinished records are kept`() {
        val now = T0.plus(Duration.ofDays(100))
        val log = InMemoryActionLogStore()
        val old = listOf(
            record(ActionStatus.RESTORED, daysAgo = 16, now = now),
            record(ActionStatus.PURGED, daysAgo = 40, now = now),
            record(ActionStatus.FAILED, daysAgo = 16, now = now),
            record(ActionStatus.DONE, kind = ActionKind.DELETE_APK, daysAgo = 16, now = now),
        ).map { log.insert(it) }
        val kept = listOf(
            record(ActionStatus.RESTORED, daysAgo = 14, now = now),
            record(ActionStatus.DONE, daysAgo = 16, now = now),
            record(ActionStatus.PENDING, daysAgo = 16, now = now),
            record(ActionStatus.DONE, kind = ActionKind.DELETE_DUPLICATE, daysAgo = 16, now = now),
        ).map { log.insert(it) }

        assertEquals(old.size, HistoryCleaner(log, FakeClock(now)).forgetOld())
        assertEquals(kept, log.all().map { it.id })
    }

    @Test
    fun `clearing history removes only finished records, never files in the trash`() {
        val log = InMemoryActionLogStore()
        val inTrash = log.insert(record(ActionStatus.DONE))
        val pending = log.insert(record(ActionStatus.PENDING))
        log.insert(record(ActionStatus.RESTORED))
        log.insert(record(ActionStatus.PURGED))

        assertEquals(2, HistoryCleaner(log, FakeClock(T0)).clearEarlier())
        assertEquals(listOf(inTrash, pending), log.all().map { it.id })
    }
}
