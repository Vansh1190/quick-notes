package com.vansh.quicknotes.data.local

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery

/**
 * FTS access via @RawQuery: Room cannot model the FTS5 virtual table with annotations
 * (created in the DB callback instead), and @RawQuery skips compile-time verification.
 * INSERT/DELETE statements are executed through the query cursor (steps once, no rows);
 * this is the pattern documented for FTS5 + Room.
 */
@Dao
interface SearchDao {
    @RawQuery
    suspend fun search(query: SupportSQLiteQuery): List<NoteSearchHit>

    @RawQuery
    suspend fun execute(query: SupportSQLiteQuery): Int

    @RawQuery
    suspend fun allIds(query: SupportSQLiteQuery): List<NoteSearchHit>
}
