package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.fakes.FakeClock
import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.InMemoryActionLogStore
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.scan.StorageLayout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SortExecutorTest {
    private val fs = FakeFileSystem()
    private val log = InMemoryActionLogStore()
    private val executor = SortExecutor(fs, log, FakeClock(T0), StorageLayout(ROOT))

    @Test
    fun `moves a file into its category folder and journals it`() {
        val content = bytes(100, 1)
        fs.add(p("DCIM/Camera/a.jpg"), content)

        val result = executor.execute(listOf(SortMove(p("DCIM/Camera/a.jpg"), 100, SortCategory.NATURE)), "s1")

        val target = p("Pictures/Сортировка/Природа/a.jpg")
        assertFalse(fs.exists(p("DCIM/Camera/a.jpg")))
        assertArrayEquals(content, fs.content(target))
        val r = log.all().single()
        assertEquals(ActionKind.SORT_MOVE, r.kind)
        assertEquals(ActionStatus.DONE, r.status)
        assertEquals("s1", r.sessionId)
        assertEquals("sort:NATURE", r.groupId)
        assertEquals("Природа", r.groupTitle)
        assertEquals(target, r.movedToPath)
        assertEquals(listOf(p("DCIM/Camera/a.jpg"), target), result.touchedPaths)
    }

    @Test
    fun `same name from two folders in one batch gets a numbered suffix`() {
        fs.add(p("DCIM/Camera/IMG_1.jpg"), bytes(10, 1))
        fs.add(p("Download/IMG_1.jpg"), bytes(10, 2))

        executor.execute(
            listOf(
                SortMove(p("DCIM/Camera/IMG_1.jpg"), 10, SortCategory.FOOD),
                SortMove(p("Download/IMG_1.jpg"), 10, SortCategory.FOOD),
            ),
            "s1",
        )

        assertArrayEquals(bytes(10, 1), fs.content(p("Pictures/Сортировка/Еда/IMG_1.jpg")))
        assertArrayEquals(bytes(10, 2), fs.content(p("Pictures/Сортировка/Еда/IMG_1 (1).jpg")))
    }

    @Test
    fun `existing file in the category folder is never overwritten`() {
        fs.add(p("Pictures/Сортировка/Еда/a.jpg"), bytes(10, 9))
        fs.add(p("DCIM/Camera/a.jpg"), bytes(10, 1))

        executor.execute(listOf(SortMove(p("DCIM/Camera/a.jpg"), 10, SortCategory.FOOD)), "s1")

        assertArrayEquals(bytes(10, 9), fs.content(p("Pictures/Сортировка/Еда/a.jpg")))
        assertArrayEquals(bytes(10, 1), fs.content(p("Pictures/Сортировка/Еда/a (1).jpg")))
    }

    @Test
    fun `a file that vanished after analysis fails, the rest still move`() {
        fs.add(p("DCIM/Camera/b.jpg"), bytes(10, 2))

        val result = executor.execute(
            listOf(
                SortMove(p("DCIM/Camera/gone.jpg"), 10, SortCategory.OTHER),
                SortMove(p("DCIM/Camera/b.jpg"), 10, SortCategory.OTHER),
            ),
            "s1",
        )

        assertEquals(listOf("файл исчез"), result.failed.map { it.reason })
        assertEquals(1, result.moved.size)
        assertTrue(fs.exists(p("Pictures/Сортировка/Разное/b.jpg")))
    }

    @Test
    fun `a move the system refuses is recorded as failed`() {
        fs.add(p("DCIM/Camera/a.jpg"), bytes(10, 1))
        fs.failingPaths += p("DCIM/Camera/a.jpg")

        val result = executor.execute(listOf(SortMove(p("DCIM/Camera/a.jpg"), 10, SortCategory.OTHER)), "s1")

        assertEquals(ActionStatus.FAILED, result.records.single().status)
        assertEquals("не удалось перенести", result.records.single().reason)
        assertTrue(fs.exists(p("DCIM/Camera/a.jpg")))
        assertTrue(result.touchedPaths.isEmpty())
    }

    @Test
    fun `progress is reported after every file`() {
        fs.add(p("a/1.jpg"), bytes(1, 1))
        fs.add(p("a/2.jpg"), bytes(1, 2))
        val progress = mutableListOf<Int>()

        executor.execute(listOf(SortMove(p("a/1.jpg"), 1, SortCategory.OTHER), SortMove(p("a/2.jpg"), 1, SortCategory.OTHER)), "s1") { progress += it }

        assertEquals(listOf(1, 2), progress)
    }
}
