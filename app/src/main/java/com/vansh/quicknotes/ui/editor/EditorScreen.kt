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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vansh.quicknotes.domain.model.Block
import com.vansh.quicknotes.domain.model.BlockType
import com.vansh.quicknotes.ui.design.LocalQNPalette
import com.vansh.quicknotes.ui.design.QNShapes
import com.vansh.quicknotes.ui.design.QNType
import com.vansh.quicknotes.ui.design.qnPressable

@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalQNPalette.current
    val focusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

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
                    BlockView(block, viewModel, focusRequester = focusRequester)
                }
            }
        }

        if (note == null) {
            Text("Loading…", style = QNType.body, color = palette.textTertiary, modifier = Modifier.align(Alignment.Center))
        }

        CaptureBar(
            onAdd = { type -> viewModel.insertBlock(type) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // open with keyboard up + cursor ready (spec §5.1)
    androidx.compose.runtime.LaunchedEffect(state.loaded) {
        if (state.loaded) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
}



@Composable
private fun BlockView(block: Block, viewModel: EditorViewModel, focusRequester: FocusRequester) {
    val palette = LocalQNPalette.current
    when (block.type) {
        BlockType.TEXT -> {
            val isFirst = block.orderIndex == 0
            BasicTextField(
                value = block.text?.text.orEmpty(),
                onValueChange = { viewModel.onBlockTextChange(block.id, it) },
                textStyle = (if (isFirst) QNType.title else QNType.body).copy(color = palette.textPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(if (isFirst) focusRequester else androidx.compose.runtime.remember { FocusRequester() })
                    .testTag("editor_block_${block.id}"),
            )
        }
        BlockType.DIVIDER -> Box(
            Modifier.fillMaxWidth().height(1.dp).background(palette.separator).testTag("editor_divider"),
        )
        else -> Box(
            Modifier
                .fillMaxWidth()
                .clip(QNShapes.tile)
                .background(palette.card)
                .padding(16.dp)
                .testTag("editor_block_${block.id}"),
        ) {
            Text(blockLabel(block), style = QNType.callout, color = palette.textSecondary)
        }
    }
}

private fun blockLabel(block: Block): String = when (block.type) {
    BlockType.CHECKLIST -> "Checklist"
    BlockType.IMAGE, BlockType.CAMERA -> "Photo"
    BlockType.DRAWING -> "Drawing"
    BlockType.VOICE -> "Voice clip"
    BlockType.LINK -> block.link?.url ?: "Link"
    else -> "Block"
}

@Composable
private fun CaptureBar(onAdd: (BlockType) -> Unit, modifier: Modifier = Modifier) {
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
        listOf(BlockType.VOICE to "Mic", BlockType.CAMERA to "Camera", BlockType.DRAWING to "Draw", BlockType.LINK to "Link", BlockType.CHECKLIST to "Checklist").forEach { (type, label) ->
            Text(
                label,
                style = QNType.headline,
                color = palette.accent,
                modifier = Modifier
                    .qnPressable { onAdd(type) }
                    .testTag("capture_${label.lowercase()}"),
            )
        }
    }
}
