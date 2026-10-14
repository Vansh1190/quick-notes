package com.vansh.quicknotes

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CrashReporter {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss.SSS").withZone(ZoneId.systemDefault())

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(appContext, thread.name, throwable) }
            previous?.uncaughtException(thread, throwable)
                ?: run { throwable.printStackTrace() }
        }
    }

    fun latestCrashFile(context: Context): File = File(crashDir(context), "last_crash.txt")

    private fun crashDir(context: Context): File = File(context.getExternalFilesDir(null), "crash-reports")

    private fun writeCrash(context: Context, threadName: String, throwable: Throwable) {
        val dir = crashDir(context)
        dir.mkdirs()
        val text = buildString {
            appendLine("timestamp=${formatter.format(Instant.now())}")
            appendLine("thread=$threadName")
            appendLine("package=${context.packageName}")
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            appendLine("version=${pkg.versionName}")
            appendLine("sdk=${Build.VERSION.SDK_INT}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            appendLine(throwable.stackTraceToString())
        }
        File(dir, "last_crash.txt").writeText(text)
        File(dir, "crash_${formatter.format(Instant.now())}.txt").writeText(text)
    }
}
