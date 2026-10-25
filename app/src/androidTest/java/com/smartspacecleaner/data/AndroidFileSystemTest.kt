package com.smartspacecleaner.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class AndroidFileSystemTest {
    private lateinit var root: File
    private lateinit var fs: AndroidFileSystem

    @Before
    fun setUp() {
        root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "fs-test").apply {
            deleteRecursively()
            mkdirs()
        }
        fs = AndroidFileSystem(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun write(rel: String, text: String) = File(root, rel).apply {
        parentFile!!.mkdirs()
        writeText(text)
    }

    private fun path(rel: String) = "${root.path}/$rel"

    @Test
    fun walkListsFilesAndSkipsExcludedDirectories() {
        write("DCIM/a.jpg", "a")
        write("Android/data/x/b.bin", "b")
        write(".SmartSpaceCleaner/trash/c", "c")
        val paths = fs.walk(root.path, setOf(path("Android/data"), path(".SmartSpaceCleaner"))).map { it.path }.toList()
        assertEquals(listOf(path("DCIM/a.jpg")), paths)
    }

    @Test
    fun walkReportsSizeAndModificationTime() {
        write("x.txt", "hello").setLastModified(1_700_000_000_000)
        val entry = fs.walk(root.path, emptySet()).single()
        assertEquals(5L, entry.size)
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000), entry.modifiedAt)
    }

    @Test
    fun moveCreatesParentsAndRefusesToOverwrite() {
        write("a.txt", "1")
        write("b.txt", "2")
        assertTrue(fs.move(path("a.txt"), path("t/x/a.txt")))
        assertFalse(File(root, "a.txt").exists())
        assertEquals("1", File(root, "t/x/a.txt").readText())
        assertFalse(fs.move(path("b.txt"), path("t/x/a.txt")))
        assertEquals("2", File(root, "b.txt").readText())
    }

    @Test
    fun copyKeepsSourceAndRefusesToOverwrite() {
        write("a.txt", "1")
        assertTrue(fs.copy(path("a.txt"), path("c/a.txt")))
        assertEquals("1", File(root, "a.txt").readText())
        assertEquals("1", File(root, "c/a.txt").readText())
        assertFalse(fs.copy(path("a.txt"), path("c/a.txt")))
    }

    @Test
    fun deleteExistsAndEmptyFile() {
        write("a.txt", "1")
        assertTrue(fs.exists(path("a.txt")))
        assertTrue(fs.delete(path("a.txt")))
        assertFalse(fs.exists(path("a.txt")))
        assertTrue(fs.mkdirs(path("d/e")))
        assertTrue(fs.createEmptyFile(path("d/e/.nomedia")))
        assertTrue(fs.createEmptyFile(path("d/e/.nomedia")))
        assertEquals("", fs.openRead(path("d/e/.nomedia")).use { it.readBytes().decodeToString() })
    }
}
