package com.vansh.quicknotes.service

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.MediaRecorder.AudioSource
import android.os.Build
import android.os.Process
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.vansh.quicknotes.data.local.MediaAssetEntity
import com.vansh.quicknotes.data.local.NoteDao
import com.vansh.quicknotes.data.media.MediaStore
import com.vansh.quicknotes.data.repository.NotesRepository
import com.vansh.quicknotes.domain.model.Block
import com.vansh.quicknotes.domain.model.BlockType
import com.vansh.quicknotes.domain.model.VoicePayload
import com.vansh.quicknotes.domain.voice.ChunkedPcmWriter
import com.vansh.quicknotes.domain.voice.PcmToAac
import com.vansh.quicknotes.domain.voice.RecordingRecovery
import com.vansh.quicknotes.ui.design.QNColorTokens
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * Microphone foreground service owning recordings (spec §6). Audio is written to disk as
 * PCM chunks continuously; Stop finalizes to AAC and attaches a voice block to the target
 * note. Survives Home/lock. On Android 16+ requests promoted-ongoing (Live Update).
 */
@AndroidEntryPoint
class RecordingService : LifecycleService() {

    @Inject lateinit var mediaStore: MediaStore
    @Inject lateinit var repository: NotesRepository
    @Inject lateinit var dao: NoteDao
    @Inject lateinit var transcriber: com.vansh.quicknotes.domain.voice.Transcriber

