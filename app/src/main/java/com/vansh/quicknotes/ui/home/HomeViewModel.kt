package com.vansh.quicknotes.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vansh.quicknotes.data.local.NoteEntity
import com.vansh.quicknotes.data.repository.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: NotesRepository,
) : ViewModel() {

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
