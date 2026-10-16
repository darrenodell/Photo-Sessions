package com.example.photosessions

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Render only: callbacks never copy, share, or upload the owner's photos. */
@RunWith(AndroidJUnit4::class)
class ExportLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun compactCountsAndLocalActionsAtPhoneWidth() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val copied = mutableIntStateOf(0)
        val fontScale = mutableFloatStateOf(1f)
        val copyCalls = AtomicInteger(); val openCalls = AtomicInteger(); val shareCalls = AtomicInteger()
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    val density = LocalDensity.current.density
                    CompositionLocalProvider(LocalDensity provides Density(density, fontScale.floatValue)) {
                        PhotoSessionsTheme {
                            Surface {
                                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.width(180.dp)) { ExportCounts(2, 2, 2) }
                                    LocalCopyActions(copied.intValue, 2, busy = false, named = true,
                                        onCopy = { copyCalls.incrementAndGet() }, onOpen = { openCalls.incrementAndGet() }, onShare = { shareCalls.incrementAndGet() })
                                    ExportActionButton("Upload to Google Drive") {}
                                    ExportActionButton("Add to Google Photos") {}
                                }
                            }
                        }
                    }
                }
            }
            val countBounds = listOf("2 in Local", "2 in Drive", "2 in Photos").map { compose.onNodeWithText(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
            assertTrue("Counts should use at most two compact lines", countBounds.map { it.top }.distinct().size <= 2)
            compose.onNodeWithText("Open Local").assertDoesNotExist()
            compose.onNodeWithText("Share copies…").assertDoesNotExist()
            compose.onNodeWithText("Copy Locally").performClick()
            compose.runOnIdle { copied.intValue = 1 }
            compose.onNodeWithText("Open Local").performScrollTo().performClick()
            compose.onNodeWithText("Share copies…").assertDoesNotExist()
            compose.runOnIdle { copied.intValue = 2; fontScale.floatValue = 1.5f }
            compose.onNodeWithText("Share copies…").performScrollTo().performClick()
            val bounds = listOf("Copy Locally", "Upload to Google Drive", "Add to Google Photos").map {
                compose.onNodeWithText(it).performScrollTo().assertIsDisplayed().assertHasClickAction().fetchSemanticsNode().boundsInRoot
            }
            val density = context.resources.displayMetrics.density
            bounds.forEach { assertTrue(it.height >= 48 * density) }
            bounds.drop(1).forEach { assertEquals(bounds.first().left, it.left, density); assertEquals(bounds.first().right, it.right, density) }
            assertEquals(1, copyCalls.get()); assertEquals(1, openCalls.get()); assertEquals(1, shareCalls.get())
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
