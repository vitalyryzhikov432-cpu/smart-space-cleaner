package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DuplicateFinderTest {
    private val fs = FakeFileSystem()
    private val finder = DuplicateFinder(Hasher(fs))
    private fun entries() = fs.walk(ROOT, emptySet()).toList()

    @Test
    fun `finds identical files in different folders`() {
        fs.add(p("DCIM/Camera/a.jpg"), bytes(4096, 1))
        fs.add(p("redmi6/a.jpg"), bytes(4096, 1))
        val sets = finder.find(entries())
        assertEquals(1, sets.size)
        assertEquals(listOf(p("DCIM/Camera/a.jpg"), p("redmi6/a.jpg")), sets[0].files.map { it.path })
        assertEquals(4096L, sets[0].size)
    }

    @Test
    fun `same size with different content is not a duplicate`() {
        fs.add(p("a.bin"), bytes(4096, 1))
        fs.add(p("b.bin"), bytes(4096, 2))
        assertTrue(finder.find(entries()).isEmpty())
    }

    @Test
    fun `ignores files smaller than 1 KB`() {
        fs.add(p("a.txt"), bytes(1000, 1))
        fs.add(p("b.txt"), bytes(1000, 1))
        assertTrue(finder.find(entries()).isEmpty())
    }

    @Test
    fun `groups three copies into one set`() {
        listOf("a", "b", "c").forEach { fs.add(p("$it/x.jpg"), bytes(2048, 7)) }
        assertEquals(3, finder.find(entries()).single().files.size)
    }

    @Test
    fun `files that differ only in the middle are not duplicates`() {
        val a = bytes(200 * 1024, 1)
        fs.add(p("a.bin"), a)
        fs.add(p("b.bin"), a.copyOf().also { it[100 * 1024] = (it[100 * 1024] + 1).toByte() })
        assertTrue(finder.find(entries()).isEmpty())
    }

    @Test
    fun `skips unreadable files`() {
        fs.add(p("a.bin"), bytes(4096, 1))
        fs.add(p("b.bin"), bytes(4096, 1))
        fs.add(p("c.bin"), bytes(4096, 1))
        fs.unreadablePaths += p("c.bin")
        assertEquals(listOf(p("a.bin"), p("b.bin")), finder.find(entries()).single().files.map { it.path })
    }

    @Test
    fun `largest waste comes first`() {
        fs.add(p("s1"), bytes(2048, 1)); fs.add(p("s2"), bytes(2048, 1))
        fs.add(p("b1"), bytes(8192, 2)); fs.add(p("b2"), bytes(8192, 2))
        assertEquals(listOf(8192L, 2048L), finder.find(entries()).map { it.size })
    }
}
