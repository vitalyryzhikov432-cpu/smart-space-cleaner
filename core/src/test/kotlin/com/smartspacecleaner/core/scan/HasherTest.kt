package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.FileEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class HasherTest {
    private val fs = FakeFileSystem()
    private val hasher = Hasher(fs)

    @Test
    fun `full hash is sha256 hex`() {
        fs.add(p("a.txt"), "abc".toByteArray())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hasher.full(p("a.txt")))
    }

    @Test
    fun `partial hash ignores the middle of large files but full hash does not`() {
        val a = bytes(200 * 1024, 1)
        val b = a.copyOf().also { it[100 * 1024] = (it[100 * 1024] + 1).toByte() }
        fs.add(p("a.bin"), a)
        fs.add(p("b.bin"), b)
        val ea = FileEntry(p("a.bin"), a.size.toLong(), T0)
        val eb = FileEntry(p("b.bin"), b.size.toLong(), T0)
        assertEquals(hasher.partial(ea), hasher.partial(eb))
        assertNotEquals(hasher.full(p("a.bin")), hasher.full(p("b.bin")))
    }

    @Test
    fun `partial hash of small file covers whole content`() {
        fs.add(p("a.bin"), bytes(5000, 1))
        fs.add(p("b.bin"), bytes(5000, 2))
        assertNotEquals(
            hasher.partial(FileEntry(p("a.bin"), 5000, T0)),
            hasher.partial(FileEntry(p("b.bin"), 5000, T0)),
        )
    }
}
