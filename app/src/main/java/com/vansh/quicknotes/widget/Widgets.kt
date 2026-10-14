package com.vansh.quicknotes.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import com.vansh.quicknotes.QuickCaptureActivity
import com.vansh.quicknotes.R

/**
 * Quick-access widgets (spec §10). Every capture entry point opens QuickCaptureActivity with
 * a mode extra (spec §5.2: the same 5 modes everywhere). Small = "New note"; medium = 5
 * buttons; large = 5 buttons + recents (LargeWidget.kt).
 */
object CaptureModes {
    const val EXTRA_MODE = "capture_mode"
    const val NOTE = "note"
    const val VOICE = "voice"
    const val CAMERA = "camera"
    const val DRAW = "draw"
    const val LIST = "list"

    fun captureIntent(context: Context, mode: String, noteId: String? = null): Intent =
        Intent(context, QuickCaptureActivity::class.java)
            .putExtra(EXTRA_MODE, mode)
            .apply { noteId?.let { putExtra("note_id", it) } }

    fun launch(context: Context, mode: String) {
        context.startActivity(captureIntent(context, mode).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

internal val captureActions = listOf(
    "Note" to CaptureModes.NOTE,
    "Voice" to CaptureModes.VOICE,
    "Camera" to CaptureModes.CAMERA,
    "Draw" to CaptureModes.DRAW,
    "List" to CaptureModes.LIST,
)

internal fun iconFor(mode: String): Int = when (mode) {
    CaptureModes.NOTE -> R.drawable.ic_widget_note
    CaptureModes.VOICE -> R.drawable.ic_widget_mic
    CaptureModes.CAMERA -> R.drawable.ic_widget_camera
    CaptureModes.DRAW -> R.drawable.ic_widget_draw
    else -> R.drawable.ic_widget_list
}

internal val widgetTextColor = ColorProvider(Color(0xDD000000.toInt()))

/** Small: tap anywhere → new note (≤300 ms path: Quick Capture straight into the editor). */
class NewNoteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ImageProvider(R.drawable.widget_background))
                        .clickable { CaptureModes.launch(context, CaptureModes.NOTE) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_note),
                        contentDescription = "New note",
                        modifier = GlanceModifier.size(32.dp),
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        "New note",
                        style = TextStyle(
                            color = widgetTextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        }
    }
}

class NewNoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NewNoteWidget()
}

/** Medium: the 5 capture buttons. */
class QuickButtonsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ImageProvider(R.drawable.widget_background))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    captureActions.forEach { (label, mode) ->
                        Column(
                            modifier = GlanceModifier
                                .clickable { CaptureModes.launch(context, mode) }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(
                                provider = ImageProvider(iconFor(mode)),
                                contentDescription = label,
                                modifier = GlanceModifier.size(26.dp),
                            )
                            Spacer(GlanceModifier.height(3.dp))
                            Text(
                                label,
                                style = TextStyle(color = widgetTextColor, fontSize = 10.sp, textAlign = TextAlign.Center),
                            )
                        }
                    }
                }
            }
        }
    }
}

class QuickButtonsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickButtonsWidget()
}
