package com.vansh.quicknotes.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vansh.quicknotes.domain.lists.ProposedList
import com.vansh.quicknotes.ui.design.LocalQNPalette
import com.vansh.quicknotes.ui.design.QNShapes
import com.vansh.quicknotes.ui.design.QNType
import com.vansh.quicknotes.ui.design.qnPressable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dump & Sort (spec §7): one text/voice dump → several proposed lists → preview → one-tap
 * confirm. The preview is the contract: nothing is written until the user taps Done.
 */
@Composable
fun DumpAndSortSheet(
    proposals: List<ProposedList>,
    onConfirm: (List<ProposedList>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalQNPalette.current
    var editable by remember { mutableStateOf(proposals) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(palette.background)
            .navigationBarsPadding()
            .imePadding()
            .padding(20.dp)
            .testTag("dump_sheet"),
    ) {
        Text("Dump & Sort", style = QNType.title, color = palette.textPrimary, modifier = Modifier.testTag("dump_title"))
        Spacer(Modifier.height(4.dp))
        Text(
            "Review the lists it made. Tap Done and they appear — nothing is saved before that.",
            style = QNType.caption,
            color = palette.textTertiary,
        )
        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            editable.forEachIndexed { listIndex, proposal ->
                ProposedListCard(
                    proposal = proposal,
                    onEditItem = { itemIndex, text ->
                        editable = editable.toMutableList().also { l ->
                            val items = proposal.items.toMutableList()
                            items[itemIndex] = items[itemIndex].copy(text = text)
                            l[listIndex] = proposal.copy(items = items)
                        }
                    },
                    onRemoveItem = { itemIndex ->
                        editable = editable.toMutableList().also { l ->
                            l[listIndex] = proposal.copy(items = proposal.items.filterIndexed { i, _ -> i != itemIndex })
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(QNShapes.card)
                    .background(palette.surface)
                    .qnPressable(onClick = onDismiss)
                    .testTag("dump_cancel"),
                contentAlignment = Alignment.Center,
            ) {
                Text("Cancel", style = QNType.subheadline, color = palette.textSecondary)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(QNShapes.card)
                    .background(palette.accent)
                    .qnPressable { onConfirm(editable.filter { it.items.isNotEmpty() }) }
                    .testTag("dump_confirm"),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓ Done", style = QNType.subheadline, color = palette.onAccent)
            }
        }
    }
}

private val dueShort = SimpleDateFormat("EEE h:mm a", Locale.getDefault())

@Composable
private fun ProposedListCard(
    proposal: ProposedList,
    onEditItem: (Int, String) -> Unit,
    onRemoveItem: (Int) -> Unit,
) {
    val palette = LocalQNPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(QNShapes.card)
            .background(palette.surface)
            .padding(14.dp)
            .testTag("dump_list_" + proposal.title),
    ) {
        Text(
            proposal.title + if (proposal.items.firstNotNullOfOrNull { it.existingListTitle } != null) " · existing" else "",
            style = QNType.headline,
            color = palette.accent,
        )
        Spacer(Modifier.height(6.dp))
        proposal.items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                TextField(
                    value = item.text,
                    onValueChange = { onEditItem(index, it) },
                    singleLine = true,
                    textStyle = QNType.body.copy(color = palette.textPrimary),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = palette.accent,
                    ),
                    modifier = Modifier.weight(1f).testTag("dump_item"),
                )
                if (item.dueAt != null) {
                    Text(
                        dueShort.format(Date(item.dueAt)),
                        style = QNType.caption,
                        color = palette.accent,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
                Box(
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).qnPressable { onRemoveItem(index) }.testTag("dump_item_remove"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("×", style = QNType.headline, color = palette.textTertiary)
                }
            }
        }
        if (proposal.items.isEmpty()) {
            Text("(empty — remove this list?)", style = QNType.caption, color = palette.textTertiary)
        }
    }
}
