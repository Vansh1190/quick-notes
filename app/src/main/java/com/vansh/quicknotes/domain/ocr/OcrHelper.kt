package com.vansh.quicknotes.domain.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** On-device OCR (ML Kit) feeding Snap to Structure. Returns null when nothing readable. */
object OcrHelper {

    // lazy: ML Kit refuses to initialize under unit-test harnesses (no GmsCore)
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun ocrJpeg(bytes: ByteArray): String? = runCatching {
        ocrInternal(bytes)
    }.getOrNull()

    private suspend fun ocrInternal(bytes: ByteArray): String? = suspendCancellableCoroutine { cont ->
        val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        if (bitmap == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text ->
                val value = text.text.trim().takeIf { it.length >= 12 }
                cont.resume(value)
            }
            .addOnFailureListener { cont.resume(null) }
    }
}
