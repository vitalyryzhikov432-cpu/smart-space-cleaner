package com.smartspacecleaner.work

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.smartspacecleaner.SmartSpaceApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Раз в сутки окончательно удаляет из корзины файлы старше 7 дней. */
class PurgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!Environment.isExternalStorageManager()) return Result.success()
        val container = (applicationContext as SmartSpaceApp).container
        // Сверку PENDING здесь не делаем: она может идти одновременно с выполнением плана
        // и затереть DONE на FAILED. Сбои разбираются при каждом запуске сканирования.
        withContext(Dispatchers.IO) { container.purger.purgeExpired() }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "trash-purge",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PurgeWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
