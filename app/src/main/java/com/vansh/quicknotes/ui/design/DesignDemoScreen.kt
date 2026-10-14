package com.vansh.quicknotes.ui.design

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * M0 styled demo screen: large collapsing title, inset grouped cards on a soft
 * background, continuous-corner shapes, press-scale, frosted capture pill.
 * Doubles as the design-system showcase and Compose-test host.
 */
@Composable
fun DesignDemoScreen() {
    val palette = LocalQNPalette.current
    val listState = rememberLazyListState()
    val collapsePx = 72f
    val raw = if (listState.firstVisibleItemIndex > 0) {
        collapsePx + listState.firstVisibleItemScrollOffset
    } else {
        listState.firstVisibleItemScrollOffset.toFloat()
    }
    val fraction = (raw / collapsePx).coerceIn(0f, 1f)
    val titleScale = 1f - 0.42f * fraction

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag("demo_root"),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 92.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("demo_list"),
        ) {
            item {
                Column {
                    Text("Captured today", style = QNType.caption, color = palette.textTertiary)
                    Spacer(Modifier.height(6.dp))
                    Text("Notes", style = QNType.largeTitle, color = palette.textPrimary)
                }
            }
            items(6) { i -> DemoCard(i, palette) }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .background(palette.background.copy(alpha = fraction))
                .padding(horizontal = (20 * (1f - fraction)).dp, vertical = 10.dp),
        ) {
            Text(
                "Notes",
                style = QNType.largeTitle.copy(fontSize = (34 * titleScale).sp),
                color = palette.textPrimary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .testTag("demo_title"),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .clip(QNShapes.pill)
                .qnFrostedSurface(QNShapes.pill)
                .padding(horizontal = 26.dp, vertical = 16.dp)
                .testTag("demo_capture_pill"),
        ) {
            listOf("Mic", "Cam", "Draw", "Link", "List").forEach { label ->
                Text(label, style = QNType.headline, color = palette.accent)
            }
        }
    }
}

@Composable
private fun DemoCard(index: Int, palette: QNPalette) {
    var pressed by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(QNShapes.card)
            .background(palette.card)
            .qnPressable { pressed = !pressed }
            .padding(18.dp)
            .testTag("demo_card_$index"),
    ) {
        Text(
            when (index % 3) {
                0 -> "Milk, eggs, and call the lab back"
                1 -> "Idea: capture first, organize later"
                else -> "Friday — send invoice to Rahul"
            },
            style = QNType.headline,
            color = palette.textPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text("Edited just now", style = QNType.caption, color = palette.textTertiary)
        if (pressed) {
            Spacer(Modifier.height(8.dp))
            Text("Pressed", style = QNType.captionTinted, color = palette.accent)
        }
    }
}
