package com.vansh.quicknotes.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.vansh.quicknotes.data.local.TodoDao
import com.vansh.quicknotes.domain.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Due-date reminders via AlarmManager (spec §7). Uses setExactAndAllowWhileIdle on 31+ —
 * SCHEDULE_EXACT_ALARM is not held, so exact alarms are refused on Android 12-12.1 unless
 * the user granted them; in that case we fall back to an inexact window and the note still
 * surfaces as "overdue" in the app, which is the honest graceful-degradation path.
 */
@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TodoDao,
) : ReminderScheduler {

    override fun schedule(itemId: String, itemText: String, dueAt: Long) {
        val pi = pending(context, itemId, itemText)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            if (dueAt > System.currentTimeMillis()) {
                if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
                    alarms.setWindow(AlarmManager.RTC_WAKEUP, dueAt, 10 * 60_000L, pi)
                } else {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pi)
                }
            }
        }
    }

    override fun cancel(itemId: String) {
        val pi = PendingIntent.getBroadcast(
            context,
            itemId.hashCode(),
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) {
            context.getSystemService(AlarmManager::class.java)?.cancel(pi)
            pi.cancel()
        }
    }

    override suspend fun rescheduleAll() {
        val upcoming = dao.upcomingDue(System.currentTimeMillis())
        upcoming.forEach { item ->
            item.dueAt?.let { schedule(item.id, item.text, it) }
        }
    }

    private fun pending(context: Context, itemId: String, text: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION)
            .putExtra(ReminderReceiver.EXTRA_ITEM_ID, itemId)
            .putExtra(ReminderReceiver.EXTRA_ITEM_TEXT, text)
        return PendingIntent.getBroadcast(
            context,
            itemId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
