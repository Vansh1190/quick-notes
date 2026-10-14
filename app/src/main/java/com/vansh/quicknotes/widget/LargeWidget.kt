package com.vansh.quicknotes.widget

import android.content.Context
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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import com.vansh.quicknotes.MainActivity
import com.vansh.quicknotes.R
import com.vansh.quicknotes.data.local.QuickNotesDatabase

/**
 * Large widget (spec §10c): the 5 capture buttons plus the 3 most recent notes; tapping a
 * recent note opens it in the editor.
 */
class LargeWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val recents = QuickNotesDatabase.build(context).noteDao().activeNotesOnce(3)
        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ImageProvider(R.drawable.widget_background))
                        .padding(12.dp),
                ) {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        captureActions.forEach { (label, mode) ->
                            Column(
                                modifier = GlanceModifier
                                    .clickable { CaptureModes.launch(context, mode) }
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Image(
                                    provider = ImageProvider(iconFor(mode)),
                                    contentDescription = label,
                                    modifier = GlanceModifier.size(24.dp),
                                )
                                Text(label, style = TextStyle(color = widgetTextColor, fontSize = 9.sp))
                            }
                        }
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    if (recents.isEmpty()) {
                        Text(
                            "No notes yet — tap a button above",
                            style = TextStyle(color = ColorProvider(Color(0x99000000)), fontSize = 12.sp),
                        )
                    } else {
                        recents.forEach { note ->
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .clickable {
                                        context.startActivity(MainActivity.openNoteIntent(context, note.id))
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    note.title.ifBlank { "Untitled" },
                                    style = TextStyle(color = widgetTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class LargeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LargeWidget()
}
