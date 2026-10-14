package com.vansh.quicknotes.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide recording state hub so the UI (recording sheet, home) observes what the
 * foreground service is doing without binding to it.
 */
object RecordingSessions {

    data class Active(val sessionId: String, val noteId: String?, val startedAt: Long, val paused: Boolean = false)

    data class Stopped(val sessionId: String, val noteId: String?, val durationMs: Long)

    private val _active = MutableStateFlow<Active?>(null)
    val active: StateFlow<Active?> = _active.asStateFlow()

    private val _stopped = MutableStateFlow<Stopped?>(null)
    val stopped: StateFlow<Stopped?> = _stopped.asStateFlow()

    fun publishActive(active: Active) {
        _active.value = active
    }

    fun publishPaused(paused: Boolean) {
        _active.value = _active.value?.copy(paused = paused)
    }

    fun publishStopped(stopped: Stopped) {
        _active.value = null
        _stopped.value = stopped
    }

    fun consumeStopped() {
        _stopped.value = null
    }
}
