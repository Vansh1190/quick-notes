package com.vansh.quicknotes.ui.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vansh.quicknotes.service.RecordingService
import com.vansh.quicknotes.service.RecordingSessions

/**
 * Owns the mic-permission flow + the recording sheet for whichever screen shows it
 * (Home, Editor, Quick Capture). Recording always starts from the foreground UI (spec §2).
 */
@Composable
fun RememberRecordingController(
    noteId: String?,
    onStopped: (RecordingSessions.Stopped) -> Unit = {},
): RecordingController {
    val context = LocalContext.current
    val active by RecordingSessions.active.collectAsStateWithLifecycle()
    val stopped by RecordingSessions.stopped.collectAsStateWithLifecycle()
    val showSheet = remember { mutableStateOf(false) }

    LaunchedEffect(stopped) {
        stopped?.let {
            onStopped(it)
            RecordingSessions.consumeStopped()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            RecordingService.start(context, noteId)
            showSheet.value = true
        }
    }
    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // proceed either way: recording works without the notification, it just stays hidden
        if (hasMicPermission(context)) {
            RecordingService.start(context, noteId)
            showSheet.value = true
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    return RecordingController(
        active = active,
        showSheetState = showSheet,
        start = {
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (hasMicPermission(context)) {
                RecordingService.start(context, noteId)
                showSheet.value = true
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        pause = { RecordingService.pause(context) },
        resume = { RecordingService.resume(context) },
        stop = {
            RecordingService.stop(context)
            showSheet.value = false
        },
        cancel = {
            RecordingService.stop(context)
            showSheet.value = false
        },
    )
}

data class RecordingController(
    val active: RecordingSessions.Active?,
    val showSheetState: androidx.compose.runtime.MutableState<Boolean>,
    val start: () -> Unit,
    val pause: () -> Unit,
    val resume: () -> Unit,
    val stop: () -> Unit,
    val cancel: () -> Unit,
)

@Composable
fun RecordingSheetHost(controller: RecordingController) {
    val show = controller.showSheetState.value
    if (show) {
        RecordingSheet(
            active = controller.active,
            onPause = controller.pause,
            onResume = controller.resume,
            onStop = controller.stop,
            onCancel = controller.cancel,
        )
    }
}

fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
