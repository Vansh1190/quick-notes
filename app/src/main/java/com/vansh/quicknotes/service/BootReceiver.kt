package com.vansh.quicknotes.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.vansh.quicknotes.domain.reminder.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Re-arms every pending due-date reminder after reboot / app update (spec §7). */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        // goAsync keeps the process alive just long enough to re-arm alarms
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                scheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
