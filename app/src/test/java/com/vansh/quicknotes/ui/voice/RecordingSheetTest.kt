package com.vansh.quicknotes.ui.voice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vansh.quicknotes.service.RecordingNotification
import com.vansh.quicknotes.service.RecordingSessions
import com.vansh.quicknotes.ui.design.QuickNotesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RecordingSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `formatTime renders mm colon ss`() {
        assertEquals("0:00", RecordingNotification.formatTime(0))
        assertEquals("0:42", RecordingNotification.formatTime(42_000))
        assertEquals("12:05", RecordingNotification.formatTime(12 * 60_000L + 5_000))
    }

    @Test
    fun `sheet shows timer waveform and stop action`() {
        composeRule.setContent {
            QuickNotesTheme(darkTheme = false) {
                RecordingSheet(
                    active = RecordingSessions.Active("s1", null, startedAt = System.currentTimeMillis() - 42_000),
                    onPause = {},
                    onResume = {},
                    onStop = {},
                    onCancel = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("recording_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("recording_waveform").assertIsDisplayed()
        composeRule.onNodeWithTag("recording_timer").assertIsDisplayed()
        composeRule.onNodeWithTag("recording_stop").performClick()
    }

    @Test
    fun `paused sheet offers resume`() {
        composeRule.setContent {
            QuickNotesTheme(darkTheme = false) {
                RecordingSheet(
                    active = RecordingSessions.Active("s1", null, startedAt = System.currentTimeMillis(), paused = true),
                    onPause = {},
                    onResume = {},
                    onStop = {},
                    onCancel = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("recording_resume").assertIsDisplayed()
    }
}
