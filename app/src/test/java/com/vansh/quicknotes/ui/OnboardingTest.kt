package com.vansh.quicknotes.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.vansh.quicknotes.data.settings.SettingsRepository
import com.vansh.quicknotes.ui.design.QuickNotesTheme
import com.vansh.quicknotes.ui.onboarding.OnboardingScreen
import com.vansh.quicknotes.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class OnboardingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `onboarding shows first page and skip completes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsRepository(context, com.vansh.quicknotes.crypto.SecretStore(context))
        var finished = false
        composeRule.setContent {
            QuickNotesTheme {
                OnboardingScreen(
                    onDone = { finished = true },
                    viewModel = androidx.compose.runtime.remember { OnboardingViewModel(settings) },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_title_0").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_skip").performClick()
        composeRule.waitForIdle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(500))
        assertTrue("finish callback did not run", finished)
        // persist is fire-and-forget; give the datastore time to land
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(1_500))
        assertTrue("onboardingDone not persisted", kotlinx.coroutines.runBlocking { settings.settings.first().onboardingDone })
    }

    @Test
    fun `next pages then done button completes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsRepository(context, com.vansh.quicknotes.crypto.SecretStore(context))
        var finished = false
        composeRule.setContent {
            QuickNotesTheme {
                OnboardingScreen(
                    onDone = { finished = true },
                    viewModel = androidx.compose.runtime.remember { OnboardingViewModel(settings) },
                )
            }
        }
        composeRule.onNodeWithTag("onboarding_next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_done").performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(500))
        composeRule.waitForIdle()
        assertTrue("done did not finish", finished)
    }
}
