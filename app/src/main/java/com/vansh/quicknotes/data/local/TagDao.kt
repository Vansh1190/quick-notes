package com.vansh.quicknotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(noteTag: NoteTagEntity)

    @Query("SELECT t.* FROM tags t JOIN note_tags nt ON nt.tagId = t.id WHERE nt.noteId = :noteId ORDER BY t.name ASC")
    suspend fun tagsFor(noteId: String): List<TagEntity>

    @Query("SELECT t.* FROM tags t JOIN note_tags nt ON nt.tagId = t.id WHERE nt.noteId = :noteId ORDER BY t.name ASC")
    fun observeTagsFor(noteId: String): Flow<List<TagEntity>>

    @Query("SELECT t.* FROM tags t JOIN note_tags nt ON nt.tagId = t.id JOIN notes n ON n.id = nt.noteId WHERE n.deletedAt IS NULL AND n.archived = 0 GROUP BY t.id ORDER BY COUNT(*) DESC LIMIT :limit")
    suspend fun popular(limit: Int = 20): List<TagEntity>

    @Query("DELETE FROM note_tags WHERE noteId = :noteId AND tagId = :tagId")
    suspend fun unlink(noteId: String, tagId: String)

    @Transaction
    suspend fun linkAll(noteId: String, names: List<String>) {
        names.filter { it.isNotBlank() }.map { it.trim().lowercase() }.distinct().forEach { name ->
            val id = "tag_" + name.hashCode().toString(16)
            insertTag(TagEntity(id, name))
            link(NoteTagEntity(noteId, id))
        }
    }
}