    private var session: RecordingSessionState? = null
    private var captureJob: Job? = null
    private var tickerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                if (session != null) return START_NOT_STICKY // already recording
                val noteId = intent.getStringExtra(EXTRA_NOTE_ID)
                beginCapture(sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: UUID.randomUUID().toString(), noteId = noteId)
                startForegroundNotification()
            }
            ACTION_STOP -> stopAndFinalize()
            ACTION_PAUSE -> session?.let {
                it.applyPaused(true)
                RecordingSessions.publishPaused(true)
                updateNotification(it.elapsedMs(), paused = true)
            }
            ACTION_RESUME -> session?.let {
                it.applyPaused(false)
                RecordingSessions.publishPaused(false)
                updateNotification(it.elapsedMs(), paused = false)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val notification = RecordingNotification.build(this, session?.elapsedMs() ?: 0L, paused = false)
        ServiceCompat.startForeground(
            this,
            RecordingNotification.NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            },
        )
    }

    private fun beginCapture(sessionId: String, noteId: String?) {
        val dir = File(filesDir, "media/recordings/$sessionId").apply { mkdirs() }
        val writer = ChunkedPcmWriter(dir, sessionId)
        val state = RecordingSessionState(sessionId = sessionId, noteId = noteId, writer = writer)
        session = state
        RecordingSessions.publishActive(
            RecordingSessions.Active(sessionId = sessionId, noteId = noteId, startedAt = state.startedAt),
        )

        captureJob = lifecycleScope.launch(Dispatchers.IO) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this@RecordingService, android.Manifest.permission.RECORD_AUDIO,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                stopAndFinalize()
                return@launch
            }
            val minBuffer = AudioRecord.getMinBufferSize(
                ChunkedPcmWriter.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(4096)
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                ChunkedPcmWriter.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2,
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                stopAndFinalize()
                return@launch
            }
            val samples = ShortArray(1600)
            try {
                record.startRecording()
            } catch (t: SecurityException) {
                // mic permission revoked after the UI check: stop cleanly, keep no chunks
                record.release()
                stopAndFinalize()
                return@launch
            }
            try {
                while (isActive && state.stopping.not()) {
                    if (state.paused) {
                        delay(100)
                        continue
                    }
                    if (RecordingRecovery.shouldStopForLowStorage(writer.freeBytesForDir(), 3200L * 4)) {
                        break // graceful stop (spec §6.5)
                    }
                    val read = record.read(samples, 0, samples.size)
                    if (read > 0) {
                        writer.append(if (read == samples.size) samples else samples.copyOf(read))
                        state.addedSamples += read
                    }
                }
            } finally {
                runCatching { record.stop() }
                record.release()
            }
            stopAndFinalize()
        }

        tickerJob = lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                if (state.stopping) break
                updateNotification(state.elapsedMs(), state.paused)
            }
        }
    }

    private fun updateNotification(elapsedMs: Long, paused: Boolean) {
        try {
            NotificationManagerCompat.from(this)
                .notify(RecordingNotification.NOTIFICATION_ID, RecordingNotification.build(this, elapsedMs, paused))
        } catch (t: SecurityException) {
            // notification permission revoked mid-recording; recording itself continues
        }
    }

    private fun stopAndFinalize() {
        val state = session ?: run {
            stopSelfSafely()
            return
        }
        state.stopping = true
        captureJob?.cancel()
        tickerJob?.cancel()
        lifecycleScope.launch(Dispatchers.IO) {
            state.writer.finishTail()
            val pcm = state.writer.readAllSamples()
            val durationMs = pcm.size * 1000L / ChunkedPcmWriter.SAMPLE_RATE
            if (pcm.isEmpty() || durationMs < 300) {
                deleteSessionDir(state)
            } else {
                val m4a = File(cacheDir, "${state.sessionId}.m4a")
                runCatching { PcmToAac.encode(pcm, m4a) }
                    .onSuccess {
                        val asset = mediaStore.saveRecordingFinal(m4a.readBytes(), state.noteId, durationMs)
                        attachVoiceBlock(state.noteId, asset, durationMs)
                    }
                    .onFailure {
                        // encoder unavailable: keep WAV rather than lose the audio
                        val asset = mediaStore.saveRecordingFinal(wavOf(pcm), state.noteId, durationMs)
                        attachVoiceBlock(state.noteId, asset, durationMs)
                    }
                m4a.delete()
                deleteSessionDir(state)
            }
            withContext(Dispatchers.Main) {
                RecordingSessions.publishStopped(
                    RecordingSessions.Stopped(sessionId = state.sessionId, noteId = state.noteId, durationMs = durationMs),
                )
                stopSelfSafely()
            }
        }
    }

    private fun deleteSessionDir(state: RecordingSessionState) {
        state.writer.chunkFiles.forEach { it.delete() }
        state.writer.chunkFiles.firstOrNull()?.parentFile?.delete()
    }

    private suspend fun attachVoiceBlock(noteId: String?, asset: MediaAssetEntity, durationMs: Long) {
        val targetId = noteId ?: run {
            val fresh = repository.createNote()
            repository.loadNote(fresh)?.let { repository.saveNote(it.copy(title = "Voice note")) }
            fresh
        }
        val note = repository.loadNote(targetId) ?: return
        val blockId = UUID.randomUUID().toString()
        repository.saveNote(
            note.copy(
                blocks = note.blocks + Block(
                    id = blockId,
                    type = BlockType.VOICE,
                    orderIndex = note.blocks.size,
                    voice = VoicePayload(assetId = asset.id, durationMs = durationMs),
                ),
            ),
        )
        // transcript arrives in the background (spec §6.6); stub transcriber returns null
        val transcript = runCatching { transcriber.transcribe(asset.id) }.getOrNull()
        if (!transcript.isNullOrBlank()) {
            val fresh = repository.loadNote(targetId) ?: return
            repository.saveNote(
                fresh.copy(
                    blocks = fresh.blocks.map {
                        if (it.id == blockId && it.voice != null) it.copy(voice = it.voice.copy(transcript = transcript)) else it
                    },
                ),
            )
            dao.mediaById(asset.id)?.let { dao.upsertMedia(it.copy(transcript = transcript)) }
        }
    }

    private fun wavOf(pcm: ShortArray): ByteArray {
        val samples = pcm.size
        val byteRate = ChunkedPcmWriter.SAMPLE_RATE * 2
        val out = java.io.ByteArrayOutputStream(44 + samples * 2)
        fun le32(v: Int) { out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())) }
        fun le16(v: Int) { out.write(byteArrayOf(v.toByte(), (v shr 8).toByte())) }
        out.write("RIFF".toByteArray()); le32(36 + samples * 2); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); le32(16); le16(1); le16(1); le32(ChunkedPcmWriter.SAMPLE_RATE); le32(byteRate); le16(2); le16(16)
        out.write("data".toByteArray()); le32(samples * 2)
        pcm.forEach { s -> le16(s.toInt()) }
        return out.toByteArray()
    }

    private fun stopSelfSafely() {
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.vansh.quicknotes.action.RECORD_START"
        const val ACTION_STOP = "com.vansh.quicknotes.action.RECORD_STOP"
        const val ACTION_PAUSE = "com.vansh.quicknotes.action.RECORD_PAUSE"
        const val ACTION_RESUME = "com.vansh.quicknotes.action.RECORD_RESUME"
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_SESSION_ID = "session_id"

        fun start(context: Context, noteId: String?, sessionId: String? = null) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_NOTE_ID, noteId)
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).apply { action = ACTION_STOP })
        }

        fun pause(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).apply { action = ACTION_PAUSE })
        }

        fun resume(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).apply { action = ACTION_RESUME })
        }

        fun ensureChannel(context: Context) {
            val channel = NotificationChannelCompat.Builder(
                RecordingNotification.CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW,
            )
                .setName("Recording")
                .setDescription("Shows while a voice note is being recorded")
                .setShowBadge(false)
                .build()
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }
    }
}

private class RecordingSessionState(
    val sessionId: String,
    val noteId: String?,
    val writer: ChunkedPcmWriter,
    var paused: Boolean = false,
    var stopping: Boolean = false,
    var addedSamples: Long = 0L,
    val startedAt: Long = System.currentTimeMillis(),
    var pausedAt: Long? = null,
    var pausedTotalMs: Long = 0L,
) {
    fun elapsedMs(): Long {
        val now = System.currentTimeMillis()
        val pausedExtra = pausedAt?.let { now - it } ?: 0L
        return (now - startedAt - pausedTotalMs - pausedExtra).coerceAtLeast(0L)
    }

    fun applyPaused(value: Boolean) {
        if (value && !paused) {
            pausedAt = System.currentTimeMillis()
        } else if (!value && paused && pausedAt != null) {
            pausedTotalMs += System.currentTimeMillis() - pausedAt!!
            pausedAt = null
        }
        paused = value
    }
}
