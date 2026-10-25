package com.smartspacecleaner.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.storage.StorageManager

/** Те же цифры, что показывает системный экран «Хранилище». */
class StorageInfo(context: Context) {
    private val stats = context.getSystemService(StorageStatsManager::class.java)

    fun freeBytes(): Long = stats.getFreeBytes(StorageManager.UUID_DEFAULT)
    fun totalBytes(): Long = stats.getTotalBytes(StorageManager.UUID_DEFAULT)
}
