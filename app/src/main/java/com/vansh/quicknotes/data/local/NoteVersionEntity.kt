package com.vansh.quicknotes.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Snapshot of a whole note before an AI edit (spec §8: undo >=10 s + version history). */
@Entity(tableName = "note_versions", indices = [Index("noteId")])
data class NoteVersionEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val createdAt: Long,
    /** what created this version: cleanup | title_tags | manual_edit … */
    val label: String,
    /** serialized Note (blocks json) so restore is trivial */
    val snapshot: String,
)
