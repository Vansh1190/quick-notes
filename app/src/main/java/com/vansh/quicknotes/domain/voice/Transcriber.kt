package com.vansh.quicknotes.domain.voice

import com.vansh.quicknotes.data.media.MediaStore
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transcription of saved voice clips (spec §8.1). Android's SpeechRecognizer only does live
 * microphone recognition — it cannot transcribe a stored file — and Vansh's endpoint does not
 * expose /audio/transcriptions (verified 404 in Phase 0). So the shipped default is a no-op:
 * voice clips simply have no transcript line. If the user's endpoint later adds
 * /audio/transcriptions, [ServerTranscriber] handles it; the AI settings toggle stays hidden
 * until a successful capability probe.
 */
interface Transcriber {
    suspend fun transcribe(assetId: String): String?
}

@Singleton
class StubTranscriber @Inject constructor() : Transcriber {
    override suspend fun transcribe(assetId: String): String? = null
}

/**
 * Sends the clip to {baseUrl}/audio/transcriptions (multipart, OpenAI-style). Only constructed
 * when the user enables server transcription AND a capability probe succeeds.
 */
@Singleton
class ServerTranscriber @Inject constructor(
    private val mediaStore: MediaStore,
    private val endpoint: com.vansh.quicknotes.data.settings.SettingsRepository,
    private val secretStore: com.vansh.quicknotes.crypto.SecretStore,
) : Transcriber {
    override suspend fun transcribe(assetId: String): String? {
        val settings = endpoint.current()
        if (!settings.serverTranscription || settings.baseUrl.isBlank()) return null
        val asset = mediaStore.byId(assetId) ?: return null
        val file = mediaStore.fileFor(asset.path)
        if (!file.exists()) return null
        return runCatching { upload(settings.baseUrl, secretStore.getApiKey().orEmpty(), settings.transcriptionModel, file) }.getOrNull()
    }

    private fun upload(baseUrl: String, apiKey: String, model: String, file: File): String? {
        val boundary = "----qn" + System.currentTimeMillis()
        val conn = java.net.URL(baseUrl.trimEnd('/') + "/audio/transcriptions").openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.doOutput = true
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.outputStream.use { out ->
            fun wr(s: String) = out.write(s.toByteArray())
            wr("--$boundary\r\n")
            wr("Content-Disposition: form-data; name=\"model\"\r\n\r\n$model\r\n")
            wr("--$boundary\r\n")
            wr("Content-Disposition: form-data; name=\"file\"; filename=\"clip\"\r\nContent-Type: ${assetMime(file)}\r\n\r\n")
            file.inputStream().use { it.copyTo(out) }
            wr("\r\n--$boundary--\r\n")
        }
        if (conn.responseCode !in 200..299) return null
        val body = conn.inputStream.bufferedReader().readText()
        return extractText(body)
    }

    private fun assetMime(file: File): String = if (file.name.endsWith(".wav")) "audio/wav" else "audio/mp4"

    private fun extractText(json: String): String? =
        Regex("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)?.groupValues?.get(1)?.replace("\\n", "\n")
}
