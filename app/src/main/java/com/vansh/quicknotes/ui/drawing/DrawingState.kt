package com.vansh.quicknotes.ui.drawing

import androidx.compose.runtime.Immutable
import com.vansh.quicknotes.domain.DrawPoint
import com.vansh.quicknotes.domain.DrawStroke
import com.vansh.quicknotes.domain.DrawTool
import com.vansh.quicknotes.domain.StrokeGeometry

/** All drawing state lives here so the canvas renders purely from state (UDF). */
@Immutable
data class DrawingState(
    val strokes: List<DrawStroke> = emptyList(),
    val redoStack: List<DrawStroke> = emptyList(),
    val tool: DrawTool = DrawTool.PEN,
    val colorArgb: Long = 0xFF1C1C1EL,
    val baseWidthPx: Float = 6f,
    val activeStroke: DrawStroke? = null,
) {
    val canUndo: Boolean get() = strokes.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
}

sealed class DrawingAction {
    data object Begin : DrawingAction()
    data class Point(val x: Float, val y: Float, val pressure: Float) : DrawingAction()
    data object End : DrawingAction()
    data class SelectTool(val tool: DrawTool) : DrawingAction()
    data class SelectColor(val argb: Long) : DrawingAction()
    data class SetWidth(val px: Float) : DrawingAction()
    data object Undo : DrawingAction()
    data object Redo : DrawingAction()
    data object Clear : DrawingAction()
    data class ErasePath(val points: List<DrawPoint>) : DrawingAction()
}

object DrawingReducer {

    fun reduce(state: DrawingState, action: DrawingAction): DrawingState = when (action) {
        DrawingAction.Begin -> when (state.tool) {
            DrawTool.ERASER -> state
            else -> state.copy(
                activeStroke = DrawStroke(
                    points = emptyList(),
                    colorArgb = if (state.tool == DrawTool.HIGHLIGHTER) fade(state.colorArgb) else state.colorArgb,
                    baseWidthPx = if (state.tool == DrawTool.HIGHLIGHTER) state.baseWidthPx * 3f else state.baseWidthPx,
                    tool = state.tool,
                ),
            )
        }

        is DrawingAction.Point -> {
            val active = state.activeStroke ?: return state
            val last = active.points.lastOrNull()
            val p = DrawPoint(action.x, action.y, action.pressure)
            val near = last != null &&
                kotlin.math.abs(action.x - last.x) < 0.6f && kotlin.math.abs(action.y - last.y) < 0.6f
            if (near) state else state.copy(activeStroke = active.copy(points = active.points + p))
        }

        DrawingAction.End -> {
            val active = state.activeStroke ?: return state
            if (active.points.isEmpty()) state.copy(activeStroke = null)
            else state.copy(
                strokes = state.strokes + active.copy(points = StrokeGeometry.decimate(active.points)),
                activeStroke = null,
                redoStack = emptyList(),
            )
        }

        is DrawingAction.SelectTool -> state.copy(tool = action.tool, activeStroke = null)
        is DrawingAction.SelectColor -> state.copy(colorArgb = action.argb)
        is DrawingAction.SetWidth -> state.copy(baseWidthPx = action.px)

        DrawingAction.Undo -> when {
            state.strokes.isEmpty() -> state
            else -> state.copy(
                strokes = state.strokes.dropLast(1),
                redoStack = state.redoStack + state.strokes.last(),
                activeStroke = null,
            )
        }

        DrawingAction.Redo -> when {
            state.redoStack.isEmpty() -> state
            else -> state.copy(
                strokes = state.strokes + state.redoStack.last(),
                redoStack = state.redoStack.dropLast(1),
            )
        }

        DrawingAction.Clear -> when {
            state.strokes.isEmpty() -> state
            else -> state.copy(strokes = emptyList(), redoStack = emptyList(), activeStroke = null)
        }

        is DrawingAction.ErasePath -> {
            val kept = StrokeGeometry.erase(state.strokes, action.points, radius = state.baseWidthPx * 3f)
            if (kept.size == state.strokes.size) state
            else state.copy(strokes = kept, redoStack = emptyList())
        }
    }

    /** Highlighter: ~35 % alpha version of the color. */
    fun fade(argb: Long): Long = (argb and 0x00FFFFFFL) or (0x59L shl 24)
}
