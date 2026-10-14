package com.vansh.quicknotes.data.media

import android.content.Context
import android.graphics.BitmapFactory
import com.vansh.quicknotes.data.local.MediaAssetEntity
import com.vansh.quicknotes.data.local.NoteDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-private media storage (spec §3): files live under filesDir/media/<kind>/... and are
 * referenced from the DB by RELATIVE path, so backup/restore can move the whole tree.
 */
@Singleton
class MediaStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: NoteDao,
) {
    val rootDir: File get() = File(context.filesDir, "media").apply { mkdirs() }
    private val root: File get() = rootDir

    fun fileFor(relativePath: String): File = File(root, relativePath)

    suspend fun saveImage(bytes: ByteArray, noteId: String?, extension: String = "jpg"): MediaAssetEntity {
        val id = UUID.randomUUID().toString()
        val rel = "image/$id.$extension"
        write(rel, bytes)
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        return upsert(
            MediaAssetEntity(
                id = id,
                noteId = noteId,
                kind = "image",
                path = rel,
                mimeType = if (extension == "png") "image/png" else "image/jpeg",
                width = opts.outWidth.takeIf { it > 0 },
                height = opts.outHeight.takeIf { it > 0 },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun saveRecordingFinal(bytes: ByteArray, noteId: String?, durationMs: Long): MediaAssetEntity {
        val id = UUID.randomUUID().toString()
        val rel = "audio/$id.m4a"
        write(rel, bytes)
        return upsert(
            MediaAssetEntity(
                id = id,
                noteId = noteId,
                kind = "audio",
                path = rel,
                mimeType = "audio/mp4",
                durationMs = durationMs,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Crash-safe recording writes (spec §6.5): chunks are tracked so recovery finds orphans. */
    suspend fun saveRecordingChunk(bytes: ByteArray, sessionId: String, index: Int): MediaAssetEntity {
        val rel = "recordings/$sessionId/chunk-%05d.pcm".format(index)
        write(rel, bytes)
        return upsert(
            MediaAssetEntity(
                id = "$sessionId-$index",
                noteId = null,
                kind = "recording_chunk",
                path = rel,
                mimeType = "audio/raw",
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteChunk(assetId: String) {
        val asset = dao.mediaById(assetId) ?: return
        fileFor(asset.path).delete()
        dao.deleteMediaById(assetId)
    }

    suspend fun saveDrawingPreview(pngBytes: ByteArray, noteId: String?, assetId: String): MediaAssetEntity {
        val rel = "drawing/$assetId.png"
        write(rel, pngBytes)
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size, opts)
        return upsert(
            MediaAssetEntity(
                id = assetId,
                noteId = noteId,
                kind = "drawing_preview",
                path = rel,
                mimeType = "image/png",
                width = opts.outWidth.takeIf { it > 0 },
                height = opts.outHeight.takeIf { it > 0 },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun upsert(asset: MediaAssetEntity): MediaAssetEntity {
        dao.upsertMedia(asset)
        return asset
    }

    suspend fun byId(id: String): MediaAssetEntity? = dao.mediaById(id)

    private fun write(relativePath: String, bytes: ByteArray) {
        val f = File(root, relativePath)
        f.parentFile?.mkdirs()
        FileOutputStream(f).use { it.write(bytes) }
    }
}
