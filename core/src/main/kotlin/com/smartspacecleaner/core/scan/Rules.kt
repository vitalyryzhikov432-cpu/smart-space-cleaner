package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.port.ApkInspector
import com.smartspacecleaner.core.port.ApkStatus
import com.smartspacecleaner.core.port.Clock
import java.time.Duration

class ApkRule(private val inspector: ApkInspector) {
    data class Result(val installed: List<FileEntry>, val unknown: List<FileEntry>)

    fun classify(entries: List<FileEntry>): Result {
        val (installed, unknown) = entries
            .filter { it.extension == "apk" }
            .partition { inspector.inspect(it.path) == ApkStatus.INSTALLED_SAME_OR_NEWER }
        return Result(installed, unknown)
    }
}

class OldScreenshotRule(
    private val layout: StorageLayout,
    private val clock: Clock,
    val maxAgeDays: Long = 180,
) {
    fun find(entries: List<FileEntry>): List<FileEntry> {
        val cutoff = clock.now().minus(Duration.ofDays(maxAgeDays))
        return entries.filter {
            (layout.isUnder(it.path, "DCIM/Screenshots") || layout.isUnder(it.path, "Pictures/Screenshots")) &&
                it.modifiedAt.isBefore(cutoff)
        }
    }
}

enum class LargeKind(val title: String) {
    VIDEO("Крупные видео"),
    ARCHIVE("Крупные архивы"),
    OTHER("Другие крупные файлы"),
}

class LargeFileRule(val minSizeBytes: Long = 100L * 1024 * 1024) {
    fun find(entries: List<FileEntry>): Map<LargeKind, List<FileEntry>> =
        entries.filter { it.size >= minSizeBytes }.groupBy { kindOf(it) }

    private fun kindOf(entry: FileEntry): LargeKind = when (entry.extension) {
        in VIDEO_EXTENSIONS -> LargeKind.VIDEO
        in ARCHIVE_EXTENSIONS -> LargeKind.ARCHIVE
        else -> LargeKind.OTHER
    }

    private companion object {
        val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "avi", "3gp", "webm")
        val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz")
    }
}

class MessengerMediaRule(
    private val layout: StorageLayout,
    private val clock: Clock,
    val maxAgeDays: Long = 90,
) {
    fun find(entries: List<FileEntry>): Map<Messenger, List<FileEntry>> {
        val cutoff = clock.now().minus(Duration.ofDays(maxAgeDays))
        return entries
            .filter { it.modifiedAt.isBefore(cutoff) }
            .mapNotNull { e -> layout.messengerOf(e.path)?.let { it to e } }
            .groupBy({ it.first }, { it.second })
    }
}
