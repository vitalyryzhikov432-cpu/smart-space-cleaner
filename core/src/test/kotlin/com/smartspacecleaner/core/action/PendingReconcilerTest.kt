package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.InMemoryActionLogStore
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PendingReconcilerTest {
    private val fs = FakeFileSystem()
    private val log = InMemoryActionLogStore()
    private val reconciler = PendingReconciler(fs, log)

    private fun pending(kind: ActionKind, path: String, trash: String? = null) = log.insert(
        ActionRecord(
            sessionId = "s", groupId = "g", groupTitle = "g", kind = kind, originalPath = path, size = 10,
            trashPath = trash, status = ActionStatus.PENDING, createdAt = T0,
        ),
    )

    @Test
    fun `file still in place means the action was interrupted`() {
        fs.add(p("a.jpg"), bytes(10, 1))
        val id = pending(ActionKind.TRASH, p("a.jpg"), p(".SmartSpaceCleaner/trash/t1"))
        assertEquals(1, reconciler.reconcile())
        assertEquals(ActionStatus.FAILED, log.get(id)!!.status)
        assertTrue(log.get(id)!!.reason!!.contains("прервано"))
    }

    @Test
    fun `file found in trash means the move completed`() {
        fs.add(p(".SmartSpaceCleaner/trash/t1"), bytes(10, 1))
        val id = pending(ActionKind.TRASH, p("a.jpg"), p(".SmartSpaceCleaner/trash/t1"))
        reconciler.reconcile()
        assertEquals(ActionStatus.DONE, log.get(id)!!.status)
    }

    @Test
    fun `file missing in both places is reported as lost`() {
        val id = pending(ActionKind.TRASH, p("a.jpg"), p(".SmartSpaceCleaner/trash/t1"))
        reconciler.reconcile()
        assertEquals(ActionStatus.FAILED, log.get(id)!!.status)
        assertTrue(log.get(id)!!.reason!!.contains("потерян"))
    }

    @Test
    fun `deleted file means the delete completed`() {
        val id = pending(ActionKind.DELETE_APK, p("Download/app.apk"))
        reconciler.reconcile()
        assertEquals(ActionStatus.DONE, log.get(id)!!.status)
    }

    @Test
    fun `finished records are not touched`() {
        val id = log.insert(
            ActionRecord(
                sessionId = "s", groupId = "g", groupTitle = "g", kind = ActionKind.TRASH, originalPath = p("a.jpg"),
                size = 10, status = ActionStatus.DONE, createdAt = T0,
            ),
        )
        assertEquals(0, reconciler.reconcile())
        assertEquals(ActionStatus.DONE, log.get(id)!!.status)
    }
}
