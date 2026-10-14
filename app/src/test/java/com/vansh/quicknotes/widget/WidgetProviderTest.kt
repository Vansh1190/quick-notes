package com.vansh.quicknotes.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vansh.quicknotes.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetProviderTest {

    private val receivers = listOf(
        NewNoteWidgetReceiver::class.java to R.xml.widget_new_note_info,
        QuickButtonsWidgetReceiver::class.java to R.xml.widget_quick_buttons_info,
        LargeWidgetReceiver::class.java to R.xml.widget_large_info,
    )

    @Test
    fun `every widget receiver is registered in the manifest with provider meta`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager
        val component = ComponentName(context, NewNoteWidgetReceiver::class.java)
        val info = pm.getReceiverInfo(component, android.content.pm.PackageManager.GET_META_DATA)
        assertNotNull("provider meta-data key", info.metaData?.getInt("android.appwidget.provider"))
    }

    @Test
    fun `capture intents target QuickCaptureActivity with the right mode`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for ((_, mode) in listOf(
            "Note" to CaptureModes.NOTE,
            "Voice" to CaptureModes.VOICE,
            "Camera" to CaptureModes.CAMERA,
            "Draw" to CaptureModes.DRAW,
            "List" to CaptureModes.LIST,
        )) {
            val intent = CaptureModes.captureIntent(context, mode)
            assertEquals(com.vansh.quicknotes.QuickCaptureActivity::class.java.name, intent.component?.className)
            assertEquals(mode, intent.getStringExtra(CaptureModes.EXTRA_MODE))
        }
    }

    @Test
    fun `openNoteIntent carries the note id`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = com.vansh.quicknotes.MainActivity.openNoteIntent(context, "abc-123")
        assertEquals("abc-123", intent.getStringExtra("open_note_id"))
        assertTrue(intent.flags and android.content.Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
