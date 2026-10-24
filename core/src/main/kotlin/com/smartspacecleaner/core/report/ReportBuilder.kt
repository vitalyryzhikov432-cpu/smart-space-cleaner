package com.smartspacecleaner.core.report

import com.smartspacecleaner.core.action.ExecutionResult
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.GroupKind
import com.smartspacecleaner.core.model.Plan
import java.time.Instant

enum class CacheOutcome { NOT_REQUESTED, SYSTEM_CLEARED, DECLINED, FALLBACK_OPENED }

data class ReportLine(val groupTitle: String, val count: Int, val bytes: Long)

data class FailedLine(val path: String, val reason: String, val manualHint: String)

data class Report(
    val freeBefore: Long,
    val freeAfter: Long,
    val done: List<ReportLine>,
    val failed: List<FailedLine>,
    val notSelected: List<String>,
    val inTrashBytes: Long,
    val trashFreesOn: Instant?,
    val cacheNote: String?,
) {
    val freedNow: Long get() = (freeAfter - freeBefore).coerceAtLeast(0)
}

object ReportBuilder {
    fun build(
        freeBefore: Long,
        freeAfter: Long,
        plan: Plan,
        selectedPaths: Set<String>,
        cacheOutcome: CacheOutcome,
        result: ExecutionResult,
    ): Report {
        val done = result.done.groupBy { it.groupTitle }.map { (title, rs) -> ReportLine(title, rs.size, rs.sumOf { it.size }) }
        val failed = result.failed.map {
            FailedLine(
                path = it.originalPath,
                reason = it.reason ?: "неизвестная причина",
                manualHint = "Если файл точно не нужен, удалите его вручную в приложении «Файлы»: ${it.originalPath}",
            )
        }
        val notSelected = plan.groups
            .filter { g -> g.kind != GroupKind.APP_CACHE && g.items.none { it.path in selectedPaths } }
            .map { it.title } +
            if (cacheOutcome == CacheOutcome.NOT_REQUESTED && plan.groups.any { it.kind == GroupKind.APP_CACHE }) listOf("Кэш приложений") else emptyList()
        val trashed = result.done.filter { it.kind == ActionKind.TRASH }
        val cacheNote = when (cacheOutcome) {
            CacheOutcome.SYSTEM_CLEARED -> "Кэш приложений очищен системой — освобождённое место уже учтено в «было → стало»"
            CacheOutcome.DECLINED -> "Очистку кэша вы отменили в системном диалоге"
            CacheOutcome.FALLBACK_OPENED -> "Этот телефон не дал очистить кэш автоматически. Откройте «Настройки → О телефоне → Хранилище» и очистите кэш там"
            CacheOutcome.NOT_REQUESTED -> null
        }
        return Report(
            freeBefore = freeBefore,
            freeAfter = freeAfter,
            done = done,
            failed = failed,
            notSelected = notSelected,
            inTrashBytes = trashed.sumOf { it.size },
            trashFreesOn = trashed.mapNotNull { it.expiresAt }.maxOrNull(),
            cacheNote = cacheNote,
        )
    }
}
