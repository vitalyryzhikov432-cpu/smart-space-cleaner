package com.smartspacecleaner.ui

import android.app.Application
import android.media.MediaScannerConnection
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartspacecleaner.SmartSpaceApp
import com.smartspacecleaner.core.action.ExecutionResult
import com.smartspacecleaner.core.action.RestoreResult
import com.smartspacecleaner.core.help.HintId
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.Group
import com.smartspacecleaner.core.model.Plan
import com.smartspacecleaner.core.model.toggleGroup
import com.smartspacecleaner.core.model.toggleItem
import com.smartspacecleaner.core.report.CacheOutcome
import com.smartspacecleaner.core.report.Report
import com.smartspacecleaner.core.report.ReportBuilder
import com.smartspacecleaner.core.report.Summary
import com.smartspacecleaner.core.report.SummaryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Screen { Onboarding, Permission, Scanning, Summary, Plan, Executing, Report, History }

data class UiState(
    val screen: Screen = Screen.Permission,
    val filesSeen: Int = 0,
    val scanMillis: Long = 0,
    val summary: Summary? = null,
    val plan: Plan? = null,
    val selected: Set<String> = emptySet(),
    val clearCache: Boolean = true,
    val cacheRequestPending: Boolean = false,
    val report: Report? = null,
    val history: List<ActionRecord> = emptyList(),
    val historyReturnTo: Screen = Screen.Summary,
    val toast: String? = null,
    val onboardingReturnTo: Screen? = null,
    val dismissedHints: Set<HintId> = emptySet(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as SmartSpaceApp).container
    private val _state = MutableStateFlow(
        UiState(
            screen = if (c.prefs.onboardingDone) Screen.Permission else Screen.Onboarding,
            dismissedHints = c.prefs.dismissedHints,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var freeBefore = 0L
    private var lastResult: ExecutionResult? = null

    fun onResume(hasAllFilesAccess: Boolean) {
        // Пока человек читает онбординг, экран разрешения не перебивает его.
        if (_state.value.screen == Screen.Onboarding) return
        if (!hasAllFilesAccess) {
            _state.update { it.copy(screen = Screen.Permission) }
        } else if (_state.value.screen == Screen.Permission) {
            startScan()
        }
    }

    fun openOnboarding() = _state.update { it.copy(onboardingReturnTo = it.screen, screen = Screen.Onboarding) }

    fun finishOnboarding(hasAllFilesAccess: Boolean) {
        c.prefs.onboardingDone = true
        val back = _state.value.onboardingReturnTo
        when {
            back != null -> _state.update { it.copy(screen = back, onboardingReturnTo = null) }
            hasAllFilesAccess -> startScan()
            else -> _state.update { it.copy(screen = Screen.Permission) }
        }
    }

    fun dismissHint(id: HintId) {
        val hints = _state.value.dismissedHints + id
        c.prefs.dismissedHints = hints
        _state.update { it.copy(dismissedHints = hints) }
    }

    fun resetHints() {
        c.prefs.dismissedHints = emptySet()
        _state.update { it.copy(dismissedHints = emptySet(), toast = "Подсказки снова включены") }
    }

    val feedbackEnabled: Boolean get() = false
    fun openFeedback() = Unit

    fun startScan() {
        _state.update { it.copy(screen = Screen.Scanning, filesSeen = 0, report = null) }
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val (plan, summary) = withContext(Dispatchers.IO) {
                c.reconciler.reconcile()
                c.purger.purgeExpired()
                c.historyCleaner.forgetOld()
                val plan = c.scanner.scan { n -> _state.update { s -> s.copy(filesSeen = n) } }
                plan to SummaryBuilder.build(c.storageInfo.freeBytes(), c.storageInfo.totalBytes(), plan)
            }
            _state.update {
                it.copy(
                    screen = Screen.Summary,
                    plan = plan,
                    summary = summary,
                    selected = plan.defaultSelection(),
                    clearCache = true,
                    scanMillis = System.currentTimeMillis() - started,
                )
            }
        }
    }

    fun showPlan() = _state.update { it.copy(screen = Screen.Plan) }
    fun backToSummary() = _state.update { it.copy(screen = Screen.Summary) }
    fun toggleGroup(group: Group) = _state.update { it.copy(selected = it.selected.toggleGroup(group)) }
    fun toggleItem(path: String) = _state.update { it.copy(selected = it.selected.toggleItem(path)) }
    fun toggleCache() = _state.update { it.copy(clearCache = !it.clearCache) }

    fun execute() {
        val snapshot = _state.value
        val plan = snapshot.plan ?: return
        _state.update { it.copy(screen = Screen.Executing) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                freeBefore = c.storageInfo.freeBytes()
                c.executor.execute(plan, snapshot.selected, UUID.randomUUID().toString())
            }
            lastResult = result
            scanMedia(result.done.map { it.originalPath })
            if (snapshot.clearCache) {
                _state.update { it.copy(cacheRequestPending = true) }
            } else {
                finish(CacheOutcome.NOT_REQUESTED)
            }
        }
    }

    fun consumeCacheRequest() = _state.update { it.copy(cacheRequestPending = false) }

    fun onCacheResult(outcome: CacheOutcome) {
        viewModelScope.launch { finish(outcome) }
    }

    private suspend fun finish(outcome: CacheOutcome) {
        val s = _state.value
        val result = lastResult ?: return
        val plan = s.plan ?: return
        val report = withContext(Dispatchers.IO) {
            ReportBuilder.build(freeBefore, c.storageInfo.freeBytes(), plan, s.selected, outcome, result)
        }
        _state.update { it.copy(screen = Screen.Report, report = report) }
    }

    fun openHistory() {
        val returnTo = _state.value.screen
        viewModelScope.launch {
            val history = loadHistory()
            _state.update { it.copy(screen = Screen.History, history = history, historyReturnTo = returnTo) }
        }
    }

    fun closeHistory() = _state.update { it.copy(screen = it.historyReturnTo) }

    fun restore(id: Long) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { c.restorer.restore(id) }
            val message = when (result) {
                is RestoreResult.Restored -> {
                    scanMedia(listOf(result.path))
                    "Файл возвращён: ${result.path.substringAfterLast('/')}"
                }
                is RestoreResult.Failed -> result.reason
            }
            _state.update { it.copy(history = loadHistory(), toast = message) }
        }
    }

    fun restoreAll(ids: List<Long>) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { c.restorer.restoreAll(ids) }
            val message = if (result.failed == 0) {
                "Возвращено файлов: ${result.restored}"
            } else {
                "Возвращено: ${result.restored}, не удалось: ${result.failed} — причины в списке ниже"
            }
            _state.update { it.copy(history = loadHistory(), toast = message) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            val cleared = withContext(Dispatchers.IO) { c.historyCleaner.clearEarlier() }
            _state.update { it.copy(history = loadHistory(), toast = "Записей стёрто: $cleared") }
        }
    }

    fun purgeNow(ids: List<Long>) {
        viewModelScope.launch {
            val purged = withContext(Dispatchers.IO) { c.purger.purgeNow(ids) }
            _state.update { it.copy(history = loadHistory(), toast = "Удалено окончательно: $purged") }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }

    private suspend fun loadHistory(): List<ActionRecord> = withContext(Dispatchers.IO) { c.log.all().reversed() }

    private fun scanMedia(paths: List<String>) {
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(getApplication(), paths.toTypedArray(), null, null)
    }
}
