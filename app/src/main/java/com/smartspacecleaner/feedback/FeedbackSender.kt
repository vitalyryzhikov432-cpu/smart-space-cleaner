package com.smartspacecleaner.feedback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.smartspacecleaner.core.feedback.Feedback
import com.smartspacecleaner.core.feedback.FeedbackSource

class FeedbackSender(private val context: Context) {
    val enabled: Boolean get() = FeedbackFormConfig.isConfigured

    /** Ставит отзыв в очередь; `true` — интернет есть прямо сейчас, и отзыв уйдёт сразу. */
    fun send(feedback: Feedback, source: FeedbackSource): Boolean {
        FeedbackWorker.enqueue(context, feedback, source)
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
