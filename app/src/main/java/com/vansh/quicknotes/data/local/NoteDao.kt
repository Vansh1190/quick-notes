package com.vansh.quicknotes.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Upsert
    suspend fun upsertNote(note: NoteEntity)

    @Upsert
    suspend fun upsertBlocks(blocks: List<BlockEntity>)

    @Delete
    suspend fun deleteBlocks(blocks: List<BlockEntity>)

    @Query("DELETE FROM blocks WHERE id IN (:ids)")
    suspend fun deleteBlockByIds(ids: List<String>)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun noteById(id: String): NoteEntity?

    @Query("SELECT * FROM blocks WHERE noteId = :noteId ORDER BY orderIndex ASC")
    suspend fun blocksFor(noteId: String): List<BlockEntity>

    @Query("SELECT * FROM blocks WHERE id = :id")
    suspend fun blockById(id: String): BlockEntity?

    @Query("SELECT MAX(orderIndex) FROM blocks WHERE noteId = :noteId")
    suspend fun maxOrderIndex(noteId: String): Int?

    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAt IS NULL AND archived = 0
        ORDER BY pinned DESC, updatedAt DESC
        """,
    )
    fun observeActiveNotes(): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAt IS NULL AND archived = 0
        ORDER BY pinned DESC, updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun activeNotesOnce(limit: Int): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND archived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashed(): Flow<List<NoteEntity>>

    @Query("UPDATE notes SET pinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, now: Long)

    @Query("UPDATE notes SET archived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, now: Long)

    @Query("UPDATE notes SET deletedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun moveToTrash(id: String, now: Long)

    @Query("UPDATE notes SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restoreFromTrash(id: String, now: Long)

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeExpiredTrash(cutoff: Long)

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NoteEntity>>

    @Query(
        """
        UPDATE notes SET title = :title, updatedAt = :now WHERE id = :id
        """,
    )
    suspend fun setTitle(id: String, title: String, now: Long)

    @Transaction
    suspend fun saveNote(note: NoteEntity, blocks: List<BlockEntity>) {
        upsertNote(note)
        if (blocks.isNotEmpty()) {
            val existing = blocksFor(note.id).associateBy { it.id }
            val incomingIds = blocks.mapTo(mutableSetOf()) { it.id }
            val removed = existing.values.filter { it.id !in incomingIds }
            if (removed.isNotEmpty()) deleteBlocks(removed)
            upsertBlocks(blocks)
        }
    }

    @Query("SELECT * FROM media_assets WHERE noteId = :noteId ORDER BY createdAt ASC")
    suspend fun mediaForNote(noteId: String): List<MediaAssetEntity>

    @Query("SELECT * FROM media_assets WHERE id = :id")
    suspend fun mediaById(id: String): MediaAssetEntity?

    @Query("DELETE FROM media_assets WHERE id = :id")
    suspend fun deleteMediaById(id: String)

    @Upsert
    suspend fun upsertMedia(asset: MediaAssetEntity)

    @Query("SELECT * FROM media_assets WHERE kind = 'recording_chunk' ORDER BY createdAt ASC")
    suspend fun allRecordingChunks(): List<MediaAssetEntity>
}
