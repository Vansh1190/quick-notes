package com.vansh.quicknotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vansh.quicknotes.domain.model.Block
import com.vansh.quicknotes.domain.model.BlockType
import com.vansh.quicknotes.domain.model.ChecklistItemPayload
import com.vansh.quicknotes.ui.design.LocalQNPalette
import com.vansh.quicknotes.ui.design.QNShapes
import com.vansh.quicknotes.ui.design.QNType
import com.vansh.quicknotes.ui.design.rememberQNHaptic
import com.vansh.quicknotes.ui.design.qnPressable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onPickImage: ((java.io.File) -> Unit) -> Unit = {},
    autoMode: String = "",
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val recording = com.vansh.quicknotes.ui.voice.RememberRecordingController(noteId = viewModel.noteIdOrNull())
    if (recording.showSheetState.value) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { recording.showSheetState.value = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            com.vansh.quicknotes.ui.voice.RecordingSheetHost(recording)
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalQNPalette.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var showCamera by remember { mutableStateOf(false) }
    var showDrawing by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkInput by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri: android.net.Uri? ->
        uri?.let { u ->
            runCatching {
                context.contentResolver.openInputStream(u)?.use { it.readBytes() }
            }.getOrNull()?.let { bytes ->
                val ext = if (context.contentResolver.getType(u)?.contains("png") == true) "png" else "jpg"
                viewModel.addImage(bytes, ext)
            }
        }
    }

    var autoModeHandled by remember { mutableStateOf(autoMode.isEmpty()) }
    LaunchedEffect(state.loaded, autoModeHandled) {
        if (!state.loaded || autoModeHandled) return@LaunchedEffect
        autoModeHandled = true
        when (autoMode) {
            "voice" -> recording.start()
            "camera" -> showCamera = true
            "draw" -> showDrawing = true
            "list" -> viewModel.addChecklist()
            else -> Unit
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.flushSave()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushSave()
        }
    }

    Box(Modifier.fillMaxSize().background(palette.background).testTag("editor_root")) {
        val note = state.note
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 60.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().testTag("editor_blocks"),
        ) {
            if (note != null) {
                items(note.blocks, key = { it.id }, contentType = { it.type }) { block ->
                    BlockView(block, viewModel)
                }
            }
        }

        if (note == null) {
            Text("Loading…", style = QNType.body, color = palette.textTertiary, modifier = Modifier.align(Alignment.Center))
        }

        CaptureBar(
            onMic = { recording.start() },
            onCamera = { showCamera = true },
            onDraw = { showDrawing = true },
            onLink = { showLinkDialog = true },
            onChecklist = { viewModel.addChecklist() },
            onGallery = { galleryLauncher.launch("image/*") },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showCamera) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showCamera = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            com.vansh.quicknotes.ui.camera.CameraScreen(
                onDone = { files ->
                    files.forEach { f -> runCatching { viewModel.addImage(f.readBytes()) } }
                    showCamera = false
                },
                onCancel = { showCamera = false },
                onError = { showCamera = false },
            )
        }
    }

    if (showDrawing) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showDrawing = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            com.vansh.quicknotes.ui.drawing.DrawingScreen(
                onSave = { drawingState ->
                    val w = 1080
                    val h = 1440
                    val bmp = com.vansh.quicknotes.ui.drawing.StrokeRenderer.render(drawingState.strokes, w, h)
                    viewModel.addDrawing(drawingState.strokes, com.vansh.quicknotes.ui.drawing.StrokeRenderer.pngBytes(bmp), w, h)
                    showDrawing = false
                },
                onCancel = { showDrawing = false },
            )
        }
    }

    if (showLinkDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (linkInput.isNotBlank()) viewModel.addLink(linkInput.trim())
                        showLinkDialog = false
                        linkInput = ""
                    },
                    modifier = Modifier.testTag("link_dialog_add"),
                ) { Text("Add", color = palette.accent) }
            },
            text = {
                BasicTextField(
                    value = linkInput,
                    onValueChange = { linkInput = it },
                    textStyle = QNType.body.copy(color = palette.textPrimary),
                    singleLine = true,
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier.fillMaxWidth().testTag("link_dialog_input"),
                )
            },
        )
    }

    LaunchedEffect(state.loaded) {
        if (state.loaded) {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }
}

@Composable
private fun BlockView(block: Block, viewModel: EditorViewModel) {
    when (block.type) {
        BlockType.TEXT -> TextBlockView(block, viewModel)
        BlockType.DIVIDER -> DividerBlockView()
        BlockType.CHECKLIST -> ChecklistBlockView(block, viewModel)
        BlockType.IMAGE, BlockType.CAMERA -> ImageBlockView(block, viewModel)
        BlockType.DRAWING -> DrawingBlockView(block, viewModel)
        BlockType.LINK -> LinkBlockView(block)
        BlockType.VOICE -> VoiceBlockView(block)
    }
}

