package com.vansh.quicknotes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.vansh.quicknotes.domain.SharedContent
import com.vansh.quicknotes.domain.ShareHandler
import com.vansh.quicknotes.domain.ShareParser
import com.vansh.quicknotes.domain.ShareRouter
import com.vansh.quicknotes.ui.QuickNotesNavHost
import com.vansh.quicknotes.ui.design.QuickNotesTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var shareRouter: ShareRouter
    @Inject lateinit var shareHandler: ShareHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            QuickNotesTheme {
                QuickNotesNavHost(shareRouter = shareRouter)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
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
