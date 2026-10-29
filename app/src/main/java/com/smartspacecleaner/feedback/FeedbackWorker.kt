package com.smartspacecleaner.feedback

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.smartspacecleaner.BuildConfig
import com.smartspacecleaner.core.feedback.DeviceInfo
import com.smartspacecleaner.core.feedback.Feedback
import com.smartspacecleaner.core.feedback.FeedbackFormEncoder
import com.smartspacecleaner.core.feedback.FeedbackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Отправляет один отзыв в Google Форму. Ждёт сеть, при сбое повторяет с растущей паузой, до 5 попыток.
 * Во входных данных лежат сырые поля: закодированный кириллический текст не влез бы в лимит Data (10 КБ).
 */
class FeedbackWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val feedback = Feedback(
            rating = inputData.getInt(KEY_RATING, 0),
            text = inputData.getString(KEY_TEXT).orEmpty(),
            name = inputData.getString(KEY_NAME).orEmpty(),
        )
        val source = FeedbackSource.entries.firstOrNull { it.code == inputData.getString(KEY_SOURCE) } ?: FeedbackSource.MENU
        val body = FeedbackFormEncoder.encode(feedback, source, deviceInfo(), FeedbackFormConfig.fields)
        val code = withContext(Dispatchers.IO) { post(body) }
        return when {
            code in 200..399 -> Result.success()
            code in 400..499 -> {
                Log.w(TAG, "Форма отклонила отзыв: HTTP $code — проверьте FORM_ID и номера полей")
                Result.failure()
            }
            runAttemptCount + 1 >= MAX_ATTEMPTS -> {
                Log.w(TAG, "Отзыв не отправлен за $MAX_ATTEMPTS попыток, последний ответ: $code")
                Result.failure()
            }
            else -> Result.retry()
        }
    }

    /** Код ответа сервера или -1, если до сервера не достучались. */
    private fun post(body: String): Int = try {
        val connection = URL(FeedbackFormConfig.submitUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    } catch (e: IOException) {
        -1
    }

    companion object {
        private const val TAG = "FeedbackWorker"
        private const val MAX_ATTEMPTS = 5
        private const val TIMEOUT_MS = 15_000
        private const val KEY_RATING = "rating"
        private const val KEY_TEXT = "text"
        private const val KEY_NAME = "name"
        private const val KEY_SOURCE = "source"

        fun enqueue(context: Context, feedback: Feedback, source: FeedbackSource) {
            val request = OneTimeWorkRequestBuilder<FeedbackWorker>()
                .setInputData(
                    workDataOf(
                        KEY_RATING to feedback.rating,
                        KEY_TEXT to feedback.text,
                        KEY_NAME to feedback.name,
                        KEY_SOURCE to source.code,
                    ),
                )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun deviceInfo() = DeviceInfo(
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            phone = "${Build.MANUFACTURER} ${Build.MODEL}",
            android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        )
    }
}
