package com.vansh.quicknotes.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vansh.quicknotes.data.local.NoteEntity
import com.vansh.quicknotes.data.repository.NotesRepository
import com.vansh.quicknotes.data.repository.SearchableText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val notes: List<NoteEntity> = emptyList(),
    val pinned: List<NoteEntity> = emptyList(),
    val query: String = "",
    val searching: Boolean = false,
    val results: List<NoteEntity> = emptyList(),
    val recap: String? = null,
    val recapBusy: Boolean = false,
)

sealed interface AskState {
    data object Idle : AskState
    data object Busy : AskState
    data class Answer(val text: String, val noteIds: List<String>) : AskState
    data class Failed(val message: String) : AskState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: NotesRepository,
    private val ai: dagger.Lazy<com.vansh.quicknotes.domain.ai.AiService>,
    private val settings: com.vansh.quicknotes.data.settings.SettingsRepository,
) : ViewModel() {

    private val _askState = MutableStateFlow<AskState>(AskState.Idle)
    val askState: StateFlow<AskState> = _askState.asStateFlow()
    private val _recap = MutableStateFlow<String?>(null)
    val recap: StateFlow<String?> = _recap.asStateFlow()

    private val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val state: StateFlow<HomeUiState> = combine(
        repository.observeActiveNotes(),
        query,
        query.debounce(250).flatMapLatest { q -> searchFlow(q) },
    ) { notes, q, results ->
        HomeUiState(
            notes = notes,
            pinned = notes.filter { it.pinned },
            query = q,
            searching = q.isNotBlank(),
            results = results,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Spec §8 feature 3: answer from the user's own notes, with citations. */
    fun ask(question: String) {
        _askState.value = AskState.Busy
        viewModelScope.launch {
            val candidates = repository.search(question.split(" ").takeLast(3).joinToString(" "))
                .ifEmpty { repository.observeActiveNotes().first().take(8).mapNotNull { repository.loadNote(it.id) } }
            val context = candidates.take(6).map { note ->
                "${note.id}|${note.title.ifBlank { "Untitled" }}" to
                    SearchableText.fromBlocks(note.blocks).take(700)
            }
            if (context.isEmpty()) {
                _askState.value = AskState.Answer("You have no notes yet.", emptyList())
                return@launch
            }
            val result = runCatching { ai.get().askMyNotes(question, context) }.getOrNull()
            _askState.value = if (result == null) {
                AskState.Failed("Couldn't reach the AI")
            } else {
                AskState.Answer(result.answer, result.noteIds.filter { id -> context.any { it.first.startsWith(id) } })
            }
        }
    }

    fun dismissAsk() { _askState.value = AskState.Idle }

    data class LooseEndsResult(val items: List<com.vansh.quicknotes.domain.ai.AiService.LooseEnd>)

    private val _looseEnds = MutableStateFlow<LooseEndsResult?>(null)
    val looseEnds: StateFlow<LooseEndsResult?> = _looseEnds.asStateFlow()

    /** Spec §8 feature 7: scan recent notes for "need to / must / remember to". */
    fun scanLooseEnds() {
        viewModelScope.launch {
            val notes = repository.observeActiveNotes().first().take(20)
            val triples = notes.mapNotNull { e ->
                repository.loadNote(e.id)?.let { Triple(it.id, it.title.ifBlank { "Untitled" }, SearchableText.fromBlocks(it.blocks)) }
            }
            if (triples.isEmpty()) return@launch
            val found = runCatching { ai.get().looseEnds(triples) }.getOrNull().orEmpty()
            _looseEnds.value = LooseEndsResult(found)
        }
    }

    fun dismissLooseEnds() { _looseEnds.value = null }

    /** One-tap: turn a loose end into a checked-off checklist item in its source note. */
    fun addLooseEndToEnd(noteId: String, text: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val note = repository.loadNote(noteId) ?: return@launch
            val block = note.blocks.firstOrNull { b -> b.type == com.vansh.quicknotes.domain.model.BlockType.CHECKLIST }
            val items = (block?.checklist?.items ?: emptyList()) +
                com.vansh.quicknotes.domain.model.ChecklistItemPayload(java.util.UUID.randomUUID().toString(), text)
            if (block != null) {
                repository.saveNote(note.copy(blocks = note.blocks.map { if (it.id == block.id) it.copy(checklist = it.checklist?.copy(items = items)) else it }))
            } else {
                repository.saveNote(
                    note.copy(
                        blocks = note.blocks + com.vansh.quicknotes.domain.model.Block(
                            id = java.util.UUID.randomUUID().toString(),
                            type = com.vansh.quicknotes.domain.model.BlockType.CHECKLIST,
                            orderIndex = note.blocks.size,
                            checklist = com.vansh.quicknotes.domain.model.ChecklistBlockPayload(items),
                        ),
                    ),
                )
            }
            onDone()
        }
    }

    /** Spec §8 feature 8: evening recap, computed on demand (and by DailyRecapWorker). */
    fun refreshRecap(force: Boolean = false) {
        viewModelScope.launch {
            val today = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            // worker's stored recap for today wins: one AI pass per day, not per Home open
            val stored = settings.settings.first()
            if (!force && stored.dailyRecap != null) {
                _recap.value = stored.dailyRecap
                return@launch
            }
            val todays = repository.observeActiveNotes().first().filter { it.updatedAt >= today }.take(6)
            if (todays.isEmpty() && !force) return@launch
            val captures = todays.mapNotNull { e -> repository.loadNote(e.id)?.let { it.title to SearchableText.fromBlocks(it.blocks) } }
            if (captures.isEmpty()) return@launch
            _recap.value = null
            val text = runCatching { ai.get().dailyRecap(captures.map { it.first to it.second.take(140) }, emptyList()) }.getOrNull()
            if (text != null) {
                _recap.value = text
                settings.setDailyRecap(text, today)
            }
        }
    }

    private fun searchFlow(q: String) = flow {
        if (q.isBlank()) {
            emit(emptyList())
        } else {
            emit(
                repository.search(q).map { note ->
                    NoteEntity(
                        id = note.id,
                        title = note.title,
                        createdAt = note.createdAt,
                        updatedAt = note.updatedAt,
                        pinned = note.pinned,
                        archived = note.archived,
                        deletedAt = note.deletedAt,
                        aiSummary = note.aiSummary,
                    )
                },
            )
        }
    }

    fun onQueryChange(q: String) {
        query.value = q
    }

    fun togglePin(id: String, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(id, pinned) }
    }

    fun archive(id: String) {
        viewModelScope.launch { repository.setArchived(id, true) }
    }

    fun trash(id: String) {
        viewModelScope.launch { repository.moveToTrash(id) }
    }

    init {
        viewModelScope.launch { repository.purgeExpiredTrash() }
    }
}
