package com.smartspacecleaner.core.report

import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.Group
import com.smartspacecleaner.core.model.GroupKind
import com.smartspacecleaner.core.model.Plan
import com.smartspacecleaner.core.model.PlannedItem
import com.smartspacecleaner.core.model.RiskLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SummaryBuilderTest {
    private val gb = 1024L * 1024 * 1024
    private val cache = Group("app-cache", GroupKind.APP_CACHE, RiskLevel.SAFE, "Кэш приложений", "", "", "", emptyList(), true)
    private fun group(id: String, title: String, bytes: Long) =
        Group(id, GroupKind.DUPLICATES, RiskLevel.EXACT, title, "", "", "", listOf(PlannedItem(p(id), bytes, T0)), true)

    @Test
    fun `severity follows free space percentage`() {
        val empty = Plan(listOf(cache), 0, 0)
        assertEquals(Severity.CRITICAL, SummaryBuilder.build(4, 100, empty).severity)
        assertEquals(Severity.ATTENTION, SummaryBuilder.build(5, 100, empty).severity)
        assertEquals(Severity.ATTENTION, SummaryBuilder.build(15, 100, empty).severity)
        assertEquals(Severity.OK, SummaryBuilder.build(16, 100, empty).severity)
    }

    @Test
    fun `lists free space and the three biggest groups`() {
        val plan = Plan(
            listOf(
                cache,
                group("a", "Дубли в папке a", 1 * gb),
                group("b", "Дубли в папке b", 3 * gb),
                group("c", "Дубли в папке c", 2 * gb),
                group("d", "Дубли в папке d", gb / 2),
            ),
            0, 0,
        )
        val summary = SummaryBuilder.build(gb, 128 * gb, plan)
        assertEquals(
            listOf(
                "Свободно 1,0 ГБ из 128,0 ГБ",
                "Дубли в папке b — 3,0 ГБ",
                "Дубли в папке c — 2,0 ГБ",
                "Дубли в папке a — 1,0 ГБ",
            ),
            summary.lines,
        )
    }

    @Test
    fun `says so when nothing was found`() {
        val summary = SummaryBuilder.build(50 * gb, 128 * gb, Plan(listOf(cache), 10, 100))
        assertEquals("Ничего заметного для уборки не нашлось — можно только очистить кэш приложений", summary.lines.last())
    }
}
