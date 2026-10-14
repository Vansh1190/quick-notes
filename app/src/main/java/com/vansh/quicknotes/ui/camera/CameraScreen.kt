package com.vansh.quicknotes.ui.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vansh.quicknotes.ui.design.LocalQNPalette
import com.vansh.quicknotes.ui.design.QNShapes
import com.vansh.quicknotes.ui.design.QNType
import com.vansh.quicknotes.ui.design.qnPressable
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Direct-capture camera (spec §5.3): tap shutter → photo appears in a strip, keep shooting,
 * Done returns the JPEG files (temp cache files; the caller promotes them into MediaStore).
 */
@Composable
fun CameraScreen(
    onDone: (List<File>) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit,
) {
    val palette = LocalQNPalette.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val previewUseCase = remember { Preview.Builder().build() }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var shots by remember { mutableStateOf<List<File>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(previewView) {
        val view = previewView ?: return@LaunchedEffect
        try {
            val p = suspendCoroutine { cont ->
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener(
                    { cont.resume(future.get()) },
                    ContextCompat.getMainExecutor(context),
                )
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            previewUseCase.surfaceProvider = view.surfaceProvider
            p.unbindAll()
            p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, previewUseCase, capture)
            provider = p
            imageCapture = capture
        } catch (t: Exception) {
            onError("Camera unavailable: ${t.message ?: "unknown error"}")
        }
    }

    DisposableEffect(Unit) {
        onDispose { provider?.unbindAll() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).testTag("camera_root")) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            },
            update = { view -> previewView = view },
            modifier = Modifier.fillMaxSize().testTag("camera_preview"),
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (shots.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                    items(shots) { file ->
                        coil3.compose.AsyncImage(
                            model = file,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp).testTag("camera_shot_thumb"),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                Text(
                    "Cancel",
                    color = Color.White,
                    style = QNType.body,
                    modifier = Modifier.qnPressable(onClick = onCancel).testTag("camera_cancel"),
                )
                Box(
                    Modifier
                        .size(68.dp)
                        .testTag("camera_shutter")
                        .qnPressable(enabled = imageCapture != null && !busy) {
                            val capture = imageCapture ?: return@qnPressable
                            busy = true
                            val out = File.createTempFile("qn_shot_", ".jpg", context.cacheDir)
                            val options = ImageCapture.OutputFileOptions.Builder(out).build()
                            capture.takePicture(
                                options,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                        shots = shots + out
                                        busy = false
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        busy = false
                                        onError("Capture failed: ${exception.message}")
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(30.dp))
                    } else {
                        Box(Modifier.size(58.dp).background(Color.White, QNShapes.pill))
                    }
                }
                Text(
                    if (shots.isEmpty()) "Done" else "Done (${shots.size})",
                    color = if (shots.isEmpty()) Color.Gray else palette.accent,
                    style = QNType.headline,
                    modifier = Modifier
                        .qnPressable(enabled = shots.isNotEmpty()) { onDone(shots) }
                        .testTag("camera_done"),
                )
            }
        }
    }
}
