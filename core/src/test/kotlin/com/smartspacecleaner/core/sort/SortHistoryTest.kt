package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.action.HistoryCleaner
import com.smartspacecleaner.core.action.TrashPurger
import com.smartspacecleaner.core.fakes.FakeClock
import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.InMemoryActionLogStore
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.report.HistorySections
import com.smartspacecleaner.core.scan.OldScreenshotRule
import com.smartspacecleaner.core.scan.StorageLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class SortHistoryTest {
    private val log = InMemoryActionLogStore()

    private fun sorted(session: String, status: ActionStatus = ActionStatus.DONE, at: Instant = T0, name: String = "a.jpg") = log.insert(
        ActionRecord(
            sessionId = session, groupId = "sort:NATURE", groupTitle = "Природа", kind = ActionKind.SORT_MOVE,
            originalPath = p("DCIM/Camera/$name"), size = 10, status = status, createdAt = at,
            movedToPath = p("Pictures/Сортировка/Природа/$name"),
        ),
    )

    @Test
    fun `sorted files are not listed one by one in the trash history`() {
        sorted("s1")
        sorted("s1", ActionStatus.RESTORED, name = "b.jpg")

        val sections = HistorySections.of(log.all())

        assertTrue(sections.inTrash.isEmpty() && sections.earlier.isEmpty())
    }

    @Test
    fun `one sort is one session with counts, newest first`() {
        sorted("old", at = T0)
        sorted("new", at = T0.plusSeconds(60))
        sorted("new", ActionStatus.RESTORED, at = T0.plusSeconds(60), name = "b.jpg")
        sorted("new", ActionStatus.FAILED, at = T0.plusSeconds(60), name = "c.jpg")

        val sessions = SortSessions.of(log.all())

        assertEquals(listOf("new", "old"), sessions.map { it.sessionId })
        assertEquals(SortSession("new", T0.plusSeconds(60), moved = 1, restored = 1, failed = 1), sessions.first())
        assertTrue(sessions.first().canRestore)
    }

    @Test
    fun `fully restored session can not be restored again`() {
        sorted("s1", ActionStatus.RESTORED)
        assertEquals(false, SortSessions.of(log.all()).single().canRestore)
    }

    @Test
    fun `clearing history right after sorting keeps the way back`() {
        val kept = sorted("s1")
        sorted("s1", ActionStatus.RESTORED, name = "b.jpg")

        HistoryCleaner(log, FakeClock(T0)).clearEarlier()

        assertEquals(listOf(kept), log.all().map { it.id })
    }

    @Test
    fun `sorted records are forgotten after 15 days, files stay where they are`() {
        val young = sorted("young", at = T0.minus(Duration.ofDays(14)))
        sorted("old", at = T0.minus(Duration.ofDays(16)))

        HistoryCleaner(log, FakeClock(T0)).forgetOld()

        assertEquals(listOf(young), log.all().map { it.id })
    }

    @Test
    fun `trash purge never deletes sorted files`() {
        val fs = FakeFileSystem()
        fs.add(p("Pictures/Сортировка/Природа/a.jpg"), bytes(10, 1))
        val id = sorted("s1", at = T0.minus(Duration.ofDays(30)))

        val purger = TrashPurger(fs, log, FakeClock(T0))
        purger.purgeExpired()
        purger.purgeNow(listOf(id))

        assertTrue(fs.exists(p("Pictures/Сортировка/Природа/a.jpg")))
        assertEquals(ActionStatus.DONE, log.get(id)!!.status)
    }

    @Test
    fun `cleanup still finds old screenshots after they were sorted`() {
        val old = FileEntry(p("Pictures/Сортировка/Скриншоты/s.png"), 10, T0.minus(Duration.ofDays(200)))

        val found = OldScreenshotRule(StorageLayout(ROOT), FakeClock(T0)).find(listOf(old))

        assertEquals(listOf(old), found)
    }
}
