package com.vansh.quicknotes.domain.reminder

/** Abstraction so repository logic is testable without the platform alarm service. */
interface ReminderScheduler {
    fun schedule(itemId: String, itemText: String, dueAt: Long)

    fun cancel(itemId: String)

    /** Re-arm everything after reboot / app update (spec: local-notification reminder). */
    suspend fun rescheduleAll()
}
