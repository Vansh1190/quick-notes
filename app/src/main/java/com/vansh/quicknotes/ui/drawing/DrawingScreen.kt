package com.vansh.quicknotes.ui.drawing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vansh.quicknotes.domain.DrawPoint
import com.vansh.quicknotes.domain.DrawStroke
import com.vansh.quicknotes.domain.DrawTool
import com.vansh.quicknotes.domain.StrokeGeometry
import com.vansh.quicknotes.ui.design.LocalQNPalette
import com.vansh.quicknotes.ui.design.QNShapes
import com.vansh.quicknotes.ui.design.QNType
import com.vansh.quicknotes.ui.design.qnPressable

val QN_DRAW_COLORS = listOf(
    0xFF1C1C1EL, // ink
    0xFFFFD60AL, // yellow
    0xFFFF3B30L, // red
    0xFF34C759L, // green
    0xFF007AFFL, // blue
    0xFFAF52DEL, // purple
)

@Composable
fun DrawingScreen(
    initial: DrawingState = DrawingState(),
    onSave: (DrawingState) -> Unit,
    onCancel: () -> Unit,
) {
    val palette = LocalQNPalette.current
    var state by remember { mutableStateOf(initial) }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.surface)
            .statusBarsPadding()
            .testTag("drawing_root"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text("Cancel", style = QNType.body, color = palette.accent, modifier = Modifier.qnPressable(onClick = onCancel).testTag("drawing_cancel"))
            Text("Done", style = QNType.headline, color = palette.accent, modifier = Modifier.qnPressable(onClick = { onSave(state) }).testTag("drawing_done"))
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 10.dp)
                .testTag("drawing_canvas")
                .pointerInput(state.tool) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            state = DrawingReducer.reduce(state, DrawingAction.Begin)
                            if (state.tool == DrawTool.ERASER) {
                                state = DrawingReducer.reduce(state, DrawingAction.ErasePath(listOf(DrawPoint(offset.x, offset.y))))
                            }
                        },
                        onDrag = { change, _ ->
                            val p = change.position
                            val pressure = change.pressure.coerceIn(0.05f, 1f)
                            state = if (state.tool == DrawTool.ERASER) {
                                DrawingReducer.reduce(state, DrawingAction.ErasePath(listOf(DrawPoint(p.x, p.y))))
                            } else {
                                DrawingReducer.reduce(state, DrawingAction.Point(p.x, p.y, pressure))
                            }
                        },
                        onDragEnd = {
                            state = DrawingReducer.reduce(state, DrawingAction.End)
                        },
                    )
                },
        ) {
            (state.strokes + listOfNotNull(state.activeStroke)).forEach { drawStroke(it) }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            listOf(DrawTool.PEN to "Pen", DrawTool.HIGHLIGHTER to "Marker", DrawTool.ERASER to "Erase").forEach { (tool, label) ->
                val selected = state.tool == tool
                Text(
                    label,
                    style = QNType.headline,
                    color = if (selected) palette.accent else palette.textSecondary,
                    modifier = Modifier
                        .qnPressable { state = DrawingReducer.reduce(state, DrawingAction.SelectTool(tool)) }
                        .testTag("tool_$label"),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text("Undo", style = QNType.body, color = if (state.canUndo) palette.accent else palette.textTertiary, modifier = Modifier.qnPressable { state = DrawingReducer.reduce(state, DrawingAction.Undo) }.testTag("drawing_undo"))
            Text("Redo", style = QNType.body, color = if (state.canRedo) palette.accent else palette.textTertiary, modifier = Modifier.qnPressable { state = DrawingReducer.reduce(state, DrawingAction.Redo) }.testTag("drawing_redo"))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 18.dp),
        ) {
            QN_DRAW_COLORS.forEach { argb ->
                Box(
                    Modifier
                        .size(26.dp)
                        .testTag("color_$argb")
                        .qnPressable { state = DrawingReducer.reduce(state, DrawingAction.SelectColor(argb)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(if (state.colorArgb == argb) 24.dp else 18.dp)
                            .background(Color(argb), CircleShape),
                    )
                }
            }
            Slider(
                value = state.baseWidthPx,
                onValueChange = { state = DrawingReducer.reduce(state, DrawingAction.SetWidth(it)) },
                valueRange = 2f..24f,
                modifier = Modifier.width(120.dp).testTag("drawing_width"),
            )
        }
    }
}

fun DrawScope.drawStroke(stroke: DrawStroke) {
    if (stroke.points.isEmpty()) return
    val path = Path().apply {
        moveTo(stroke.points.first().x, stroke.points.first().y)
        StrokeGeometry.bezierSegments(stroke.points).forEach { seg ->
            cubicTo(seg[0], seg[1], seg[2], seg[3], seg[4], seg[5])
        }
    }
    val color = Color(stroke.colorArgb)
    if (stroke.points.size == 1) {
        val p = stroke.points.single()
        drawCircle(color, radius = StrokeGeometry.widthFor(stroke.baseWidthPx, p.pressure) / 2f, center = Offset(p.x, p.y))
        return
    }
    val segs = StrokeGeometry.bezierSegments(stroke.points)
    if (segs.isEmpty()) return
    // variable width: one stroke path per pressure bucket, drawn in order
    val avgPressure = stroke.points.map { it.pressure }.average().toFloat()
    drawPath(
        path,
        color = color,
        style = Stroke(
            width = StrokeGeometry.widthFor(stroke.baseWidthPx, avgPressure),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}
