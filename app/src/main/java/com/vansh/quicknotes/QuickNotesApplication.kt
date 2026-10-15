package com.vansh.quicknotes

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class QuickNotesApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    // Installed as early as possible so even DI/class-load failures during startup are captured.
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        CrashReporter.install(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
