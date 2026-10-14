package com.vansh.quicknotes

import com.vansh.quicknotes.domain.ai.AiClient
import com.vansh.quicknotes.domain.ai.AiException
import com.vansh.quicknotes.domain.ai.AiService
import com.vansh.quicknotes.domain.ai.ChatMessage
import com.vansh.quicknotes.domain.ai.ChatResult
import dagger.Lazy

/** Offline AiService for JVM tests: every call fails like an unconfigured device. */
object TestDoubles {
    val OfflineAiLazy: Lazy<AiService> = Lazy {
        AiService(object : AiClient {
            override fun isConfigured() = false
            override suspend fun chat(
                messages: List<ChatMessage>,
                temperature: Double,
                maxTokens: Int,
                jsonMode: Boolean,
                toolName: String?,
                toolDescription: String,
                toolSchema: kotlinx.serialization.json.JsonObject?,
            ): ChatResult = throw AiException("offline")
        })
    }

    fun aiReturning(reply: String): Lazy<AiService> = Lazy {
        AiService(object : AiClient {
            override fun isConfigured() = true
            override suspend fun chat(
                messages: List<ChatMessage>,
                temperature: Double,
                maxTokens: Int,
                jsonMode: Boolean,
                toolName: String?,
                toolDescription: String,
                toolSchema: kotlinx.serialization.json.JsonObject?,
            ) = ChatResult(reply, "stop", 1, null)
        })
    }
}
