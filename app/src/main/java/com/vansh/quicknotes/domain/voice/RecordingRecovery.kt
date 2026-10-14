package com.vansh.quicknotes.domain.voice

import java.io.File

/**
 * Pure decision logic + filesystem scan for the crash-safe recorder (spec §6.5).
 * Free of Android framework types so recovery rules are unit-testable.
 */
data class ChunkMeta(
    val id: String,
    val sessionId: String,
    val index: Int,
    val path: String,
    val existsOnDisk: Boolean,
    val createdAt: Long,
)

sealed class RecoveryOutcome {
    data object Nothing : RecoveryOutcome()
    data class Ignore(val sessionId: String) : RecoveryOutcome()
    data class Recover(val sessionId: String, val orderedChunkPaths: List<String>) : RecoveryOutcome()
}

object RecordingRecovery {

    /**
     * Chunks live at <recordingsDir>/<sessionId>/chunk-00042.pcm. Finalization deletes the
     * session dir, so any surviving chunks belong to a recording that never finalized.
     */
    fun scanChunks(recordingsDir: File): List<ChunkMeta> {
        if (!recordingsDir.isDirectory) return emptyList()
        val out = ArrayList<ChunkMeta>()
        recordingsDir.listFiles { f -> f.isDirectory }?.forEach { sessionDir ->
            sessionDir.listFiles { f -> f.isFile && f.name.endsWith(".pcm") }?.forEach { chunk ->
                val index = chunk.name.removePrefix("chunk-").removeSuffix(".pcm").toIntOrNull() ?: return@forEach
                out += ChunkMeta(
                    id = "${sessionDir.name}-$index",
                    sessionId = sessionDir.name,
                    index = index,
                    path = chunk.absolutePath,
                    existsOnDisk = chunk.length() > 0,
                    createdAt = chunk.lastModified(),
                )
            }
        }
        return out
    }

    fun decide(
        chunks: List<ChunkMeta>,
        finalizedSessionIds: Set<String> = emptySet(),
        liveSessionIds: Set<String> = emptySet(),
    ): RecoveryOutcome {
        val usable = chunks.filter { it.existsOnDisk }
            .groupBy { it.sessionId }
            .mapValues { (_, cs) -> cs.sortedBy { it.index } }
        val orphaned = usable.filterKeys { it !in finalizedSessionIds && it !in liveSessionIds }
        if (orphaned.isEmpty()) return RecoveryOutcome.Nothing
        val sessionId = orphaned.maxByOrNull { (_, cs) -> cs.maxOf { it.createdAt } }?.key
            ?: return RecoveryOutcome.Nothing
        return RecoveryOutcome.Recover(sessionId, orphaned.getValue(sessionId).map { it.path })
    }

    fun shouldStopForLowStorage(freeBytes: Long, chunkBytes: Long): Boolean =
        freeBytes < chunkBytes * MIN_CHUNKS_HEADROOM

    const val MIN_CHUNKS_HEADROOM = 4L
}
