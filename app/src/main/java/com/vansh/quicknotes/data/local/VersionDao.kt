package com.vansh.quicknotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface VersionDao {
    @Insert
    suspend fun insert(version: NoteVersionEntity)

    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(noteId: String, limit: Int = 20): List<NoteVersionEntity>

    @Query("SELECT * FROM note_versions WHERE id = :id")
    suspend fun byId(id: String): NoteVersionEntity?

    @Query("DELETE FROM note_versions WHERE noteId = :noteId AND createdAt < :before")
    suspend fun deleteOlderThan(noteId: String, before: Long)

    @Query(
        """
        DELETE FROM note_versions WHERE id NOT IN (
            SELECT id FROM note_versions WHERE noteId = :noteId ORDER BY createdAt DESC LIMIT :keep
        ) AND noteId = :noteId
        """,
    )
    suspend fun trim(noteId: String, keep: Int)
}
