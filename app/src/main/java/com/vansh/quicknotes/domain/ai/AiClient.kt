package com.vansh.quicknotes.domain.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.net.ssl.HttpsURLConnection
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI-compatible chat client for the user's endpoint (spec §8). Single endpoint, Bearer
 * auth, model from Settings. The upstream is a reasoning model (vLLM) that may return the
 * answer under `content`, sometimes only in `reasoning_content`, with `</think>` markers —
 * [ChatChoice.content] normalizes all of that. [AiException] is always safe to show: it
 * never contains the key or upstream bodies beyond a short status line.
 */
class AiException(message: String, val offline: Boolean = false, val status: Int? = null) : Exception(message)

data class ChatMessage(val role: String, val content: String)

data class ChatResult(val content: String, val finishReason: String?, val usageTokens: Int?)

interface AiClient {
    suspend fun chat(
        messages: List<ChatMessage>,
        temperature: Double = 0.3,
        maxTokens: Int = 900,
        jsonMode: Boolean = false,
    ): ChatResult

    fun isConfigured(): Boolean
}

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Double,
    @kotlinx.serialization.SerialName("max_tokens") val maxTokens: Int,
    @kotlinx.serialization.SerialName("response_format") val responseFormat: ResponseFormatDto? = null,
)

@Serializable
private data class ChatMessageDto(val role: String, val content: String)

@Serializable
private data class ResponseFormatDto(val type: String)

@Serializable
private data class ChatResponseDto(
    val choices: List<ChoiceDto> = emptyList(),
    val usage: UsageDto? = null,
    val error: JsonObject? = null,
)

@Serializable
private data class ChoiceDto(
    val message: MessageDto? = null,
    @kotlinx.serialization.SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
private data class MessageDto(
    val content: String? = null,
    @kotlinx.serialization.SerialName("reasoning_content") val reasoningContent: String? = null,
)

@Serializable
private data class UsageDto(@kotlinx.serialization.SerialName("total_tokens") val totalTokens: Int? = null)

class HttpAiClient(
    private val baseUrl: () -> String,
    private val apiKey: () -> String?,
    private val model: () -> String,
) : AiClient {

    override fun isConfigured(): Boolean = baseUrl().isNotBlank() && apiKey() != null

    override suspend fun chat(
        messages: List<ChatMessage>,
        temperature: Double,
        maxTokens: Int,
        jsonMode: Boolean,
    ): ChatResult = withContext(Dispatchers.IO) {
        val base = baseUrl().trimEnd('/')
        val key = apiKey()
        if (base.isBlank() || key == null) throw AiException("AI is not set up yet", offline = true)
        val body = Json.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(
                model = model(),
                messages = messages.map { ChatMessageDto(it.role, it.content) },
                temperature = temperature,
                maxTokens = maxTokens,
                responseFormat = if (jsonMode) ResponseFormatDto("json_object") else null,
            ),
        )
        val connection = try {
            URL("$base/chat/completions").openConnection() as HttpURLConnection
        } catch (t: Exception) {
            throw AiException("Bad AI URL", offline = true)
        }
        try {
            if (connection is HttpsURLConnection) {
                // no-op default TLS; explicit hook for future cert pinning
            }
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 120_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            if (status !in 200..299) {
                val short = text.take(200).replace("\n", " ")
                throw AiException("AI endpoint returned $status: $short", status = status)
            }
            val dto = try {
                Json.decodeFromString(ChatResponseDto.serializer(), text)
            } catch (t: Exception) {
                throw AiException("Unexpected reply from the AI endpoint")
            }
            val choice = dto.choices.firstOrNull()
            val raw = choice?.message?.content.orEmpty().ifBlank { choice?.message?.reasoningContent.orEmpty() }
            val cleaned = normalizeReasoning(raw)
            if (cleaned.isBlank()) throw AiException("The AI returned nothing usable")
            ChatResult(cleaned, choice?.finishReason, dto.usage?.totalTokens)
        } catch (e: AiException) {
            throw e
        } catch (t: java.net.SocketTimeoutException) {
            throw AiException("The AI took too long to answer", offline = true)
        } catch (t: java.io.IOException) {
            throw AiException("Couldn't reach the AI endpoint", offline = true)
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    companion object {
        /** strip  reasoning blobs and stray code fences around the actual answer */
        fun normalizeReasoning(raw: String): String {
            var s = raw
            val close = s.indexOf("</think>")
            if (close >= 0) s = s.substring(close + "</think>".length)
            s = s.trim()
            if (s.startsWith("```")) {
                s = s.removePrefix("```json").removePrefix("```").trim()
                val fence = s.lastIndexOf("```")
                if (fence >= 0) s = s.substring(0, fence).trim()
            }
            return s.trim()
        }
    }
}

object JsonExtractor {
    /** Grab the first JSON object/array from a reply that may contain prose or fences. */
    fun firstJson(text: String): JsonElement? {
        val start = listOf(text.indexOf('{'), text.indexOf('[')).filter { it >= 0 }.minOrNull() ?: return null
        val open = text[start]
        val close = if (open == '{') '}' else ']'
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    open -> depth += 1
                    close -> {
                        depth -= 1
                        if (depth == 0) {
                            return runCatching { Json.parseToJsonElement(text.substring(start, i + 1)) }.getOrNull()
                        }
                    }
                }
            }
        }
        return null
    }

    fun obj(text: String): JsonObject? = firstJson(text) as? JsonObject

    fun str(obj: JsonObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { k -> (obj[k] as? JsonPrimitive)?.contentOrNull }
}
