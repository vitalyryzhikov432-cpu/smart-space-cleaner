package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.fakes.FakeApkInspector
import com.smartspacecleaner.core.fakes.FakeClock
import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.model.GroupKind
import com.smartspacecleaner.core.model.RiskLevel
import com.smartspacecleaner.core.port.ApkStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class PlannerTest {
    private val fs = FakeFileSystem()
    private val clock = FakeClock(T0)
    private val layout = StorageLayout(ROOT)
    private var apkStatuses = mapOf<String, ApkStatus>()
    private fun planner() = Planner(
        layout,
        KeepCopyPolicy(layout),
        DuplicateFinder(Hasher(fs)),
        ApkRule(FakeApkInspector(apkStatuses)),
        OldScreenshotRule(layout, clock),
        LargeFileRule(),
        MessengerMediaRule(layout, clock),
    )
    private fun entries() = fs.walk(ROOT, layout.excludedPrefixes).toList()
    private fun daysAgo(days: Long) = T0.minus(Duration.ofDays(days))

    @Test
    fun `always offers app cache group first`() {
        val plan = planner().plan(emptyList())
        assertEquals(listOf("app-cache"), plan.groups.map { it.id })
        val cache = plan.groups.single()
        assertEquals(GroupKind.APP_CACHE, cache.kind)
        assertEquals(RiskLevel.SAFE, cache.level)
        assertTrue(cache.selectedByDefault)
        assertTrue(cache.items.isEmpty())
    }

    @Test
    fun `groups duplicate copies by folder and keeps camera original`() {
        fs.add(p("DCIM/Camera/IMG_1.jpg"), bytes(4096, 1))
        fs.add(p("redmi6/IMG_1.jpg"), bytes(4096, 1))
        fs.add(p("Download/IMG_1(1).jpg"), bytes(4096, 1))
        val plan = planner().plan(entries())

        val redmi = plan.groups.single { it.id == "dup:redmi6" }
        assertEquals(RiskLevel.EXACT, redmi.level)
        assertTrue(redmi.selectedByDefault)
        assertEquals(listOf(p("redmi6/IMG_1.jpg")), redmi.items.map { it.path })
        assertEquals(p("DCIM/Camera/IMG_1.jpg"), redmi.items[0].keptOriginalPath)
        assertEquals(64, redmi.items[0].sha256!!.length)
        assertEquals("Дубли в папке redmi6", redmi.title)
        assertEquals("1 копия из этой папки", redmi.willRemove)
        assertEquals("оригиналы в: DCIM/Camera", redmi.willKeep)

        assertTrue(plan.groups.any { it.id == "dup:Download" })
        assertTrue(plan.groups.flatMap { it.items }.none { it.path == p("DCIM/Camera/IMG_1.jpg") })
    }

    @Test
    fun `duplicates only inside work folders are not selected by default`() {
        fs.add(p("Documents/project-a/data.bin"), bytes(4096, 3))
        fs.add(p("Documents/project-b/data.bin"), bytes(4096, 3))
        val group = planner().plan(entries()).groups.single { it.kind == GroupKind.DUPLICATES }
        assertFalse(group.selectedByDefault)
        assertTrue(group.why.contains("рабочую"))
    }

    @Test
    fun `a file appears in at most one group`() {
        fs.add(p("DCIM/Camera/a.jpg"), bytes(4096, 2))
        fs.add(p("Telegram/Telegram Images/a.jpg"), bytes(4096, 2), daysAgo(200))
        val plan = planner().plan(entries())
        val all = plan.groups.flatMap { g -> g.items.map { it.path } }
        assertEquals(all.size, all.toSet().size)
        assertTrue(plan.groups.none { it.kind == GroupKind.MESSENGER_MEDIA })
        assertTrue(plan.groups.single { it.kind == GroupKind.DUPLICATES }.items.any { it.path == p("Telegram/Telegram Images/a.jpg") })
    }

    @Test
    fun `personal groups are never selected by default`() {
        fs.add(p("DCIM/Screenshots/old.png"), bytes(3000, 4), daysAgo(365))
        val big = FileEntry(p("Movies/big.mp4"), 200L * 1024 * 1024, T0)
        val plan = planner().plan(entries() + big)
        val personal = plan.groups.filter { it.level == RiskLevel.PERSONAL }
        assertEquals(setOf("screenshots", "large:video"), personal.map { it.id }.toSet())
        assertTrue(personal.none { it.selectedByDefault })
        assertTrue(plan.defaultSelection().none { it == p("DCIM/Screenshots/old.png") || it == big.path })
        assertEquals("Скриншоты старше 180 дней", personal.single { it.id == "screenshots" }.title)
        assertEquals("Крупные видео (от 100 МБ)", personal.single { it.id == "large:video" }.title)
    }

    @Test
    fun `installed apk is level 1 and unknown apk is level 2`() {
        fs.add(p("Download/app.apk"), bytes(3000, 5))
        fs.add(p("Download/other.apk"), bytes(3100, 6))
        apkStatuses = mapOf(p("Download/app.apk") to ApkStatus.INSTALLED_SAME_OR_NEWER)
        val plan = planner().plan(entries())
        val installed = plan.groups.single { it.id == "apk-installed" }
        val unknown = plan.groups.single { it.id == "apk-unknown" }
        assertEquals(RiskLevel.EXACT, installed.level)
        assertTrue(installed.selectedByDefault)
        assertEquals(RiskLevel.PERSONAL, unknown.level)
        assertFalse(unknown.selectedByDefault)
    }

    @Test
    fun `groups are ordered by level then by size`() {
        fs.add(p("DCIM/Camera/a.jpg"), bytes(4096, 1)); fs.add(p("x/a.jpg"), bytes(4096, 1))
        fs.add(p("DCIM/Camera/b.jpg"), bytes(8192, 2)); fs.add(p("y/b.jpg"), bytes(8192, 2))
        fs.add(p("DCIM/Screenshots/old.png"), bytes(3000, 4), daysAgo(365))
        val plan = planner().plan(entries())
        assertEquals(listOf("app-cache", "dup:y", "dup:x", "screenshots"), plan.groups.map { it.id })
        assertEquals(entries().size, plan.scannedFiles)
    }
}
