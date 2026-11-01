package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.text.PreviewKind
import com.smartspacecleaner.core.text.previewKind

/** Какие файлы участвуют в раскладке: фото анализируются, видео идут в «Видео» без анализа. */
object SortMedia {
    private val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")

    private fun extension(path: String) = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()

    fun isPhoto(path: String): Boolean = extension(path) in PHOTO_EXTENSIONS
    fun isVideo(path: String): Boolean = previewKind(path) == PreviewKind.VIDEO
    fun isSortable(path: String): Boolean = isPhoto(path) || isVideo(path)
}
