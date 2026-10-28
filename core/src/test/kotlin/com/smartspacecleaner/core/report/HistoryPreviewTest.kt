package com.smartspacecleaner.core.report

import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HistoryPreviewTest {
    private fun record(kind: ActionKind, status: ActionStatus, kept: String? = p("DCIM/Camera/IMG_1.jpg")) = ActionRecord(
        sessionId = "s", groupId = "g", groupTitle = "t", kind = kind,
        originalPath = p("Download/IMG_1 (1).jpg"), size = 10,
        trashPath = p(".SmartSpaceCleaner/trash/uuid-1"), keptOriginalPath = kept,
        status = status, createdAt = T0,
    )

    @Test
    fun `file in trash is shown from the trash copy`() {
        assertEquals(p(".SmartSpaceCleaner/trash/uuid-1"), HistoryPreview.contentPath(record(ActionKind.TRASH, ActionStatus.DONE)))
    }

    @Test
    fun `restored, failed and unfinished files are shown from where they are now`() {
        listOf(ActionStatus.RESTORED, ActionStatus.FAILED, ActionStatus.PENDING).forEach {
            assertEquals(p("Download/IMG_1 (1).jpg"), HistoryPreview.contentPath(record(ActionKind.TRASH, it)))
        }
    }

    @Test
    fun `gone duplicate is shown from its kept original, which has the same content`() {
        assertEquals(p("DCIM/Camera/IMG_1.jpg"), HistoryPreview.contentPath(record(ActionKind.TRASH, ActionStatus.PURGED)))
        assertEquals(p("DCIM/Camera/IMG_1.jpg"), HistoryPreview.contentPath(record(ActionKind.DELETE_DUPLICATE, ActionStatus.DONE)))
    }

    @Test
    fun `gone file without an original has no picture`() {
        assertNull(HistoryPreview.contentPath(record(ActionKind.TRASH, ActionStatus.PURGED, kept = null)))
        assertNull(HistoryPreview.contentPath(record(ActionKind.DELETE_APK, ActionStatus.DONE, kept = null)))
    }

    @Test
    fun `origin is the folder the file came from`() {
        assertEquals("Download", HistoryPreview.origin(record(ActionKind.TRASH, ActionStatus.DONE)))
        assertEquals(
            "корень памяти",
            HistoryPreview.origin(record(ActionKind.TRASH, ActionStatus.DONE).copy(originalPath = p("x.jpg"))),
        )
    }
}
