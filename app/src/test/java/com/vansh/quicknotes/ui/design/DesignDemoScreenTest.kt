package com.vansh.quicknotes.ui.design

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.captureToImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DesignDemoScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun demoScreenRendersTitleCardsAndCapturePill() {
        composeRule.setContent {
            QuickNotesTheme(darkTheme = false) { DesignDemoScreen() }
        }
        composeRule.onNodeWithTag("demo_root").assertIsDisplayed()
        composeRule.onNodeWithTag("demo_title").assertIsDisplayed()
        composeRule.onNodeWithTag("demo_capture_pill").assertIsDisplayed()
        composeRule.onNodeWithTag("demo_card_0").assertExists()
    }

    @Test
    fun saveDesignEvidenceScreenshot() {
        composeRule.setContent {
            QuickNotesTheme(darkTheme = false) { DesignDemoScreen() }
        }
        composeRule.waitForIdle()
        val image = composeRule.onNodeWithTag("demo_root").captureToImage()
        val dir = File("../docs/evidence")
        dir.mkdirs()
        File(dir, "m0-design-demo.png").outputStream().use { out ->
            val bitmap = image.asAndroidBitmap()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
