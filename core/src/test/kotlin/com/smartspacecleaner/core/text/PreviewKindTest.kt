package com.smartspacecleaner.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PreviewKindTest {
    @Test
    fun `classifies files by extension case-insensitively`() {
        assertEquals(PreviewKind.IMAGE, previewKind("/storage/emulated/0/DCIM/a.JPG"))
        assertEquals(PreviewKind.IMAGE, previewKind("/x/photo.heif"))
        assertEquals(PreviewKind.VIDEO, previewKind("/x/clip.mp4"))
        assertEquals(PreviewKind.PDF, previewKind("/x/ozonbank_document_2024.pdf"))
        assertEquals(PreviewKind.OTHER, previewKind("/x/setup.apk"))
        assertEquals(PreviewKind.OTHER, previewKind("/x/noext"))
    }

    @Test
    fun `badge label is the upper-cased extension, capped at four chars`() {
        assertEquals("PDF", extensionLabel("/x/a.pdf"))
        assertEquals("DOCX", extensionLabel("/x/a.docx"))
        assertEquals("XLSX", extensionLabel("/x/Рыжиков зима 2018г (1).xlsx"))
        assertEquals("TORR", extensionLabel("/x/a.torrent"))
        assertEquals("", extensionLabel("/x/noext"))
        assertEquals("", extensionLabel("/x.dir/noext"))
    }
}
