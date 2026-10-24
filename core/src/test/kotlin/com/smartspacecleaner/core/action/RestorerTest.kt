package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.fakes.FakeClock
import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.InMemoryActionLogStore
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.model.Group
import com.smartspacecleaner.core.model.GroupKind
import com.smartspacecleaner.core.model.Plan
import com.smartspacecleaner.core.model.PlannedItem
import com.smartspacecleaner.core.model.RiskLevel
import com.smartspacecleaner.core.scan.Hasher
import com.smartspacecleaner.core.scan.StorageLayout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RestorerTest {
    private val fs = FakeFileSystem()
    private val log = InMemoryActionLogStore()
    private val hasher = Hasher(fs)
    private val layout = StorageLayout(ROOT)
    private val restorer = Restorer(fs, log, hasher)
    private var n = 0

    private fun run(strategy: DuplicateStrategy, plan: Plan, vararg selected: String): List<ActionRecord> =
        Executor(fs, log, FakeClock(T0), hasher, layout, strategy) { "t${++n}" }
            .execute(plan, selected.toSet(), "s1").records

    private fun dupPlan(copy: String, keeper: String) = Plan(
        listOf(
            Group(
                "dup:x", GroupKind.DUPLICATES, RiskLevel.EXACT, "Дубли", "", "", "",
                listOf(PlannedItem(copy, fs.content(copy).size.toLong(), T0, keeper, hasher.full(copy))), true,
            ),
        ),
        0, 0,
    )

    private fun personalPlan(vararg paths: String) = Plan(
        listOf(
            Group(
                "screenshots", GroupKind.OLD_SCREENSHOTS, RiskLevel.PERSONAL, "Скриншоты", "", "", "",
                paths.map { PlannedItem(it, fs.content(it).size.toLong(), T0) }, false,
            ),
        ),
        0, 0,
    )

    @Test
    fun `restores trashed file to its original path`() {
        val content = bytes(3000, 1)
        fs.add(p("DCIM/Screenshots/s.png"), content)
        val record = run(DuplicateStrategy.TRASH, personalPlan(p("DCIM/Screenshots/s.png")), p("DCIM/Screenshots/s.png")).single()

        assertEquals(RestoreResult.Restored(p("DCIM/Screenshots/s.png")), restorer.restore(record.id))
        assertArrayEquals(content, fs.content(p("DCIM/Screenshots/s.png")))
        assertFalse(fs.exists(record.trashPath!!))
        assertEquals(ActionStatus.RESTORED, log.get(record.id)!!.status)
    }

    @Test
    fun `restores under a new name when the original path is taken`() {
        fs.add(p("DCIM/Screenshots/s.png"), bytes(3000, 1))
        val record = run(DuplicateStrategy.TRASH, personalPlan(p("DCIM/Screenshots/s.png")), p("DCIM/Screenshots/s.png")).single()
        fs.add(p("DCIM/Screenshots/s.png"), bytes(10, 2))

        assertEquals(RestoreResult.Restored(p("DCIM/Screenshots/s (восстановлено).png")), restorer.restore(record.id))
    }

    @Test
    fun `restores duplicate deleted with restore-copy strategy by copying the original back`() {
        fs.add(p("DCIM/Camera/x.jpg"), bytes(2048, 1))
        fs.add(p("Download/x.jpg"), bytes(2048, 1))
        val record = run(DuplicateStrategy.DELETE_WITH_RESTORE_COPY, dupPlan(p("Download/x.jpg"), p("DCIM/Camera/x.jpg")), p("Download/x.jpg")).single()
        assertFalse(fs.exists(p("Download/x.jpg")))

        assertEquals(RestoreResult.Restored(p("Download/x.jpg")), restorer.restore(record.id))
        assertEquals(record.sha256, hasher.full(p("Download/x.jpg")))
        assertTrue(fs.exists(p("DCIM/Camera/x.jpg")))
    }

    @Test
    fun `fails to restore duplicate when its original is gone`() {
        fs.add(p("DCIM/Camera/x.jpg"), bytes(2048, 1))
        fs.add(p("Download/x.jpg"), bytes(2048, 1))
        val record = run(DuplicateStrategy.DELETE_WITH_RESTORE_COPY, dupPlan(p("Download/x.jpg"), p("DCIM/Camera/x.jpg")), p("Download/x.jpg")).single()
        fs.delete(p("DCIM/Camera/x.jpg"))

        val result = restorer.restore(record.id)
        assertTrue(result is RestoreResult.Failed && result.reason.contains("оригинал"))
        assertEquals(ActionStatus.DONE, log.get(record.id)!!.status)
    }

    @Test
    fun `fails to restore duplicate when there is not enough space`() {
        fs.add(p("DCIM/Camera/x.jpg"), bytes(2048, 1))
        fs.add(p("Download/x.jpg"), bytes(2048, 1))
        val record = run(DuplicateStrategy.DELETE_WITH_RESTORE_COPY, dupPlan(p("Download/x.jpg"), p("DCIM/Camera/x.jpg")), p("Download/x.jpg")).single()
        fs.free = 100

        val result = restorer.restore(record.id)
        assertTrue(result is RestoreResult.Failed && result.reason.contains("места"))
        assertFalse(fs.exists(p("Download/x.jpg")))
    }

    @Test
    fun `restores duplicate copy even if its original was trashed in the same session`() {
        fs.add(p("DCIM/Screenshots/s.png"), bytes(2048, 1))
        fs.add(p("Download/s.png"), bytes(2048, 1))
        val dup = dupPlan(p("Download/s.png"), p("DCIM/Screenshots/s.png")).groups.single()
        val personal = personalPlan(p("DCIM/Screenshots/s.png")).groups.single()
        val records = run(DuplicateStrategy.TRASH, Plan(listOf(dup, personal), 0, 0), p("Download/s.png"), p("DCIM/Screenshots/s.png"))
        assertEquals(listOf(ActionStatus.DONE, ActionStatus.DONE), records.map { it.status })

        records.forEach { assertTrue(restorer.restore(it.id) is RestoreResult.Restored) }
        assertTrue(fs.exists(p("Download/s.png")))
        assertTrue(fs.exists(p("DCIM/Screenshots/s.png")))
    }

    @Test
    fun `cannot restore a deleted apk`() {
        fs.add(p("Download/app.apk"), bytes(3000, 1))
        val plan = Plan(
            listOf(
                Group(
                    "apk-installed", GroupKind.APK_INSTALLED, RiskLevel.EXACT, "Установщики", "", "", "",
                    listOf(PlannedItem(p("Download/app.apk"), 3000, T0)), true,
                ),
            ),
            0, 0,
        )
        val record = run(DuplicateStrategy.TRASH, plan, p("Download/app.apk")).single()

        val result = restorer.restore(record.id)
        assertTrue(result is RestoreResult.Failed && result.reason.contains("скачайте"))
    }

    @Test
    fun `cannot restore twice or after purge`() {
        fs.add(p("DCIM/Screenshots/s.png"), bytes(3000, 1))
        val record = run(DuplicateStrategy.TRASH, personalPlan(p("DCIM/Screenshots/s.png")), p("DCIM/Screenshots/s.png")).single()
        restorer.restore(record.id)
        assertTrue(restorer.restore(record.id) is RestoreResult.Failed)

        fs.add(p("DCIM/Screenshots/t.png"), bytes(3000, 2))
        val second = run(DuplicateStrategy.TRASH, personalPlan(p("DCIM/Screenshots/t.png")), p("DCIM/Screenshots/t.png")).single()
        fs.delete(second.trashPath!!)
        val result = restorer.restore(second.id)
        assertTrue(result is RestoreResult.Failed && result.reason.contains("окончательно"))
    }
}
