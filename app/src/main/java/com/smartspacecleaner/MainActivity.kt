package com.smartspacecleaner

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.MimeTypeMap
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
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.report.CacheOutcome
import com.smartspacecleaner.core.report.FileDetails
import com.smartspacecleaner.data.FileFactsReader
import com.smartspacecleaner.core.report.HistoryPreview
import com.smartspacecleaner.ui.ExecutingScreen
import com.smartspacecleaner.ui.HistoryScreen
import com.smartspacecleaner.ui.MainViewModel
import com.smartspacecleaner.ui.PermissionScreen
import com.smartspacecleaner.ui.PlanScreen
import com.smartspacecleaner.ui.ReportScreen
import com.smartspacecleaner.ui.ScanningScreen
import com.smartspacecleaner.ui.Screen
import com.smartspacecleaner.ui.SummaryScreen
import java.io.File
import java.time.ZoneId

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
                                onOpenFile = ::openFile,
                                onExecute = vm::execute,
                            )
                            Screen.Executing -> ExecutingScreen()
                            Screen.Report -> ReportScreen(state.report!!, onHistory = vm::openHistory, onDone = vm::startScan)
                            Screen.History -> HistoryScreen(
                                records = state.history,
                                onRestore = vm::restore,
                                onPurge = vm::purgeNow,
                                onRestoreMany = vm::restoreAll,
                                onClearEarlier = vm::clearHistory,
                                onOpen = ::openRecord,
                                readDetails = ::readDetails,
                                onBack = vm::closeHistory,
                            )
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

    /** Открывает файл из «Истории» в программе телефона только для просмотра; из корзины он не переносится. */
    private fun openRecord(record: ActionRecord) {
        val path = HistoryPreview.contentPath(record) ?: return
        openFile(path, record.originalPath)
    }

    /** [path] — где лежит содержимое, [name] — имя, по расширению которого выбирается программа. */
    private fun openFile(path: String, name: String) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "Файла уже нет", Toast.LENGTH_LONG).show()
            return
        }
        val extension = name.substringAfterLast('/').substringAfterLast('.', "").lowercase()
        val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        // Через каталог медиафайлов Галерея знает файл и заполняет «Сведения»; файлов из корзины в нём нет.
        val uri = mediaStoreUri(path) ?: FileProvider.getUriForFile(this, "$packageName.files", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Нет приложения, чтобы открыть этот файл", Toast.LENGTH_LONG).show()
        }
    }

    private fun mediaStoreUri(path: String): Uri? = try {
        contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE),
            "${MediaStore.Files.FileColumns.DATA} = ?",
            arrayOf(path),
            null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            val id = c.getLong(0)
            val base = when (c.getInt(1)) {
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Files.getContentUri("external")
            }
            ContentUris.withAppendedId(base, id)
        }
    } catch (e: Exception) {
        null
    }

    private fun readDetails(record: ActionRecord): List<Pair<String, String>>? {
        val path = HistoryPreview.contentPath(record) ?: return null
        val inTrashUntil = record.expiresAt.takeIf { record.status == ActionStatus.DONE && record.kind == ActionKind.TRASH }
        val facts = FileFactsReader.read(path, record.originalPath, inTrashUntil) ?: return null
        return FileDetails.lines(facts, ZoneId.systemDefault())
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