@Composable
private fun TextBlockView(block: Block, viewModel: EditorViewModel) {
    val palette = LocalQNPalette.current
    val isFirst = block.orderIndex == 0
    val focusRequester = remember { FocusRequester() }
    BasicTextField(
        value = block.text?.text.orEmpty(),
        onValueChange = { viewModel.onBlockTextChange(block.id, it) },
        textStyle = (if (isFirst) QNType.title else QNType.body).copy(color = palette.textPrimary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        cursorBrush = SolidColor(palette.accent),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .testTag("editor_block_${block.id}"),
    )
    LaunchedEffect(Unit) {
        if (isFirst) runCatching { focusRequester.requestFocus() }
    }
}

@Composable
private fun DividerBlockView() {
    val palette = LocalQNPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.separator)
            .testTag("editor_divider"),
    )
}

@Composable
private fun ChecklistBlockView(block: Block, viewModel: EditorViewModel) {
    val palette = LocalQNPalette.current
    val haptic = rememberQNHaptic()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(QNShapes.card)
            .background(palette.card)
            .padding(14.dp)
            .testTag("editor_checklist_${block.id}"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val items = block.checklist?.items.orEmpty()
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (item.done) palette.success else palette.separator)
                        .qnPressable {
                            haptic()
                            viewModel.updateChecklist(block.id, items.map { if (it.id == item.id) it.copy(done = !it.done) else it })
                        }
                        .testTag("check_${item.id}"),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    item.text,
                    style = QNType.body,
                    color = if (item.done) palette.textTertiary else palette.textPrimary,
                    modifier = Modifier.testTag("check_text_${item.id}"),
                )
            }
        }
        if (items.isEmpty()) {
            Text("Empty checklist", style = QNType.callout, color = palette.textTertiary)
        }
    }
}

@Composable
private fun ImageBlockView(block: Block, viewModel: EditorViewModel) {
    val palette = LocalQNPalette.current
    var file by remember(block.id) { mutableStateOf<java.io.File?>(null) }
    LaunchedEffect(block.id) {
        file = withContext(Dispatchers.IO) { runCatching { viewModel.mediaFile(block.image?.assetId.orEmpty()) }.getOrNull() }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(QNShapes.card)
            .testTag("editor_image_${block.id}"),
    ) {
        val f = file
        if (f != null) {
            coil3.compose.AsyncImage(
                model = f,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Box(Modifier.fillMaxWidth().height(140.dp).background(palette.separator))
        }
    }
}

@Composable
private fun DrawingBlockView(block: Block, viewModel: EditorViewModel) {
    val palette = LocalQNPalette.current
    var file by remember(block.id) { mutableStateOf<java.io.File?>(null) }
    LaunchedEffect(block.id) {
        file = withContext(Dispatchers.IO) { runCatching { viewModel.mediaFile(block.drawing?.previewAssetId.orEmpty()) }.getOrNull() }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(QNShapes.card)
            .background(palette.card)
            .testTag("editor_drawing_${block.id}"),
    ) {
        val f = file
        if (f != null) {
            coil3.compose.AsyncImage(
                model = f,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text("Drawing", style = QNType.callout, color = palette.textSecondary, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun LinkBlockView(block: Block) {
    val palette = LocalQNPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(QNShapes.card)
            .background(palette.card)
            .padding(16.dp)
            .testTag("editor_link_${block.id}"),
    ) {
        block.link?.siteName?.let {
            Text(it.uppercase(), style = QNType.captionTinted, color = palette.textTertiary)
            Spacer(Modifier.height(4.dp))
        }
        Text(
            block.link?.title?.takeIf { it.isNotBlank() } ?: block.link?.url.orEmpty(),
            style = QNType.headline,
            color = palette.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!block.link?.title.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(block.link?.url.orEmpty(), style = QNType.caption, color = palette.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun VoiceBlockView(block: Block) {
    val palette = LocalQNPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(QNShapes.card)
            .background(palette.card)
            .padding(16.dp)
            .testTag("editor_voice_${block.id}"),
    ) {
        Text("●", color = palette.destructive, style = QNType.title)
        Column(Modifier.weight(1f)) {
            Text("Voice clip", style = QNType.headline, color = palette.textPrimary)
            val dur = (block.voice?.durationMs ?: 0) / 1000
            Text("%d:%02d".format(dur / 60, dur % 60), style = QNType.caption, color = palette.textTertiary)
        }
        block.voice?.transcript?.let {
            Text(it.take(60), style = QNType.caption, color = palette.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(120.dp))
        }
    }
}

@Composable
private fun CaptureBar(
    onMic: () -> Unit,
    onCamera: () -> Unit,
    onDraw: () -> Unit,
    onLink: () -> Unit,
    onChecklist: () -> Unit,
    onGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalQNPalette.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(bottom = 20.dp)
            .imePadding()
            .clip(QNShapes.pill)
            .background(palette.card)
            .padding(horizontal = 22.dp, vertical = 14.dp)
            .testTag("editor_capture_bar"),
    ) {
        listOf("Mic" to onMic, "Camera" to onCamera, "Draw" to onDraw, "Link" to onLink, "Checklist" to onChecklist, "Photo" to onGallery).forEach { (label, action) ->
            Text(
                label,
                style = QNType.headline,
                color = palette.accent,
                modifier = Modifier
                    .qnPressable(onClick = action)
                    .testTag("capture_${label.lowercase()}"),
            )
        }
    }
}
