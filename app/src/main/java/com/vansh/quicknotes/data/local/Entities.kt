package com.vansh.quicknotes.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [Index("updatedAt"), Index("pinned"), Index("deletedAt")],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    /** epoch millis when moved to trash; null = active. Auto-purged after 30 days. */
    val deletedAt: Long? = null,
    val folderId: String? = null,
    val aiSummary: String? = null,
)

@Entity(
    tableName = "blocks",
    foreignKeys = [
        ForeignKey(NoteEntity::class, ["id"], ["noteId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("noteId")],
)
data class BlockEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    /** text | checklist | image | camera | drawing | voice | link | divider */
    val type: String,
    val orderIndex: Int,
    /** JSON payload; shape depends on [type]. */
    val payload: String = "{}",
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(NoteEntity::class, ["id"], ["noteId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class NoteTagEntity(
    val noteId: String,
    val tagId: String,
)

@Entity(
    tableName = "media_assets",
    indices = [Index("noteId")],
)
data class MediaAssetEntity(
    @PrimaryKey val id: String,
    val noteId: String? = null,
    /** image | audio | drawing_preview | recording_chunk */
    val kind: String,
    /** relative path inside app-private media dir */
    val path: String,
    val mimeType: String,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val transcript: String? = null,
    val ocrText: String? = null,
    val createdAt: Long = 0,
)

/** Search hit POJO returned by the raw FTS5 query. */
data class NoteSearchHit(
    @ColumnInfo(name = "note_id") val noteId: String,
    @ColumnInfo(name = "rank") val rank: Double,
)
