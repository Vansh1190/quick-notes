package com.vansh.quicknotes.service

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vansh.quicknotes.MainActivity
import com.vansh.quicknotes.R

/** Fires the local-notification reminder when an item's alarm goes off. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
        val text = intent.getStringExtra(EXTRA_ITEM_TEXT) ?: return
        RecordingService.ensureChannel(context)
        ensureReminderChannel(context)
        val open = PendingIntent.getActivity(
            context,
            itemId.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_LISTS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_widget_list)
            .setContentTitle("Reminder")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(itemId.hashCode(), notification)
        }
    }

    companion object {
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_ITEM_TEXT = "item_text"
        const val CHANNEL_ID = "reminders"
        const val ACTION = "com.vansh.quicknotes.action.REMINDER"

        fun ensureReminderChannel(context: Context) {
            val channel = androidx.core.app.NotificationChannelCompat.Builder(
                CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH,
            )
                .setName("Reminders")
                .setDescription("Due-date reminders for list items")
                .build()
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }
    }
}
