package com.vansh.quicknotes.data.repository

import com.vansh.quicknotes.data.local.BlockEntity
import com.vansh.quicknotes.data.local.MediaAssetEntity
import com.vansh.quicknotes.data.local.NoteDao
import com.vansh.quicknotes.data.local.NoteEntity
import com.vansh.quicknotes.data.local.NoteSearchIndex
import com.vansh.quicknotes.data.local.TagDao
import com.vansh.quicknotes.domain.model.Block
import com.vansh.quicknotes.domain.model.BlockType
import com.vansh.quicknotes.domain.model.ChecklistBlockPayload
import com.vansh.quicknotes.domain.model.DrawingPayload
import com.vansh.quicknotes.domain.model.ImagePayload
import com.vansh.quicknotes.domain.model.LinkPayload
import com.vansh.quicknotes.domain.model.Note
import com.vansh.quicknotes.domain.model.TextPayload
import com.vansh.quicknotes.domain.model.VoicePayload
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/** Extracts the searchable text of a note (title + text blocks + checklist + transcript/OCR/link titles). */
object SearchableText {

    fun fromBlocks(blocks: List<Block>): String = buildString {
        blocks.forEach { block ->
            when (block.type) {
                BlockType.TEXT -> block.text?.text?.let { append(it).append('\n') }
                BlockType.CHECKLIST -> block.checklist?.items?.forEach {
                    append(it.text).append('\n')
                }
                BlockType.VOICE -> block.voice?.let {
                    append(it.transcript.orEmpty()).append('\n')
                }
                BlockType.LINK -> block.link?.let {
                    append(it.url).append(' ').append(it.title.orEmpty()).append('\n')
                }
                else -> Unit
            }
        }
    }

    fun withMedia(blocks: List<Block>, media: List<MediaAssetEntity>): String {
        val mediaByd = media.associateBy { it.id }
        return buildString {
            append(fromBlocks(blocks))
            blocks.filter { it.type == BlockType.IMAGE }.forEach { b ->
                b.image?.assetId?.let { mediaByd[it]?.ocrText }?.let { append(it).append('\n') }
            }
            blocks.filter { it.type == BlockType.VOICE }.forEach { b ->
                b.voice?.assetId?.let { mediaByd[it]?.transcript }?.let { append(it).append('\n') }
            }
        }
    }

    /**
     * Convert user input into a safe FTS5 MATCH query: quote each token, drop FTS
     * operators. An empty result means "no query".
     */
    fun toMatchQuery(raw: String): String? {
        val tokens = raw.trim().split(Regex("\\s+"))
            .map { it.replace("\"", "").trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "\"" + it + "\"*" }
    }
}

