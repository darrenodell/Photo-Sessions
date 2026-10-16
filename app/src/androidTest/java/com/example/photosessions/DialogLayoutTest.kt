package com.example.photosessions

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Layout/callback regression check; never authorizes Drive or uploads photos. */
@RunWith(AndroidJUnit4::class)
class DialogLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun uploadChoicesRemainReadableAndReachable() = checkDialog(false)
    @Test fun photosChoicesRemainReadableAndReachable() = checkDialog(true)
    private fun checkDialog(photos: Boolean) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val wifi = AtomicInteger(); val any = AtomicInteger(); val cancel = AtomicInteger()
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    PhotoSessionsTheme {
                        UploadNetworkDialog("2026-10-05 — A long session name for a workshop photo collection",
                            onWifi = { wifi.incrementAndGet() }, onAnyNetwork = { any.incrementAndGet() },
                            onDismiss = { cancel.incrementAndGet() }, googlePhotos = photos)
                    }
                }
            }
            compose.onNodeWithText(if(photos) "Add to Google Photos" else "Upload to Google Drive").assertExists()
            compose.waitForIdle()
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(350)
            instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
                File(context.filesDir, if(photos) "validation-dialog-photos.png" else "validation-dialog-upload.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.recycle()
            }
            val density = context.resources.displayMetrics.density
            val bounds = listOf("Wait for Wi-Fi", "Upload now", "Cancel").map { label ->
                val node = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertHasClickAction()
                val rect = node.fetchSemanticsNode().boundsInRoot
                assertTrue("$label needs a usable touch target", rect.height >= 48 * density)
                node.performClick()
                rect
            }
            bounds.drop(1).forEach {
                assertEquals(bounds.first().left, it.left, density)
                assertEquals(bounds.first().right, it.right, density)
            }
            assertEquals(1, wifi.get()); assertEquals(1, any.get()); assertEquals(1, cancel.get())
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
