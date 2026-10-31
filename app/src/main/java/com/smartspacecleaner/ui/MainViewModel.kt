package com.smartspacecleaner.ui

import android.app.Application
import android.media.MediaScannerConnection
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartspacecleaner.SmartSpaceApp
import com.smartspacecleaner.core.action.ExecutionResult
import com.smartspacecleaner.core.action.RestoreResult
import com.smartspacecleaner.core.feedback.FeedbackCheck
import com.smartspacecleaner.core.feedback.FeedbackDraft
import com.smartspacecleaner.core.feedback.FeedbackPromptPolicy
import com.smartspacecleaner.core.feedback.FeedbackSource
import com.smartspacecleaner.core.feedback.FeedbackValidator
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
import com.smartspacecleaner.core.sort.SortCategory
import com.smartspacecleaner.core.sort.SortPlan
import com.smartspacecleaner.core.sort.SortSection
import com.smartspacecleaner.core.sort.SortTexts
import com.smartspacecleaner.core.sort.SourceFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Screen { Onboarding, Permission, Scanning, Summary, Plan, Executing, Report, History, SortSources, SortAnalyzing, SortPlan, SortMoving, SortResult }

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
    val feedback: FeedbackSource? = null,
    val feedbackName: String = "",
    /** null — список папок ещё собирается. */
    val sortSources: List<SourceFolder>? = null,
    val sortPicked: Set<String> = emptySet(),
    val sortDone: Int = 0,
    val sortTotal: Int = 0,
    val sortPlan: SortPlan? = null,
    val sortSelected: Set<String> = emptySet(),
    /** Путь «Разложить сразу»: анализ закончен, ждём ответа в диалоге. */
    val sortQuickConfirm: Boolean = false,
    val sortResult: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as SmartSpaceApp).container
    private val _state = MutableStateFlow(
        UiState(
            screen = if (c.prefs.onboardingDone) Screen.Permission else Screen.Onboarding,
            dismissedHints = c.prefs.dismissedHints,
            feedbackName = c.prefs.feedbackName,
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

    val feedbackEnabled: Boolean get() = c.feedback.enabled

    fun openFeedback() = _state.update { it.copy(feedback = FeedbackSource.MENU) }
    fun feedbackLater() = _state.update { it.copy(feedback = null) }

    fun feedbackOptOut() {
        c.prefs.feedbackOptOut = true
        _state.update { it.copy(feedback = null) }
    }

    fun sendFeedback(draft: FeedbackDraft) {
        val feedback = (FeedbackValidator.check(draft) as? FeedbackCheck.Ok)?.feedback ?: return
        val online = c.feedback.send(feedback, _state.value.feedback ?: FeedbackSource.MENU)
        c.prefs.feedbackSent = true
        c.prefs.feedbackName = feedback.name
        val message = if (online) "Спасибо! Отзыв отправлен" else "Спасибо! Отправим, когда появится интернет"
        _state.update { it.copy(feedback = null, feedbackName = feedback.name, toast = message) }
    }

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
        val ask = c.feedback.enabled && FeedbackPromptPolicy.shouldPrompt(
            cleanedSomething = FeedbackPromptPolicy.cleanedSomething(report),
            alreadySent = c.prefs.feedbackSent,
            optedOut = c.prefs.feedbackOptOut,
        )
        _state.update { it.copy(screen = Screen.Report, report = report, feedback = if (ask) FeedbackSource.AFTER_CLEANUP else null) }
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

    fun restoreSort(sessionId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { c.sortRestorer.restoreSession(sessionId) }
            scanMedia(result.touchedPaths)
            _state.update { it.copy(history = loadHistory(), toast = SortTexts.restored(result)) }
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

    private var sortJob: Job? = null

    fun openSortSources() {
        _state.update { it.copy(screen = Screen.SortSources, sortSources = null, sortPicked = emptySet(), sortPlan = null, sortResult = null) }
        viewModelScope.launch {
            val sources = withContext(Dispatchers.IO) { c.photoSources.list() }
            _state.update { it.copy(sortSources = sources) }
        }
    }

    fun toggleSortSource(path: String) =
        _state.update { it.copy(sortPicked = if (path in it.sortPicked) it.sortPicked - path else it.sortPicked + path) }

    fun startSortAnalysis(quick: Boolean) {
        val s = _state.value
        val files = s.sortSources.orEmpty().filter { it.path in s.sortPicked }.flatMap { it.files }
        if (files.isEmpty()) return
        _state.update { it.copy(screen = Screen.SortAnalyzing, sortDone = 0, sortTotal = files.size, sortQuickConfirm = false) }
        sortJob = viewModelScope.launch {
            val plan = withContext(Dispatchers.IO) {
                c.sortAnalysis.run(files, onProgress = { n -> _state.update { it.copy(sortDone = n) } }, shouldStop = { !isActive })
            } ?: return@launch
            val selected = plan.defaultSelection()
            _state.update {
                when {
                    !quick -> it.copy(screen = Screen.SortPlan, sortPlan = plan, sortSelected = selected)
                    selected.isEmpty() -> it.copy(screen = Screen.SortPlan, sortPlan = plan, sortSelected = selected, toast = SortTexts.NOTHING_SURE)
                    else -> it.copy(sortPlan = plan, sortSelected = selected, sortQuickConfirm = true)
                }
            }
        }
    }

    fun cancelSortAnalysis() {
        sortJob?.cancel()
        _state.update { it.copy(screen = Screen.SortSources, sortQuickConfirm = false) }
    }

    /** «Посмотреть план» в диалоге «Разложить сразу». */
    fun reviewSortPlan() = _state.update { it.copy(screen = Screen.SortPlan, sortQuickConfirm = false) }

    fun toggleSortItem(path: String) =
        _state.update { it.copy(sortSelected = if (path in it.sortSelected) it.sortSelected - path else it.sortSelected + path) }

    fun toggleSortSection(section: SortSection) = _state.update {
        val paths = section.items.map { i -> i.path }.toSet()
        it.copy(sortSelected = if (it.sortSelected.containsAll(paths)) it.sortSelected - paths else it.sortSelected + paths)
    }

    fun reassignSort(path: String, category: SortCategory) = _state.update {
        it.copy(sortPlan = it.sortPlan?.reassign(path, category), sortSelected = it.sortSelected + path)
    }

    fun moveSorted() {
        val s = _state.value
        val plan = s.sortPlan ?: return
        val moves = plan.moves(s.sortSelected)
        if (moves.isEmpty()) return
        _state.update { it.copy(screen = Screen.SortMoving, sortQuickConfirm = false, sortDone = 0, sortTotal = moves.size) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                c.sortExecutor.execute(moves, UUID.randomUUID().toString()) { n -> _state.update { it.copy(sortDone = n) } }
            }
            scanMedia(result.touchedPaths)
            val rest = plan.without(result.moved.map { it.originalPath }.toSet())
            val folders = result.moved.map { it.groupTitle }.distinct().size
            val text = SortTexts.result(result.moved.size, folders, rest.items.size, result.failed.size)
            _state.update { it.copy(screen = Screen.SortResult, sortPlan = rest, sortResult = text) }
        }
    }

    /**
     * «Назад» из плана: к списку папок с прежними галочками, без нового поиска.
     * Если перенос в этом заходе уже был, список папок устарел — возвращаемся к итогу, откуда выход через [closeSort].
     */
    fun backToSortSources() = _state.update {
        it.copy(screen = if (it.sortResult != null) Screen.SortResult else Screen.SortSources)
    }

    /** «Разобрать оставшиеся»: план только из «Проверьте». */
    fun reviewSortLeftovers() = _state.update {
        it.copy(screen = Screen.SortPlan, sortPlan = it.sortPlan?.reviewOnly(), sortSelected = emptySet())
    }

    /** Файлы переехали — старый план очистки устарел, поэтому сканируем заново. */
    fun closeSort() = startScan()

    private fun scanMedia(paths: List<String>) {
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(getApplication(), paths.toTypedArray(), null, null)
    }
}
