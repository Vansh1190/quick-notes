package com.vansh.quicknotes.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vansh.quicknotes.data.local.NoteEntity
import com.vansh.quicknotes.ui.design.LocalQNPalette
import com.vansh.quicknotes.ui.design.QNShapes
import com.vansh.quicknotes.ui.design.QNType
import com.vansh.quicknotes.ui.design.qnPressable
import java.text.DateFormat
import java.util.Date
import kotlin.math.absoluteValue

@Composable
fun HomeScreen(
    onOpenNote: (String) -> Unit,
    onNewNote: () -> Unit,
    onVoiceNote: () -> Unit = {},
    onOpenLists: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalQNPalette.current
    var searchExpanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val recap by viewModel.recap.collectAsStateWithLifecycle()
    val askState by viewModel.askState.collectAsStateWithLifecycle()
    var showAsk by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var askInput by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.refreshRecap() }

    Box(Modifier.fillMaxSize().background(palette.background).testTag("home_root")) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().testTag("home_list"),
        ) {
            item {
                Column(Modifier.padding(top = 30.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Notes", style = QNType.largeTitle, color = palette.textPrimary, modifier = Modifier.weight(1f).testTag("home_title"))
                        Box(
                            modifier = Modifier
                                .background(palette.surface, RoundedCornerShape(12.dp))
                                .qnPressable(onClick = onOpenLists)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("home_lists_button"),
                        ) {
                            Text("Lists", style = QNType.captionTinted, color = palette.accent)
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(palette.surface, RoundedCornerShape(12.dp))
                                .qnPressable(onClick = onOpenSettings)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("home_settings_button"),
                        ) {
                            Text("⚙", style = QNType.captionTinted, color = palette.textSecondary)
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(palette.surface, RoundedCornerShape(12.dp))
                                .qnPressable(onClick = { showAsk = true })
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("home_ask_button"),
                        ) {
                            Text("Ask", style = QNType.captionTinted, color = palette.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SearchField(
                        query = state.query,
                        onQueryChange = viewModel::onQueryChange,
                        expanded = searchExpanded.value,
                        onExpand = { searchExpanded.value = true },
                    )
                }
            }
            if (state.searching) {
                items(state.results, key = { "res_" + it.id }) { note ->
                    NoteCard(note, onClick = { onOpenNote(note.id) })
                }
            } else {
                val recapText = recap
                if (!recapText.isNullOrBlank()) {
                    item {
                        Column(
                            Modifier
                                .background(palette.surface, RoundedCornerShape(18.dp))
                                .padding(16.dp)
                                .testTag("home_recap_card"),
                        ) {
                            Text("Today", style = QNType.captionTinted, color = palette.accent)
                            Spacer(Modifier.height(6.dp))
                            Text(recapText, style = QNType.body, color = palette.textSecondary)
                        }
                    }
                }
                if (state.pinned.isNotEmpty()) {
                    item {
                        Column {
                            Text("Pinned", style = QNType.captionTinted, color = palette.textTertiary)
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(state.pinned, key = { "pin_" + it.id }) { note ->
                                    Box(Modifier.width(150.dp)) {
                                        NoteCard(note, onClick = { onOpenNote(note.id) }, pinnedStyle = true)
                                    }
                                }
                            }
                        }
                    }
                }
                items(state.notes.filterNot { it.pinned }, key = { it.id }) { note ->
                    NoteCard(note, onClick = { onOpenNote(note.id) })
                }
                if (state.notes.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No notes yet", style = QNType.title, color = palette.textSecondary)
                            Spacer(Modifier.height(6.dp))
                            Text("Tap + to capture your first thought", style = QNType.body, color = palette.textTertiary)
                        }
                    }
                }
            }
        }

        CapturePill(
            onNewNote = onNewNote,
            onVoice = onVoiceNote,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp),
        )

        if (showAsk) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showAsk = false; viewModel.dismissAsk() }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(palette.surface)
                        .padding(18.dp)
                        .testTag("ask_dialog"),
                ) {
                    Text("Ask your notes", style = QNType.title, color = palette.textPrimary)
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.TextField(
                        value = askInput,
                        onValueChange = { askInput = it },
                        placeholder = { Text("When is the dentist?", color = palette.textTertiary) },
                        singleLine = true,
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedContainerColor = palette.card,
                            unfocusedContainerColor = palette.card,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("ask_input"),
                    )
                    Spacer(Modifier.height(10.dp))
                    when (val a = askState) {
                        is AskState.Busy -> Text("Searching your notes…", style = QNType.caption, color = palette.textTertiary)
                        is AskState.Failed -> Text(a.message, style = QNType.caption, color = palette.destructive)
                        is AskState.Answer -> {
                            Text(a.text, style = QNType.body, color = palette.textPrimary)
                            if (a.noteIds.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    a.noteIds.take(3).forEach { id ->
                                        Text(
                                            "open",
                                            style = QNType.captionTinted,
                                            color = palette.accent,
                                            modifier = Modifier
                                                .qnPressable { showAsk = false; onOpenNote(id) }
                                                .testTag("ask_cite"),
                                        )
                                    }
                                }
                            }
                        }
                        AskState.Idle -> Box(
                            Modifier
                                .align(Alignment.End)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.accent)
                                .qnPressable(enabled = askInput.isNotBlank()) { viewModel.ask(askInput) }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                                .testTag("ask_submit"),
                        ) {
                            Text("Ask", style = QNType.subheadline, color = palette.onAccent)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Close",
                        style = QNType.headline,
                        color = palette.textTertiary,
                        modifier = Modifier.align(Alignment.End).qnPressable { showAsk = false; viewModel.dismissAsk() }.testTag("ask_close"),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, expanded: Boolean, onExpand: () -> Unit) {
    val palette = LocalQNPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(QNShapes.field)
            .background(palette.card)
            .qnPressable(enabled = !expanded) { onExpand() }
            .padding(horizontal = 16.dp, vertical = 13.dp)
            .testTag("home_search"),
    ) {
        Text("Search", style = QNType.body, color = palette.textTertiary)
        if (expanded) {
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = QNType.body.copy(color = palette.textPrimary),
                singleLine = true,
                modifier = Modifier.testTag("home_search_input"),
            )
        }
    }
}

