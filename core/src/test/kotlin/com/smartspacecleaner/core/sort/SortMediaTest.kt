package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.fakes.p
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SortMediaTest {
    @Test
    fun `photo extensions from the spec are photos, case does not matter`() {
        listOf("a.jpg", "a.JPEG", "a.png", "a.webp", "a.HEIC", "a.heif").forEach { assertTrue(SortMedia.isPhoto(p("DCIM/$it")), it) }
    }

    @Test
    fun `videos are recognised by extension`() {
        listOf("v.mp4", "v.MKV", "v.mov", "v.3gp", "v.webm", "v.avi").forEach { assertTrue(SortMedia.isVideo(p("DCIM/$it")), it) }
    }

    @Test
    fun `gif, raw and documents are not sorted`() {
        listOf("a.gif", "a.dng", "a.bmp", "a.pdf", "a.apk", "noext").forEach { assertFalse(SortMedia.isSortable(p("Download/$it")), it) }
    }
}
