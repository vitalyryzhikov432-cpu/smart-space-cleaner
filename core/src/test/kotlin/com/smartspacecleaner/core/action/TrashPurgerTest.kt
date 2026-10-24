package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.fakes.FakeClock
import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.InMemoryActionLogStore
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class TrashPurgerTest {
    private val fs = FakeFileSystem()
    private val log = InMemoryActionLogStore()
    private val clock = FakeClock(T0)
    private val purger = TrashPurger(fs, log, clock)

    private fun trashed(name: String, kind: ActionKind = ActionKind.TRASH, status: ActionStatus = ActionStatus.DONE): Long {
        val trash = p(".SmartSpaceCleaner/trash/$name")
        fs.add(trash, bytes(100, 1))
        return log.insert(
            ActionRecord(
                sessionId = "s", groupId = "g", groupTitle = "g", kind = kind, originalPath = p("x/$name"), size = 100,
                trashPath = trash, status = status, createdAt = T0, expiresAt = T0.plus(Duration.ofDays(7)),
            ),
        )
    }

    @Test
    fun `keeps files younger than 7 days`() {
        val id = trashed("t1")
        clock.current = T0.plus(Duration.ofDays(7)).minusSeconds(1)
        assertEquals(0, purger.purgeExpired())
        assertEquals(ActionStatus.DONE, log.get(id)!!.status)
        assertTrue(fs.exists(p(".SmartSpaceCleaner/trash/t1")))
    }

    @Test
    fun `purges files once 7 days have passed`() {
        val id = trashed("t1")
        clock.current = T0.plus(Duration.ofDays(7))
        assertEquals(1, purger.purgeExpired())
        assertEquals(ActionStatus.PURGED, log.get(id)!!.status)
        assertFalse(fs.exists(p(".SmartSpaceCleaner/trash/t1")))
    }

    @Test
    fun `purge now deletes only done trash records`() {
        val done = trashed("t1")
        val restored = trashed("t2", status = ActionStatus.RESTORED)
        assertEquals(1, purger.purgeNow(listOf(done, restored, 999)))
        assertEquals(ActionStatus.PURGED, log.get(done)!!.status)
        assertEquals(ActionStatus.RESTORED, log.get(restored)!!.status)
        assertTrue(fs.exists(p(".SmartSpaceCleaner/trash/t2")))
    }

    @Test
    fun `keeps record when the file cannot be deleted`() {
        val id = trashed("t1")
        fs.failingPaths += p(".SmartSpaceCleaner/trash/t1")
        assertEquals(0, purger.purgeNow(listOf(id)))
        assertEquals(ActionStatus.DONE, log.get(id)!!.status)
    }

    @Test
    fun `marks record purged when the trash file is already gone`() {
        val id = trashed("t1")
        fs.delete(p(".SmartSpaceCleaner/trash/t1"))
        assertEquals(1, purger.purgeNow(listOf(id)))
        assertEquals(ActionStatus.PURGED, log.get(id)!!.status)
    }
}
