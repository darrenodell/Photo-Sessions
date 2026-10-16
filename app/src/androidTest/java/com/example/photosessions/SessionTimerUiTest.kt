package com.example.photosessions

import android.content.Intent
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

@RunWith(AndroidJUnit4::class)
class SessionTimerUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun manualChoicesAndCustomValidation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val starts = mutableListOf<Pair<String, Long>>()
        try {
            instrumentation.runOnMainSync { activity.setContent { PhotoSessionsTheme {
                StartSessionDialog(false, { name, duration -> starts += name to duration }, {})
            } } }
            compose.onNodeWithText("Start session").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("Session name").performTextInput("Outing")
            compose.onNodeWithText("2 hours").performScrollTo().performClick()
            compose.onNodeWithText("Start session").performScrollTo().performClick()
            compose.onNodeWithText("1 hour").performScrollTo().performClick()
            compose.onNodeWithText("Start session").performScrollTo().performClick()
            compose.onNodeWithText("30 minutes").performScrollTo().performClick()
            compose.onNodeWithText("Start session").performScrollTo().performClick()
            compose.onNodeWithText("Custom hours").performScrollTo().performTextReplacement("0")
            compose.onNodeWithText("Start session").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("Custom hours").performScrollTo().performTextReplacement("1.5")
            compose.onNodeWithText("Start session").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("Custom hours").performScrollTo().performTextReplacement("3")
            compose.onNodeWithText("Start session").performScrollTo().performClick()
            assertEquals(listOf("Outing" to 2 * HOUR, "Outing" to HOUR, "Outing" to HOUR / 2, "Outing" to 3 * HOUR), starts)
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun resumeButtonsAndCustomFieldRemainReachable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val durations = mutableListOf<Long>()
        try {
            instrumentation.runOnMainSync { activity.setContent { PhotoSessionsTheme {
                SessionDialog("Resume session", {}, { ExtensionChoices(true) { durations += it } }, { DialogAction("Cancel", {}) })
            } } }
            compose.onNodeWithText("30 minutes").performScrollTo().performClick()
            compose.onNodeWithText("1 hour").performScrollTo().performClick()
            compose.onNodeWithText("2 hours").performScrollTo().performClick()
            compose.onNodeWithText("Resume for custom hours").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("Custom hours").performScrollTo().performTextInput("2.5")
            compose.onNodeWithText("Resume for custom hours").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("Custom hours").performScrollTo().performTextReplacement("3")
            compose.onNodeWithText("Resume for custom hours").performScrollTo().performClick()
            assertEquals(listOf(HOUR / 2, HOUR, 2 * HOUR, 3 * HOUR), durations)
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
