package com.vansh.quicknotes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import com.vansh.quicknotes.ui.QuickNotesNavHost
import com.vansh.quicknotes.ui.design.QuickNotesTheme
import com.vansh.quicknotes.widget.CaptureModes
import dagger.hilt.android.AndroidEntryPoint

/**
 * Quick Capture (spec §10, key screen 5): compact partial-height entry point from widgets,
 * the QS tile, and launcher shortcuts. Kept thin — it forwards to MainActivity with a
 * capture-mode extra so the ≤300 ms path is the normal editor cold-start, no second UI to
 * maintain and no duplicated state. Theme = translucent splash so it feels overlay-like.
 */
@AndroidEntryPoint
class QuickCaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra(CaptureModes.EXTRA_MODE) ?: CaptureModes.NOTE
        enableEdgeToEdge()
        setContent {
            QuickNotesTheme {
                LaunchedEffect(Unit) {
                    // Hand off to the real host which owns the nav graph; finish so back
                    // doesn't return to this forwarder.
                    startActivity(
                        Intent(this@QuickCaptureActivity, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_CAPTURE_MODE, mode)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                    finish()
                }
            }
        }
    }
}
