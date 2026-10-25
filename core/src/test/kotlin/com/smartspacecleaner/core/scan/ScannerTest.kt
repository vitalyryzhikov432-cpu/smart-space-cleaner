package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.fakes.FakeApkInspector
import com.smartspacecleaner.core.fakes.FakeClock
import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScannerTest {
    @Test
    fun `scans storage except excluded folders and reports progress`() {
        val fs = FakeFileSystem()
        val layout = StorageLayout(ROOT)
        val clock = FakeClock()
        fs.add(p("DCIM/Camera/a.jpg"), bytes(4096, 1))
        fs.add(p("Download/a.jpg"), bytes(4096, 1))
        fs.add(p("Android/data/com.x/cache.bin"), bytes(4096, 1))
        fs.add(p(".SmartSpaceCleaner/trash/t1"), bytes(4096, 1))
        val planner = Planner(
            layout, KeepCopyPolicy(layout), DuplicateFinder(Hasher(fs)), ApkRule(FakeApkInspector()),
            OldScreenshotRule(layout, clock), LargeFileRule(), MessengerMediaRule(layout, clock),
        )
        val progress = mutableListOf<Int>()

        val plan = Scanner(fs, layout, planner).scan { progress += it }

        assertEquals(2, plan.scannedFiles)
        assertEquals(2, progress.last())
        val dup = plan.groups.single { it.id == "dup:Download" }
        assertEquals(1, dup.items.size)
        assertTrue(plan.groups.flatMap { it.items }.none { it.path.contains("Android/data") || it.path.contains(".SmartSpaceCleaner") })
    }

    @Test
    fun `skips hidden files and hidden folders such as nomedia, gallery trash and app link caches`() {
        val fs = FakeFileSystem()
        val layout = StorageLayout(ROOT)
        val clock = FakeClock()
        fs.add(p("Download/a.jpg"), bytes(4096, 1))
        fs.add(p("DCIM/.globalTrash/.a.jpg"), bytes(4096, 1))
        fs.add(p("Android/media/com.whatsapp/WhatsApp/Media/.Links/x1"), bytes(2048, 2))
        fs.add(p("Android/media/com.whatsapp/WhatsApp/Media/.Links/x2"), bytes(2048, 2))
        fs.add(p("Telegram/Telegram Images/.nomedia"), bytes(43, 3), clock.now().minus(java.time.Duration.ofDays(400)))
        val planner = Planner(
            layout, KeepCopyPolicy(layout), DuplicateFinder(Hasher(fs)), ApkRule(FakeApkInspector()),
            OldScreenshotRule(layout, clock), LargeFileRule(), MessengerMediaRule(layout, clock),
        )

        val plan = Scanner(fs, layout, planner).scan()

        assertEquals(1, plan.scannedFiles)
        assertTrue(plan.groups.flatMap { it.items }.isEmpty())
    }
}
