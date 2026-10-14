package com.vansh.quicknotes

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import androidx.core.content.getSystemService
import com.vansh.quicknotes.widget.CaptureModes

/**
 * Dynamic launcher shortcuts (spec §10): New note, Voice note, Scan (→ camera for now),
 * New list. Published on first launch; long-press the launcher icon to see them.
 */
object ShortcutPublisher {

    private data class Spec(val id: String, val label: String, val mode: String, val iconRes: Int)

    private val specs = listOf(
        Spec("new_note", "New note", CaptureModes.NOTE, R.drawable.ic_widget_note),
        Spec("voice_note", "Voice note", CaptureModes.VOICE, R.drawable.ic_widget_mic),
        Spec("scan", "Scan", CaptureModes.CAMERA, R.drawable.ic_widget_camera),
        Spec("new_list", "New list", CaptureModes.LIST, R.drawable.ic_widget_list),
    )

    fun publish(context: Context) {
        val manager = context.getSystemService<ShortcutManager>() ?: return
        if (!manager.isRequestPinShortcutSupported) return
        val wanted = specs.map { it.id }
        val existing = manager.dynamicShortcuts.mapTo(mutableSetOf()) { it.id }
        if (existing.containsAll(wanted)) return
        manager.dynamicShortcuts = specs.map { spec ->
            ShortcutInfo.Builder(context, spec.id)
                .setShortLabel(spec.label)
                .setIcon(Icon.createWithResource(context, spec.iconRes))
                .setIntent(
                    android.content.Intent(context, QuickCaptureActivity::class.java)
                        .setAction(android.content.Intent.ACTION_VIEW)
                        .putExtra(CaptureModes.EXTRA_MODE, spec.mode),
                )
                .build()
        }
    }
}
