package com.vansh.quicknotes.data.local

/**
 * JVM-test stand-in for the FTS5 index (Robolectric's SQLite lacks the fts5 module).
 * Implements the same contract with substring matching; sufficient for repository
 * behavior tests. Real FTS5 is covered by androidTest on-device.
 */
class FakeNoteSearchIndex : NoteSearchIndex {

    private data class Row(val title: String, val text: String)

    private val rows = mutableMapOf<String, Row>()

    override suspend fun reindex(noteId: String, title: String, text: String) {
        rows[noteId] = Row(title, text)
    }

    override suspend fun remove(noteId: String) {
        rows.remove(noteId)
    }

    override suspend fun orphanIds(): List<String> = emptyList()

    override suspend fun search(query: String, limit: Int): List<String> {
        val tokens = query.split(Regex("\\s+"))
            .map { it.removeSuffix("*").trim('"').lowercase() }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return rows.filter { (_, row) ->
            val hay = (row.title + " " + row.text).lowercase()
            tokens.all { hay.contains(it) }
        }.keys.take(limit).toList()
    }
}
