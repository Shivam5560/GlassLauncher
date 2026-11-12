package com.shivam.glasslauncher.ui

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.MainActivity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherInteractionTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun drawerSearchAndHomeRecoverAfterRepeatedInterruptions() {
        val app = ApplicationProvider.getApplicationContext<GlassApp>()
        app.settings.setOnboardingDone(true)
        runBlocking { app.settings.state.first { it?.onboardingDone == true } }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // Isolated emulator test: avoid permission dialogs obscuring the search interaction.
        device.executeShellCommand("pm grant ${app.packageName} android.permission.READ_CONTACTS")
        device.executeShellCommand("pm grant ${app.packageName} android.permission.CALL_PHONE")
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15000) { compose.onAllNodesWithTag("launcher-home").fetchSemanticsNodes().isNotEmpty() }
            repeat(3) {
                compose.waitForIdle()
                compose.onNodeWithTag("launcher-home").performTouchInput {
                    swipe(androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.75f),
                        androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.35f), 200)
                }
                compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Search apps").fetchSemanticsNodes().isNotEmpty() }
                val searchButtons = compose.onAllNodesWithContentDescription("Search apps")
                val visibleIndex = searchButtons.fetchSemanticsNodes().indices.first { searchButtons[it].isDisplayed() }
                searchButtons[visibleIndex].performClick()
                compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Close search").fetchSemanticsNodes().isNotEmpty() }
                app.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setClass(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Close search").fetchSemanticsNodes().isEmpty() }
                compose.onAllNodesWithContentDescription("Search apps").assertCountEquals(0)
            }
        }
    }
}
