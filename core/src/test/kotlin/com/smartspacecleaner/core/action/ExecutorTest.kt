package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.fakes.FakeClock
import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.InMemoryActionLogStore
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.model.Group
import com.smartspacecleaner.core.model.GroupKind
import com.smartspacecleaner.core.model.Plan
import com.smartspacecleaner.core.model.PlannedItem
import com.smartspacecleaner.core.model.RiskLevel
import com.smartspacecleaner.core.scan.Hasher
import com.smartspacecleaner.core.scan.StorageLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class ExecutorTest {
    private val fs = FakeFileSystem()
    private val log = InMemoryActionLogStore()
    private val clock = FakeClock(T0)
    private val layout = StorageLayout(ROOT)
    private val hasher = Hasher(fs)
    private var trashCounter = 0

    private fun executor(strategy: DuplicateStrategy = DuplicateStrategy.TRASH) =
        Executor(fs, log, clock, hasher, layout, strategy) { "t${++trashCounter}" }

    private fun dupGroup(vararg copyToKeeper: Pair<String, String>) = Group(
        "dup:test", GroupKind.DUPLICATES, RiskLevel.EXACT, "Дубли", "", "", "",
        copyToKeeper.map { (copy, keeper) ->
            PlannedItem(copy, fs.content(copy).size.toLong(), T0, keptOriginalPath = keeper, sha256 = hasher.full(copy))
        },
        true,
    )

    private fun group(id: String, kind: GroupKind, level: RiskLevel, vararg paths: String) = Group(
        id, kind, level, id, "", "", "",
        paths.map { PlannedItem(it, fs.content(it).size.toLong(), T0) },
        false,
    )

    private fun plan(vararg groups: Group) = Plan(groups.toList(), 0, 0)

    // --- Красные линии ---

    @Test
    fun `never removes the last copy even if the plan marks copies as duplicates of each other`() {
        fs.add(p("A/x.jpg"), bytes(2048, 1))
        fs.add(p("B/x.jpg"), bytes(2048, 1))
        val g = dupGroup(p("A/x.jpg") to p("B/x.jpg"), p("B/x.jpg") to p("A/x.jpg"))

        val result = executor(DuplicateStrategy.DELETE_WITH_RESTORE_COPY).execute(plan(g), setOf(p("A/x.jpg"), p("B/x.jpg")), "s1")

        assertEquals(1, result.done.size)
        assertEquals(1, result.failed.size)
        assertTrue(fs.exists(p("B/x.jpg")))
        assertTrue(result.failed[0].reason!!.contains("оригинал не найден"))
    }

    @Test
    fun `does not touch a duplicate whose content changed after the scan`() {
        fs.add(p("DCIM/Camera/x.jpg"), bytes(2048, 1))
        fs.add(p("Download/x.jpg"), bytes(2048, 1))
        val g = dupGroup(p("Download/x.jpg") to p("DCIM/Camera/x.jpg"))
        fs.write(p("Download/x.jpg"), bytes(2048, 9))

        val result = executor().execute(plan(g), setOf(p("Download/x.jpg")), "s1")

        assertEquals(ActionStatus.FAILED, result.records.single().status)
        assertTrue(result.records.single().reason!!.contains("изменился"))
        assertTrue(fs.exists(p("Download/x.jpg")))
    }

    @Test
    fun `does not touch a duplicate whose original changed after the scan`() {
        fs.add(p("DCIM/Camera/x.jpg"), bytes(2048, 1))
        fs.add(p("Download/x.jpg"), bytes(2048, 1))
        val g = dupGroup(p("Download/x.jpg") to p("DCIM/Camera/x.jpg"))
        fs.write(p("DCIM/Camera/x.jpg"), bytes(2048, 9))

        val result = executor().execute(plan(g), setOf(p("Download/x.jpg")), "s1")

        assertEquals(ActionStatus.FAILED, result.records.single().status)
        assertTrue(fs.exists(p("Download/x.jpg")))
    }

    @Test
    fun `leaves unselected files alone`() {
        fs.add(p("DCIM/Screenshots/a.png"), bytes(2048, 1))
        fs.add(p("DCIM/Screenshots/b.png"), bytes(2048, 2))
        val g = group("screenshots", GroupKind.OLD_SCREENSHOTS, RiskLevel.PERSONAL, p("DCIM/Screenshots/a.png"), p("DCIM/Screenshots/b.png"))

        val result = executor().execute(plan(g), setOf(p("DCIM/Screenshots/a.png")), "s1")

        assertEquals(listOf(p("DCIM/Screenshots/a.png")), result.records.map { it.originalPath })
        assertTrue(fs.exists(p("DCIM/Screenshots/b.png")))
    }

    // --- Стратегии и виды действий ---

    @Test
    fun `trash strategy moves duplicate to trash for 7 days`() {
        fs.add(p("DCIM/Camera/x.jpg"), bytes(2048, 1))
        fs.add(p("Download/x.jpg"), bytes(2048, 1))
        val g = dupGroup(p("Download/x.jpg") to p("DCIM/Camera/x.jpg"))

        val record = executor().execute(plan(g), setOf(p("Download/x.jpg")), "s1").records.single()

        assertEquals(ActionKind.TRASH, record.kind)
        assertEquals(ActionStatus.DONE, record.status)
        assertEquals(p(".SmartSpaceCleaner/trash/t1"), record.trashPath)
        assertEquals(T0.plus(Duration.ofDays(7)), record.expiresAt)
        assertEquals(p("DCIM/Camera/x.jpg"), record.keptOriginalPath)
        assertFalse(fs.exists(p("Download/x.jpg")))
        assertTrue(fs.exists(p(".SmartSpaceCleaner/trash/t1")))
        assertTrue(fs.exists(p(".SmartSpaceCleaner/trash/.nomedia")))
        assertEquals(record, log.get(record.id))
    }

    @Test
    fun `restore-copy strategy deletes duplicate right away`() {
        fs.add(p("DCIM/Camera/x.jpg"), bytes(2048, 1))
        fs.add(p("Download/x.jpg"), bytes(2048, 1))
        val g = dupGroup(p("Download/x.jpg") to p("DCIM/Camera/x.jpg"))

        val record = executor(DuplicateStrategy.DELETE_WITH_RESTORE_COPY).execute(plan(g), setOf(p("Download/x.jpg")), "s1").records.single()

        assertEquals(ActionKind.DELETE_DUPLICATE, record.kind)
        assertEquals(ActionStatus.DONE, record.status)
        assertEquals(null, record.trashPath)
        assertFalse(fs.exists(p("Download/x.jpg")))
        assertTrue(fs.exists(p("DCIM/Camera/x.jpg")))
    }

    @Test
    fun `installed apk is deleted right away`() {
        fs.add(p("Download/app.apk"), bytes(3000, 1))
        val g = group("apk-installed", GroupKind.APK_INSTALLED, RiskLevel.EXACT, p("Download/app.apk"))

        val record = executor().execute(plan(g), setOf(p("Download/app.apk")), "s1").records.single()

        assertEquals(ActionKind.DELETE_APK, record.kind)
        assertEquals(ActionStatus.DONE, record.status)
        assertFalse(fs.exists(p("Download/app.apk")))
    }

    @Test
    fun `personal file goes to trash`() {
        fs.add(p("Movies/big.mp4"), bytes(5000, 1))
        val g = group("large:video", GroupKind.LARGE_FILES, RiskLevel.PERSONAL, p("Movies/big.mp4"))

        val record = executor().execute(plan(g), setOf(p("Movies/big.mp4")), "s1").records.single()

        assertEquals(ActionKind.TRASH, record.kind)
        assertTrue(fs.exists(record.trashPath!!))
        assertEquals("s1", record.sessionId)
        assertEquals("large:video", record.groupId)
    }

    // --- Ошибки ---

    @Test
    fun `reports failure and keeps the file when move is refused`() {
        fs.add(p("Movies/big.mp4"), bytes(5000, 1))
        fs.failingPaths += p("Movies/big.mp4")
        val g = group("large:video", GroupKind.LARGE_FILES, RiskLevel.PERSONAL, p("Movies/big.mp4"))

        val record = executor().execute(plan(g), setOf(p("Movies/big.mp4")), "s1").records.single()

        assertEquals(ActionStatus.FAILED, record.status)
        assertTrue(record.reason!!.contains("нет доступа"))
        assertTrue(fs.exists(p("Movies/big.mp4")))
    }

    @Test
    fun `reports failure when the file is already gone`() {
        fs.add(p("Movies/big.mp4"), bytes(5000, 1))
        val g = group("large:video", GroupKind.LARGE_FILES, RiskLevel.PERSONAL, p("Movies/big.mp4"))
        fs.delete(p("Movies/big.mp4"))

        val record = executor().execute(plan(g), setOf(p("Movies/big.mp4")), "s1").records.single()

        assertEquals(ActionStatus.FAILED, record.status)
        assertTrue(record.reason!!.contains("отсутствует"))
    }

    // --- Порядок ---

    @Test
    fun `executes level 1 before level 2 and ignores app cache group`() {
        fs.add(p("DCIM/Camera/x.jpg"), bytes(2048, 1))
        fs.add(p("Download/x.jpg"), bytes(2048, 1))
        fs.add(p("Movies/big.mp4"), bytes(5000, 1))
        val personal = group("large:video", GroupKind.LARGE_FILES, RiskLevel.PERSONAL, p("Movies/big.mp4"))
        val dup = dupGroup(p("Download/x.jpg") to p("DCIM/Camera/x.jpg"))
        val cache = Group("app-cache", GroupKind.APP_CACHE, RiskLevel.SAFE, "", "", "", "", emptyList(), true)

        val result = executor().execute(plan(cache, personal, dup), setOf(p("Movies/big.mp4"), p("Download/x.jpg")), "s1")

        assertEquals(listOf("dup:test", "large:video"), result.records.map { it.groupId })
    }
}
