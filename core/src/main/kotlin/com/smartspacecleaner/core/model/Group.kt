package com.smartspacecleaner.core.model

import java.time.Instant

enum class RiskLevel(val number: Int) { SAFE(0), EXACT(1), PERSONAL(2) }

enum class GroupKind { APP_CACHE, DUPLICATES, APK_INSTALLED, APK_UNKNOWN, OLD_SCREENSHOTS, LARGE_FILES, MESSENGER_MEDIA }

data class PlannedItem(
    val path: String,
    val size: Long,
    val modifiedAt: Instant,
    /** Для дублей: путь к копии, которая остаётся. */
    val keptOriginalPath: String? = null,
    /** Для дублей: SHA-256 содержимого на момент скана. */
    val sha256: String? = null,
)

data class Group(
    val id: String,
    val kind: GroupKind,
    val level: RiskLevel,
    val title: String,
    val willRemove: String,
    val willKeep: String,
    val why: String,
    val items: List<PlannedItem>,
    val selectedByDefault: Boolean,
) {
    val totalBytes: Long get() = items.sumOf { it.size }
}

data class Plan(val groups: List<Group>, val scannedFiles: Int, val scannedBytes: Long) {
    fun defaultSelection(): Set<String> =
        groups.filter { it.selectedByDefault }.flatMapTo(mutableSetOf()) { g -> g.items.map { it.path } }

    fun selectedBytes(selected: Set<String>): Long =
        groups.flatMap { it.items }.filter { it.path in selected }.sumOf { it.size }
}
