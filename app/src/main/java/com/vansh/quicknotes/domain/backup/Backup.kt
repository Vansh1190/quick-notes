package com.vansh.quicknotes.domain.backup

import android.content.Context
import com.vansh.quicknotes.data.local.BlockEntity
import com.vansh.quicknotes.data.local.MediaAssetEntity
import com.vansh.quicknotes.data.local.TagDao
import com.vansh.quicknotes.data.media.MediaStore
import com.vansh.quicknotes.data.repository.NotesRepository
import com.vansh.quicknotes.domain.model.Block
import com.vansh.quicknotes.domain.model.Note
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Backup/export (spec §9): a ZIP with note.json (lossless rows) + one Markdown per note +
 * every media file under media/ + manifest.json. Import restores exactly: same ids,
 * timestamps, pins, archive/trash state, tags and media bytes. Keys never travel.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: NotesRepository,
    private val tagDao: TagDao,
    private val mediaStore: MediaStore,
) {

    @Serializable
    private data class Manifest(
        val app: String = "quick-notes",
        val version: Int = 1,
        val exportedAt: Long = 0,
        val noteCount: Int = 0,
        val mediaCount: Int = 0,
    )

    @Serializable
    private data class BlockRow(
        val id: String,
        val type: String,
        val orderIndex: Int,
        val payload: String = "{}",
    )

    @Serializable
    private data class MediaRow(
        val id: String,
        val kind: String,
        val path: String,
        val mimeType: String,
        val durationMs: Long? = null,
        val width: Int? = null,
        val height: Int? = null,
        val transcript: String? = null,
        val ocrText: String? = null,
        val createdAt: Long = 0,
    )

    @Serializable
    private data class NoteRecord(
        val id: String,
        val title: String = "",
        val createdAt: Long = 0,
        val updatedAt: Long = 0,
        val pinned: Boolean = false,
        val archived: Boolean = false,
        val deletedAt: Long? = null,
        val aiSummary: String? = null,
        val blocks: List<BlockRow> = emptyList(),
        val tags: List<String> = emptyList(),
        val media: List<MediaRow> = emptyList(),
    )

    private fun BlockEntity.toRow() = BlockRow(id, type, orderIndex, payload)

    private fun BlockRow.toEntity(noteId: String) = BlockEntity(id = id, noteId = noteId, type = type, orderIndex = orderIndex, payload = payload)

    private fun MediaAssetEntity.toRow() = MediaRow(id, kind, path, mimeType, durationMs, width, height, transcript, ocrText, createdAt)

    private fun MediaRow.toEntity(noteId: String?) = MediaAssetEntity(
        id = id, noteId = noteId, kind = kind, path = path, mimeType = mimeType,
        durationMs = durationMs, width = width, height = height, transcript = transcript,
        ocrText = ocrText, createdAt = createdAt,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun exportZip(target: File): Int {
        val notes = repository.allNotesForExport()
        var mediaCount = 0
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            val records = notes.map { note ->
                NoteRecord(
                    id = note.id,
                    title = note.title,
                    createdAt = note.createdAt,
                    updatedAt = note.updatedAt,
                    pinned = note.pinned,
                    archived = note.archived,
                    deletedAt = note.deletedAt,
                    aiSummary = note.aiSummary,
                    blocks = repository.rawBlocks(note.id).map { it.toRow() },
                    tags = tagDao.tagsFor(note.id).map { it.name },
                    media = repository.rawMedia(note.id).map { it.toRow() },
                )
            }
            zip.putNextEntry(ZipEntry("note.json"))
            zip.write(json.encodeToString(ListSerializer(NoteRecord.serializer()), records).toByteArray())
            zip.closeEntry()

            records.forEach { record ->
                val safe = record.title.ifBlank { "note" }
                    .replace(Regex("[^A-Za-z0-9 _-]"), "").take(40).trim().ifBlank { "note" }
                zip.putNextEntry(ZipEntry("markdown/$safe-${record.id.take(8)}.md"))
                zip.write(toMarkdown(record).toByteArray())
                zip.closeEntry()
            }

            records.forEach { record ->
                record.media.forEach { asset ->
                    val file = mediaStore.fileFor(asset.path)
                    if (file.isFile) {
                        mediaCount += 1
                        zip.putNextEntry(ZipEntry("media/${asset.path}"))
                        FileInputStream(file).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }

            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(
                json.encodeToString(
                    Manifest.serializer(),
                    Manifest(exportedAt = System.currentTimeMillis(), noteCount = records.size, mediaCount = mediaCount),
                ).toByteArray(),
            )
            zip.closeEntry()
        }
        return notes.size
    }

    data class ImportResult(val notes: Int, val media: Int, val skipped: Int)

    suspend fun importZip(source: File): ImportResult {
        var notes = 0
        var media = 0
        var skipped = 0
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(FileInputStream(source)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        val manifest = entries["manifest.json"] ?: return ImportResult(0, 0, 0)
        val version = runCatching { json.decodeFromString(Manifest.serializer(), manifest.decodeToString()).version }.getOrNull()
        if (version == null || version > 1) return ImportResult(0, 0, entries.size)

        val records = entries["note.json"]?.decodeToString()?.let {
            runCatching { json.decodeFromString(ListSerializer(NoteRecord.serializer()), it) }.getOrNull()
        } ?: return ImportResult(0, 0, entries.size)

        records.forEach { record ->
            if (repository.exists(record.id)) {
                skipped += 1
                return@forEach
            }
            // note row first: blocks and media both have FKs to it
            repository.restoreNote(
                Note(
                    id = record.id,
                    title = record.title,
                    createdAt = record.createdAt,
                    updatedAt = record.updatedAt,
                    pinned = record.pinned,
                    archived = record.archived,
                    deletedAt = record.deletedAt,
                    aiSummary = record.aiSummary,
                    blocks = record.blocks.map { repository.toDomain(it.toEntity(record.id)) },
                ),
            )
            record.media.forEach { asset ->
                val payload = entries["media/${asset.path}"]
                if (payload != null) {
                    mediaStore.writeRestored(asset.path, payload)
                    mediaStore.upsert(asset.toEntity(record.id))
                    media += 1
                }
            }
            tagDao.linkAll(record.id, record.tags)
            notes += 1
        }
        return ImportResult(notes, media, skipped)
    }

    /** A note as Markdown, for the Settings share path. */
    suspend fun markdownFor(noteId: String): String? {
        val note = repository.loadNote(noteId) ?: return null
        return toMarkdown(
            NoteRecord(
                id = note.id,
                title = note.title,
                createdAt = note.createdAt,
                updatedAt = note.updatedAt,
                blocks = repository.rawBlocks(note.id).map { it.toRow() },
                tags = tagDao.tagsFor(noteId).map { it.name },
            ),
        )
    }

    private fun toMarkdown(record: NoteRecord): String = buildString {
        if (record.title.isNotBlank()) {
            appendLine("# ${record.title}")
            appendLine()
        }
        if (record.tags.isNotEmpty()) {
            appendLine(record.tags.joinToString(" ") { "#$it" })
            appendLine()
        }
        record.blocks.forEach { entity ->
            val block = repository.toDomain(entity.toEntity(record.id))
            when {
                block.text?.text?.isNotBlank() == true -> appendLine(block.text.text)
                block.checklist != null -> block.checklist.items.forEach { item ->
                    appendLine(if (item.done) "- [x] ${item.text}" else "- [ ] ${item.text}")
                }
                block.image?.assetId != null -> appendLine("![](media:${block.image.assetId})")
                block.drawing?.previewAssetId != null -> appendLine("_drawing_ ![](media:${block.drawing.previewAssetId})")
                block.voice?.assetId != null -> {
                    appendLine("_voice_ media:${block.voice.assetId}")
                    block.voice.transcript?.let { appendLine("> $it") }
                }
                block.link != null -> appendLine("[${block.link.title ?: block.link.url}](${block.link.url})")
                block.type.wire == "divider" -> appendLine("---")
            }
            appendLine()
        }
        appendLine("_created ${record.createdAt} · updated ${record.updatedAt} · id ${record.id}_")
    }

    /** Export lands in app external files: reachable via SAF share without storage perms. */
    fun defaultExportFile(): File {
        val dir = File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        return File(dir, "quick-notes-$stamp.zip")
    }
}