@Singleton
class NotesRepository @Inject constructor(
    private val dao: NoteDao,
    private val tagDao: TagDao,
    private val versionDao: com.vansh.quicknotes.data.local.VersionDao,
    private val searchIndex: NoteSearchIndex,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val now: () -> Long = System::currentTimeMillis

    fun observeActiveNotes(): Flow<List<NoteEntity>> = dao.observeActiveNotes()

    fun observeArchived(): Flow<List<NoteEntity>> = dao.observeArchived()

    fun observeTrashed(): Flow<List<NoteEntity>> = dao.observeTrashed()

    fun observeRecent(limit: Int): Flow<List<NoteEntity>> = dao.observeRecent(limit)

    suspend fun loadNote(id: String): Note? {
        val entity = dao.noteById(id) ?: return null
        val blocks = dao.blocksFor(id).map { toDomain(it) }
        return entity.toDomain(blocks)
    }

    suspend fun createNote(initialBlockText: String? = null): String {
        val id = newId()
        val t = now()
        val blocks = mutableListOf<Block>()
        blocks += Block(id = newId(), type = BlockType.TEXT, orderIndex = 0, text = TextPayload(initialBlockText.orEmpty()))
        saveNoteInternal(
            Note(
                id = id,
                title = initialBlockText?.lineSequence()?.firstOrNull().orEmpty(),
                createdAt = t,
                updatedAt = t,
                blocks = blocks,
            ),
        )
        return id
    }

    suspend fun saveNote(note: Note) {
        val title = deriveTitle(note)
        val blocks = note.blocks.mapIndexed { i, b -> b.copy(orderIndex = i) }
        saveNoteInternal(note.copy(title = title, updatedAt = now(), blocks = blocks))
    }

    private suspend fun saveNoteInternal(note: Note) {
        val entities = note.blocks.map { toEntity(note.id, it) }
        val media = dao.mediaForNote(note.id)
        dao.saveNote(
            note = NoteEntity(
                id = note.id,
                title = note.title,
                createdAt = note.createdAt,
                updatedAt = note.updatedAt,
                pinned = note.pinned,
                archived = note.archived,
                deletedAt = note.deletedAt,
                aiSummary = note.aiSummary,
            ),
            blocks = entities,
        )
        searchIndex.reindex(note.id, note.title, SearchableText.withMedia(note.blocks, media))
    }

    private fun deriveTitle(note: Note): String {
        if (note.title.isNotBlank() && note.blocks.none { it.type == BlockType.TEXT }) return note.title
        val firstLine = note.blocks.firstOrNull { it.type == BlockType.TEXT }?.text?.text
            ?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        return firstLine.take(120)
    }

    /** Snapshot the whole note before an AI edit so undo/version history works (spec §8). */
    suspend fun snapshotForAi(noteId: String, label: String) {
        val note = loadNote(noteId) ?: return
        versionDao.insert(
            com.vansh.quicknotes.data.local.NoteVersionEntity(
                id = newId(),
                noteId = noteId,
                createdAt = now(),
                label = label,
                snapshot = json.encodeToString(Note.serializer(), note),
            ),
        )
        versionDao.trim(noteId, 20)
    }

    suspend fun restoreVersion(versionId: String): String? {
        val version = versionDao.byId(versionId) ?: return null
        val note = runCatching { json.decodeFromString(Note.serializer(), version.snapshot) }.getOrNull() ?: return null
        saveNoteInternal(note.copy(updatedAt = now()))
        return note.id
    }

    suspend fun versions(noteId: String): List<com.vansh.quicknotes.data.local.NoteVersionEntity> =
        versionDao.recent(noteId)

    /** apply AI title/tags: title wins over derived, tags are appended (max 6 total) */
    suspend fun applyAiTitleAndTags(noteId: String, title: String, tags: List<String>) {
        val existing = tagDao.tagsFor(noteId).map { it.name }
        tagDao.linkAll(noteId, (existing + tags).distinct().take(6))
        dao.setTitle(noteId, title.trim(), now())
    }

    suspend fun setPinned(id: String, pinned: Boolean) = dao.setPinned(id, pinned, now())

    suspend fun setArchived(id: String, archived: Boolean) = dao.setArchived(id, archived, now())

    suspend fun moveToTrash(id: String) {
        dao.moveToTrash(id, now())
        searchIndex.remove(id)
    }

    suspend fun restore(id: String) {
        dao.restoreFromTrash(id, now())
        loadNote(id)?.let { saveNoteInternal(it.copy(title = it.title)) }
    }

    suspend fun purgeExpiredTrash() {
        dao.purgeExpiredTrash(now() - TRASH_RETENTION_MS) // FK cascade removes blocks; fts cleaned below
        for (hit in searchIndex.orphanIds()) {
            searchIndex.remove(hit)
        }
    }

    suspend fun search(query: String): List<Note> {
        val match = SearchableText.toMatchQuery(query) ?: return emptyList()
        return searchIndex.search(match).mapNotNull { id -> dao.noteById(id)?.toDomain(dao.blocksFor(id).map(::toDomain)) }
    }

    private fun NoteEntity.toDomain(blocks: List<Block>) = Note(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pinned = pinned,
        archived = archived,
        deletedAt = deletedAt,
        aiSummary = aiSummary,
        blocks = blocks,
    )

    private fun toEntity(noteId: String, block: Block): BlockEntity {
        val payload: String = when (block.type) {
            BlockType.TEXT -> json.encodeToString(block.text ?: TextPayload())
            BlockType.CHECKLIST -> json.encodeToString(block.checklist ?: ChecklistBlockPayload())
            BlockType.IMAGE -> json.encodeToString(block.image ?: ImagePayload(""))
            BlockType.DRAWING -> json.encodeToString(block.drawing ?: DrawingPayload())
            BlockType.VOICE -> json.encodeToString(block.voice ?: VoicePayload(""))
            BlockType.LINK -> json.encodeToString(block.link ?: LinkPayload(""))
            BlockType.DIVIDER -> "{}"
            BlockType.CAMERA -> json.encodeToString(block.image ?: ImagePayload(""))
        }
        return BlockEntity(id = block.id, noteId = noteId, type = block.type.wire, orderIndex = block.orderIndex, payload = payload)
    }

    private fun toDomain(entity: BlockEntity): Block {
        val type = BlockType.from(entity.type) ?: BlockType.TEXT
        val payload = runCatching {
            when (type) {
                BlockType.TEXT -> TextPayload.serializer().deserialize(json, entity.payload)
                BlockType.CHECKLIST -> ChecklistBlockPayload.serializer().deserialize(json, entity.payload)
                BlockType.IMAGE -> ImagePayload.serializer().deserialize(json, entity.payload)
                BlockType.DRAWING -> DrawingPayload.serializer().deserialize(json, entity.payload)
                BlockType.VOICE -> VoicePayload.serializer().deserialize(json, entity.payload)
                BlockType.LINK -> LinkPayload.serializer().deserialize(json, entity.payload)
                BlockType.DIVIDER -> null
                BlockType.CAMERA -> ImagePayload.serializer().deserialize(json, entity.payload)
            }
        }.getOrNull()
        return Block(
            id = entity.id,
            type = type,
            orderIndex = entity.orderIndex,
            text = payload as? TextPayload,
            checklist = payload as? ChecklistBlockPayload,
            image = payload as? ImagePayload,
            drawing = payload as? DrawingPayload,
            voice = payload as? VoicePayload,
            link = payload as? LinkPayload,
        )
    }

    private fun newId(): String = java.util.UUID.randomUUID().toString()

    companion object {
        const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}

private fun <T> kotlinx.serialization.KSerializer<T>.deserialize(json: Json, string: String): T =
    json.decodeFromString(this, string)
