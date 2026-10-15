package com.vansh.quicknotes.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vansh.quicknotes.TestDoubles
import com.vansh.quicknotes.crypto.SecretStore
import com.vansh.quicknotes.data.local.FakeNoteSearchIndex
import com.vansh.quicknotes.data.local.QuickNotesDatabase
import com.vansh.quicknotes.data.media.MediaStore
import com.vansh.quicknotes.data.repository.ListsRepository
import com.vansh.quicknotes.data.repository.NotesRepository
import com.vansh.quicknotes.data.settings.SettingsRepository
import com.vansh.quicknotes.domain.backup.BackupManager
import com.vansh.quicknotes.domain.model.Block
import com.vansh.quicknotes.domain.model.BlockType
import com.vansh.quicknotes.domain.model.ChecklistBlockPayload
import com.vansh.quicknotes.domain.model.ChecklistItemPayload
import com.vansh.quicknotes.domain.model.LinkPayload
import com.vansh.quicknotes.domain.model.TextPayload
import com.vansh.quicknotes.domain.reminder.ReminderScheduler
import com.vansh.quicknotes.ui.design.QuickNotesTheme
import com.vansh.quicknotes.ui.editor.EditorScreen
import com.vansh.quicknotes.ui.editor.EditorViewModel
import com.vansh.quicknotes.ui.home.HomeScreen
import com.vansh.quicknotes.ui.home.HomeViewModel
import com.vansh.quicknotes.ui.lists.ListDetailScreen
import com.vansh.quicknotes.ui.lists.ListDetailViewModel
import com.vansh.quicknotes.ui.lists.ListsScreen
import com.vansh.quicknotes.ui.lists.ListsViewModel
import com.vansh.quicknotes.ui.onboarding.OnboardingScreen
import com.vansh.quicknotes.ui.onboarding.OnboardingViewModel
import com.vansh.quicknotes.ui.settings.SettingsScreen
import com.vansh.quicknotes.ui.settings.SettingsViewModel
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen at a real phone viewport (411x891 dp, 2x) so layout and hierarchy can be
 * reviewed as images. Output goes outside the repo to /tmp/cora/shots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class UiViewportShotsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: QuickNotesDatabase
    private lateinit var notes: NotesRepository
    private lateinit var lists: ListsRepository
    private lateinit var mediaStore: MediaStore
    private lateinit var settings: SettingsRepository
    private lateinit var context: Context

    private val inline = java.util.concurrent.Executor { it.run() }

    private val noOpReminders = object : ReminderScheduler {
        override fun schedule(itemId: String, itemText: String, dueAt: Long) = Unit
        override fun cancel(itemId: String) = Unit
        override suspend fun rescheduleAll() = Unit
    }

    private val offlineClient = object : com.vansh.quicknotes.domain.ai.AiClient {
        override fun isConfigured() = false
        override suspend fun chat(
            messages: List<com.vansh.quicknotes.domain.ai.ChatMessage>,
            temperature: Double,
            maxTokens: Int,
            jsonMode: Boolean,
            toolName: String?,
            toolDescription: String,
            toolSchema: kotlinx.serialization.json.JsonObject?,
        ): com.vansh.quicknotes.domain.ai.ChatResult = throw com.vansh.quicknotes.domain.ai.AiException("offline")
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, QuickNotesDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(inline)
            .setTransactionExecutor(inline)
            .build()
        notes = NotesRepository(db.noteDao(), db.tagDao(), db.versionDao(), FakeNoteSearchIndex())
        lists = ListsRepository(db.todoDao(), noOpReminders)
        mediaStore = MediaStore(context, db.noteDao())
        settings = SettingsRepository(context, SecretStore(context))
    }

    @After
    fun tearDown() = db.close()

    private fun settle() {
        composeRule.waitForIdle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(500))
        composeRule.waitForIdle()
    }

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            Box(Modifier.testTag("shot_root")) {
                QuickNotesTheme(darkTheme = false) { content() }
            }
        }
        settle()
        val dir = File("/tmp/cora/shots").apply { mkdirs() }
        composeRule.onNodeWithTag("shot_root").captureToImage().asAndroidBitmap()
            .compress(Bitmap.CompressFormat.PNG, 100, File(dir, "$name.png").outputStream())
    }

    private fun seedNotes() = runBlocking {
        notes.createNote("Milk, eggs, and call the lab back")
        notes.createNote("Idea: capture first, organize later")
        val id = notes.createNote()
        notes.saveNote(
            notes.loadNote(id)!!.copy(
                pinned = true,
                blocks = listOf(
                    Block("b1", BlockType.TEXT, 0, TextPayload("Friday — send invoice to Rahul")),
                    Block(
                        "b2", BlockType.CHECKLIST, 1,
                        checklist = ChecklistBlockPayload(
                            listOf(
                                ChecklistItemPayload("i1", "draft email", true),
                                ChecklistItemPayload("i2", "attach pdf"),
                            ),
                        ),
                    ),
                    Block("b3", BlockType.LINK, 2, link = LinkPayload("https://developer.android.com", title = "Android Developers", siteName = "developer.android.com", fetched = true)),
                ),
            ),
        )
    }

    @Test
    fun home() {
        seedNotes()
        shot("home") {
            HomeScreen(
                onOpenNote = {}, onNewNote = {}, viewModel = HomeViewModel(notes, TestDoubles.OfflineAiLazy, settings),
            )
        }
    }

    @Test
    fun homeEmpty() {
        shot("home_empty") {
            HomeScreen(
                onOpenNote = {}, onNewNote = {}, viewModel = HomeViewModel(notes, TestDoubles.OfflineAiLazy, settings),
            )
        }
    }

    @Test
    fun editor() {
        seedNotes()
        val id = runBlocking { notes.createNote("Friday — send invoice to Rahul") }
        shot("editor") {
            EditorScreen(
                onBack = {},
                viewModel = EditorViewModel(
                    SavedStateHandle(mapOf("noteId" to id, "mode" to "")),
                    notes, mediaStore, com.vansh.quicknotes.domain.HttpLinkFetcher(), TestDoubles.OfflineAiLazy,
                ),
            )
        }
    }

    @Test
    fun lists() {
        runBlocking {
            val a = lists.createList("Groceries")
            val first = lists.addItem(a, "milk")
            lists.addItem(a, "eggs")
            lists.setDone(first, true)
            lists.createList("Work")
            lists.createList("Personal")
        }
        shot("lists") { ListsScreen(onOpenList = {}, viewModel = ListsViewModel(lists)) }
    }

    @Test
    fun listDetail() {
        val id = runBlocking {
            val a = lists.createList("Groceries")
            lists.addItem(a, "milk")
            lists.addItem(a, "eggs")
            lists.addItem(a, "sourdough bread")
            a
        }
        shot("list_detail") {
            ListDetailScreen(onBack = {}, viewModel = ListDetailViewModel(lists, SavedStateHandle(mapOf("listId" to id))))
        }
    }

    @Test
    fun settings() {
        shot("settings") {
            SettingsScreen(
                onBack = {},
                viewModel = SettingsViewModel(settings, offlineClient, BackupManager(context, notes, db.tagDao(), mediaStore)),
            )
        }
    }

    @Test
    fun onboarding() {
        shot("onboarding") { OnboardingScreen(onDone = {}, viewModel = OnboardingViewModel(settings)) }
    }
}
