package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.fakes.FakeClock
import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.InMemoryActionLogStore
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.scan.StorageLayout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SortRestorerTest {
    private val fs = FakeFileSystem()
    private val log = InMemoryActionLogStore()
    private val layout = StorageLayout(ROOT)
    private val restorer = SortRestorer(fs, log, layout)

    private fun sort(session: String, vararg moves: Pair<String, SortCategory>) {
        moves.forEachIndexed { i, (rel, _) -> if (!fs.exists(p(rel))) fs.add(p(rel), bytes(10, i + 1)) }
        SortExecutor(fs, log, FakeClock(T0), layout).execute(moves.map { (rel, c) -> SortMove(p(rel), 10, c) }, session)
    }

    @Test
    fun `puts every file of the session back where it was`() {
        sort("s1", "DCIM/Camera/a.jpg" to SortCategory.NATURE, "Download/b.jpg" to SortCategory.FOOD)

        val result = restorer.restoreSession("s1")

        assertEquals(SortRestore(2, 0, result.touchedPaths), result)
        assertTrue(fs.exists(p("DCIM/Camera/a.jpg")))
        assertTrue(fs.exists(p("Download/b.jpg")))
        assertFalse(fs.exists(p("Pictures/Сортировка/Природа/a.jpg")))
        assertTrue(log.all().all { it.status == ActionStatus.RESTORED })
        assertTrue(p("Pictures/Сортировка/Природа/a.jpg") in result.touchedPaths && p("DCIM/Camera/a.jpg") in result.touchedPaths)
    }

    @Test
    fun `occupied original place gets a numbered suffix, nothing is overwritten`() {
        sort("s1", "DCIM/Camera/a.jpg" to SortCategory.NATURE)
        fs.add(p("DCIM/Camera/a.jpg"), bytes(10, 99))

        restorer.restoreSession("s1")

        assertArrayEquals(bytes(10, 99), fs.content(p("DCIM/Camera/a.jpg")))
        assertArrayEquals(bytes(10, 1), fs.content(p("DCIM/Camera/a (1).jpg")))
        assertEquals("возвращён как a (1).jpg", log.all().single().reason)
    }

    @Test
    fun `file already back from an interrupted restore counts as restored`() {
        sort("s1", "DCIM/Camera/a.jpg" to SortCategory.NATURE)
        fs.move(p("Pictures/Сортировка/Природа/a.jpg"), p("DCIM/Camera/a.jpg"))

        assertEquals(1, restorer.restoreSession("s1").restored)
        assertEquals(ActionStatus.RESTORED, log.all().single().status)
    }

    @Test
    fun `file deleted from the sort folder is skipped, others come back`() {
        sort("s1", "DCIM/Camera/a.jpg" to SortCategory.NATURE, "DCIM/Camera/b.jpg" to SortCategory.NATURE)
        fs.delete(p("Pictures/Сортировка/Природа/a.jpg"))

        val result = restorer.restoreSession("s1")

        assertEquals(1, result.restored)
        assertEquals(1, result.skipped)
        assertEquals("уже не в папке сортировки", log.all().first().reason)
        assertTrue(fs.exists(p("DCIM/Camera/b.jpg")))
    }

    @Test
    fun `only the chosen session is restored`() {
        sort("s1", "DCIM/Camera/a.jpg" to SortCategory.NATURE)
        sort("s2", "DCIM/Camera/b.jpg" to SortCategory.FOOD)

        restorer.restoreSession("s1")

        assertTrue(fs.exists(p("Pictures/Сортировка/Еда/b.jpg")))
    }

    @Test
    fun `empty category folders and the empty root are removed, busy ones stay`() {
        sort("s1", "DCIM/Camera/a.jpg" to SortCategory.NATURE)
        sort("s2", "DCIM/Camera/b.jpg" to SortCategory.FOOD)

        restorer.restoreSession("s1")
        assertTrue(p("Pictures/Сортировка/Природа") in fs.deletedDirs)
        assertFalse(p("Pictures/Сортировка/Еда") in fs.deletedDirs)
        assertFalse(p("Pictures/Сортировка") in fs.deletedDirs)

        restorer.restoreSession("s2")
        assertTrue(p("Pictures/Сортировка") in fs.deletedDirs)
    }

    @Test
    fun `restoring twice does nothing the second time`() {
        sort("s1", "DCIM/Camera/a.jpg" to SortCategory.NATURE)
        restorer.restoreSession("s1")

        assertEquals(SortRestore(0, 0, emptyList()), restorer.restoreSession("s1"))
    }
}
