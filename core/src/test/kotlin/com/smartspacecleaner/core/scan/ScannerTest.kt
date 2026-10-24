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
}
