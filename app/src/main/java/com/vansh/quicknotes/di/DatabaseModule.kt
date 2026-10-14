package com.vansh.quicknotes.di

import android.content.Context
import com.vansh.quicknotes.data.local.NoteDao
import com.vansh.quicknotes.data.local.QuickNotesDatabase
import com.vansh.quicknotes.data.local.NoteSearchIndex
import com.vansh.quicknotes.domain.HttpLinkFetcher
import com.vansh.quicknotes.domain.LinkFetcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): QuickNotesDatabase = QuickNotesDatabase.build(context)

    @Provides
    fun noteDao(db: QuickNotesDatabase): NoteDao = db.noteDao()

    @Provides
    fun todoDao(db: QuickNotesDatabase): com.vansh.quicknotes.data.local.TodoDao = db.todoDao()

    @Provides
    fun searchIndex(db: QuickNotesDatabase): NoteSearchIndex = db.searchIndex

    @Provides
    @Singleton
    fun linkFetcher(): LinkFetcher = HttpLinkFetcher()

    @Provides
    @Singleton
    fun reminderScheduler(impl: com.vansh.quicknotes.service.AlarmReminderScheduler): com.vansh.quicknotes.domain.reminder.ReminderScheduler = impl

    @Provides
    @Singleton
    fun transcriber(
        stub: com.vansh.quicknotes.domain.voice.StubTranscriber,
        server: com.vansh.quicknotes.domain.voice.ServerTranscriber,
        settings: com.vansh.quicknotes.data.settings.SettingsRepository,
    ): com.vansh.quicknotes.domain.voice.Transcriber =
        if (settings.current().serverTranscription) server else stub

    @Provides
    @Singleton
    fun aiClient(
        settings: com.vansh.quicknotes.data.settings.SettingsRepository,
        secrets: com.vansh.quicknotes.crypto.SecretStore,
    ): com.vansh.quicknotes.domain.ai.AiClient = com.vansh.quicknotes.domain.ai.HttpAiClient(
        baseUrl = { settings.current().baseUrl },
        apiKey = { secrets.getApiKey() },
        model = { settings.current().model },
    )
}
