package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.p
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StorageLayoutTest {
    private val layout = StorageLayout(ROOT)

    @Test
    fun `knows primary media, downloads and messengers`() {
        assertTrue(layout.isPrimaryMedia(p("DCIM/Camera/a.jpg")))
        assertTrue(layout.isPrimaryMedia(p("Pictures/Album/a.jpg")))
        assertFalse(layout.isPrimaryMedia(p("DCIM/Screenshots/a.png")))
        assertTrue(layout.isDownload(p("Download/a.pdf")))
        assertEquals(Messenger.TELEGRAM, layout.messengerOf(p("Telegram/Telegram Images/a.jpg")))
        assertEquals(Messenger.TELEGRAM, layout.messengerOf(p("Android/media/org.telegram.messenger/Telegram/a.jpg")))
        assertEquals(Messenger.WHATSAPP, layout.messengerOf(p("Android/media/com.whatsapp/WhatsApp/Media/a.jpg")))
        assertNull(layout.messengerOf(p("DCIM/Camera/a.jpg")))
    }

    @Test
    fun `folder checks ignore case`() {
        assertTrue(layout.isPrimaryMedia(p("dcim/camera/a.jpg")))
        assertTrue(layout.isDownload(p("download/a.pdf")))
        assertTrue(layout.isUnder(p("dcim/screenshots/a.png"), "DCIM/Screenshots"))
    }

    @Test
    fun `isUnder does not match folder name prefixes`() {
        assertFalse(layout.isUnder(p("Downloads2/a.pdf"), "Download"))
    }

    @Test
    fun `work folder is anything outside camera, pictures, downloads and messengers`() {
        assertTrue(layout.isWorkFolder(p("Documents/project/a.psd")))
        assertTrue(layout.isWorkFolder(p("redmi6/a.jpg")))
        assertFalse(layout.isWorkFolder(p("DCIM/Screenshots/a.png")))
        assertFalse(layout.isWorkFolder(p("Download/a.pdf")))
        assertFalse(layout.isWorkFolder(p("Telegram/a.jpg")))
    }

    @Test
    fun `computes parent folder relative to root and service paths`() {
        assertEquals("DCIM/Camera", layout.parentRelative(p("DCIM/Camera/a.jpg")))
        assertEquals("", layout.parentRelative(p("a.jpg")))
        assertEquals(p(".SmartSpaceCleaner/trash"), layout.trashRoot)
        assertEquals(setOf(p("Android/data"), p("Android/obb"), p(".SmartSpaceCleaner")), layout.excludedPrefixes)
    }
}
