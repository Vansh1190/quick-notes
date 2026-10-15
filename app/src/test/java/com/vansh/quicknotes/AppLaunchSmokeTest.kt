package com.vansh.quicknotes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vansh.quicknotes.data.settings.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Launch smoke tests. v1.0.0/v1.0.1 shipped an instant crash on both debug and release: the nav
 * graph had no destination for the onboarding start destination (and later: no list detail), so
 * NavHost threw before the first frame. Nothing in the suite launched the real activity, so it
 * slipped through. These tests do.
 */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [34])
class AppLaunchSmokeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun markOnboardingDone() {
        runBlocking { settings.setOnboardingDone(true) }
    }

    private fun markOnboardingPending() {
        // Written explicitly: the DataStore singleton is shared across tests in this sandbox, so
        // "never launched" cannot be assumed from test order.
        runBlocking { settings.setOnboardingDone(false) }
    }

    @Test
    fun `not-yet-onboarded launch shows onboarding and skip reaches home`() {
        markOnboardingPending()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("onboarding_root").assertIsDisplayed()
            composeRule.onNodeWithTag("onboarding_skip").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("home_root").assertIsDisplayed()
        }
    }

    @Test
    fun `already-onboarded launch opens home directly`() {
        markOnboardingDone()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("home_root").assertIsDisplayed()
        }
    }

    @Test
    fun `opening a list from the lists screen shows its detail`() {
        markOnboardingDone()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("home_root").assertIsDisplayed()

            composeRule.onNodeWithTag("home_lists_button").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("lists_root").assertIsDisplayed()

            composeRule.onNodeWithTag("lists_new_input").performTextInput("Groceries")
            composeRule.onNodeWithTag("lists_new_add").performClick()
            composeRule.waitForIdle()

            composeRule.onAllNodesWithText("Groceries")[0].performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("list_detail_root").assertIsDisplayed()
        }
    }
}
