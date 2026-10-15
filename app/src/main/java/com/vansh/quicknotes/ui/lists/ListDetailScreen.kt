package com.vansh.quicknotes.ui.lists

import com.vansh.quicknotes.ui.design.qnNavBarInset
import com.vansh.quicknotes.ui.design.qnStatusBarInset
import androidx.compose.foundation.layout.imePadding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vansh.quicknotes.ui.design.LocalQNPalette
import com.vansh.quicknotes.ui.design.QNType
import com.vansh.quicknotes.ui.design.qnPressable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListDetailScreen(
    onBack: () -> Unit,
    viewModel: ListDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalQNPalette.current
    val haptics = LocalHapticFeedback.current
    var draft by remember { mutableStateOf("") }
    var order by remember(state.items) { mutableStateOf(state.items) }
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 68.dp.toPx() }

    LaunchedEffect(state.items) {
        if (draggingId == null) order = state.items
    }

    Box(Modifier.fillMaxSize().background(palette.background).testTag("list_detail_root")) {
        Column(Modifier.fillMaxSize().qnStatusBarInset()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).qnPressable(onClick = onBack).testTag("list_detail_back"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("‹", style = QNType.largeTitle, color = palette.accent)
                }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.list?.title ?: "List",
                        style = QNType.title,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("list_detail_title"),
                    )
                    Text(
                        "${state.items.count { it.done }} of ${state.items.size} done",
                        style = QNType.caption,
                        color = palette.textTertiary,
                    )
                }
                if (state.items.isNotEmpty()) {
                    ProgressRing(progress = state.progress, color = palette.accent, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.width(12.dp))
                }
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 130.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("list_detail_items"),
            ) {
                items(order, key = { it.id }) { item ->
                    val isDragging = draggingId == item.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffset else 0f
                                scaleX = if (isDragging) 1.03f else 1f
                                scaleY = if (isDragging) 1.03f else 1f
                            }
                            .pointerInput(item.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingId = item.id
                                        dragOffset = 0f
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { _, dragAmount ->
                                        dragOffset += dragAmount.y
                                        val shift = (dragOffset / rowHeightPx).toInt()
                                        if (shift != 0) {
                                            val from = order.indexOfFirst { it.id == item.id }
                                            val to = (from + shift).coerceIn(0, order.lastIndex)
                                            if (to != from) {
                                                val moved = order.toMutableList()
                                                val el = moved.removeAt(from)
                                                moved.add(to, el)
                                                order = moved
                                                dragOffset -= (to - from) * rowHeightPx
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggingId = null
                                        viewModel.reorder(order.map { it.id })
                                    },
                                    onDragCancel = { draggingId = null; order = state.items },
                                )
                            },
                    ) {
                        val dismissState = rememberSwipeToDismissBoxState(
                            positionalThreshold = { it * 0.55f },
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.remove(item.id)
                                    true
                                } else {
                                    false
                                }
                            },
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Box(
                                    Modifier.fillMaxSize().background(palette.destructive, RoundedCornerShape(14.dp)).padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Text("Delete", style = QNType.caption, color = Color.White)
                                }
                            },
                        ) {
                            ItemRow(
                                text = item.text,
                                done = item.done,
                                dueLabel = item.dueAt?.let { dueFormat.format(Date(it)) },
                                onToggle = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.setDone(item.id, !item.done)
                                },
                                onCycleDue = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (item.dueAt == null) {
                                        val cal = java.util.GregorianCalendar.getInstance()
                                        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                                        cal.set(java.util.Calendar.HOUR_OF_DAY, 9)
                                        cal.set(java.util.Calendar.MINUTE, 0)
                                        viewModel.setDue(item.id, cal.timeInMillis)
                                    } else {
                                        viewModel.setDue(item.id, null)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().qnNavBarInset().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Add item…", color = palette.textTertiary) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = palette.surface,
                        unfocusedContainerColor = palette.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).testTag("list_detail_input"),
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(46.dp).background(palette.accent, RoundedCornerShape(14.dp))
                        .qnPressable {
                            if (draft.isNotBlank()) {
                                viewModel.add(draft)
                                draft = ""
                            }
                        }
                        .testTag("list_detail_add"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", style = QNType.headline, color = Color.White)
                }
            }
        }
    }
}

private val dueFormat = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())

@Composable
private fun ItemRow(
    text: String,
    done: Boolean,
    dueLabel: String?,
    onToggle: () -> Unit,
    onCycleDue: () -> Unit,
) {
    val palette = LocalQNPalette.current
    val alpha by animateFloatAsState(if (done) 0.45f else 1f, label = "itemAlpha")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(palette.surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(
                    if (done) palette.accent else Color.Transparent,
                    CircleShape,
                )
                .qnPressable(onClick = onToggle)
                .testTag("item_toggle"),
            contentAlignment = Alignment.Center,
        ) {
            if (!done) {
                Box(Modifier.size(26.dp).background(palette.card, CircleShape))
            } else {
                Text("✓", style = QNType.caption, color = palette.onAccent)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text,
                style = QNType.body,
                color = palette.textPrimary.copy(alpha = alpha),
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("item_text"),
            )
            if (dueLabel != null) {
                Text("Due $dueLabel", style = QNType.caption, color = palette.accent.copy(alpha = 0.9f))
            }
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .qnPressable(onClick = onCycleDue)
                .testTag("item_due_toggle"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (dueLabel != null) "●" else "○",
                style = QNType.body,
                color = if (dueLabel != null) palette.accent else palette.textTertiary,
            )
        }
    }
}
