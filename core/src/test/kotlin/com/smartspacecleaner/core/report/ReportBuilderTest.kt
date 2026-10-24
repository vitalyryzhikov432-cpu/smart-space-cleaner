package com.smartspacecleaner.core.report

import com.smartspacecleaner.core.action.ExecutionResult
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.model.Group
import com.smartspacecleaner.core.model.GroupKind
import com.smartspacecleaner.core.model.Plan
import com.smartspacecleaner.core.model.PlannedItem
import com.smartspacecleaner.core.model.RiskLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class ReportBuilderTest {
    private val cache = Group("app-cache", GroupKind.APP_CACHE, RiskLevel.SAFE, "Кэш приложений", "", "", "", emptyList(), true)
    private val dup = Group("dup:x", GroupKind.DUPLICATES, RiskLevel.EXACT, "Дубли в папке x", "", "", "", listOf(PlannedItem(p("x/a"), 100, T0), PlannedItem(p("x/b"), 50, T0)), true)
    private val apk = Group("apk-installed", GroupKind.APK_INSTALLED, RiskLevel.EXACT, "Установщики", "", "", "", listOf(PlannedItem(p("Download/a.apk"), 30, T0)), true)
    private val shots = Group("screenshots", GroupKind.OLD_SCREENSHOTS, RiskLevel.PERSONAL, "Скриншоты", "", "", "", listOf(PlannedItem(p("s.png"), 10, T0)), false)
    private val plan = Plan(listOf(cache, dup, apk, shots), 0, 0)

    private fun record(path: String, group: Group, kind: ActionKind, status: ActionStatus, size: Long, reason: String? = null) = ActionRecord(
        sessionId = "s", groupId = group.id, groupTitle = group.title, kind = kind, originalPath = path, size = size,
        status = status, reason = reason, createdAt = T0,
        expiresAt = if (kind == ActionKind.TRASH) T0.plus(Duration.ofDays(7)) else null,
    )

    @Test
    fun `summarises done, failed, unselected and trash`() {
        val result = ExecutionResult(
            "s",
            listOf(
                record(p("x/a"), dup, ActionKind.TRASH, ActionStatus.DONE, 100),
                record(p("x/b"), dup, ActionKind.TRASH, ActionStatus.FAILED, 50, "файл изменился после сканирования — не трогаю"),
                record(p("Download/a.apk"), apk, ActionKind.DELETE_APK, ActionStatus.DONE, 30),
            ),
        )

        val report = ReportBuilder.build(1000, 1030, plan, setOf(p("x/a"), p("x/b"), p("Download/a.apk")), CacheOutcome.SYSTEM_CLEARED, result)

        assertEquals(30L, report.freedNow)
        assertEquals(listOf(ReportLine("Дубли в папке x", 1, 100), ReportLine("Установщики", 1, 30)), report.done)
        assertEquals(p("x/b"), report.failed.single().path)
        assertTrue(report.failed.single().manualHint.contains("«Файлы»"))
        assertEquals(listOf("Скриншоты"), report.notSelected)
        assertEquals(100L, report.inTrashBytes)
        assertEquals(T0.plus(Duration.ofDays(7)), report.trashFreesOn)
        assertTrue(report.cacheNote!!.contains("очищен системой"))
    }

    @Test
    fun `mentions cache as not selected and never reports negative freed space`() {
        val report = ReportBuilder.build(1000, 900, plan, emptySet(), CacheOutcome.NOT_REQUESTED, ExecutionResult("s", emptyList()))
        assertEquals(0L, report.freedNow)
        assertEquals(listOf("Дубли в папке x", "Установщики", "Скриншоты", "Кэш приложений"), report.notSelected)
        assertNull(report.cacheNote)
        assertNull(report.trashFreesOn)
    }

    @Test
    fun `explains the manual way when system cache cleaning is unavailable`() {
        val report = ReportBuilder.build(0, 0, plan, emptySet(), CacheOutcome.FALLBACK_OPENED, ExecutionResult("s", emptyList()))
        assertTrue(report.cacheNote!!.contains("Хранилище"))
    }
}
