package com.vansh.quicknotes.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vansh.quicknotes.MainActivity
import com.vansh.quicknotes.R

/**
 * Ongoing recording notification: chronometer + Stop; tapping returns to the app.
 * On Android 16+ (API 36) additionally requests promoted ongoing (Live Update chip) and
 * uses a determinate progress bar so the system can surface it as a status-bar chip.
 * Every 36+ call is guarded; promotion is system-discretionary and older devices simply
 * get the plain ongoing notification.
 */
object RecordingNotification {

    const val CHANNEL_ID = "recording"
    const val NOTIFICATION_ID = 1001
    private const val RECENT_WINDOW_MS = 30 * 60 * 1000L

    fun build(context: Context, elapsedMs: Long, paused: Boolean): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, context.let { RecordingService::class.java }).apply { action = RecordingService.ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (paused) "Recording paused" else "Recording")
            .setContentText("Voice note · " + formatTime(elapsedMs))
            .setNumber((elapsedMs / 1000).toInt())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setUsesChronometer(!paused)
            .setWhen(System.currentTimeMillis() - elapsedMs)
            .setContentIntent(openIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Stop", stopIntent)

        if (Build.VERSION.SDK_INT >= 36) {
            runCatching {
                builder.setRequestPromotedOngoing(true)
                // determinate-ish progress so the Live Update has something to show
                builder.setProgress(100, ((elapsedMs / 1000) % 100).toInt(), false)
            }
        }
        return builder.build()
    }

    fun formatTime(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
}
