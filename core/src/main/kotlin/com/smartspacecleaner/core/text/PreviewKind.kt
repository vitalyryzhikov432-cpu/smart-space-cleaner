package com.smartspacecleaner.core.text

enum class PreviewKind { IMAGE, VIDEO, PDF, OTHER }

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "avif", "dng")
private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "3gp", "webm", "avi")

private fun extension(path: String): String =
    path.substringAfterLast('/').substringAfterLast('.', "").lowercase()

/** Как рисовать превью файла в плане: по картинке/кадру/первой странице или значком. */
fun previewKind(path: String): PreviewKind = when (extension(path)) {
    in IMAGE_EXTENSIONS -> PreviewKind.IMAGE
    in VIDEO_EXTENSIONS -> PreviewKind.VIDEO
    "pdf" -> PreviewKind.PDF
    else -> PreviewKind.OTHER
}

/** Подпись значка для файлов без превью — расширение, ведь в обрезанном имени его не видно. */
fun extensionLabel(path: String): String = extension(path).take(4).uppercase()
