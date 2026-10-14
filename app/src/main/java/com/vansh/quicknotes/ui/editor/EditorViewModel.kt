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
    private val ai: dagger.Lazy<com.vansh.quicknotes.domain.ai.AiService>,
) : ViewModel() {

    sealed interface AiState {
        data object Idle : AiState
        data object Busy : AiState
        data class Preview(val label: String, val newText: String) : AiState
        data object Undone : AiState
        data class Failed(val message: String) : AiState
    }

    private val _aiState = kotlinx.coroutines.flow.MutableStateFlow<AiState>(AiState.Idle)
    val aiState: kotlinx.coroutines.flow.StateFlow<AiState> = _aiState.asStateFlow()
    private var aiPreviewVersionId: String? = null

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

    /** Spec §8 feature 4: transcript/rough text -> tidy note (preview first, undo after). */
    fun aiCleanUp(mode: String = "tidy") {
        val note = current ?: return
        _aiState.value = AiState.Busy
        viewModelScope.launch {
            val body = com.vansh.quicknotes.data.repository.SearchableText.withMedia(
                note.blocks,
                emptyList(),
            )
            val cleaned = runCatching { ai.get().cleanUp(body, mode) }.getOrNull()
            if (cleaned.isNullOrBlank()) {
                _aiState.value = AiState.Failed("Couldn't reach the AI")
                return@launch
            }
            val versionsBefore = repository.versions(note.id).maxOfOrNull { it.createdAt } ?: 0L
            repository.snapshotForAi(note.id, "cleanup:$mode")
            aiPreviewVersionId = repository.versions(note.id).firstOrNull { it.createdAt > versionsBefore }?.id
            applyCleanedText(cleaned)
            _aiState.value = AiState.Preview("Cleaned up", cleaned)
        }
    }

    fun aiDismissPreview() {
        val versionId = aiPreviewVersionId
        aiPreviewVersionId = null
        _aiState.value = AiState.Idle
        if (versionId != null) {
            viewModelScope.launch { repository.restoreVersion(versionId) }
        }
    }

    fun aiAcceptPreview() {
        aiPreviewVersionId = null
        _aiState.value = AiState.Idle
    }

    /** Spec §8 feature 5: AI title + tags, one-tap accept. */
    fun aiTitleAndTags(onResult: (title: String, tags: List<String>) -> Unit) {
        val note = current ?: return
        viewModelScope.launch {
            val body = com.vansh.quicknotes.data.repository.SearchableText.fromBlocks(note.blocks)
            val tt = runCatching { ai.get().titleAndTags(body) }.getOrNull()
            if (tt == null) {
                _aiState.value = AiState.Failed("Couldn't reach the AI")
            } else {
                repository.applyAiTitleAndTags(note.id, tt.title, tt.tags)
                current = note.copy(title = tt.title)
                _state.value = EditorUiState(note = current, loaded = true)
                onResult(tt.title, tt.tags)
            }
        }
    }

    private fun applyCleanedText(text: String) {
        val note = current ?: return
        val textBlocks = note.blocks.filter { it.type == BlockType.TEXT }
        if (textBlocks.isEmpty()) {
            mutate { n -> n.copy(blocks = n.blocks + Block(java.util.UUID.randomUUID().toString(), BlockType.TEXT, n.blocks.size, text = TextPayload(text))) }
            return
        }
        // replace the first text block with the cleaned whole-note text; drop the other text blocks
        val firstId = textBlocks.first().id
        mutate { n ->
            n.copy(
                blocks = n.blocks.mapNotNull { b ->
                    when {
                        b.id == firstId -> b.copy(text = TextPayload(text))
                        b.type == BlockType.TEXT -> null
                        else -> b
                    }
                },
            )
        }
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
