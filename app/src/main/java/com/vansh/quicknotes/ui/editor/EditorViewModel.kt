package com.vansh.quicknotes.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vansh.quicknotes.data.media.MediaStore
import com.vansh.quicknotes.data.repository.NotesRepository
import com.vansh.quicknotes.domain.LinkFetcher
import com.vansh.quicknotes.domain.model.ChecklistBlockPayload
import com.vansh.quicknotes.domain.model.ChecklistItemPayload
import com.vansh.quicknotes.domain.model.DrawingPayload
import com.vansh.quicknotes.domain.model.ImagePayload
import com.vansh.quicknotes.domain.model.LinkPayload
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
    private val mediaStore: MediaStore,
    private val linkFetcher: LinkFetcher,
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

    fun addImage(bytes: ByteArray, extension: String = "jpg"): String {
        val note = current ?: return ""
        val id = java.util.UUID.randomUUID().toString()
        viewModelScope.launch {
            val asset = mediaStore.saveImage(bytes, note.id, extension)
            mutate { n ->
                n.copy(blocks = n.blocks + Block(id = id, type = BlockType.IMAGE, orderIndex = n.blocks.size, image = ImagePayload(asset.id, asset.width ?: 0, asset.height ?: 0)))
            }
        }
        return id
    }

    fun addDrawing(strokes: List<com.vansh.quicknotes.domain.DrawStroke>, previewPng: ByteArray, previewWidth: Int, previewHeight: Int) {
        val note = current ?: return
        val blockId = java.util.UUID.randomUUID().toString()
        viewModelScope.launch {
            val payloadStrokes = strokes.map { s ->
                com.vansh.quicknotes.domain.model.StrokePayload(
                    points = s.points.flatMap { listOf(it.x, it.y, it.pressure) },
                    colorArgb = s.colorArgb,
                    widthPx = s.baseWidthPx,
                    kind = if (s.tool == com.vansh.quicknotes.domain.DrawTool.HIGHLIGHTER) "highlighter" else "pen",
                )
            }
            val asset = mediaStore.saveDrawingPreview(previewPng, note.id, blockId + "-preview")
            mutate { n ->
                n.copy(blocks = n.blocks + Block(blockId, BlockType.DRAWING, n.blocks.size, drawing = DrawingPayload(payloadStrokes, asset.id)))
            }
        }
    }

    fun addLink(url: String) {
        val note = current ?: return
        val id = java.util.UUID.randomUUID().toString()
        mutate { n ->
            n.copy(blocks = n.blocks + Block(id = id, type = BlockType.LINK, orderIndex = n.blocks.size, link = LinkPayload(url)))
        }
        viewModelScope.launch {
            val meta = linkFetcher.fetch(url)
            mutate { n ->
                n.copy(blocks = n.blocks.map {
                    if (it.id == id && it.link != null) it.copy(link = it.link.copy(title = meta.title ?: it.link.title, siteName = meta.siteName ?: it.link.siteName, fetched = true)) else it
                })
            }
        }
    }

    fun addChecklist(items: List<String> = emptyList()) {
        val note = current ?: return
        val id = java.util.UUID.randomUUID().toString()
        mutate { n ->
            n.copy(
                blocks = n.blocks + Block(
                    id = id,
                    type = BlockType.CHECKLIST,
                    orderIndex = n.blocks.size,
                    checklist = ChecklistBlockPayload(items.map { ChecklistItemPayload(java.util.UUID.randomUUID().toString(), it) }),
                ),
            )
        }
    }

    fun updateChecklist(blockId: String, items: List<ChecklistItemPayload>) {
        mutate { n ->
            n.copy(blocks = n.blocks.map { if (it.id == blockId) it.copy(checklist = it.checklist?.copy(items = items)) else it })
        }
    }

    fun addDivider() {
        val note = current ?: return
        mutate { n -> n.copy(blocks = n.blocks + Block(java.util.UUID.randomUUID().toString(), BlockType.DIVIDER, n.blocks.size)) }
    }

    suspend fun mediaFile(assetId: String): java.io.File? =
        mediaStore.byId(assetId)?.let { mediaStore.fileFor(it.path) }

    private inline fun mutate(crossinline transform: (Note) -> Note) {
        val note = current ?: return
        current = transform(note)
        _state.value = EditorUiState(note = current, loaded = true)
        autoSave.edit()
    }

    /** Spec §5.1: also flush on pause/stop — called from the screen's lifecycle observer. */
    fun noteIdOrNull(): String? = current?.id

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
