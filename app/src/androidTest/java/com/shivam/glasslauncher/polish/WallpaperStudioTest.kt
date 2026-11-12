package com.shivam.glasslauncher.polish

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.settings.SettingsActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WallpaperStudioTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<GlassApp>()

    @Test fun slidersAllowVerticalScrollingAndKeepHorizontalAdjustment() {
        ActivityScenario.launch<SettingsActivity>(SettingsActivity.intent(app, "wallpaper:lagoon")).use {
            compose.waitUntil(15000) { compose.onAllNodesWithTag("wallpaper-preview").fetchSemanticsNodes().isNotEmpty() }
            val slider = compose.onNodeWithContentDescription("Zoom")
            compose.onNodeWithTag("settings-list").performScrollToNode(hasContentDescription("Zoom"))
            slider.performScrollTo().performTouchInput { swipeRight() }
            val value = slider.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current
            assertTrue("Horizontal dragging adjusts zoom", value > 1.5f)
            val before = slider.fetchSemanticsNode().boundsInRoot.top
            slider.performTouchInput { swipeUp() }
            compose.waitForIdle()
            val node = slider.fetchSemanticsNode()
            assertTrue("Vertical dragging scrolls the list", node.boundsInRoot.top < before - 10f)
            assertEquals(value, node.config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current, 0.01f)
        }
    }

    @Test fun swipingOverHomePreviewScrollsSettings() {
        ActivityScenario.launch<SettingsActivity>(SettingsActivity.intent(app, "wallpaper:lagoon")).use {
            compose.waitUntil(15000) { compose.onAllNodesWithTag("wallpaper-preview").fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            val before = compose.onNodeWithTag("wallpaper-preview").fetchSemanticsNode().boundsInRoot.top
            compose.onNodeWithTag("wallpaper-preview").performTouchInput { swipeUp() }
            compose.waitForIdle()
            val after = compose.onAllNodesWithTag("wallpaper-preview").fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top
            assertTrue("A drag over the preview must scroll its parent", after == null || after < before - 20f)
        }
    }

    private fun screenshot(device: androidx.test.uiautomator.UiDevice, name: String) {
        val directory = java.io.File(androidx.test.platform.app.InstrumentationRegistry.getArguments()
            .getString("additionalTestOutputDir") ?: app.getExternalFilesDir(null)!!.path).apply { mkdirs() }
        assertTrue(device.takeScreenshot(java.io.File(directory, name)))
    }

    @Test fun favoritesPersistAndGalleryFilters() {
        runBlocking {
            if ("graphite" in app.polish.settings.flow.first().wallpaperFavorites) {
                app.polish.settings.toggleWallpaperFavorite("graphite")
                app.polish.settings.flow.first { "graphite" !in it.wallpaperFavorites }
            }
        }
        ActivityScenario.launch<SettingsActivity>(SettingsActivity.intent(app, "wallpaper:graphite")).use { scenario ->
            compose.waitUntil(15000) { compose.onAllNodesWithText("Favorite").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Favorite").performScrollTo().performClick()
            compose.waitUntil(5000) { "graphite" in (app.polish.settings.state.value?.wallpaperFavorites ?: emptySet()) }
            scenario.recreate()
            compose.onNodeWithText("Favorite").performScrollTo().assertExists()
            assertTrue("graphite" in runBlocking { app.polish.settings.flow.first() }.wallpaperFavorites)
        }
        ActivityScenario.launch<SettingsActivity>(SettingsActivity.intent(app, "wallpapers")).use {
            compose.waitUntil(10000) { compose.onAllNodesWithText("Favorites").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Favorites").performClick()
            compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("wallpaper-graphite"))
            compose.onNodeWithTag("wallpaper-graphite").assertExists()
            compose.onNodeWithTag("wallpaper-lagoon").assertDoesNotExist()
        }
    }

    @Test fun adjustmentsSurviveRecreationAndApplyThroughUi() {
        runBlocking { app.polish.settings.setWallpaperApplied(null, null, 0, false, false) }
        ActivityScenario.launch<SettingsActivity>(SettingsActivity.intent(app, "wallpaper:mist")).use { scenario ->
            compose.waitUntil(15000) { compose.onAllNodesWithTag("wallpaper-preview").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Dim"))
            compose.onNodeWithText("Dim").performScrollTo()
            // Slider semantics expose progress; use the fourth slider (Dim) in its parent group.
            compose.onNodeWithContentDescription("Dim").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.3f) }
            scenario.recreate()
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Dim"))
            compose.onNodeWithText("Dim").performScrollTo()
            compose.onNodeWithText("30%").assertExists()
            compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("apply-wallpaper"))
            compose.onNodeWithTag("apply-wallpaper").performScrollTo().performClick()
            compose.waitUntil(20000) { app.polish.settings.state.value?.let { it.wallpaperId == "mist" && it.wallpaperEdit.dim == 0.3f } == true }
            val state = runBlocking { app.polish.settings.flow.first() }
            assertEquals(WallpaperManager.getInstance(app).getWallpaperId(WallpaperManager.FLAG_SYSTEM), state.wallpaperSystemId)
            compose.waitUntil(15000) { app.wallpaper.snapshot.value.path == com.shivam.glasslauncher.ui.glass.WallpaperPath.OwnCopy }
        }
    }

    @Test fun automaticSwitchKeepsEditsAndStopsAfterExternalChange() {
        val device = androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("cmd uimode night no")
        ActivityScenario.launch<SettingsActivity>(SettingsActivity.intent(app, "wallpaper:graphite")).use {
            compose.waitUntil(10000) { !app.polish.night.value }
            val edit = WallpaperEdit(1.25f, 0.3f, 0.7f, 0.2f, 0.4f)
            assertTrue(runBlocking { app.polish.applyWallpaper("graphite", "light", false, true, edit) }.isSuccess)
            device.executeShellCommand("cmd uimode night yes")
            compose.waitUntil(20000) { app.polish.settings.state.value?.wallpaperApplied == "dark" }
            assertEquals(edit, runBlocking { app.polish.settings.flow.first() }.wallpaperEdit)
            val external = Bitmap.createBitmap(32, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
            val manager = WallpaperManager.getInstance(app)
            val externalId = manager.setBitmap(external, null, true, WallpaperManager.FLAG_SYSTEM)
            external.recycle()
            device.executeShellCommand("cmd uimode night no")
            compose.waitUntil(20000) { app.polish.settings.state.value?.wallpaperId == null }
            assertEquals(externalId, manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
        }
    }

    @Test fun manualVariantAndResetStayLocalUntilApply() {
        val before = runBlocking { app.polish.settings.flow.first() }
        ActivityScenario.launch<SettingsActivity>(SettingsActivity.intent(app, "wallpaper:clay")).use {
            compose.waitUntil(15000) { compose.onAllNodesWithTag("wallpaper-preview").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Dark"))
            compose.onNodeWithText("Dark").performScrollTo().performClick()
            compose.onNodeWithText("Auto light/dark").performScrollTo().assertIsOff()
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Zoom"))
            compose.onNodeWithContentDescription("Zoom").performScrollTo()
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1.8f) }
            compose.onNodeWithText("Reset adjustments").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Zoom").performScrollTo().assert(
                SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
                    androidx.compose.ui.semantics.ProgressBarRangeInfo(1f, 1f..2f)))
        }
        assertEquals(before, runBlocking { app.polish.settings.flow.first() })
    }

    @Test fun largeTextAndLandscapeKeepControlsReachable() {
        val device = androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
        try {
            device.executeShellCommand("settings put system font_scale 1.5")
            ActivityScenario.launch<SettingsActivity>(SettingsActivity.intent(app, "wallpaper:silkflow")).use { scenario ->
                compose.waitUntil(15000) { compose.onAllNodesWithTag("wallpaper-preview").fetchSemanticsNodes().isNotEmpty() }
                screenshot(device, "studio-large-text.png")
                compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("apply-wallpaper"))
                compose.onNodeWithTag("apply-wallpaper").assertIsDisplayed().assertIsEnabled()
                scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                compose.waitUntil(10000) { app.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
                compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("apply-wallpaper"))
                compose.onNodeWithTag("apply-wallpaper").assertIsDisplayed()
                screenshot(device, "studio-landscape.png")
            }
        } finally { device.executeShellCommand("settings put system font_scale 1.0") }
    }

    @Test fun failedApplyDoesNotChangeSelectionOrAutoMode() = runBlocking {
        val before = app.polish.settings.flow.first()
        assertTrue(app.polish.applyWallpaper("missing-design", "light", false, !before.wallpaperAutoDark).isFailure)
        assertTrue(app.polish.applyWallpaper("mist", "invalid-mode", false).isFailure)
        assertEquals(before, app.polish.settings.flow.first())
    }

    @Test fun rendererUsesCropAndColorAndDoesNotMutateSource() {
        val source = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.RED)
        for (x in 2..3) for (y in 0..1) source.setPixel(x, y, Color.BLUE)
        val result = WallpaperRenderer.render(source, 2, 2, WallpaperEdit(focusX = 1f, dim = 0.5f))
        assertTrue(Color.blue(result.getPixel(1, 1)) in 127..128)
        assertEquals(0, Color.red(result.getPixel(1, 1)))
        assertEquals(Color.BLUE, source.getPixel(3, 1))
        assertFalse(source.isRecycled)
        result.recycle(); source.recycle()
    }

    @Test fun allBundledImagesDecodeAndThumbCacheReusesBitmaps() {
        for (design in app.polish.wallpapers.all) for (mode in listOf("light", "dark")) {
            val bitmap = app.polish.wallpapers.loadFull(design.id, mode)!!
            assertEquals(1440, bitmap.width); assertEquals(3200, bitmap.height); bitmap.recycle()
            val thumb = app.polish.wallpapers.loadThumb(design.id, mode)!!
            assertEquals(270, thumb.width); assertEquals(600, thumb.height)
            assertSame(thumb, app.polish.wallpapers.loadThumb(design.id, mode))
        }
        assertNull(app.polish.wallpapers.loadThumb("../invalid", "dark"))
        assertNull(app.polish.wallpapers.loadPreview("mist", "invalid"))
    }
}
