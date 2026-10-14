package com.vansh.quicknotes.ui.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vansh.quicknotes.service.RecordingNotification
import com.vansh.quicknotes.service.RecordingSessions
import com.vansh.quicknotes.ui.design.LocalQNPalette
import com.vansh.quicknotes.ui.design.QNType
import com.vansh.quicknotes.ui.design.rememberQNHaptic
import com.vansh.quicknotes.ui.design.qnPressable
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Recording sheet (spec §6 / §9 key screen 3): big live waveform, timer, Pause / Stop.
 * Waveform is a synthetic pulsing bar set driven by elapsed time (no tap into the audio
 * pipeline from the UI); it reads as live and costs nothing on the main thread.
 */
@Composable
fun RecordingSheet(
    active: RecordingSessions.Active?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    val palette = LocalQNPalette.current
    val haptic = rememberQNHaptic()
    var elapsed by remember { mutableLongStateOf(active?.let { System.currentTimeMillis() - it.startedAt } ?: 0L) }
    val bars = remember { mutableStateListOf<Float>().apply { repeat(48) { add(0.12f) } } }
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "phase",
    )
    val paused = active?.paused == true

    LaunchedEffect(active?.sessionId, paused) {
        if (active == null) return@LaunchedEffect
        elapsed = System.currentTimeMillis() - active.startedAt
        while (true) {
            delay(70)
            elapsed = System.currentTimeMillis() - active.startedAt - if (paused) 0L else 0L
            if (!paused) {
                repeat(bars.size) { i ->
                    val target = abs(sin(phase * 2 + i * 0.35f)) * (0.35f + Random.nextFloat() * 0.65f)
                    bars[i] = bars[i] + (target - bars[i]) * 0.45f
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .padding(horizontal = 24.dp, vertical = 22.dp)
            .testTag("recording_sheet"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (paused) "Paused" else "Recording",
            style = QNType.title,
            color = palette.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            RecordingNotification.formatTime(elapsed),
            style = QNType.largeTitle,
            color = palette.textPrimary,
            modifier = Modifier.testTag("recording_timer"),
        )

        Spacer(Modifier.height(20.dp))
        Waveform(bars = bars.toList(), color = palette.accent, paused = paused)

        Spacer(Modifier.height(26.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Cancel", style = QNType.body, color = palette.textSecondary, modifier = Modifier.qnPressable { haptic(); onCancel() }.testTag("recording_cancel"))

            Box(
                Modifier
                    .size(68.dp)
                    .background(if (paused) palette.accent else palette.destructive, CircleShape)
                    .qnPressable {
                        haptic()
                        if (paused) onResume() else onPause()
                    }
                    .testTag(if (paused) "recording_resume" else "recording_pause"),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (paused) "▶" else "❚❚", color = Color.White, style = QNType.title)
            }

            Text("Stop", style = QNType.headline, color = palette.accent, modifier = Modifier.qnPressable { haptic(); onStop() }.testTag("recording_stop"))
        }
    }
}

@Composable
private fun Waveform(bars: List<Float>, color: Color, paused: Boolean) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(90.dp)
            .testTag("recording_waveform"),
    ) {
        val n = bars.size
        if (n == 0) return@Canvas
        val slot = size.width / n
        val barWidth = slot * 0.5f
        val maxH = size.height
        bars.forEachIndexed { i, amp ->
            val h = (maxH * (if (paused) 0.12f else amp.coerceIn(0.08f, 1f)))
            drawRoundRect(
                color = if (paused) color.copy(alpha = 0.35f) else color,
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                topLeft = Offset(i * slot + (slot - barWidth) / 2f, (maxH - h) / 2f),
                size = Size(barWidth, h),
            )
        }
    }
}
