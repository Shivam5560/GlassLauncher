package com.shivam.glassicons

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Variant suffixes, matching the convention Glass Launcher reads (see README "Glass Icons"). */
enum class Variant(val title: String, val suffix: String) { Light("Light", ""), Dark("Dark", "_dark"), Tinted("Tinted", "_tinted") }

const val GLASS_LAUNCHER = "com.shivam.glasslauncher"
const val ACTION_APPLY = "com.shivam.glasslauncher.action.APPLY_ICON_PACK"
const val EXTRA_PACK = "com.shivam.glasslauncher.extra.ICON_PACK"

class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent { Gallery() }
    }
}

private val TintSwatches = listOf(Color(0xFFFFD60A), Color(0xFF64D2FF), Color(0xFF30D158), Color(0xFFFF9F0A), Color(0xFFBF5AF2), Color(0xFFFFFFFF))

@Composable
private fun Gallery() {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    var variant by remember { mutableStateOf(if (systemDark) Variant.Dark else Variant.Light) }
    var tab by remember { mutableIntStateOf(0) }
    var tint by remember { mutableStateOf(TintSwatches[0]) }
    var wallpaperIndex by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var root by remember { mutableStateOf(IntSize.Zero) }

    val icons by produceState(emptyList<PackIcon>()) { value = withContext(Dispatchers.IO) { IconCatalog.icons(context) } }
    val wallpapers by produceState(emptyList<Wallpaper>()) { value = withContext(Dispatchers.IO) { IconCatalog.wallpapers(context) } }
    // The backdrop follows the variant: dark icons over the dark version of the wallpaper.
    val darkBackdrop = variant != Variant.Light
    val wp = wallpapers.getOrNull(wallpaperIndex)
    val backdropImage by produceState<ImageBitmap?>(null, wp, darkBackdrop) {
        value = wp?.let { w -> withContext(Dispatchers.IO) { IconCatalog.loadAsset(context, if (darkBackdrop) w.dark else w.light)?.asImageBitmap() } }
    }
    LaunchedEffect(message) { if (message != null) { delay(3500); message = null } }

    val backdrop = Backdrop(backdropImage, root, darkBackdrop)
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { root = it }) {
            backdropImage?.let { img -> Canvas(Modifier.fillMaxSize()) { drawCropped(img, root) } }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(10.dp))
                GlassLabel("Glass Icons", size = 32, weight = FontWeight.Bold, color = Color.White)
                GlassLabel("${icons.size} original icons · light, dark and tinted", size = 14, alpha = 0.8f, color = Color.White)
                Spacer(Modifier.height(12.dp))
                GlassSegmented(listOf("Icons", "Requests", "About"), tab, { tab = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                when (tab) {
                    0 -> IconsTab(Modifier.weight(1f), icons, variant, { variant = it }, tint, { tint = it }, wallpapers, wallpaperIndex, { wallpaperIndex = it }, darkBackdrop)
                    1 -> RequestsTab(Modifier.weight(1f)) { message = it }
                    else -> AboutTab(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Apply to Glass Launcher", onClick = { message = applyToGlass(context) }, Modifier.weight(1f), prominent = true)
                }
                Spacer(Modifier.height(10.dp))
            }
            message?.let { m ->
                GlassPanel(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp, start = 24.dp, end = 24.dp), shape = RoundedCornerShape(22.dp)) {
                    GlassLabel(m, Modifier.padding(horizontal = 18.dp, vertical = 12.dp), size = 14, weight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun applyToGlass(context: android.content.Context): String {
    val intent = Intent(ACTION_APPLY).setPackage(GLASS_LAUNCHER).putExtra(EXTRA_PACK, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        "Applied in Glass Launcher"
    } catch (e: ActivityNotFoundException) {
        // Not installed (or too old): fall back to Nova's documented apply intent, then to instructions.
        try {
            context.startActivity(
                Intent("com.teslacoilsw.launcher.APPLY_ICON_THEME").setPackage("com.teslacoilsw.launcher")
                    .putExtra("com.teslacoilsw.launcher.extra.ICON_THEME_TYPE", "GO")
                    .putExtra("com.teslacoilsw.launcher.extra.ICON_THEME_PACKAGE", context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            "Glass Launcher isn't installed — opened Nova instead"
        } catch (e2: ActivityNotFoundException) {
            "Glass Launcher isn't installed. Pick “Glass Icons” in your launcher's icon pack settings."
        }
    }
}

@Composable
private fun IconsTab(
    modifier: Modifier,
    icons: List<PackIcon>,
    variant: Variant,
    onVariant: (Variant) -> Unit,
    tint: Color,
    onTint: (Color) -> Unit,
    wallpapers: List<Wallpaper>,
    wallpaperIndex: Int,
    onWallpaper: (Int) -> Unit,
    dark: Boolean,
) {
    val context = LocalContext.current
    Column(modifier) {
        GlassSegmented(Variant.entries.map { it.title }, variant.ordinal, { onVariant(Variant.entries[it]) }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        if (variant == Variant.Tinted) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassLabel("Tint", size = 14, weight = FontWeight.Medium, color = Color.White)
                TintSwatches.forEach { c ->
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(c)
                            .border(if (c == tint) 2.5.dp else 1.dp, Color.White.copy(alpha = if (c == tint) 1f else 0.4f), CircleShape)
                            .clickable { onTint(c) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(wallpapers.size) { i ->
                val w = wallpapers[i]
                val thumb by produceState<ImageBitmap?>(null, w, dark) {
                    value = withContext(Dispatchers.IO) { IconCatalog.loadAsset(context, if (dark) w.dark else w.light)?.let { Bitmap.createScaledBitmap(it, 72, 160, true).asImageBitmap() } }
                }
                Box(
                    Modifier.size(36.dp, 64.dp).clip(RoundedCornerShape(10.dp))
                        .border(if (i == wallpaperIndex) 2.dp else 1.dp, Color.White.copy(alpha = if (i == wallpaperIndex) 1f else 0.4f), RoundedCornerShape(10.dp))
                        .clickable { onWallpaper(i) },
                ) { thumb?.let { Image(it, null, Modifier.fillMaxSize()) } }
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassPanel(Modifier.fillMaxWidth().weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(76.dp),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                var lastCategory = ""
                icons.forEach { icon ->
                    if (icon.category != lastCategory) {
                        lastCategory = icon.category
                        item(key = "cat-${icon.category}", span = { GridItemSpan(maxLineSpan) }) {
                            GlassLabel(icon.category, Modifier.padding(top = 4.dp, start = 4.dp), size = 13, weight = FontWeight.SemiBold, alpha = 0.7f)
                        }
                    }
                    item(key = icon.name) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PackIconImage(icon.name, variant, tint, 60.dp)
                            Spacer(Modifier.height(5.dp))
                            GlassLabel(icon.label, size = 11, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** One icon in a variant. Tinted = our dark glass tile + the monochrome glyph tinted with [tint]. */
@Composable
fun PackIconImage(name: String, variant: Variant, tint: Color, size: Dp) {
    val context = LocalContext.current
    val id = remember(name, variant) {
        IconCatalog.drawableId(context, name + variant.suffix).takeIf { it != 0 } ?: IconCatalog.drawableId(context, name)
    }
    if (id == 0) return
    if (variant == Variant.Tinted) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.25f))
                .background(Brush.verticalGradient(listOf(Color(0xFF303136), Color(0xFF111113)))),
        ) {
            Image(painterResource(id), null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn))
        }
    } else {
        Image(painterResource(id), null, Modifier.size(size))
    }
}

@Composable
private fun RequestsTab(modifier: Modifier, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val missing by produceState<List<MissingApp>?>(null) { value = withContext(Dispatchers.IO) { IconCatalog.missing(context) } }
    Column(modifier) {
        GlassPanel(Modifier.fillMaxWidth().weight(1f)) {
            val list = missing
            when {
                list == null -> GlassLabel("Looking at your apps…", Modifier.padding(18.dp))
                list.isEmpty() -> GlassLabel("Every app on this phone has a Glass icon.", Modifier.padding(18.dp))
                else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        GlassLabel("${list.size} installed apps have no icon yet", size = 13, weight = FontWeight.SemiBold, alpha = 0.7f)
                    }
                    items(list, key = { it.component.flattenToShortString() }) { app ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val icon by produceState<ImageBitmap?>(null, app) {
                                value = withContext(Dispatchers.IO) {
                                    try { context.packageManager.getActivityIcon(app.component).toBitmap(96, 96).asImageBitmap() } catch (e: Exception) { null }
                                }
                            }
                            Box(Modifier.size(38.dp)) { icon?.let { Image(it, null, Modifier.fillMaxSize()) } }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                GlassLabel(app.label, size = 15, maxLines = 1)
                                GlassLabel(app.component.flattenToShortString(), size = 11, alpha = 0.6f, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassButton("Share request list", onClick = {
            val list = missing.orEmpty()
            if (list.isEmpty()) {
                onMessage("Nothing to request")
            } else {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, IconCatalog.requestText(list))
                context.startActivity(Intent.createChooser(send, "Share icon requests").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun AboutTab(modifier: Modifier) {
    GlassPanel(modifier.fillMaxWidth()) {
        LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { GlassLabel("About Glass Icons", size = 20, weight = FontWeight.Bold) }
            item { GlassLabel("Every glyph is original artwork drawn for this pack. Third-party apps get simplified, stylised glyphs in our own style; no official logo files are traced or embedded. Not affiliated with Apple or with any app shown.", size = 14, alpha = 0.85f) }
            item { GlassLabel("Works as a standard ADW/Nova icon pack, so Nova, Lawnchair, Smart Launcher and others can use it too. Glass Launcher also picks the dark and tinted variants to match your theme.", size = 14, alpha = 0.85f) }
            item { GlassLabel("No internet access, no analytics. The request list stays on your phone until you share it.", size = 14, alpha = 0.85f) }
        }
    }
}
