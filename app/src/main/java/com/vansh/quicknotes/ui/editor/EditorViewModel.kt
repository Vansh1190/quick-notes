package com.vansh.quicknotes.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vansh.quicknotes.data.repository.NotesRepository
import com.vansh.quicknotes.domain.AutoSave
import com.vansh.quicknotes.domain.model.Block
import com.vansh.quicknotes.domain.model.BlockType
import com.vansh.quicknotes.domain.model.Note
import com.vansh.quicknotes.domain.model.TextPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class EditorUiState(
    val note: Note? = null,
    val loaded: Boolean = false,
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NotesRepository,
) : ViewModel() {

    private val noteId: String = savedStateHandle.get<String>("noteId") ?: ""
    private val mutex = Mutex()
    private var current: Note? = null

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val autoSave = AutoSave(viewModelScope) {
        mutex.withLock {
            current?.let { repository.saveNote(it) }
        }
    }

    /** Survives viewModelScope cancellation so onCleared's final flush still runs. */
    private val flushScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    init {
        viewModelScope.launch {
            val existing = if (noteId.isEmpty() || noteId == NEW_NOTE_ID) null else repository.loadNote(noteId)
            val target = existing ?: run {
                val id = repository.createNote()
                repository.loadNote(id)!!
            }
            current = target
            _state.value = EditorUiState(note = target, loaded = true)
        }
    }

    fun onBlockTextChange(blockId: String, text: String) {
        val note = current ?: return
        val blocks = note.blocks.map { b ->
            if (b.id == blockId) b.copy(text = b.text?.copy(text = text) ?: TextPayload(text)) else b
        }
        current = note.copy(blocks = blocks)
        _state.value = EditorUiState(note = current, loaded = true)
        autoSave.edit()
    }

    fun insertBlock(type: BlockType, block: Block? = null): String {
        val note = current ?: return ""
        val newBlock = block ?: Block(id = java.util.UUID.randomUUID().toString(), type = type)
        val blocks = note.blocks.toMutableList().also { it += newBlock }
        current = note.copy(blocks = blocks)
        _state.value = EditorUiState(note = current, loaded = true)
        autoSave.edit()
        return newBlock.id
    }

    fun removeBlock(blockId: String) {
        val note = current ?: return
        current = note.copy(blocks = note.blocks.filterNot { it.id == blockId })
        _state.value = EditorUiState(note = current, loaded = true)
        autoSave.edit()
    }

    /** Spec §5.1: also flush on pause/stop — called from the screen's lifecycle observer. */
    fun flushSave() {
        viewModelScope.launch { autoSave.flush() }
    }

    override fun onCleared() {
        super.onCleared()
        autoSave.cancel()
        flushScope.launch { autoSave.flush() }
    }

    companion object {
        const val NEW_NOTE_ID = "new"
    }
}
