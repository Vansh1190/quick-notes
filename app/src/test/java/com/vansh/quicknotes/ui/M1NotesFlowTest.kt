package com.vansh.quicknotes.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vansh.quicknotes.data.local.FakeNoteSearchIndex
import com.vansh.quicknotes.data.local.QuickNotesDatabase
import com.vansh.quicknotes.data.repository.NotesRepository
import com.vansh.quicknotes.ui.design.QuickNotesTheme
import com.vansh.quicknotes.ui.editor.EditorScreen
import com.vansh.quicknotes.ui.editor.EditorViewModel
import com.vansh.quicknotes.ui.home.HomeScreen
import com.vansh.quicknotes.ui.home.HomeViewModel
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class M1NotesFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Let Room flows + the 250 ms debounce on Main actually elapse. */
    private fun settle() {
        composeRule.waitForIdle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(400))
        composeRule.waitForIdle()
    }

    private lateinit var db: QuickNotesDatabase
    private lateinit var repository: NotesRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val inlineExecutor = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, QuickNotesDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(inlineExecutor)
            .setTransactionExecutor(inlineExecutor)
            .build()
        repository = NotesRepository(db.noteDao(), FakeNoteSearchIndex())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `create a note in the editor then see it on home`() {
        lateinit var editorVm: EditorViewModel
        composeRule.setContent {
            QuickNotesTheme {
                editorVm = androidx.compose.runtime.remember {
                    EditorViewModel(SavedStateHandle(mapOf("noteId" to "new")), repository)
                }
                EditorScreen(onBack = {}, viewModel = editorVm)
            }
        }
        settle()
        composeRule.onNodeWithTag("editor_capture_bar").assertIsDisplayed()

        // type via the view model path the UI uses
        val noteId = editorVm.state.value.note!!.id
        val blockId = editorVm.state.value.note!!.blocks.first().id
        composeRule.runOnIdle { editorVm.onBlockTextChange(blockId, "milk and eggs") }
        composeRule.runOnIdle { editorVm.flushSave() }

        settle()
        val saved = kotlinx.coroutines.runBlocking { repository.loadNote(noteId) }!!
        assertEquals("milk and eggs", saved.title)
        assertEquals("milk and eggs", saved.blocks.first().text?.text)
    }

    @Test
    fun `home shows created note and search finds it`() {
        val id = kotlinx.coroutines.runBlocking { repository.createNote("call mom tonight") }

        composeRule.setContent {
            QuickNotesTheme {
                HomeScreen(
                    onOpenNote = {},
                    onNewNote = {},
                    viewModel = HomeViewModel(repository),
                )
            }
        }
        settle()
        composeRule.onNodeWithTag("home_capture_pill").assertIsDisplayed()
        composeRule.onNodeWithTag("note_card_$id").assertIsDisplayed()

        composeRule.onNodeWithTag("home_search").performClick()
        composeRule.onNodeWithTag("home_search_input").performTextInput("mom")
        settle()
        composeRule.onNodeWithTag("note_card_$id").assertIsDisplayed()
    }

    @Test
    fun `empty home shows the first-run message`() {
        composeRule.setContent {
            QuickNotesTheme {
                HomeScreen(onOpenNote = {}, onNewNote = {}, viewModel = HomeViewModel(repository))
            }
        }
        settle()
        composeRule.onNodeWithText("No notes yet").assertIsDisplayed()
    }

}
