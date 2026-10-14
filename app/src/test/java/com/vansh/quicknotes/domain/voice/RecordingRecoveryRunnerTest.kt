package com.vansh.quicknotes.domain.voice

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vansh.quicknotes.data.local.FakeNoteSearchIndex
import com.vansh.quicknotes.data.local.QuickNotesDatabase
import com.vansh.quicknotes.data.media.MediaStore
import com.vansh.quicknotes.data.repository.NotesRepository
import com.vansh.quicknotes.domain.model.BlockType
import java.io.File
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
class RecordingRecoveryRunnerTest {

    private lateinit var db: QuickNotesDatabase
    private lateinit var mediaStore: MediaStore
    private lateinit var repository: NotesRepository
    private lateinit var runner: RecordingRecoveryRunner

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
        runner = RecordingRecoveryRunner(mediaStore, repository)
    }

    @After
    fun tearDown() = db.close()

    private fun writeChunks(session: String, frames: Int) {
        val dir = File(mediaStore.rootDir, "recordings/$session")
        dir.mkdirs()
        repeat(frames) { i ->
            // a 1600-sample frame of PCM16 = 3200 bytes of a rising tone
            val bytes = ByteArray(3200)
            for (b in bytes.indices) bytes[b] = ((i * 7 + b) % 251).toByte()
            File(dir, "chunk-%05d.pcm".format(i)).writeBytes(bytes)
        }
    }

    @Test
    fun `orphaned chunks become a recovered voice note`() = runTest {
        writeChunks("deadbeef", frames = 6)

        val recovered = runner.run()
        assertEquals(1, recovered)

        val notes = repository.search("Recovered recording")
        assertEquals(1, notes.size)
        val note = notes.single()
        val voice = note.blocks.first { it.type == BlockType.VOICE }.voice!!
        assertTrue(voice.durationMs > 0)
        val asset = mediaStore.byId(voice.assetId)!!
        assertTrue(mediaStore.fileFor(asset.path).exists())

        // chunks are consumed: running again recovers nothing
        assertEquals(0, runner.run())
    }

    @Test
    fun `multiple orphaned sessions each produce a note`() = runTest {
        writeChunks("sess-a", frames = 4)
        writeChunks("sess-b", frames = 3)
        assertEquals(2, runner.run())
        assertEquals(2, repository.search("Recovered recording").size)
    }

    @Test
    fun `empty chunk dir is ignored`() = runTest {
        File(mediaStore.rootDir, "recordings/empty").mkdirs()
        assertEquals(0, runner.run())
    }

    @Test
    fun `pcm decoder round trips little endian shorts`() {
        val samples = shortArrayOf(0, 1, -1, 256, -256, Short.MAX_VALUE, Short.MIN_VALUE)
        val bytes = PcmDecoder.toWav(samples).copyOfRange(44, 44 + samples.size * 2)
        val decoded = PcmDecoder.decode(bytes)
        assertEquals(samples.toList(), decoded.toList())
    }

    @Test
    fun `wav header is well formed`() {
        val wav = PcmDecoder.toWav(ShortArray(1600))
        assertEquals('R', wav[0].toInt().toChar())
        assertEquals('I', wav[1].toInt().toChar())
        assertEquals('F', wav[2].toInt().toChar())
        assertEquals('F', wav[3].toInt().toChar())
        assertEquals(44 + 3200, wav.size)
    }
}
