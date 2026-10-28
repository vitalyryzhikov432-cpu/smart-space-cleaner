package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.fakes.FakeApkInspector
import com.smartspacecleaner.core.fakes.FakeClock
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.port.ApkStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class RulesTest {
    private val layout = StorageLayout(ROOT)
    private val clock = FakeClock(T0)
    private fun daysAgo(days: Long) = T0.minus(Duration.ofDays(days))
    private fun entry(rel: String, size: Long = 2048, days: Long = 0) = FileEntry(p(rel), size, daysAgo(days))

    @Test
    fun `apk of installed app is separated from unknown apk`() {
        val rule = ApkRule(
            FakeApkInspector(
                mapOf(
                    p("Download/app.apk") to ApkStatus.INSTALLED_SAME_OR_NEWER,
                    p("Download/old.apk") to ApkStatus.NOT_INSTALLED_OR_OLDER,
                ),
            ),
        )
        val result = rule.classify(
            listOf(entry("Download/app.apk"), entry("Download/old.apk"), entry("Download/broken.APK"), entry("Download/a.pdf")),
        )
        assertEquals(listOf(p("Download/app.apk")), result.installed.map { it.path })
        assertEquals(listOf(p("Download/old.apk"), p("Download/broken.APK")), result.unknown.map { it.path })
    }

    @Test
    fun `old screenshots are strictly older than 180 days`() {
        val rule = OldScreenshotRule(layout, clock)
        val found = rule.find(
            listOf(
                entry("DCIM/Screenshots/old.png", days = 181),
                entry("Pictures/Screenshots/old2.png", days = 400),
                entry("DCIM/Screenshots/edge.png", days = 180),
                entry("DCIM/Camera/old.jpg", days = 400),
            ),
        )
        assertEquals(listOf(p("DCIM/Screenshots/old.png"), p("Pictures/Screenshots/old2.png")), found.map { it.path })
        assertEquals(180L, rule.maxAgeDays)
    }

    @Test
    fun `old screenshot rule ignores folder case`() {
        val found = OldScreenshotRule(layout, clock).find(listOf(entry("dcim/screenshots/old.png", days = 200)))
        assertEquals(1, found.size)
    }

    @Test
    fun `large files start at exactly 100 MB and are split by kind`() {
        val mb100 = 100_000_000L
        val found = LargeFileRule().find(
            listOf(
                entry("Movies/a.MP4", size = mb100),
                entry("Download/b.zip", size = mb100 * 2),
                entry("Download/c.iso", size = mb100 + 1),
                entry("Movies/small.mp4", size = mb100 - 1),
            ),
        )
        assertEquals(listOf(p("Movies/a.MP4")), found[LargeKind.VIDEO]!!.map { it.path })
        assertEquals(listOf(p("Download/b.zip")), found[LargeKind.ARCHIVE]!!.map { it.path })
        assertEquals(listOf(p("Download/c.iso")), found[LargeKind.OTHER]!!.map { it.path })
    }

    @Test
    fun `messenger media older than 90 days is grouped by messenger`() {
        val found = MessengerMediaRule(layout, clock).find(
            listOf(
                entry("Telegram/Telegram Images/a.jpg", days = 100),
                entry("Android/media/com.whatsapp/WhatsApp/Media/b.jpg", days = 91),
                entry("Android/media/com.whatsapp/WhatsApp/Media/fresh.jpg", days = 89),
                entry("DCIM/Camera/c.jpg", days = 500),
            ),
        )
        assertEquals(listOf(p("Telegram/Telegram Images/a.jpg")), found[Messenger.TELEGRAM]!!.map { it.path })
        assertEquals(listOf(p("Android/media/com.whatsapp/WhatsApp/Media/b.jpg")), found[Messenger.WHATSAPP]!!.map { it.path })
        assertTrue(found.values.flatten().none { it.path.endsWith("fresh.jpg") || it.path.endsWith("c.jpg") })
    }
}
