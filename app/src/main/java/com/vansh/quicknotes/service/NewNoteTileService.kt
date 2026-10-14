package com.vansh.quicknotes.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vansh.quicknotes.QuickCaptureActivity
import com.vansh.quicknotes.R
import com.vansh.quicknotes.widget.CaptureModes

/**
 * Quick Settings tile (spec §10): tap = Quick Capture (new note); long-press opens the app
 * (system default behavior via activity).
 */
class NewNoteTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.state = Tile.STATE_INACTIVE
            tile.label = "New note"
            tile.setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_tile_note))
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickCaptureActivity::class.java)
            .putExtra(CaptureModes.EXTRA_MODE, CaptureModes.NOTE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
