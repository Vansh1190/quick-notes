package com.vansh.quicknotes

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

/**
 * Last-resort crash reporter (no dependencies, safe to install from attachBaseContext): persists
 * the stack trace to app-private storage and to the external files dir, then chains to whatever
 * handler was installed before so the platform still handles the death normally.
 *
 * [latestCrashFile] is read on the next launch so a device without adb can still reveal the cause.
 */
object CrashReporter {
    private const val INTERNAL_FILE = "last-crash.txt"
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss.SSS").withZone(ZoneId.systemDefault())

    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext ?: context
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(appContext, thread.name, throwable) }
            runCatching { previous?.uncaughtException(thread, throwable) }
            runCatching { exitProcess(2) }
        }
    }

    /** The file the next-launch dialog reads. */
    fun latestCrashFile(context: Context): File = File(context.filesDir, INTERNAL_FILE)

    private fun externalDir(context: Context): File? = context.getExternalFilesDir(null)?.let { File(it, "crash-reports") }

    private fun writeCrash(context: Context, threadName: String, throwable: Throwable) {
        val text = buildString {
            appendLine("timestamp=${formatter.format(Instant.now())}")
            appendLine("thread=$threadName")
            appendLine("package=${context.packageName}")
            runCatching {
                val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
                appendLine("version=${pkg.versionName} (${pkg.versionCode})")
            }
            appendLine("sdk=${Build.VERSION.SDK_INT}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            appendLine(stackWithCauses(throwable))
        }
        runCatching {
            context.openFileOutput(INTERNAL_FILE, Context.MODE_PRIVATE).use { it.write(text.toByteArray()) }
        }
        runCatching {
            val dir = externalDir(context) ?: return@runCatching
            dir.mkdirs()
            File(dir, "last_crash.txt").writeText(text)
            File(dir, "crash_${formatter.format(Instant.now())}.txt").writeText(text)
        }
    }

    private fun stackWithCauses(throwable: Throwable): String {
        val main = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val causes = StringBuilder("\n--- cause chain ---\n")
        var cause = throwable.cause
        while (cause != null) {
            causes.append(cause).append('\n')
            cause = cause.cause
        }
        return main + causes
    }
}
