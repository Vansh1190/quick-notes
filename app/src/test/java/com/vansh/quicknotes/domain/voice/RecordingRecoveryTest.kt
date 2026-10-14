package com.vansh.quicknotes.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingRecoveryTest {

    private fun chunk(session: String, index: Int, exists: Boolean = true, at: Long = index * 10L) =
        ChunkMeta("$session-$index", session, index, "recordings/$session/chunk-%05d.pcm".format(index), exists, at)

    @Test
    fun `no chunks means nothing to recover`() {
        assertEquals(RecoveryOutcome.Nothing, RecordingRecovery.decide(emptyList(), emptySet(), emptySet()))
    }

    @Test
    fun `missing files on disk do not count`() {
        val chunks = listOf(chunk("s1", 0, exists = false), chunk("s1", 1, exists = false))
        assertEquals(RecoveryOutcome.Nothing, RecordingRecovery.decide(chunks, emptySet(), emptySet()))
    }

    @Test
    fun `finalized or live sessions are ignored`() {
        val finalized = listOf(chunk("s1", 0), chunk("s1", 1))
        assertEquals(RecoveryOutcome.Nothing, RecordingRecovery.decide(finalized, setOf("s1"), emptySet()))
        val live = listOf(chunk("s2", 0))
        assertEquals(RecoveryOutcome.Nothing, RecordingRecovery.decide(live, emptySet(), setOf("s2")))
    }

    @Test
    fun `orphaned chunks are recovered in index order`() {
        val chunks = listOf(chunk("s1", 2), chunk("s1", 0), chunk("s1", 1))
        val outcome = RecordingRecovery.decide(chunks, emptySet(), emptySet())
        assertTrue(outcome is RecoveryOutcome.Recover)
        outcome as RecoveryOutcome.Recover
        assertEquals("s1", outcome.sessionId)
        assertEquals(
            listOf("recordings/s1/chunk-00000.pcm", "recordings/s1/chunk-00001.pcm", "recordings/s1/chunk-00002.pcm"),
            outcome.orderedChunkPaths,
        )
    }

    @Test
    fun `newest orphaned session wins when several dangle`() {
        val old = listOf(chunk("old", 0, at = 100), chunk("old", 1, at = 110))
        val newer = listOf(chunk("new", 0, at = 500), chunk("new", 1, at = 510))
        val outcome = RecordingRecovery.decide(old + newer, setOf("old"), emptySet())
        assertTrue(outcome is RecoveryOutcome.Recover)
        assertEquals("new", (outcome as RecoveryOutcome.Recover).sessionId)
    }

    @Test
    fun `low storage graceful stop triggers below headroom`() {
        assertEquals(false, RecordingRecovery.shouldStopForLowStorage(freeBytes = 100_000, chunkBytes = 16_000))
        assertEquals(true, RecordingRecovery.shouldStopForLowStorage(freeBytes = 60_000, chunkBytes = 16_000))
    }

    private fun assertTrue(condition: Boolean) = org.junit.Assert.assertTrue(condition)
}
