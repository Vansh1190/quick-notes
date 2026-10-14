package com.vansh.quicknotes.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vansh.quicknotes.data.local.FakeNoteSearchIndex
import com.vansh.quicknotes.data.local.QuickNotesDatabase
import com.vansh.quicknotes.domain.model.Block
import com.vansh.quicknotes.domain.model.BlockType
import com.vansh.quicknotes.domain.model.TextPayload
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotesRepositoryTest {

    private lateinit var db: QuickNotesDatabase
    private lateinit var repo: NotesRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            QuickNotesDatabase::class.java,
        )
            .allowMainThreadQueries()
            .addCallback(QuickNotesDatabase.callback)
            .setQueryExecutor(java.util.concurrent.Executor { it.run() })
            .setTransactionExecutor(java.util.concurrent.Executor { it.run() })
            .build()
        repo = NotesRepository(db.noteDao(), db.tagDao(), db.versionDao(), FakeNoteSearchIndex())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `createNote then load returns a note with one text block`() = runTest {
        val id = repo.createNote("hello world")
        val note = repo.loadNote(id)!!
        assertEquals("hello world", note.title)
        assertEquals(1, note.blocks.size)
        assertEquals("hello world", note.blocks[0].text?.text)
    }

    @Test
    fun `title derives from first line of first text block`() = runTest {
        val id = repo.createNote()
        var note = repo.loadNote(id)!!
        note = note.copy(
            blocks = listOf(
                Block("b1", BlockType.TEXT, 0, TextPayload("Invoice to Rahul\nsecond line")),
                Block("b2", BlockType.TEXT, 1, TextPayload("other")),
            ),
        )
        repo.saveNote(note)
        val saved = repo.loadNote(id)!!
        assertEquals("Invoice to Rahul", saved.title)
    }

    @Test
    fun `fts search finds text transcripts and link titles`() = runTest {
        val id = repo.createNote()
        val note = repo.loadNote(id)!!.copy(
            blocks = listOf(
                Block("b1", BlockType.TEXT, 0, TextPayload("groceries milk and bread")),
                Block("b2", BlockType.LINK, 1, link = com.vansh.quicknotes.domain.model.LinkPayload("https://cora.ai", title = "Cora assistant docs")),
                Block("b3", BlockType.VOICE, 2, voice = com.vansh.quicknotes.domain.model.VoicePayload("m1", transcript = "call the dentist tomorrow")),
            ),
        )
        repo.saveNote(note)

        assertTrue(repo.search("groceries").any { it.id == id })
        assertTrue(repo.search("dentist").any { it.id == id })
        assertTrue(repo.search("cora").any { it.id == id })
        assertTrue(repo.search("pineapple").isEmpty())
    }

    @Test
    fun `match query neutralises operators and empty input`() {
        assertEquals(null, SearchableText.toMatchQuery("   "))
        assertEquals("\"hello\"*", SearchableText.toMatchQuery("hello"))
        assertEquals("\"and\"* \"the\"*", SearchableText.toMatchQuery("and the"))
        // every token is phrase-quoted, so FTS operators (NEAR/OR/NOT) become literal words
        val safe = SearchableText.toMatchQuery("NEAR(a b) OR NOT")!!
        assertEquals("\"NEAR(a\"* \"b)\"* \"OR\"* \"NOT\"*", safe)
    }

    @Test
    fun `search handles quotes and operators without crashing`() = runTest {
        val id = repo.createNote("plain text here")
        assertTrue(repo.search("\"").isEmpty())
        assertTrue(repo.search("AND OR NOT").isEmpty())
        assertTrue(repo.search("plain").size == 1)
    }

    @Test
    fun `pin archive trash and restore flows`() = runTest {
        val id = repo.createNote("task")
        repo.setPinned(id, true)
        assertTrue(repo.loadNote(id)!!.pinned)

        repo.moveToTrash(id)
        assertTrue(repo.loadNote(id)!!.deletedAt != null)

        repo.restore(id)
        val restored = repo.loadNote(id)!!
        assertEquals(null, restored.deletedAt)

        repo.setArchived(id, true)
        assertTrue(repo.loadNote(id)!!.archived)
    }

    @Test
    fun `trashed notes are excluded from active list and still inside retention`() = runTest {
        val id = repo.createNote("temp")
        val activeBefore = repo.observeActiveNotes().first()
        assertTrue(activeBefore.any { it.id == id })

        repo.moveToTrash(id)
        val activeAfter = repo.observeActiveNotes().first()
        assertTrue(activeAfter.none { it.id == id })

        repo.purgeExpiredTrash()
        assertTrue(repo.loadNote(id) != null) // still inside retention window
    }

    @Test
    fun `blocks are reordered and removed on save`() = runTest {
        val id = repo.createNote()
        var note = repo.loadNote(id)!!
        note = note.copy(
            blocks = listOf(
                Block("b1", BlockType.TEXT, 0, TextPayload("first")),
                Block("b2", BlockType.TEXT, 1, TextPayload("second")),
            ),
        )
        repo.saveNote(note)
        assertEquals(listOf("b1", "b2"), repo.loadNote(id)!!.blocks.map { it.id })
        repo.saveNote(note.copy(blocks = listOf(Block("b2", BlockType.TEXT, 0, TextPayload("second")))))
        val after = repo.loadNote(id)!!
        assertEquals(listOf("b2"), after.blocks.map { it.id })
    }
}
