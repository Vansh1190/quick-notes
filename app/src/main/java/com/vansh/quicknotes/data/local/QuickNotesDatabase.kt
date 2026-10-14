package com.vansh.quicknotes.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Real FTS5 index. note_id stays UNINDEXED so rows are updated by delete+insert
 * (FTS5 virtual tables have no ON CONFLICT upsert). The table is created by the database
 * callback because Room cannot model FTS5 virtual tables with annotations; all access
 * funnels raw SQL through [SearchDao] (@RawQuery skips compile-time verification).
 */
class Fts5SearchIndex(private val dao: SearchDao) : NoteSearchIndex {

    override suspend fun reindex(noteId: String, title: String, text: String) {
        dao.execute(SimpleSQLiteQuery("DELETE FROM $TABLE WHERE note_id = ?", arrayOf(noteId)))
        dao.execute(
            SimpleSQLiteQuery(
                "INSERT INTO $TABLE (note_id, title, text) VALUES (?, ?, ?)",
                arrayOf(noteId, title, text),
            ),
        )
    }

    override suspend fun remove(noteId: String) {
        dao.execute(SimpleSQLiteQuery("DELETE FROM $TABLE WHERE note_id = ?", arrayOf(noteId)))
    }

    override suspend fun orphanIds(): List<String> =
        dao.search(SimpleSQLiteQuery(ORPHAN_SQL)).map { it.noteId }

    override suspend fun search(query: String, limit: Int): List<String> =
        dao.search(SimpleSQLiteQuery(SEARCH_SQL, arrayOf<Any>(query, limit))).map { it.noteId }

    companion object {
        const val TABLE = "note_fts_index"
        const val CREATE_SQL =
            "CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE USING fts5(" +
                "note_id UNINDEXED, title, text, tokenize = 'unicode61 remove_diacritics 2')"
        private const val SEARCH_SQL =
            "SELECT note_id, bm25($TABLE) AS rank FROM $TABLE WHERE $TABLE MATCH ? ORDER BY rank LIMIT ?"
        private const val ORPHAN_SQL =
            "SELECT note_id, 0 AS rank FROM $TABLE WHERE note_id NOT IN (SELECT id FROM notes)"
    }
}

@Database(
    entities = [
        NoteEntity::class,
        BlockEntity::class,
        TagEntity::class,
        NoteTagEntity::class,
        MediaAssetEntity::class,
        TodoListEntity::class,
        TodoItemEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class QuickNotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun searchDao(): SearchDao

    abstract fun todoDao(): TodoDao

    val searchIndex: NoteSearchIndex by lazy { Fts5SearchIndex(searchDao()) }

    companion object {
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS todo_lists (" +
                        "id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                        "source TEXT NOT NULL, archived INTEGER NOT NULL DEFAULT 0)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS todo_items (" +
                        "id TEXT NOT NULL PRIMARY KEY, listId TEXT NOT NULL, text TEXT NOT NULL, " +
                        "done INTEGER NOT NULL DEFAULT 0, dueAt INTEGER, parentId TEXT, orderIndex INTEGER NOT NULL DEFAULT 0, " +
                        "FOREIGN KEY(listId) REFERENCES todo_lists(id) ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_items_listId ON todo_items(listId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_items_dueAt ON todo_items(dueAt)")
            }
        }

        val callback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                try {
                    db.execSQL(Fts5SearchIndex.CREATE_SQL)
                } catch (t: Exception) {
                    // Test harnesses whose SQLite lacks the fts5 module (Robolectric):
                    // search degrades to the injected fallback; on-device FTS5 always exists.
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                try {
                    db.execSQL(Fts5SearchIndex.CREATE_SQL)
                } catch (t: Exception) {
                    // see onCreate
                }
            }
        }

        fun build(context: Context): QuickNotesDatabase =
            Room.databaseBuilder(context, QuickNotesDatabase::class.java, "quicknotes.db")
                .addCallback(callback)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
