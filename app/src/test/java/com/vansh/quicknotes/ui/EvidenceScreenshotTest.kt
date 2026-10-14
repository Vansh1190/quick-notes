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
import com.vansh.quicknotes.domain.HttpLinkFetcher
import com.vansh.quicknotes.domain.DrawPoint
import com.vansh.quicknotes.domain.DrawStroke
import com.vansh.quicknotes.domain.DrawTool
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
import kotlinx.coroutines.flow.first
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

    private object LinkFetcherStub : com.vansh.quicknotes.domain.LinkFetcher {
        override suspend fun fetch(url: String) = com.vansh.quicknotes.domain.LinkMetadata(url)
    }


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
        repository = NotesRepository(db.noteDao(), db.tagDao(), db.versionDao(), FakeNoteSearchIndex())
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
    fun m2MixedBlocksNote() {
        val id = runBlocking { repository.createNote("Project alpha") }
        runBlocking {
            val note = repository.loadNote(id)!!
            repository.saveNote(
                note.copy(
                    blocks = note.blocks + listOf(
                        Block("b2", com.vansh.quicknotes.domain.model.BlockType.CHECKLIST, 1, checklist = ChecklistBlockPayload(listOf(ChecklistItemPayload("i1", "draft the brief", true), ChecklistItemPayload("i2", "review with team")))),
                        Block("b3", com.vansh.quicknotes.domain.model.BlockType.LINK, 2, link = LinkPayload("https://developer.android.com/about/versions/16/features", title = "Android 16 features · developer.android.com", siteName = "developer.android.com", fetched = true)),
                    ),
                ),
            )
        }
        lateinit var vm: com.vansh.quicknotes.ui.editor.EditorViewModel
        composeRule.setContent {
            Box(Modifier.testTag("shot_root")) {
                QuickNotesTheme(darkTheme = false) {
                    vm = remember { com.vansh.quicknotes.ui.editor.EditorViewModel(SavedStateHandle(mapOf("noteId" to id)), repository, mediaStore, LinkFetcherStub) }
                    EditorScreen(onBack = {}, onPickImage = {}, viewModel = vm)
                }
            }
        }
        settle()
        save("m2-editor-blocks")
    }

    @Test
    fun m2DrawingScreen() {
        composeRule.setContent {
            Box(Modifier.testTag("shot_root")) {
                QuickNotesTheme(darkTheme = false) {
                    com.vansh.quicknotes.ui.drawing.DrawingScreen(
                        initial = com.vansh.quicknotes.ui.drawing.DrawingState(
                            strokes = listOf(
                                DrawStroke(listOf(DrawPoint(30f, 200f, 1f), DrawPoint(120f, 120f, 0.8f), DrawPoint(240f, 240f, 0.6f), DrawPoint(300f, 140f, 0.9f)), 0xFF007AFFL, 8f, DrawTool.PEN),
                                DrawStroke(listOf(DrawPoint(40f, 300f, 1f), DrawPoint(280f, 300f, 1f)), 0x59FFD60AL, 20f, DrawTool.HIGHLIGHTER),
                            ),
                            tool = DrawTool.PEN,
                        ),
                        onSave = {},
                        onCancel = {},
                    )
                }
            }
        }
        settle()
        save("m2-drawing")
    }

    private object NoReminders : com.vansh.quicknotes.domain.reminder.ReminderScheduler {
        override fun schedule(itemId: String, itemText: String, dueAt: Long) {}
        override fun cancel(itemId: String) {}
        override suspend fun rescheduleAll() {}
    }

    @Test
    fun m5ListsScreen() {
        val lists = com.vansh.quicknotes.data.repository.ListsRepository(db.todoDao(), NoReminders)
        runBlocking {
            val a = lists.createList("Groceries", items = listOf("milk", "eggs", "bread"))
            lists.setDone(lists.observeList(a).first().second[0].id, true)
            lists.createList("Work", items = listOf("send the invoice", "fix the login bug"))
            lists.createList("Personal", items = listOf("call mom"))
        }
        composeRule.setContent {
            Box(Modifier.testTag("shot_root")) {
                QuickNotesTheme(darkTheme = false) {
                    com.vansh.quicknotes.ui.lists.ListsScreen(
                        onOpenList = {},
                        viewModel = remember { com.vansh.quicknotes.ui.lists.ListsViewModel(lists) },
                    )
                }
            }
        }
        settle()
        save("m5-lists")
    }

    @Test
    fun m3RecordingSheet() {
        composeRule.setContent {
            Box(Modifier.testTag("shot_root")) {
                QuickNotesTheme(darkTheme = false) {
                    com.vansh.quicknotes.ui.voice.RecordingSheet(
                        active = com.vansh.quicknotes.service.RecordingSessions.Active("s1", null, startedAt = System.currentTimeMillis() - 42_000),
                        onPause = {}, onResume = {}, onStop = {}, onCancel = {},
                    )
                }
            }
        }
        settle()
        save("m3-recording-sheet")
    }

    @Test
    fun editorWithBlocks() {
        lateinit var vm: EditorViewModel
        composeRule.setContent {
            Box(Modifier.testTag("shot_root")) {
                QuickNotesTheme(darkTheme = false) {
                    vm = remember {
                        EditorViewModel(SavedStateHandle(mapOf("noteId" to "new")), repository, mediaStore, HttpLinkFetcher())
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
