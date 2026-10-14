package com.vansh.quicknotes.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vansh.quicknotes.data.repository.ListsRepository
import com.vansh.quicknotes.data.repository.NotesRepository
import com.vansh.quicknotes.data.repository.SearchableText
import com.vansh.quicknotes.data.settings.SettingsRepository
import com.vansh.quicknotes.domain.ai.AiService
import dagger.Lazy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import kotlinx.coroutines.flow.first

/**
 * Spec §8.8 Daily Recap: runs once daily after 19:00 local. Computes today's captures +
 * tomorrow's due items, asks the AI for a short recap and stores it for the Home card.
 * No AI configured or nothing to say = silent success.
 */
@HiltWorker
class DailyRecapWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: NotesRepository,
    private val lists: ListsRepository,
    private val settings: SettingsRepository,
    private val ai: Lazy<AiService>,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!ai.get().isConfigured()) return Result.success()
        val startOfToday = Calendar.getInstance().apply { midnight() }.timeInMillis
        val todays = repository.observeActiveNotes().first()
            .filter { it.updatedAt >= startOfToday }
            .take(8)

        val tomorrowStart = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); midnight() }.timeInMillis
        val tomorrowEnd = tomorrowStart + 24L * 60 * 60 * 1000 - 1
        val dueTomorrow = lists.upcomingDueBetween(tomorrowStart, tomorrowEnd)

        if (todays.isEmpty() && dueTomorrow.isEmpty()) return Result.success()

        val captures = todays.mapNotNull { entity ->
            repository.loadNote(entity.id)?.let { note ->
                note.title.ifBlank { "Untitled" } to SearchableText.fromBlocks(note.blocks).take(140)
            }
        }
        val text = runCatching { ai.get().dailyRecap(captures, dueTomorrow) }.getOrNull()
            ?: return Result.retry()
        settings.setDailyRecap(text.ifBlank { null }, startOfToday)
        return Result.success()
    }

    private fun Calendar.midnight() {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    companion object {
        const val UNIQUE = "daily-recap"
    }
}
