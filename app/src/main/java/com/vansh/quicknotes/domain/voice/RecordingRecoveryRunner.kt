package com.vansh.quicknotes.domain.voice

import com.vansh.quicknotes.data.media.MediaStore
import com.vansh.quicknotes.data.local.MediaAssetEntity
import com.vansh.quicknotes.data.repository.NotesRepository
import com.vansh.quicknotes.domain.model.Block
import com.vansh.quicknotes.domain.model.BlockType
import com.vansh.quicknotes.domain.model.VoicePayload
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec §6.5: "On next launch, any orphaned recording is recovered automatically and shown
 * as a note." Surviving chunk dirs mean a session that never finalized (crash / force-kill).
 * Each orphan becomes its own voice note.
 */
@Singleton
class RecordingRecoveryRunner @Inject constructor(
    private val mediaStore: MediaStore,
    private val repository: NotesRepository,
) {
    /** @return number of sessions recovered. */
    suspend fun run(): Int {
        val recordingsRoot = File(mediaStore.rootDir, "recordings")
        val chunks = RecordingRecovery.scanChunks(recordingsRoot)
        val bySession = chunks.filter { it.existsOnDisk }.groupBy { it.sessionId }
        var recovered = 0
        for ((sessionId, files) in bySession) {
            val ordered = files.sortedBy { it.index }
            val pcm = ordered.mapNotNull { File(it.path).takeIf { f -> f.exists() }?.readBytes() }
            if (pcm.isEmpty()) continue
            val samples = PcmDecoder.decode(pcm.fold(byteArrayOf()) { a, b -> a + b })
            val durationMs = samples.size * 1000L / ChunkedPcmWriter.SAMPLE_RATE
            if (samples.isEmpty() || durationMs < 300) {
                ordered.forEach { File(it.path).delete() }
                File(ordered.first().path).parentFile?.delete()
                continue
            }
            val noteId = repository.createNote()
            val note = repository.loadNote(noteId)!!
            val asset = runCatching {
                val m4a = File.createTempFile("recovered_", ".m4a", mediaStore.rootDir)
                try {
                    PcmToAac.encode(samples, m4a)
                    mediaStore.saveRecordingFinal(m4a.readBytes(), noteId, durationMs)
                } finally {
                    m4a.delete()
                }
            }.getOrElse {
                mediaStore.saveRecordingFinal(PcmDecoder.toWav(samples), noteId, durationMs)
            }
            repository.saveNote(
                note.copy(
                    title = "Recovered recording",
                    blocks = listOf(
                        Block(
                            id = UUID.randomUUID().toString(),
                            type = BlockType.TEXT,
                            orderIndex = 0,
                            text = com.vansh.quicknotes.domain.model.TextPayload("Recovered recording"),
                        ),
                        Block(
                            id = UUID.randomUUID().toString(),
                            type = BlockType.VOICE,
                            orderIndex = 1,
                            voice = VoicePayload(assetId = asset.id, durationMs = durationMs),
                        ),
                    ),
                ),
            )
            ordered.forEach { File(it.path).delete() }
            File(ordered.first().path).parentFile?.delete()
            recovered++
        }
        return recovered
    }
}

/** Kept public for tests: pure PCM16-LE byte ↔ short helpers. */
object PcmDecoder {
    fun decode(bytes: ByteArray): ShortArray {
        val out = ShortArray(bytes.size / 2)
        for (i in out.indices) {
            out[i] = ((bytes[i * 2].toInt() and 0xFF) or ((bytes[i * 2 + 1].toInt() and 0xFF) shl 8)).toShort()
        }
        return out
    }

    fun toWav(pcm: ShortArray, sampleRate: Int = ChunkedPcmWriter.SAMPLE_RATE): ByteArray {
        val n = pcm.size
        val out = java.io.ByteArrayOutputStream(44 + n * 2)
        fun le32(v: Int) { out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())) }
        fun le16(v: Int) { out.write(byteArrayOf(v.toByte(), (v shr 8).toByte())) }
        out.write("RIFF".toByteArray()); le32(36 + n * 2); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); le32(16); le16(1); le16(1); le32(sampleRate); le32(sampleRate * 2); le16(2); le16(16)
        out.write("data".toByteArray()); le32(n * 2)
        pcm.forEach { le16(it.toInt()) }
        return out.toByteArray()
    }
}
