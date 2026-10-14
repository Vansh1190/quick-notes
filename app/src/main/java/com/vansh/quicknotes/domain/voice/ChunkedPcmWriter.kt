package com.vansh.quicknotes.domain.voice

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Crash-safe recorder (spec §6.5): PCM16 chunks are flushed to disk as they are captured
 * (survives force-kill), then finalized to AAC/M4A with MediaCodec + MediaMuxer.
 * Frame size 1600 samples ≈ 100 ms at 16 kHz mono.
 */
class ChunkedPcmWriter(private val dir: File, private val sessionId: String) {

    val chunkFiles = mutableListOf<File>()
    private var buffer = ShortArray(0)
    private var closed = false

    val frameSamples = 1600

    fun append(samples: ShortArray) {
        check(!closed)
        buffer += samples
        while (buffer.size >= frameSamples) {
            val frame = buffer.copyOfRange(0, frameSamples)
            buffer = buffer.copyOfRange(frameSamples, buffer.size)
            writeChunk(frame)
        }
    }

    /** Flushes the tail (< 1 frame) so nothing is lost on stop/crash-boundary. */
    fun finishTail() {
        if (buffer.isNotEmpty()) {
            writeChunk(buffer)
            buffer = ShortArray(0)
        }
        closed = true
    }

    fun freeBytesForDir(): Long = dir.usableSpace

    private fun writeChunk(frame: ShortArray) {
        val file = File(dir, "chunk-%05d.pcm".format(chunkFiles.size))
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            val bytes = ByteArray(frame.size * 2)
            for (i in frame.indices) {
                bytes[i * 2] = (frame[i].toInt() and 0xFF).toByte()
                bytes[i * 2 + 1] = ((frame[i].toInt() shr 8) and 0xFF).toByte()
            }
            out.write(bytes)
            out.flush()
            try {
                out.fd.sync()
            } catch (_: Exception) {
                // sync unsupported on this filesystem; file is still closed/flushed
            }
        }
        chunkFiles += file
    }

    fun readAllSamples(): ShortArray {
        val total = chunkFiles.sumOf { it.length().toInt() } / 2
        val out = ShortArray(total)
        var pos = 0
        chunkFiles.forEach { f ->
            val bytes = f.readBytes()
            for (i in bytes.indices step 2) {
                out[pos++] = ((bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)).toShort()
            }
        }
        return out
    }

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}

/** PCM16 mono (our capture format) → AAC M4A. */
object PcmToAac {

    fun encode(pcm: ShortArray, out: File, sampleRate: Int = ChunkedPcmWriter.SAMPLE_RATE, bitrate: Int = 48_000) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        val pcmBytes = ByteBuffer.allocate(pcm.size * 2)
        for (s in pcm) pcmBytes.putShort(s)
        pcmBytes.flip()

        var inputDone = false
        var endOfStream = false
        try {
            while (!endOfStream) {
                if (!inputDone) {
                    val inIndex = encoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = encoder.getInputBuffer(inIndex)!!
                        val remaining = pcmBytes.remaining()
                        val toCopy = minOf(remaining, inBuf.capacity())
                        if (toCopy <= 0) {
                            encoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val chunk = ByteArray(toCopy)
                            pcmBytes.get(chunk)
                            inBuf.clear()
                            inBuf.put(chunk)
                            val ptsUs = ((pcmBytes.capacity() - pcmBytes.position()) / 2.0 / sampleRate * 1_000_000).toLong()
                            encoder.queueInputBuffer(inIndex, 0, toCopy, ptsUs, 0)
                            pcmBytes.position(pcmBytes.position() + toCopy)
                        }
                    }
                }
                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val outBuf = encoder.getOutputBuffer(outIndex)!!
                        if (bufferInfo.size > 0 && muxerStarted) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) endOfStream = true
                    }
                }
            }
        } finally {
            runCatching { encoder.stop() }
            encoder.release()
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
        }
    }
}
