package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FreeNameTest {
    private val fs = FakeFileSystem()

    @Test
    fun `free path is returned as is`() {
        assertEquals(p("a/x.jpg"), freeTarget(fs, p("a/x.jpg")) { "$it" })
    }

    @Test
    fun `taken path gets the first free numbered label`() {
        fs.add(p("a/x.jpg"), bytes(1, 1))
        fs.add(p("a/x (1).jpg"), bytes(1, 2))
        assertEquals(p("a/x (2).jpg"), freeTarget(fs, p("a/x.jpg")) { "$it" })
    }

    @Test
    fun `name without extension gets no dot`() {
        fs.add(p("a/x"), bytes(1, 1))
        assertEquals(p("a/x (1)"), freeTarget(fs, p("a/x")) { "$it" })
    }

    @Test
    fun `label decides the wording`() {
        fs.add(p("a/x.jpg"), bytes(1, 1))
        assertEquals(p("a/x (восстановлено).jpg"), freeTarget(fs, p("a/x.jpg")) { if (it == 1) "восстановлено" else "восстановлено $it" })
    }
}
