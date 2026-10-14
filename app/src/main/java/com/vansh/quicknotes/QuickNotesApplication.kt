package com.vansh.quicknotes

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
class QuickNotesApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        CrashCatcher.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        CrashCatcher.install(this)
    }
}

/**
 * Last-resort crash reporter for field debugging: persists the stack to a file and, on the
 * next launch, surfaces it in a dialog so a device without adb can still reveal the cause.
 */
object CrashCatcher {
    private const val FILE = "last-crash.txt"
    @Volatile private var installed = false

    fun crashFile(context: android.content.Context): File = File(context.filesDir, FILE)

    fun install(app: Application) {
        if (installed) return
        installed = true
        val default = Thread.getDefaultUncaughtExceptionHandler()
        val handler = Thread.UncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = java.io.StringWriter().also { w ->
                    error.printStackTrace(java.io.PrintWriter(w))
                    w.append("\n\n--- prior cause chain ---\n")
                    var c = error.cause
                    while (c != null) { w.append(c.toString()).append("\n"); c.printStackTrace(java.io.PrintWriter(w)); c = c.cause }
                }.toString()
                app.openFileOutput(FILE, android.content.Context.MODE_PRIVATE).use {
                    it.write(("time=${java.util.Date()} thread=${thread.name}\n$trace").toByteArray())
                }
            }
            default?.uncaughtException(thread, error)
            runCatching { exitProcess(2) }
        }
        Thread.setDefaultUncaughtExceptionHandler(handler)
    }
}
