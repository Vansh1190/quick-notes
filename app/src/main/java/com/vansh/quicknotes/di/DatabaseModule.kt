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
    fun searchIndex(db: QuickNotesDatabase): NoteSearchIndex = db.searchIndex

    @Provides
    @Singleton
    fun linkFetcher(): LinkFetcher = HttpLinkFetcher()
}
