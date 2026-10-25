package com.smartspacecleaner.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager

/** Те же цифры, что показывает системный экран «Хранилище». */
class StorageInfo(context: Context) {
    private val stats = context.getSystemService(StorageStatsManager::class.java)

    /**
     * Реально доступное место, как «Доступно» в MIUI. getFreeBytes() сюда не подходит:
     * он заранее засчитывает свободным кэш, который система может удалить (на Redmi +0,4 ГБ).
     */
    fun freeBytes(): Long = Environment.getDataDirectory().usableSpace
    fun totalBytes(): Long = stats.getTotalBytes(StorageManager.UUID_DEFAULT)
}
