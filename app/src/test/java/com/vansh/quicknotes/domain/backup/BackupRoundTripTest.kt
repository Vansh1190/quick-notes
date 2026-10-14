package com.vansh.quicknotes.domain.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vansh.quicknotes.data.local.FakeNoteSearchIndex
import com.vansh.quicknotes.data.local.QuickNotesDatabase
import com.vansh.quicknotes.data.media.MediaStore
import com.vansh.quicknotes.data.repository.NotesRepository
import com.vansh.quicknotes.domain.model.Block
import com.vansh.quicknotes.domain.model.BlockType
import com.vansh.quicknotes.domain.model.ChecklistBlockPayload
import com.vansh.quicknotes.domain.model.ChecklistItemPayload
import com.vansh.quicknotes.domain.model.ImagePayload
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRoundTripTest {

    private class Bundle(val db: QuickNotesDatabase, val repo: NotesRepository, val media: MediaStore, val backup: BackupManager)

    private fun bundle(): Bundle {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val inline = java.util.concurrent.Executor { it.run() }
        val db = Room.inMemoryDatabaseBuilder(context, QuickNotesDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(inline)
            .setTransactionExecutor(inline)
            .build()
        val repo = NotesRepository(db.noteDao(), db.tagDao(), db.versionDao(), FakeNoteSearchIndex())
        val media = MediaStore(context, db.noteDao())
        return Bundle(db, repo, media, BackupManager(context, repo, db.tagDao(), media))
    }

    /** a real decodable image; Robolectric's Bitmap shim refuses fake bytes */
    private fun jpegBytes(): ByteArray {
        val bmp = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888)
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }

    @Test
    fun `export then import into a fresh database restores exactly`() {
        val source = bundle()
        val bytes = jpegBytes()
        val noteId = runBlocking {
            val id = source.repo.createNote("groceries and plans")
            val note = source.repo.loadNote(id)!!
            val asset = source.media.saveImage(bytes, id, "jpg")
            source.repo.saveNote(
                note.copy(
                    blocks = note.blocks +
                        Block(
                            "c1", BlockType.CHECKLIST, 1,
                            checklist = ChecklistBlockPayload(
                                listOf(ChecklistItemPayload("i1", "milk"), ChecklistItemPayload("i2", "call mom", done = true)),
                            ),
                        ) +
                        Block("img1", BlockType.IMAGE, 2, image = ImagePayload(asset.id, 10, 10)) +
                        Block("d1", BlockType.DIVIDER, 3),
                ),
            )
            source.db.tagDao().linkAll(id, listOf("home", "errands"))
            source.repo.setPinned(id, true)
            id
        }

        val zipFile = File.createTempFile("qn-backup", ".zip")
        val exported = runBlocking { source.backup.exportZip(zipFile) }
        assertEquals(1, exported)
        assertTrue("zip too small", zipFile.length() > 100)

        val target = bundle()
        val result = runBlocking { target.backup.importZip(zipFile) }
        assertEquals(1, result.notes)
        assertEquals(0, result.skipped)
        assertEquals(1, result.media)

        val original = runBlocking { source.repo.loadNote(noteId) }!!
        val restored = runBlocking { target.repo.loadNote(noteId) }!!
        assertEquals(original.id, restored.id)
        assertEquals(original.title, restored.title)
        assertEquals(original.createdAt, restored.createdAt)
        assertEquals(original.updatedAt, restored.updatedAt)
        assertEquals(true, restored.pinned)
        assertEquals(original.blocks.map { it.type }, restored.blocks.map { it.type })

        val checklist = restored.blocks.first { it.type == BlockType.CHECKLIST }.checklist!!
        assertEquals("milk", checklist.items[0].text)
        assertTrue(checklist.items[1].done)

        val assetId = restored.blocks.first { it.type == BlockType.IMAGE }.image!!.assetId
        val assetPath = runBlocking { target.media.byId(assetId) }!!.path
        assertTrue(target.media.fileFor(assetPath).readBytes().contentEquals(bytes))

        assertEquals(listOf("errands", "home"), runBlocking { target.db.tagDao().tagsFor(noteId) }.map { it.name })

        val again = runBlocking { target.backup.importZip(zipFile) }
        assertEquals(0, again.notes)
        assertEquals(1, again.skipped)

        source.db.close(); target.db.close(); zipFile.delete()
    }

    @Test
    fun `markdown export is readable and includes checklist state`() {
        val b = bundle()
        val id = runBlocking {
            val newId = b.repo.createNote("trip plan")
            val note = b.repo.loadNote(newId)!!
            b.repo.saveNote(
                note.copy(
                    blocks = note.blocks + Block(
                        "c", BlockType.CHECKLIST, 1,
                        checklist = ChecklistBlockPayload(
                            listOf(ChecklistItemPayload("x", "pack charger", done = true), ChecklistItemPayload("y", "book train")),
                        ),
                    ),
                ),
            )
            b.db.tagDao().linkAll(newId, listOf("travel"))
            newId
        }
        val md = runBlocking { b.backup.markdownFor(id) }!!
        assertTrue(md, md.contains("# trip plan"))
        assertTrue(md, md.contains("- [x] pack charger"))
        assertTrue(md, md.contains("- [ ] book train"))
        assertTrue(md, md.contains("#travel"))
        b.db.close()
    }
}
