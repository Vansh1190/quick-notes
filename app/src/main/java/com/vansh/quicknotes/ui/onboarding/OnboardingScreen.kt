package com.vansh.quicknotes.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.vansh.quicknotes.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vansh.quicknotes.ui.design.LocalQNPalette
import com.vansh.quicknotes.ui.design.QNType
import com.vansh.quicknotes.ui.design.qnPressable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : androidx.lifecycle.ViewModel() {

    /** Survives VM clear: navigating away right after finish must not cancel the flag write. */
    private val persistScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    fun finish(onDone: () -> Unit) {
        onDone()
        persistScope.launch { runCatching { settings.setOnboardingDone(true) } }
    }
}

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val palette = LocalQNPalette.current
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag("onboarding_root"),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            Page(page)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(3) { index ->
                val selected = pagerState.currentPage == index
                val width by animateFloatAsState(if (selected) 22f else 8f, label = "dot")
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(width.dp)
                        .clip(CircleShape)
                        .background(if (selected) palette.accent else palette.separator),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pagerState.currentPage < 2) {
                Text(
                    "Skip",
                    style = QNType.subheadline,
                    color = palette.textTertiary,
                    modifier = Modifier.qnPressable { viewModel.finish(onDone) }.testTag("onboarding_skip"),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.accent)
                        .qnPressable { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }
                        .padding(horizontal = 30.dp, vertical = 13.dp)
                        .testTag("onboarding_next"),
                ) {
                    Text("Next", style = QNType.subheadline, color = palette.onAccent)
                }
            } else {
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.accent)
                        .qnPressable { viewModel.finish(onDone) }
                        .padding(horizontal = 30.dp, vertical = 13.dp)
                        .testTag("onboarding_done"),
                ) {
                    Text("Start capturing", style = QNType.subheadline, color = palette.onAccent)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun Page(page: Int) {
    val palette = LocalQNPalette.current
    val emoji: String
    val title: String
    val body: String
    when (page) {
        0 -> { emoji = "⚡"; title = "Capture first, organize later"; body = "One tap from anywhere: mic, camera, draw, link, checklist. The note saves itself while you type — even if the app dies." }
        1 -> { emoji = "🎙️"; title = "Talk, we'll sort it"; body = "Dump a whole day out loud — buy milk, send the invoice, call mom — Quick Notes splits it into lists you keep. Nothing is written until you tap Done." }
        else -> { emoji = "🧩"; title = "Make it yours"; body = "Add a widget or the Quick Settings tile for one-tap capture, and connect your own AI endpoint in Settings for tidy-ups, summaries and Ask My Notes." }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 34.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, style = QNType.largeTitle, modifier = Modifier.testTag("onboarding_emoji_$page"))
        Spacer(Modifier.height(18.dp))
        Text(title, style = QNType.title, color = palette.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.testTag("onboarding_title_$page"))
        Spacer(Modifier.height(10.dp))
        Text(body, style = QNType.body, color = palette.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.testTag("onboarding_body_$page"))
    }
}
