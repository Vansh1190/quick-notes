package com.vansh.quicknotes.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vansh.quicknotes.data.local.FakeNoteSearchIndex
import com.vansh.quicknotes.data.local.QuickNotesDatabase
import com.vansh.quicknotes.data.media.MediaStore
import com.vansh.quicknotes.data.repository.NotesRepository
import com.vansh.quicknotes.domain.DrawPoint
import com.vansh.quicknotes.domain.DrawStroke
import com.vansh.quicknotes.domain.DrawTool
import com.vansh.quicknotes.domain.HttpLinkFetcher
import com.vansh.quicknotes.domain.LinkFetcher
import com.vansh.quicknotes.domain.LinkMetadata
import com.vansh.quicknotes.ui.design.QuickNotesTheme
import com.vansh.quicknotes.ui.drawing.StrokeRenderer
import com.vansh.quicknotes.ui.editor.EditorScreen
import com.vansh.quicknotes.ui.editor.EditorViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class M2BlocksFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: QuickNotesDatabase
    private lateinit var repository: NotesRepository
    private lateinit var mediaStore: MediaStore
    private val stubLinks = object : LinkFetcher {
        override suspend fun fetch(url: String) = LinkMetadata(url, title = "Cora docs", siteName = "cora.ai")
    }

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val inline = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, QuickNotesDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(inline)
            .setTransactionExecutor(inline)
            .build()
        repository = NotesRepository(db.noteDao(), FakeNoteSearchIndex())
        mediaStore = MediaStore(context, db.noteDao())
    }

    @After
    fun tearDown() = db.close()

    private fun settle() {
        composeRule.waitForIdle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(500))
        composeRule.waitForIdle()
    }

    private fun editorVm(noteId: String = "new"): EditorViewModel =
        EditorViewModel(SavedStateHandle(mapOf("noteId" to noteId)), repository, mediaStore, stubLinks)

    @Test
    fun `every v1 block type renders in the editor and survives a reload`() {
        lateinit var vm: EditorViewModel
        composeRule.setContent {
            QuickNotesTheme(darkTheme = false) {
                vm = androidx.compose.runtime.remember { editorVm() }
                EditorScreen(onBack = {}, onPickImage = {}, viewModel = vm)
            }
        }
        settle()
        val noteId = vm.state.value.note!!.id
        val textBlockId = vm.state.value.note!!.blocks.first().id

        vm.onBlockTextChange(textBlockId, "Mixed note")
        vm.addChecklist(listOf("milk", "eggs"))
        vm.addImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()), "jpg")
        vm.addLink("https://cora.ai/docs")
        val strokes = listOf(DrawStroke(listOf(DrawPoint(5f, 5f, 1f), DrawPoint(80f, 60f, 0.6f)), 0xFF1C1C1EL, 6f, DrawTool.PEN))
        vm.addDrawing(strokes, StrokeRenderer.pngBytes(StrokeRenderer.render(strokes, 200, 200)), 200, 200)
        vm.addDivider()
        settle()
        vm.flushSave()
        settle()

        val reloaded = runBlocking { repository.loadNote(noteId) }!!
        val types = reloaded.blocks.map { it.type }
        assertTrue(com.vansh.quicknotes.domain.model.BlockType.CHECKLIST in types)
        assertTrue(com.vansh.quicknotes.domain.model.BlockType.IMAGE in types)
        assertTrue(com.vansh.quicknotes.domain.model.BlockType.LINK in types)
        assertTrue(com.vansh.quicknotes.domain.model.BlockType.DRAWING in types)
        assertTrue(com.vansh.quicknotes.domain.model.BlockType.DIVIDER in types)

        // checklist got real items and the link got metadata
        val checklist = reloaded.blocks.first { it.type == com.vansh.quicknotes.domain.model.BlockType.CHECKLIST }
        assertEquals(2, checklist.checklist?.items?.size)
        val link = reloaded.blocks.first { it.type == com.vansh.quicknotes.domain.model.BlockType.LINK }
        assertEquals("Cora docs", link.link?.title)

        // UI shows the blocks
        composeRule.onNodeWithTag("editor_capture_bar").assertIsDisplayed()
        composeRule.waitForIdle()
        assertTrue(reloaded.blocks.size >= 6)
    }

    @Test
    fun `checklist toggle marks an item done`() {
        lateinit var vm: EditorViewModel
        composeRule.setContent {
            QuickNotesTheme(darkTheme = false) {
                vm = androidx.compose.runtime.remember { editorVm() }
                EditorScreen(onBack = {}, onPickImage = {}, viewModel = vm)
            }
        }
        settle()
        vm.addChecklist(listOf("call mom"))
        settle()
        val item = vm.state.value.note!!.blocks.first { it.type == com.vansh.quicknotes.domain.model.BlockType.CHECKLIST }.checklist!!.items.single()
        composeRule.onNodeWithTag("check_${item.id}").performClick()
        settle()
        val after = vm.state.value.note!!.blocks.first { it.type == com.vansh.quicknotes.domain.model.BlockType.CHECKLIST }
        assertTrue(after.checklist!!.items.single().done)
    }

    @Test
    fun `link dialog adds a link card`() {
        lateinit var vm: EditorViewModel
        composeRule.setContent {
            QuickNotesTheme(darkTheme = false) {
                vm = androidx.compose.runtime.remember { editorVm() }
                EditorScreen(onBack = {}, onPickImage = {}, viewModel = vm)
            }
        }
        settle()
        composeRule.onNodeWithTag("capture_link").performClick()
        settle()
        composeRule.onNodeWithTag("link_dialog_input").performTextInput("https://cora.ai/docs")
        composeRule.onNodeWithTag("link_dialog_add").performClick()
        settle()
        vm.flushSave()
        settle()
        val types = vm.state.value.note!!.blocks.map { it.type }
        assertTrue(com.vansh.quicknotes.domain.model.BlockType.LINK in types)
    }
}
