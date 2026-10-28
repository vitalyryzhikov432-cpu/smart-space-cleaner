package com.smartspacecleaner

import android.app.Application
import com.smartspacecleaner.work.PurgeWorker

class SmartSpaceApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        PurgeWorker.schedule(this)
    }
}
