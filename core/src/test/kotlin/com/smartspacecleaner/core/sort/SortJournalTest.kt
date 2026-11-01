package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.action.RestoreResult
import com.smartspacecleaner.core.action.Restorer
import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.InMemoryActionLogStore
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.report.HistoryPreview
import com.smartspacecleaner.core.scan.Hasher
import com.smartspacecleaner.core.scan.StorageLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SortJournalTest {
    private val layout = StorageLayout(ROOT)

    private fun sorted(status: ActionStatus = ActionStatus.DONE) = ActionRecord(
        sessionId = "s", groupId = "sort:NATURE", groupTitle = "Природа", kind = ActionKind.SORT_MOVE,
        originalPath = p("DCIM/Camera/a.jpg"), size = 10, status = status, createdAt = T0,
        movedToPath = p("Pictures/Сортировка/Природа/a.jpg"),
    )

    @Test
    fun `sort root lives in Pictures`() {
        assertEquals(p("Pictures/Сортировка"), layout.sortRoot)
    }

    @Test
    fun `every category has its own folder under the sort root`() {
        assertEquals(p("Pictures/Сортировка/Документы"), SortCategory.DOCUMENTS.folder(layout))
        assertEquals("Pictures/Сортировка/Видео", SortCategory.VIDEO.relativeFolder)
        assertEquals(
            listOf("Документы", "Скриншоты", "Люди", "Еда", "Природа", "Животные", "Мессенджеры", "Разное", "Видео"),
            SortCategory.entries.map { it.title },
        )
    }

    @Test
    fun `sorted file is shown from its new place in history`() {
        assertEquals(p("Pictures/Сортировка/Природа/a.jpg"), HistoryPreview.contentPath(sorted()))
        assertEquals(p("DCIM/Camera/a.jpg"), HistoryPreview.contentPath(sorted(ActionStatus.RESTORED)))
    }

    @Test
    fun `single-file restore refuses sorted files and points to the whole-session button`() {
        val fs = FakeFileSystem()
        val log = InMemoryActionLogStore()
        fs.add(p("Pictures/Сортировка/Природа/a.jpg"), bytes(10, 1))
        val id = log.insert(sorted())

        val result = Restorer(fs, log, Hasher(fs)).restore(id)

        assertTrue(result is RestoreResult.Failed && "Вернуть всё на место" in result.reason)
        assertEquals(ActionStatus.DONE, log.get(id)!!.status)
    }

    @Test
    fun `fake deletes only empty folders and remembers them`() {
        val fs = FakeFileSystem()
        fs.add(p("Pictures/Сортировка/Еда/x.jpg"), bytes(1, 1))

        assertFalse(fs.deleteEmptyDir(p("Pictures/Сортировка/Еда")))
        assertTrue(fs.deleteEmptyDir(p("Pictures/Сортировка/Природа")))
        assertEquals(listOf(p("Pictures/Сортировка/Природа")), fs.deletedDirs)
    }
}
