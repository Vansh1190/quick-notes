package com.vansh.quicknotes.domain

import androidx.room.Room
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vansh.quicknotes.data.local.FakeNoteSearchIndex
import com.vansh.quicknotes.data.local.QuickNotesDatabase
import com.vansh.quicknotes.data.media.MediaStore
import com.vansh.quicknotes.data.repository.NotesRepository
import com.vansh.quicknotes.domain.model.BlockType
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
class ShareHandlerTest {

    private lateinit var db: QuickNotesDatabase
    private lateinit var repository: NotesRepository
    private lateinit var mediaStore: MediaStore
    private lateinit var handler: ShareHandler

    private val stubFetcher = object : LinkFetcher {
        override suspend fun fetch(url: String) = LinkMetadata(url = url, title = "Cora docs", siteName = "cora.ai")
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
        handler = ShareHandler(repository, mediaStore, stubFetcher)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `shared text becomes a note titled by its first line`() = runTest {
        val id = handler.handle(SharedContent.Text("shared thought from another app"))
        val note = repository.loadNote(id)!!
        assertEquals("shared thought from another app", note.title)
    }

    @Test
    fun `shared url becomes a link block enriched with metadata`() = runTest {
        val id = handler.handle(SharedContent.Link("https://cora.ai/docs"))
        val note = repository.loadNote(id)!!
        val link = note.blocks.first { it.type == BlockType.LINK }.link!!
        assertEquals("https://cora.ai/docs", link.url)
        assertEquals("Cora docs", link.title)
        assertEquals("cora.ai", link.siteName)
        assertTrue(link.fetched)
    }

    @Test
    fun `shared image becomes an image block with a file on disk`() = runTest {
        val id = handler.handle(SharedContent.Image(byteArrayOf(1, 2, 3), "image/jpeg"))
        val note = repository.loadNote(id)!!
        val image = note.blocks.first { it.type == BlockType.IMAGE }.image!!
        val asset = mediaStore.byId(image.assetId)!!
        assertTrue(mediaStore.fileFor(asset.path).exists())
    }
}
