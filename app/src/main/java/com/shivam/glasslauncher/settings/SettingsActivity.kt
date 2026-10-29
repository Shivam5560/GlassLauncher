package com.shivam.glasslauncher.settings

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.ui.glass.Backdrop
import com.shivam.glasslauncher.ui.glass.Contrast
import com.shivam.glasslauncher.ui.glass.LocalScreenContentColor
import com.shivam.glasslauncher.ui.glass.SurfaceKind
import com.shivam.glasslauncher.ui.glass.screenTextShadow
import androidx.compose.ui.graphics.toArgb
import com.shivam.glasslauncher.ui.glass.GlassIconButton
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassTheme
import com.shivam.glasslauncher.ui.glass.GlassToastHost
import com.shivam.glasslauncher.ui.glass.GlassToastState
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.WallpaperBackdropLayer
import com.shivam.glasslauncher.ui.glass.rememberCrossWindowBlur
import com.shivam.glasslauncher.ui.glass.rememberGlassLight
import com.shivam.glasslauncher.ui.home.LocalIconGeneration
import com.shivam.glasslauncher.ui.home.LocalIconRenderer

/**
 * Settings and the first-run setup guide. The window is transparent over the wallpaper, which the
 * compositor blurs (cross-window blur); every group, row and control on top is Liquid Glass.
 */
class SettingsActivity : ComponentActivity() {

    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val start = intent.getStringExtra(EXTRA_SCREEN) ?: "main"
        setContent { SettingsRoot(start, resumeTick.intValue, onFinish = { finish() }) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
        GlassApp.from(this).wallpaper.refresh()
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        fun intent(context: Context, screen: String?): Intent =
            Intent(context, SettingsActivity::class.java)
                .putExtra(EXTRA_SCREEN, screen)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** Navigation for settings screens. */
class SettingsNav(start: String, private val finish: () -> Unit) {
    var stack by mutableStateOf(listOf(start))
        private set
    val current get() = stack.last()
    fun push(screen: String) { stack = stack + screen }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) else finish() }
    val canPop get() = stack.size > 1
}

@Composable
private fun SettingsRoot(start: String, resumeTick: Int, onFinish: () -> Unit) {
    val context = LocalContext.current
    val app = GlassApp.from(context)
    val settings = app.settings.state.collectAsState().value ?: return
    val snapshot by app.wallpaper.snapshot.collectAsState()
    val generation by app.icons.generation.collectAsState()
    val nav = remember { SettingsNav(start, onFinish) }
    val toast = remember { GlassToastState() }
    val light = rememberGlassLight(settings.glass.tiltLighting)
    val bgRole = settings.glass.role(SurfaceKind.Background)
    val crossBlur = rememberCrossWindowBlur(bgRole.blur ?: settings.glass.blurRadius)
    val wallpaperLayer = rememberGraphicsLayer()
    val hasWallpaper = settings.glass.wallpaperBackdrop && snapshot.image != null
    val backdrop = remember(hasWallpaper, snapshot) { Backdrop(if (hasWallpaper) listOf(wallpaperLayer) else emptyList(), snapshot) }

    BackHandler { nav.pop() }

    GlassTheme(settings.glass, light, snapshot.globalLuminance, backdrop) {
        androidx.compose.runtime.CompositionLocalProvider(LocalIconRenderer provides app.icons, LocalIconGeneration provides generation) {
            Box(Modifier.fillMaxSize()) {
                WallpaperBackdropLayer(snapshot, wallpaperLayer, hasWallpaper)
                if (!crossBlur) {
                    val img = snapshot.image
                    if (img != null) {
                        Image(img, null, Modifier.fillMaxSize().blur(settings.glass.blurRadius.dp), contentScale = ContentScale.Crop)
                    } else {
                        GlassSurface(Modifier.fillMaxSize(), shape = GlassShape.rounded(0.dp), backdrop = null, role = null, kind = SurfaceKind.Background)
                    }
                }
                // Background role (Appearance → Edit theme → Background): a dim so text drawn
                // straight on the wallpaper (titles, section headers) stays legible; its colour is
                // picked by contrast against the dimmed wallpaper.
                val dim = androidx.compose.ui.graphics.Color(bgRole.color ?: 0xFF000000.toInt()).copy(alpha = 1f)
                val dimAlpha = bgRole.opacity ?: 0.38f
                Box(Modifier.fillMaxSize().background(dim.copy(alpha = dimAlpha)))
                val screenText = bgRole.contentColor?.let { androidx.compose.ui.graphics.Color(it) }
                    ?: androidx.compose.ui.graphics.Color(Contrast.pickText(Contrast.glassLuminance(Contrast.gammaToLinear(snapshot.globalLuminance), dim.toArgb(), dimAlpha)))
                androidx.compose.runtime.CompositionLocalProvider(LocalScreenContentColor provides screenText) {
                    key(nav.current) {
                        SettingsScreen(nav, settings, snapshot, resumeTick, toast, onFinish)
                    }
                }
                GlassToastHost(toast)
            }
        }
    }
}

/** Common screen chrome: glass back button, large title, scrolling content. */
@Composable
fun ScreenScaffold(title: String, nav: SettingsNav, content: LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (nav.canPop) {
                GlassIconButton(GlassIcons.ChevronLeft, onClick = { nav.pop() }, size = 40.dp)
                Spacer(Modifier.width(10.dp))
            }
        }
        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item("title") {
                val c = LocalScreenContentColor.current
                GlassText(title, Modifier.padding(start = 4.dp, bottom = 2.dp), style = GlassType.LargeTitle.copy(shadow = screenTextShadow(c)), color = c)
            }
            content()
            item("navpad") { Spacer(Modifier.navigationBarsPadding().height(8.dp)) }
        }
    }
}

