package com.shivam.glasslauncher.settings

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.polish.*
import com.shivam.glasslauncher.ui.glass.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun WallpaperGalleryScreen(nav: SettingsNav) {
    val app = GlassApp.from(LocalContext.current)
    val p = app.polish.settings.state.collectAsState().value ?: return
    val night by app.polish.night.collectAsState()
    var category by rememberSaveable { mutableStateOf("All") }
    var showDark by rememberSaveable { mutableStateOf(night) }
    val all by produceState<List<GlassWallpaper>>(emptyList()) {
        value = withContext(Dispatchers.IO) { app.polish.wallpapers.all }
    }
    val categories = listOf("All", "Favorites") + all.map { it.category }.distinct()
    val list = all.filter { category == "All" || (category == "Favorites" && it.id in p.wallpaperFavorites) || it.category == category }
    ScreenScaffold("Wallpapers", nav, "Find your backdrop. Make it yours.") {
        item {
            GlassListGroup {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        GlassSegmented(categories, categories.indexOf(category).coerceAtLeast(0), { category = categories[it] }, Modifier.width((categories.size * 112).dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    GlassSegmented(listOf("Light", "Dark"), if (showDark) 1 else 0, { showDark = it == 1 })
                }
            }
        }
        if (list.isEmpty()) item {
            GlassListGroup { GlassText(if (category == "Favorites") "Your favorites will appear here. Open a wallpaper and save it to favorites." else "Loading wallpapers…", Modifier.padding(20.dp)) }
        }
        items(list.chunked(3), key = { row -> "$category-${row.first().id}" }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { w ->
                    WallpaperThumb(w, if (showDark) "dark" else "light", w.id == p.wallpaperId, Modifier.weight(1f)) { nav.push("wallpaper:${w.id}") }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun WallpaperThumb(w: GlassWallpaper, mode: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val app = GlassApp.from(LocalContext.current)
    val thumb by produceState<ImageBitmap?>(null, w.id, mode) {
        value = null
        value = withContext(Dispatchers.IO) { app.polish.wallpapers.loadThumb(w.id, mode)?.asImageBitmap() }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        GlassSurface(Modifier.fillMaxWidth().aspectRatio(0.55f).testTag("wallpaper-${w.id}"), shape = GlassShape.rounded(18.dp),
            variant = GlassVariant.Clear, highlighted = selected, onClick = onClick) {
            thumb?.let { Image(it, w.name, Modifier.fillMaxSize().padding(3.dp).clip(GlassShape.rounded(15.dp)), contentScale = ContentScale.Crop) }
        }
        GlassText(w.name, Modifier.padding(top = 6.dp), style = GlassType.Subhead, color = LocalScreenContentColor.current, maxLines = 2)
    }
}

@Composable
internal fun WallpaperDetailScreen(nav: SettingsNav, id: String, toast: GlassToastState) {
    val context = LocalContext.current
    val app = GlassApp.from(context)
    val w = app.polish.wallpapers.byId(id)
    if (w == null) {
        ScreenScaffold("Wallpaper unavailable", nav) { item { GlassText("Choose another wallpaper from the collection.") } }
        return
    }
    val p = app.polish.settings.state.collectAsState().value ?: return
    val night by app.polish.night.collectAsState()
    val saved = if (p.wallpaperId == id) p.wallpaperEdit else WallpaperEdit(focusX = w.focusX, focusY = w.focusY)
    var dark by rememberSaveable(id) { mutableStateOf(if (p.wallpaperId == id) p.wallpaperApplied == "dark" else night) }
    var auto by rememberSaveable(id) { mutableStateOf(p.wallpaperAutoDark) }
    var lockToo by rememberSaveable(id) { mutableStateOf(p.wallpaperLockToo) }
    var zoom by rememberSaveable(id) { mutableFloatStateOf(saved.zoom) }
    var x by rememberSaveable(id) { mutableFloatStateOf(saved.focusX) }
    var y by rememberSaveable(id) { mutableFloatStateOf(saved.focusY) }
    var dim by rememberSaveable(id) { mutableFloatStateOf(saved.dim) }
    var warmth by rememberSaveable(id) { mutableFloatStateOf(saved.warmth) }
    var showHome by rememberSaveable(id) { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val edit = WallpaperEdit(zoom, x, y, dim, warmth)
    // With Auto on, both preview and apply use the current system variant.
    val mode = if (if (auto) night else dark) "dark" else "light"
    val preview by produceState<Bitmap?>(null, id, mode) {
        value = null
        value = withContext(Dispatchers.IO) { app.polish.wallpapers.loadPreview(id, mode) }
    }
    ScreenScaffold(w.name, nav, w.category) {
        item { WallpaperStudioPreview(preview, edit, showHome) }
        item {
            GlassListGroup {
                GlassSwitchRow("Preview home layout", showHome, { showHome = it }, "Your icons and built-in widgets. Stacks and other apps’ widgets show their placement.")
                GlassSwitchRow("Favorite", id in p.wallpaperFavorites, { app.polish.settings.toggleWallpaperFavorite(id) })
                Column(Modifier.padding(12.dp)) {
                    GlassSegmented(listOf("Light", "Dark"), if (mode == "dark") 1 else 0, { dark = it == 1; auto = false })
                }
                GlassSwitchRow("Auto light/dark", auto, { auto = it }, "Follows the system theme, including this preview")
                GlassSwitchRow("Also set lock screen", lockToo, { lockToo = it })
            }
        }
        item {
            GlassListGroup(title = "Make it yours") {
                GlassSliderRow("Zoom", zoom, 1f..2f, { "%.2f×".format(it) }, { zoom = it })
                GlassSliderRow("Horizontal position", x, 0f..1f, { "${(it * 100).toInt()}%" }, { x = it })
                GlassSliderRow("Vertical position", y, 0f..1f, { "${(it * 100).toInt()}%" }, { y = it })
                GlassSliderRow("Dim", dim, 0f..0.6f, { "${(it * 100).toInt()}%" }, { dim = it })
                GlassSliderRow("Warmth", warmth, -1f..1f, { if (it == 0f) "Neutral" else "${(it * 100).toInt()}" }, { warmth = it })
                GlassRow("Reset adjustments", onClick = { zoom = 1f; x = w.focusX; y = w.focusY; dim = 0f; warmth = 0f })
            }
        }
        item {
            GlassButton(if (busy) "Applying…" else "Apply wallpaper", enabled = !busy && preview != null,
                prominent = true, modifier = Modifier.fillMaxWidth().testTag("apply-wallpaper"), onClick = {
                    busy = true
                    // Application scope survives navigation; all values are captured for this transaction.
                    val selectedMode = mode; val selectedEdit = edit; val selectedAuto = auto; val selectedLock = lockToo
                    app.scope.launch {
                        try {
                            val result = app.polish.applyWallpaper(id, selectedMode, selectedLock, selectedAuto, selectedEdit)
                            toast.show(if (result.isSuccess) "Wallpaper applied" else "Couldn't apply wallpaper. Please try again.")
                        } finally { busy = false }
                    }
                })
            if (preview == null) GlassText("Loading preview. If the image remains unavailable, choose another wallpaper.", Modifier.padding(top = 12.dp), style = GlassType.Footnote, color = LocalScreenContentColor.current)
        }
    }
}

@Composable
private fun WallpaperStudioPreview(source: Bitmap?, edit: WallpaperEdit, showHome: Boolean) {
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    val aspect = minOf(metrics.widthPixels, metrics.heightPixels).toFloat() / maxOf(metrics.widthPixels, metrics.heightPixels)
    val layer = rememberGraphicsLayer()
    val origin = remember { mutableStateOf(Offset.Zero) }
    val paint = remember(edit) { Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(edit.colorMatrix()) } }
    // Sample the candidate, not the currently installed wallpaper, for preview text contrast.
    val luminance = remember(source, edit, aspect) {
        if (source == null) 0.3f else {
            var sum = 0f
            val crop = edit.crop(source.width, source.height, minOf(metrics.widthPixels, metrics.heightPixels), maxOf(metrics.widthPixels, metrics.heightPixels))
            for (row in 0..7) for (col in 0..3) {
                val px = (crop.left + (crop.right - crop.left - 1) * col / 3).toInt().coerceIn(0, source.width - 1)
                val py = (crop.top + (crop.bottom - crop.top - 1) * row / 7).toInt().coerceIn(0, source.height - 1)
                val pixel = source.getPixel(px, py)
                val r = ((((pixel shr 16) and 255) + edit.warmth * 24f) * (1f - edit.dim)).coerceIn(0f, 255f)
                val g = ((((pixel shr 8) and 255) + edit.warmth * 4f) * (1f - edit.dim)).coerceIn(0f, 255f)
                val b = (((pixel and 255) - edit.warmth * 24f) * (1f - edit.dim)).coerceIn(0f, 255f)
                sum += (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
            }
            sum / 32
        }
    }
    val snapshot = remember(luminance) { WallpaperSource.Snapshot(null, 1f, 0, 0, null, 0, 0, luminance) }
    val backdrop = remember(snapshot) { Backdrop(listOf(layer), snapshot) { origin.value } }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.width(240.dp).aspectRatio(aspect).clip(GlassShape.rounded(24.dp)).testTag("wallpaper-preview")
            .onGloballyPositioned { origin.value = it.positionInWindow() }) {
            Box(Modifier.fillMaxSize().recordBackdrop(layer).drawWithCache {
                val matrix = source?.let {
                    val crop = edit.crop(it.width, it.height, size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
                    Matrix().apply { setRectToRect(android.graphics.RectF(crop.left, crop.top, crop.right, crop.bottom),
                        android.graphics.RectF(0f, 0f, size.width, size.height), Matrix.ScaleToFit.FILL) }
                }
                onDrawBehind {
                    if (source != null && matrix != null) drawContext.canvas.nativeCanvas.drawBitmap(source, matrix, paint)
                }
            })
            CompositionLocalProvider(LocalBackdrop provides backdrop, LocalFallbackLuminance provides luminance) {
                if (showHome && source != null) WallpaperHomePreview(luminance)
            }
        }
    }
}
