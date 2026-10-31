package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.port.FileSystem
import com.smartspacecleaner.core.scan.StorageLayout

/** Папка-источник для шага «Откуда разложить»; [files] — только файлы, лежащие прямо в ней. */
data class SourceFolder(val path: String, val files: List<FileEntry>) {
    val photos: Int get() = files.count { SortMedia.isPhoto(it.path) }
    val videos: Int get() = files.count { SortMedia.isVideo(it.path) }
    val bytes: Long get() = files.sumOf { it.size }
}

class PhotoSources(private val fs: FileSystem, private val layout: StorageLayout) {
    /** Папки с фото и видео, самые тяжёлые сверху. Уже разложенное и служебное не показываем. */
    fun list(): List<SourceFolder> =
        fs.walk(layout.root, layout.excludedPrefixes + layout.sortRoot)
            // Пустые файлы — заглушки облачной галереи MIUI, их не прочитать.
            .filter { SortMedia.isSortable(it.path) && it.size > 0L && !layout.isHidden(it.path) }
            .groupBy { it.path.substringBeforeLast('/') }
            .map { (dir, files) -> SourceFolder(dir, files) }
            .sortedByDescending { it.bytes }
}