@Composable
fun NoteCard(note: NoteEntity, onClick: () -> Unit, pinnedStyle: Boolean = false) {
    val palette = LocalQNPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(QNShapes.card)
            .background(if (pinnedStyle) palette.accent.copy(alpha = 0.14f) else palette.card)
            .qnPressable(onClick = onClick)
            .padding(18.dp)
            .testTag("note_card_${note.id}"),
    ) {
        Text(
            note.title.ifBlank { "Untitled note" },
            style = QNType.headline,
            color = palette.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (note.pinned) {
                Text("Pinned", style = QNType.captionTinted, color = palette.accent)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                DateFormat.getDateInstance(DateFormat.SHORT).format(Date(note.updatedAt)),
                style = QNType.caption,
                color = palette.textTertiary,
            )
        }
    }
}

@Composable
fun CapturePill(onNewNote: () -> Unit, onVoice: () -> Unit = {}, modifier: Modifier = Modifier) {
    val palette = LocalQNPalette.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(QNShapes.pill)
            .background(palette.card)
            .padding(horizontal = 24.dp, vertical = 15.dp)
            .testTag("home_capture_pill"),
    ) {
        listOf("Mic" to onVoice, "Camera" to onNewNote, "Draw" to onNewNote, "Link" to onNewNote, "List" to onNewNote).forEach { (label, action) ->
            Text(label, style = QNType.headline, color = palette.accent, modifier = Modifier.qnPressable(onClick = action).testTag("pill_$label"))
        }
        Box(
            Modifier
                .size(36.dp)
                .clip(QNShapes.pill)
                .background(palette.accent)
                .qnPressable(onClick = onNewNote),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = QNType.title, color = palette.onAccent, modifier = Modifier.testTag("home_new_note"))
        }
    }
}
