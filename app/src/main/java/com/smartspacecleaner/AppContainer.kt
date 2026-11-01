package com.smartspacecleaner

import android.content.Context
import android.os.Environment
import androidx.room.Room
import com.smartspacecleaner.core.action.HistoryCleaner
import com.smartspacecleaner.core.action.DuplicateStrategy
import com.smartspacecleaner.core.action.Executor
import com.smartspacecleaner.core.action.PendingReconciler
import com.smartspacecleaner.core.action.Restorer
import com.smartspacecleaner.core.action.TrashPurger
import com.smartspacecleaner.core.port.ActionLogStore
import com.smartspacecleaner.core.port.WallClock
import com.smartspacecleaner.core.scan.ApkRule
import com.smartspacecleaner.core.scan.DuplicateFinder
import com.smartspacecleaner.core.scan.Hasher
import com.smartspacecleaner.core.scan.KeepCopyPolicy
import com.smartspacecleaner.core.scan.LargeFileRule
import com.smartspacecleaner.core.scan.MessengerMediaRule
import com.smartspacecleaner.core.scan.OldScreenshotRule
import com.smartspacecleaner.core.scan.Planner
import com.smartspacecleaner.core.scan.Scanner
import com.smartspacecleaner.core.scan.StorageLayout
import com.smartspacecleaner.core.sort.PhotoClassifier
import com.smartspacecleaner.core.sort.PhotoSources
import com.smartspacecleaner.core.sort.SortAnalysis
import com.smartspacecleaner.core.sort.SortExecutor
import com.smartspacecleaner.core.sort.SortRestorer
import com.smartspacecleaner.data.AndroidApkInspector
import com.smartspacecleaner.data.AndroidFileSystem
import com.smartspacecleaner.data.AppDatabase
import com.smartspacecleaner.data.MlKitPhotoAnalyzer
import com.smartspacecleaner.data.RoomActionLogStore
import com.smartspacecleaner.data.StorageInfo
import com.smartspacecleaner.data.UiPrefs
import com.smartspacecleaner.feedback.FeedbackSender

/** Ручная сборка зависимостей. Стратегию дублей менять здесь. */
class AppContainer(context: Context) {
    @Suppress("DEPRECATION")
    private val storageRoot = Environment.getExternalStorageDirectory()
    private val clock = WallClock
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "smart-space.db")
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .build()
    private val hasher: Hasher

    val layout = StorageLayout(storageRoot.path)
    val fs = AndroidFileSystem(storageRoot)
    val log: ActionLogStore = RoomActionLogStore(db.actionRecords())
    val storageInfo = StorageInfo(context)
    val prefs = UiPrefs(context)
    val feedback = FeedbackSender(context)
    val duplicateStrategy = DuplicateStrategy.TRASH
    val photoSources = PhotoSources(fs, layout)
    // Модели ML Kit грузятся в память только при первой сортировке.
    val sortAnalysis: SortAnalysis by lazy { SortAnalysis(PhotoClassifier(layout, MlKitPhotoAnalyzer())) }
    val sortExecutor = SortExecutor(fs, log, clock, layout)
    val sortRestorer = SortRestorer(fs, log, layout)

    val scanner: Scanner
    val executor: Executor
    val restorer: Restorer
    val purger: TrashPurger
    val historyCleaner: HistoryCleaner
    val reconciler: PendingReconciler

    init {
        hasher = Hasher(fs)
        val planner = Planner(
            layout,
            KeepCopyPolicy(layout),
            DuplicateFinder(hasher),
            ApkRule(AndroidApkInspector(context.packageManager)),
            OldScreenshotRule(layout, clock),
            LargeFileRule(),
            MessengerMediaRule(layout, clock),
        )
        scanner = Scanner(fs, layout, planner)
        executor = Executor(fs, log, clock, hasher, layout, duplicateStrategy)
        restorer = Restorer(fs, log, hasher)
        purger = TrashPurger(fs, log, clock)
        historyCleaner = HistoryCleaner(log, clock)
        reconciler = PendingReconciler(fs, log)
    }
}
