package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.scan.StorageLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PhotoSourcesTest {
    private val fs = FakeFileSystem()
    private val sources = PhotoSources(fs, StorageLayout(ROOT))

    @Test
    fun `groups photos and videos by the folder they lie in directly`() {
        fs.add(p("DCIM/Camera/a.jpg"), bytes(100, 1))
        fs.add(p("DCIM/Camera/v.mp4"), bytes(300, 2))
        fs.add(p("DCIM/Camera/Sub/b.jpg"), bytes(50, 3))

        val list = sources.list()

        assertEquals(listOf(p("DCIM/Camera"), p("DCIM/Camera/Sub")), list.map { it.path })
        val camera = list.first()
        assertEquals(1, camera.photos)
        assertEquals(1, camera.videos)
        assertEquals(400L, camera.bytes)
    }

    @Test
    fun `heaviest folder comes first`() {
        fs.add(p("Download/a.jpg"), bytes(10, 1))
        fs.add(p("Telegram/Telegram Images/b.jpg"), bytes(500, 2))

        assertEquals(listOf(p("Telegram/Telegram Images"), p("Download")), sources.list().map { it.path })
    }

    @Test
    fun `already sorted, hidden, trash and app data are not offered`() {
        fs.add(p("Pictures/Сортировка/Природа/a.jpg"), bytes(10, 1))
        fs.add(p("DCIM/.thumbnails/t.jpg"), bytes(10, 2))
        fs.add(p(".SmartSpaceCleaner/trash/x.jpg"), bytes(10, 3))
        fs.add(p("Android/data/com.app/cache/c.jpg"), bytes(10, 4))
        fs.add(p("Pictures/Screenshots/s.png"), bytes(10, 5))

        assertEquals(listOf(p("Pictures/Screenshots")), sources.list().map { it.path })
    }

    @Test
    fun `folders with only documents or gifs are not listed`() {
        fs.add(p("Download/doc.pdf"), bytes(10, 1))
        fs.add(p("Download/anim.gif"), bytes(10, 2))

        assertEquals(emptyList<SourceFolder>(), sources.list())
    }
}
