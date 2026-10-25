package com.smartspacecleaner

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartspacecleaner.core.report.CacheOutcome
import com.smartspacecleaner.ui.ExecutingScreen
import com.smartspacecleaner.ui.HistoryScreen
import com.smartspacecleaner.ui.MainViewModel
import com.smartspacecleaner.ui.PermissionScreen
import com.smartspacecleaner.ui.PlanScreen
import com.smartspacecleaner.ui.ReportScreen
import com.smartspacecleaner.ui.ScanningScreen
import com.smartspacecleaner.ui.Screen
import com.smartspacecleaner.ui.SummaryScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val clearCacheLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.onCacheResult(if (result.resultCode == RESULT_OK) CacheOutcome.SYSTEM_CLEARED else CacheOutcome.DECLINED)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val state by vm.state.collectAsStateWithLifecycle()

                LaunchedEffect(state.cacheRequestPending) {
                    if (state.cacheRequestPending) {
                        vm.consumeCacheRequest()
                        launchCacheClear()
                    }
                }
                LaunchedEffect(state.toast) {
                    state.toast?.let {
                        Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                        vm.consumeToast()
                    }
                }
                BackHandler(enabled = state.screen == Screen.Plan || state.screen == Screen.History) {
                    if (state.screen == Screen.Plan) vm.backToSummary() else vm.closeHistory()
                }

                Scaffold { padding ->
                    Box(Modifier.padding(padding)) {
                        when (state.screen) {
                            Screen.Permission -> PermissionScreen(onGrant = ::openAllFilesAccessSettings)
                            Screen.Scanning -> ScanningScreen(state.filesSeen)
                            Screen.Summary -> SummaryScreen(state.summary!!, state.scanMillis, vm::showPlan, vm::openHistory)
                            Screen.Plan -> PlanScreen(
                                plan = state.plan!!,
                                selected = state.selected,
                                clearCache = state.clearCache,
                                onToggleGroup = vm::toggleGroup,
                                onToggleItem = vm::toggleItem,
                                onToggleCache = vm::toggleCache,
                                onExecute = vm::execute,
                            )
                            Screen.Executing -> ExecutingScreen()
                            Screen.Report -> ReportScreen(state.report!!, onHistory = vm::openHistory, onDone = vm::startScan)
                            Screen.History -> HistoryScreen(state.history, vm::restore, vm::purgeNow, vm::closeHistory)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume(Environment.isExternalStorageManager())
    }

    private fun openAllFilesAccessSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    /** Системная очистка кэша всех приложений; на MIUI может быть недоступна — тогда открываем «Хранилище». */
    private fun launchCacheClear() {
        val intent = Intent(StorageManager.ACTION_CLEAR_APP_CACHE)
        if (intent.resolveActivity(packageManager) != null) {
            try {
                clearCacheLauncher.launch(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // переходим к запасному пути
            } catch (e: SecurityException) {
                // переходим к запасному пути
            }
        }
        startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        vm.onCacheResult(CacheOutcome.FALLBACK_OPENED)
    }
}
