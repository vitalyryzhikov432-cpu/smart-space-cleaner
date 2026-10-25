package com.smartspacecleaner.data

import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import com.smartspacecleaner.core.report.FileFacts
import com.smartspacecleaner.core.text.PreviewKind
import com.smartspacecleaner.core.text.previewKind
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Читает сведения о файле для окна «Сведения»; [contentPath] — где лежит содержимое, [originalPath] — откуда он. */
object FileFactsReader {
    private val EXIF_DATE = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    private val VIDEO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun read(contentPath: String, originalPath: String, inTrashUntil: Instant?): FileFacts? {
        val file = File(contentPath)
        if (!file.exists()) return null
        var takenAt: Instant? = null
        var width: Int? = null
        var height: Int? = null
        var durationMs: Long? = null
        try {
            when (previewKind(originalPath)) {
                PreviewKind.IMAGE -> {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(contentPath, bounds)
                    if (bounds.outWidth > 0) {
                        width = bounds.outWidth
                        height = bounds.outHeight
                    }
                    takenAt = ExifInterface(contentPath).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?.let { LocalDateTime.parse(it, EXIF_DATE).atZone(ZoneId.systemDefault()).toInstant() }
                }
                PreviewKind.VIDEO -> MediaMetadataRetriever().apply {
                    try {
                        setDataSource(contentPath)
                        width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                        height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                        durationMs = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        // Формат вида 20261025T122900.000Z; у части видео даты нет или она нулевая.
                        takenAt = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                            ?.take(15)?.let { LocalDateTime.parse(it, VIDEO_DATE).atZone(ZoneId.of("UTC")).toInstant() }
                            ?.takeIf { it.isAfter(Instant.parse("1971-01-01T00:00:00Z")) }
                    } finally {
                        release()
                    }
                }
                else -> Unit
            }
        } catch (e: Exception) {
            // Нет или битые метаданные — покажем то, что есть: размер и дату изменения.
        }
        return FileFacts(
            originalPath = originalPath,
            size = file.length(),
            takenAt = takenAt,
            modifiedAt = Instant.ofEpochMilli(file.lastModified()),
            width = width,
            height = height,
            durationMs = durationMs,
            inTrashUntil = inTrashUntil,
        )
    }
}
