package com.vansh.quicknotes

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.vansh.quicknotes.widget.CaptureModes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickCaptureActivityTest {

    @Test
    fun `voice mode forwards to MainActivity with the mode extra and finishes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, QuickCaptureActivity::class.java)
            .putExtra(CaptureModes.EXTRA_MODE, CaptureModes.VOICE)
        val activity = Robolectric.buildActivity(QuickCaptureActivity::class.java, intent).setup().get()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(MainActivity::class.java.name, started?.component?.className)
        assertEquals(CaptureModes.VOICE, started?.getStringExtra(MainActivity.EXTRA_CAPTURE_MODE))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `default mode is new note`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, QuickCaptureActivity::class.java)
        val activity = Robolectric.buildActivity(QuickCaptureActivity::class.java, intent).setup().get()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(CaptureModes.NOTE, started?.getStringExtra(MainActivity.EXTRA_CAPTURE_MODE))
    }
}
