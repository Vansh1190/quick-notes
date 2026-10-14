package com.vansh.quicknotes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import com.vansh.quicknotes.domain.SharedContent
import com.vansh.quicknotes.domain.ShareHandler
import com.vansh.quicknotes.domain.ShareParser
import com.vansh.quicknotes.domain.ShareRouter
import com.vansh.quicknotes.ui.QuickNotesNavHost
import com.vansh.quicknotes.ui.design.QuickNotesTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var shareRouter: ShareRouter
    @Inject lateinit var shareHandler: ShareHandler
    @Inject lateinit var recoveryRunner: com.vansh.quicknotes.domain.voice.RecordingRecoveryRunner
    @Inject lateinit var settings: com.vansh.quicknotes.data.settings.SettingsRepository

    private val pendingOpenNote = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val pendingOpenListsFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val onboardingPending = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val pendingCaptureMode = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch { recoveryRunner.run() }
        routeExtras(intent)
        handleIntent(intent)
        ShortcutPublisher.publish(this)
        lifecycleScope.launch {
            onboardingPending.value = settings.settings.first().onboardingDone != true
        }
        setContent {
            QuickNotesTheme {
                QuickNotesNavHost(
                    shareRouter = shareRouter,
                    onboardingPending = onboardingPending.collectAsState().value,
                    startAtNoteId = pendingOpenNote.collectAsState().value,
                    startInCaptureMode = pendingCaptureMode.collectAsState().value,
                    startAtLists = pendingOpenListsFlow.collectAsState().value,
                    onLaunched = {
                        pendingOpenNote.value = null
                        pendingCaptureMode.value = null
                        pendingOpenListsFlow.value = false
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeExtras(intent)
        handleIntent(intent)
        ShortcutPublisher.publish(this)
    }

    private fun routeExtras(intent: Intent?) {
        intent ?: return
        intent.getStringExtra("open_note_id")?.let { pendingOpenNote.value = it }
        intent.getStringExtra(EXTRA_CAPTURE_MODE)?.let { pendingCaptureMode.value = it }
        if (intent.getBooleanExtra(EXTRA_OPEN_LISTS, false)) pendingOpenListsFlow.value = true
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val isShare = intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE
        if (!isShare) return
        lifecycleScope.launch {
            val content = parseShare(this@MainActivity, intent) ?: return@launch
            val noteId = shareHandler.handle(content)
            shareRouter.submit(content)
            shareRouter.noteOpened(noteId)
        }
    }

    companion object {
        const val EXTRA_CAPTURE_MODE = "capture_mode"
        const val EXTRA_OPEN_LISTS = "open_lists"

        fun openNoteIntent(context: android.content.Context, noteId: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra("open_note_id", noteId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        suspend fun parseShare(context: Context, intent: Intent): SharedContent? {
            val textType = intent.type?.startsWith("text/") == true
            val bytes: Pair<ByteArray, String>? = if (intent.type?.startsWith("image/") == true) {
                val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                uri?.let { u ->
                    runCatching {
                        context.contentResolver.openInputStream(u)?.use { stream -> stream.readBytes() }
                    }.getOrNull()?.let { it to intent.type!! }
                }
            } else {
                null
            }
            return ShareParser.parse(textType, bytes, intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }
}
