package com.vansh.quicknotes.ui.lists

import com.vansh.quicknotes.ui.design.QNHeader
import com.vansh.quicknotes.ui.design.QNHeaderAction
import com.vansh.quicknotes.ui.design.qnStatusBarInset
import com.vansh.quicknotes.ui.design.qnNavBarInset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vansh.quicknotes.data.repository.ListsRepository
import com.vansh.quicknotes.ui.design.LocalQNPalette
import com.vansh.quicknotes.ui.design.QNType
import com.vansh.quicknotes.ui.design.qnPressable

@Composable
fun ListsScreen(
    onOpenList: (String) -> Unit,
    onBack: () -> Unit = {},
    viewModel: ListsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalQNPalette.current
    var newTitle by remember { mutableStateOf("") }
    var showDump by remember { mutableStateOf(false) }
    val recording = com.vansh.quicknotes.ui.voice.RememberRecordingController(noteId = null)
    var dumpText by remember { mutableStateOf("") }
    var proposals by remember { mutableStateOf<List<com.vansh.quicknotes.domain.lists.ProposedList>>(emptyList()) }

    Box(Modifier.fillMaxSize().background(palette.background).testTag("lists_root")) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().qnStatusBarInset().testTag("lists_column"),
        ) {
            item {
                Column(Modifier.padding(top = 8.dp)) {
                    QNHeader(
                        title = "Lists",
                        onBack = onBack,
                        backTag = "lists_back",
                        titleTag = "lists_title",
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            placeholder = { Text("New list…", color = palette.textTertiary) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = palette.surface,
                                unfocusedContainerColor = palette.surface,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).testTag("lists_new_input"),
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(palette.accent, RoundedCornerShape(14.dp))
                                .qnPressable { viewModel.createList(newTitle) { newTitle = ""; onOpenList(it) } }
                                .testTag("lists_new_add"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("+", style = QNType.headline, color = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(palette.card, RoundedCornerShape(14.dp))
                                .semantics { contentDescription = "Record a list" }
                                .qnPressable { recording.start() }
                                .testTag("lists_mic"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("●", style = QNType.title, color = palette.destructive)
                        }
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.card, RoundedCornerShape(18.dp))
                        .qnPressable { showDump = true }
                        .padding(16.dp)
                        .testTag("lists_dump_entry"),
                ) {
                    Text("Talk a whole day into lists", style = QNType.headline, color = palette.accent)
                    Text(
                        "Dump everything - buy milk, send the invoice, call mom - we split it into lists you keep.",
                        style = QNType.caption,
                        color = palette.textTertiary,
                    )
                }
            }
            if (state.summaries.isEmpty()) {
                item {
                    Text(
                        "No lists yet. Create one above, or use the mic in a list to talk a whole shopping trip into existence.",
                        style = QNType.body,
                        color = palette.textTertiary,
                        modifier = Modifier.testTag("lists_empty"),
                    )
                }
            }
            items(state.summaries, key = { it.list.id }) { summary ->
                ListCard(summary = summary, onClick = { onOpenList(summary.list.id) })
            }
        }
    }

    if (recording.showSheetState.value) {
        Dialog(
            onDismissRequest = { recording.showSheetState.value = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            com.vansh.quicknotes.ui.voice.RecordingSheetHost(recording)
        }
    }

    if (showDump) {
        Dialog(onDismissRequest = { showDump = false; proposals = emptyList() }) {
            if (proposals.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(palette.background)
                        .padding(18.dp),
                ) {
                    Text("Your dump", style = QNType.title, color = palette.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = dumpText,
                        onValueChange = { dumpText = it },
                        placeholder = { Text("buy milk, eggs and bread. For work, send the invoice. Also call mom in the evening.", color = palette.textTertiary) },
                        minLines = 4,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = palette.surface,
                            unfocusedContainerColor = palette.surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("dump_input"),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(palette.surface)
                                .qnPressable { showDump = false }.testTag("dump_entry_cancel"),
                            contentAlignment = Alignment.Center,
                        ) { Text("Cancel", style = QNType.subheadline, color = palette.textSecondary) }
                        Box(
                            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(palette.accent)
                                .qnPressable { viewModel.propose(dumpText) { proposals = it } }.testTag("dump_entry_sort"),
                            contentAlignment = Alignment.Center,
                        ) { Text("Sort", style = QNType.subheadline, color = palette.onAccent) }
                    }
                }
            } else {
                DumpAndSortSheet(
                    proposals = proposals,
                    onConfirm = { viewModel.commit(it); showDump = false; proposals = emptyList() },
                    onDismiss = { proposals = emptyList() },
                )
            }
        }
    }
}

@Composable
private fun ListCard(summary: ListsRepository.ListSummary, onClick: () -> Unit) {
    val palette = LocalQNPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface, RoundedCornerShape(18.dp))
            .qnPressable(onClick = onClick)
            .padding(16.dp)
            .testTag("list_card_" + summary.list.id),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressRing(
            progress = summary.progress,
            color = if (summary.openCount == 0 && summary.progress > 0f) palette.success else palette.accent,
            modifier = Modifier.size(40.dp).testTag("list_ring_" + summary.list.id),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                summary.list.title,
                style = QNType.title,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    summary.openCount == 0 && summary.progress == 0f -> "No items yet"
                    summary.openCount == 0 -> "All done"
                    else -> "${summary.openCount} open"
                },
                style = QNType.caption,
                color = palette.textTertiary,
            )
        }
    }
}

@Composable
internal fun ProgressRing(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalQNPalette.current
    Canvas(modifier) {
        val stroke = 5.dp.toPx()
        val inset = stroke / 2
        drawArc(
            color = palette.separator,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = stroke),
            size = Size(size.width - stroke, size.height - stroke),
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            size = Size(size.width - stroke, size.height - stroke),
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        )
    }
}

