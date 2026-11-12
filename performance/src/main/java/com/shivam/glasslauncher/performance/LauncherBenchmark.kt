package com.shivam.glasslauncher.performance

import android.content.Intent
import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Before
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run on a physical device for release decisions; emulator results are diagnostics only. */
@RunWith(AndroidJUnit4::class)
class LauncherBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()
    private val pkg = "com.shivam.glasslauncher"
    private fun main() = Intent(Intent.ACTION_MAIN).setClassName(pkg, "$pkg.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    private fun gallery() = Intent().setClassName(pkg, "$pkg.settings.SettingsActivity")
        .putExtra("screen", "wallpapers").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    @Before fun prepareRepeatableHome() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        android.util.Log.i("GlassBenchmark", "Preparing home")
        device.executeShellCommand("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n $pkg/.MainActivity")
        android.util.Log.i("GlassBenchmark", "Home started")
        device.waitForIdle(1000)
        if (device.hasObject(By.text("Set up Glass"))) findByScrolling(device, "Done").click()
        device.waitForIdle(1000)
        android.util.Log.i("GlassBenchmark", "Opening wallpaper")
        device.executeShellCommand("am start -W -f 0x10008000 -n $pkg/.settings.SettingsActivity --es screen wallpaper:lagoon")
        android.util.Log.i("GlassBenchmark", "Wallpaper opened")
        val studio = InstrumentationRegistry.getArguments().getString("studio", "true") == "true"
        val label = if (studio) "Apply wallpaper" else "Set"
        if (studio) findByScrolling(device, "Reset adjustments").click()
        android.util.Log.i("GlassBenchmark", "Finding $label")
        val manager = android.app.WallpaperManager.getInstance(InstrumentationRegistry.getInstrumentation().context)
        val before = manager.getWallpaperId(android.app.WallpaperManager.FLAG_SYSTEM)
        findByScrolling(device, label).click()
        android.util.Log.i("GlassBenchmark", "Clicked $label")
        val deadline = android.os.SystemClock.uptimeMillis() + 20000
        while (manager.getWallpaperId(android.app.WallpaperManager.FLAG_SYSTEM) == before && android.os.SystemClock.uptimeMillis() < deadline) {
            android.os.SystemClock.sleep(100)
        }
        check(manager.getWallpaperId(android.app.WallpaperManager.FLAG_SYSTEM) != before) { "Wallpaper did not change" }
        check(device.wait(Until.hasObject(By.text(label)), 10000))
        device.pressBack()
        device.waitForIdle(1000)
        android.util.Log.i("GlassBenchmark", "Fixture ready")
    }

    private fun findByScrolling(device: UiDevice, text: String): UiObject2 {
        repeat(24) {
            device.findObject(By.text(text))?.let { if (!it.visibleBounds.isEmpty) return it }
            device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5,
                device.displayWidth / 2, device.displayHeight / 3, 24)
            device.waitForIdle(1000)
        }
        error("Could not reach $text")
    }

    @Test fun coldStartup() = benchmark.measureRepeated(
        packageName = pkg, metrics = listOf(StartupTimingMetric()), iterations = 5,
        startupMode = StartupMode.COLD, compilationMode = CompilationMode.Full(),
        setupBlock = { pressHome() },
    ) { startActivityAndWait(main()) }

    @Test fun drawerRoundTrip() = benchmark.measureRepeated(
        packageName = pkg, metrics = listOf(FrameTimingMetric()), iterations = 5,
        compilationMode = CompilationMode.Full(),
        setupBlock = { startActivityAndWait(main()); device.waitForIdle(1000) },
    ) {
        val w = device.displayWidth; val h = device.displayHeight
        repeat(4) {
            device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 20)
            device.waitForIdle(1000)
            device.pressBack()
            device.waitForIdle(1000)
        }
    }

    @Test fun wallpaperBrowsing() = benchmark.measureRepeated(
        packageName = pkg, metrics = listOf(FrameTimingMetric()), iterations = 5,
        compilationMode = CompilationMode.Full(),
        setupBlock = { startActivityAndWait(gallery()); check(device.wait(Until.hasObject(By.text("Lagoon")), 10000)) },
    ) {
        val w = device.displayWidth; val h = device.displayHeight
        repeat(3) {
            device.swipe(w / 2, h * 4 / 5, w / 2, h / 3, 18)
            device.swipe(w / 2, h / 3, w / 2, h * 4 / 5, 18)
        }
        device.waitForIdle(1000)
    }
}
