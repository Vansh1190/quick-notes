package com.vansh.quicknotes.data.local

/**
 * Search abstraction so repository logic is testable on the JVM (Robolectric's SQLite
 * is built without FTS5) while production uses the real FTS5 index via [Fts5SearchIndex].
 * Instrumented tests (androidTest) exercise the real FTS5 implementation on-device.
 */
interface NoteSearchIndex {
    suspend fun reindex(noteId: String, title: String, text: String)

    suspend fun remove(noteId: String)

    /** note_ids present in the index whose note rows no longer exist. */
    suspend fun orphanIds(): List<String>

    /** note ids matching a query, best rank first. */
    suspend fun search(query: String, limit: Int = 100): List<String>
}
