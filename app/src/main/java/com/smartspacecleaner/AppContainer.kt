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
import com.smartspacecleaner.data.AndroidApkInspector
import com.smartspacecleaner.data.AndroidFileSystem
import com.smartspacecleaner.data.AppDatabase
import com.smartspacecleaner.data.RoomActionLogStore
import com.smartspacecleaner.data.StorageInfo

/** Ручная сборка зависимостей. Стратегию дублей менять здесь. */
class AppContainer(context: Context) {
    @Suppress("DEPRECATION")
    private val storageRoot = Environment.getExternalStorageDirectory()
    private val clock = WallClock
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "smart-space.db").build()
    private val hasher: Hasher

    val layout = StorageLayout(storageRoot.path)
    val fs = AndroidFileSystem(storageRoot)
    val log: ActionLogStore = RoomActionLogStore(db.actionRecords())
    val storageInfo = StorageInfo(context)
    val duplicateStrategy = DuplicateStrategy.TRASH

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
