package com.vansh.quicknotes.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TextPayload(
    val text: String = "",
    val spans: List<SpanPayload> = emptyList(),
)

@Serializable
data class SpanPayload(
    val start: Int,
    val end: Int,
    /** bold | italic | heading1 | heading2 | bullet | numbered */
    val kind: String,
)

@Serializable
data class ChecklistBlockPayload(
    val items: List<ChecklistItemPayload> = emptyList(),
)

@Serializable
data class ChecklistItemPayload(
    val id: String,
    val text: String = "",
    val done: Boolean = false,
)

@Serializable
data class ImagePayload(
    val assetId: String,
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class DrawingPayload(
    val strokes: List<StrokePayload> = emptyList(),
    val previewAssetId: String? = null,
)

@Serializable
data class StrokePayload(
    val points: List<Float> = emptyList(),
    val colorArgb: Long = 0xFF1C1C1EL,
    val widthPx: Float = 4f,
    /** pen | highlighter */
    val kind: String = "pen",
)

@Serializable
data class VoicePayload(
    val assetId: String,
    val durationMs: Long = 0,
    val waveform: List<Float> = emptyList(),
    val transcript: String? = null,
)

@Serializable
data class LinkPayload(
    val url: String,
    val title: String? = null,
    val siteName: String? = null,
    val thumbnailPath: String? = null,
    val fetched: Boolean = false,
)

enum class BlockType(val wire: String) {
    TEXT("text"),
    CHECKLIST("checklist"),
    IMAGE("image"),
    CAMERA("image"),
    DRAWING("drawing"),
    VOICE("voice"),
    LINK("link"),
    DIVIDER("divider");

    companion object {
        fun from(wire: String): BlockType? = entries.firstOrNull { it.wire == wire }
    }
}

@Serializable
data class Block(
    val id: String,
    val type: BlockType,
    val orderIndex: Int = 0,
    val text: TextPayload? = null,
    val checklist: ChecklistBlockPayload? = null,
    val image: ImagePayload? = null,
    val drawing: DrawingPayload? = null,
    val voice: VoicePayload? = null,
    val link: LinkPayload? = null,
)

@Serializable
data class Note(
    val id: String,
    val title: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val deletedAt: Long? = null,
    val aiSummary: String? = null,
    val blocks: List<Block> = emptyList(),
)
