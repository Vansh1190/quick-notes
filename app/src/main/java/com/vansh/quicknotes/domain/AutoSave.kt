package com.vansh.quicknotes.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debounced auto-save (spec §5.1: ~500 ms after the last change, plus explicit flush on
 * pause/stop). Coalescing counts saves: rapid edits produce exactly one write.
 */
class AutoSave(
    private val scope: CoroutineScope,
    private val delayMillis: Long = 500,
    private val save: suspend () -> Unit,
) {
    private var job: Job? = null
    private var dirty = false

    var saveCount: Int = 0
        private set

    fun edit() {
        dirty = true
        job?.cancel()
        job = scope.launch {
            delay(delayMillis)
            flush()
        }
    }

    suspend fun flush() {
        if (!dirty) return
        dirty = false
        saveCount++
        save()
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
