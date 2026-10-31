package com.smartspacecleaner.data

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.smartspacecleaner.core.sort.Analysis
import com.smartspacecleaner.core.sort.Label
import com.smartspacecleaner.core.sort.PhotoAnalyzer
import com.smartspacecleaner.core.sort.PhotoSignals
import java.io.File
import java.util.Locale

/** Смотрит фото тремя встроенными моделями ML Kit. Вызывать не из главного потока: ждёт результатов синхронно. */
class MlKitPhotoAnalyzer : PhotoAnalyzer {
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.5f).build())
    private val texts = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val faces = FaceDetection.getClient(
        FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build(),
    )

    override fun analyze(path: String): Analysis {
        val bitmap = decode(path) ?: return Analysis.Unreadable
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val area = (bitmap.width.toFloat() * bitmap.height).coerceAtLeast(1f)
            val text = Tasks.await(texts.process(image))
            val textArea = text.textBlocks.sumOf { b -> b.boundingBox?.let { it.width().toLong() * it.height() } ?: 0L }
            val textChars = text.textBlocks.sumOf { b -> b.text.count { !it.isWhitespace() } }
            val largestFace = Tasks.await(faces.process(image)).maxOfOrNull { it.boundingBox.width().toLong() * it.boundingBox.height() } ?: 0L
            val labels = Tasks.await(labeler.process(image)).map { Label(it.text, it.confidence) }
            val signals = PhotoSignals(
                textAreaRatio = (textArea / area).coerceAtMost(1f),
                textChars = textChars,
                largestFaceRatio = (largestFace / area).coerceAtMost(1f),
                labels = labels,
            )
            // Временно: строка на фото для подстройки порогов на приёмке; убрать после подстройки.
            Log.d(
                TAG,
                String.format(
                    Locale.ROOT, "signals %s face=%.4f textArea=%.4f chars=%d labels=%s",
                    File(path).name, signals.largestFaceRatio, signals.textAreaRatio, signals.textChars,
                    labels.joinToString(",") { String.format(Locale.ROOT, "%s:%.2f", it.name, it.confidence) },
                ),
            )
            Analysis.Ok(signals)
        } catch (e: Exception) {
            Log.w(TAG, "analyze failed: $path", e)
            Analysis.Unreadable
        } finally {
            bitmap.recycle()
        }
    }

    /** Уменьшенная копия до ~640 px по длинной стороне; поворот из EXIF ImageDecoder применяет сам. */
    private fun decode(path: String): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val k = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize(maxOf(1, (info.size.width * k).toInt()), maxOf(1, (info.size.height * k).toInt()))
            }
            // ML Kit читает пиксели напрямую; аппаратный bitmap для него не годится.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val TAG = "MlKitPhotoAnalyzer"
        const val MAX_SIDE = 640
    }
}
