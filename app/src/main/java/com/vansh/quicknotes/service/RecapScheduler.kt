package com.vansh.quicknotes.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** §8.8: one recap pass per day, first run anchored after 19:00 local. */
object RecapScheduler {

    fun schedule(context: Context) {
        val anchor = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (anchor.timeInMillis <= System.currentTimeMillis()) {
            anchor.add(Calendar.DAY_OF_YEAR, 1)
        }
        val request = PeriodicWorkRequest.Builder(DailyRecapWorker::class.java, 1, TimeUnit.DAYS)
            .setInitialDelay(anchor.timeInMillis - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .addTag(DailyRecapWorker.UNIQUE)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DailyRecapWorker.UNIQUE,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
