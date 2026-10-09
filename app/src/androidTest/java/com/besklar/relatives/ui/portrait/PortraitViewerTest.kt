package com.besklar.relatives.ui.portrait

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.besklar.relatives.data.RefreshResult
import com.besklar.relatives.model.*
import com.besklar.relatives.ui.PortraitLoader
import com.besklar.relatives.ui.profile.ProfileScreen
import com.besklar.relatives.ui.profile.ProfileState
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PortraitViewerTest {
    @get:Rule val compose = createComposeRule()
    private val image = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
    private val profile = PersonProfile(PersonSummary("A-1", PersonName("Ada", "Whitcomb"), "female", true,
        LifeEvent("1838", 1838, "Nauvoo"), null, "photo.jpg"), null, "Story", emptyList(), "today", 1234)
    private val loader = object : PortraitLoader {
        override fun peek(path: String): Bitmap = image
        override suspend fun load(path: String, targetPixels: Int): Bitmap = image
    }
    private fun zoomPercent() = compose.onNodeWithTag("zoomable-portrait").fetchSemanticsNode()
        .config[SemanticsProperties.StateDescription].removeSuffix("%").toInt()
    private fun openProfile() {
        compose.setContent { MaterialTheme { ProfileScreen(ProfileState(profile, readingStore = false), {}, {}, {}, loader) } }
        compose.onNodeWithContentDescription("Open fullscreen portrait").performClick()
        compose.onNodeWithTag("portrait-viewer").assertIsDisplayed()
    }
    private fun pinch(out: Boolean) {
        compose.onNodeWithTag("zoomable-portrait").performTouchInput {
            val near = width * 0.1f
            val far = width * 0.35f
            val start = if (out) near else far
            val end = if (out) far else near
            pinch(start0 = center - Offset(start, 0f), end0 = center - Offset(end, 0f),
                start1 = center + Offset(start, 0f), end1 = center + Offset(end, 0f))
        }
    }

    @Test fun realPinchZoomPanAndCloseReopenReset() {
        openProfile()
        assertEquals(100, zoomPercent())
        pinch(true)
        assertTrue(zoomPercent() in 200..400)
        val zoomed = zoomPercent()
        compose.onNodeWithTag("zoomable-portrait").performTouchInput {
            swipe(center, Offset(width * 0.9f, height * 0.6f))
        }
        assertEquals(zoomed, zoomPercent())
        pinch(false)
        assertTrue(zoomPercent() < zoomed)
        compose.onNodeWithContentDescription("Close portrait").performClick()
        compose.onNodeWithTag("portrait-viewer").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open fullscreen portrait").performClick()
        assertEquals(100, zoomPercent())
    }

    @Test fun backClosesOnlyViewerAndLeavesProfile() {
        var profileBacks = 0
        compose.setContent { MaterialTheme { ProfileScreen(ProfileState(profile, readingStore = false), {},
            { profileBacks++ }, {}, loader) } }
        compose.onNodeWithContentDescription("Open fullscreen portrait").performClick()
        compose.onNodeWithTag("portrait-viewer").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("portrait-viewer").assertDoesNotExist()
        compose.onNodeWithText("Ada Whitcomb").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, profileBacks) }
    }

    @Test fun failedImageHasRetryAndClose() {
        val open = mutableStateOf(true)
        var available = false
        val retryLoader = PortraitLoader { _, _ -> if (available) image else null }
        compose.setContent { MaterialTheme { if (open.value) PortraitViewer("photo.jpg", "Ada", retryLoader) { open.value = false } } }
        compose.onNodeWithText("This portrait isn’t available. Try again.").assertIsDisplayed()
        compose.runOnIdle { available = true }
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithTag("zoomable-portrait").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close portrait").performClick()
        compose.onNodeWithTag("portrait-viewer").assertDoesNotExist()
    }

    @Test fun cachedImageRemainsUsableWhileUpgradeIsPendingOrFails() {
        val gate = CompletableDeferred<Bitmap?>()
        val cached = object : PortraitLoader {
            override fun peek(path: String): Bitmap = image
            override suspend fun load(path: String, targetPixels: Int): Bitmap? = gate.await()
        }
        compose.setContent { MaterialTheme { PortraitViewer("photo.jpg", "Ada", cached, {}) } }
        compose.onNodeWithTag("zoomable-portrait").assertIsDisplayed()
        pinch(true)
        val scale = zoomPercent()
        compose.runOnIdle { gate.complete(null) }
        compose.onNodeWithTag("zoomable-portrait").assertIsDisplayed()
        assertEquals(scale, zoomPercent())
    }
}
