package com.vansh.quicknotes.ui

import android.graphics.Bitmap
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vansh.quicknotes.data.local.FakeNoteSearchIndex
import com.vansh.quicknotes.data.local.QuickNotesDatabase
import com.vansh.quicknotes.data.media.MediaStore
import com.vansh.quicknotes.data.repository.NotesRepository
import com.vansh.quicknotes.domain.LinkFetcher
import com.vansh.quicknotes.domain.model.Block
import com.vansh.quicknotes.domain.model.BlockType
import com.vansh.quicknotes.domain.model.ChecklistBlockPayload
import com.vansh.quicknotes.domain.model.ChecklistItemPayload
import com.vansh.quicknotes.domain.model.LinkPayload
import com.vansh.quicknotes.domain.model.TextPayload
import com.vansh.quicknotes.ui.design.QuickNotesTheme
import com.vansh.quicknotes.ui.editor.EditorScreen
import com.vansh.quicknotes.ui.editor.EditorViewModel
import com.vansh.quicknotes.ui.home.HomeScreen
import com.vansh.quicknotes.ui.home.HomeViewModel
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EvidenceScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: QuickNotesDatabase
    private lateinit var repository: NotesRepository
    private lateinit var mediaStore: MediaStore

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
            .idleFor(java.time.Duration.ofMillis(400))
        composeRule.waitForIdle()
    }

    private fun save(tag: String) {
        val image = composeRule.onNodeWithTag("shot_root").captureToImage()
        val dir = File("../docs/evidence")
        dir.mkdirs()
        File(dir, "$tag.png").outputStream().use { out ->
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun homeWithNotes() {
        runBlocking {
            repository.createNote("Milk, eggs, and call the lab back")
            repository.createNote("Idea: capture first, organize later")
            val id = repository.createNote()
            repository.saveNote(
                repository.loadNote(id)!!.copy(
                    pinned = true,
                    blocks = listOf(
                        Block("b1", BlockType.TEXT, 0, TextPayload("Friday — send invoice to Rahul")),
                        Block("b2", BlockType.CHECKLIST, 1, checklist = ChecklistBlockPayload(listOf(ChecklistItemPayload("i1", "draft email", true), ChecklistItemPayload("i2", "attach pdf")))),
                        Block("b3", BlockType.LINK, 2, link = LinkPayload("https://developer.android.com/about/versions/16/features", title = "Android 16 Live Updates")),
                    ),
                ),
            )
        }
        composeRule.setContent {
            Box(Modifier.testTag("shot_root")) {
                QuickNotesTheme(darkTheme = false) {
                    HomeScreen(onOpenNote = {}, onNewNote = {}, viewModel = HomeViewModel(repository))
                }
            }
        }
        settle()
        save("m1-home")
    }

    @Test
    fun editorWithBlocks() {
        lateinit var vm: EditorViewModel
        composeRule.setContent {
            Box(Modifier.testTag("shot_root")) {
                QuickNotesTheme(darkTheme = false) {
                    vm = remember {
                        EditorViewModel(SavedStateHandle(mapOf("noteId" to "new")), repository, mediaStore, LinkFetcher())
                    }
                    EditorScreen(onBack = {}, onPickImage = {}, viewModel = vm)
                }
            }
        }
        settle()
        composeRule.runOnIdle {
            vm.onBlockTextChange(vm.state.value.note!!.blocks.first().id, "Groceries run")
            vm.insertBlock(BlockType.CHECKLIST)
            vm.insertBlock(BlockType.VOICE)
            vm.flushSave()
        }
        settle()
        save("m1-editor")
    }
}
