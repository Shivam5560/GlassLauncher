package com.shivam.glasslauncher.ui.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.shivam.glasslauncher.data.AppItem
import com.shivam.glasslauncher.data.DropTarget
import com.shivam.glasslauncher.data.FolderItem
import com.shivam.glasslauncher.data.HomeLayout
import com.shivam.glasslauncher.data.Slot
import com.shivam.glasslauncher.ui.LauncherViewModel
import com.shivam.glasslauncher.ui.glass.Backdrop
import com.shivam.glasslauncher.ui.glass.GlassButton
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassTheme
import com.shivam.glasslauncher.ui.glass.GlassToastHost
import com.shivam.glasslauncher.ui.glass.LocalBackdrop
import com.shivam.glasslauncher.ui.glass.WallpaperBackdropLayer
import com.shivam.glasslauncher.ui.glass.recordBackdrop
import com.shivam.glasslauncher.ui.glass.rememberCrossWindowBlur
import com.shivam.glasslauncher.ui.glass.rememberGlassLight
import com.shivam.glasslauncher.ui.library.AppLibrary
import com.shivam.glasslauncher.ui.spotlight.SpotlightOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The launcher: wallpaper (drawn by the system behind the transparent window), paged 4x6 grid,
 * App Library page, glass page-dot pill and dock, plus overlays (folders, Spotlight, menus).
 *
 * Backdrop nesting (no glass ever refracts a layer containing itself):
 *  - glass inside pages (folder icons, drop targets, library) refracts: wallpaper copy
 *  - dock, page dots, edit toolbar refract: wallpaper copy + pages
 *  - overlays refract: wallpaper copy + whole home (pages + dock)
 */
@Composable
fun LauncherScreen(
    vm: LauncherViewModel,
    homePresses: Int,
    openSettings: (String?) -> Unit,
) {
    val settings = vm.settings.collectAsState().value ?: return
    val layout = vm.layout.collectAsState().value ?: return
    val appsByKey by vm.appsByKey.collectAsState()
    val visibleApps by vm.visibleApps.collectAsState()
    val badges by vm.badges.collectAsState()
    val groups by vm.libraryGroups.collectAsState()
    val snapshot by vm.wallpaper.collectAsState()
    val generation by vm.iconGeneration.collectAsState()
    val activity = LocalContext.current as Activity
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val scope = rememberCoroutineScope()
    val ui = remember { LauncherUiState() }

    val dragging = ui.drag.info != null
    val extraPage = dragging && layout.pages.last().any { it != null }
    val homePageCount = layout.pages.size + if (extraPage) 1 else 0
    val pagerState = rememberPagerState { homePageCount + 1 }
    val layoutState = rememberUpdatedState(layout)
    val homeCountState = rememberUpdatedState(homePageCount)

    val glass = settings.glass
    val light = rememberGlassLight(glass.tiltLighting)
    val overlayOpen = ui.overlayOpen
    val crossWindowBlur = rememberCrossWindowBlur(if (overlayOpen) glass.blurRadius else 0f)
    val overlayAnim by animateFloatAsState(if (overlayOpen) 1f else 0f, spring(dampingRatio = 1f, stiffness = 300f), label = "overlay")
    val lightWallpaper = snapshot.globalLuminance > 0.7f

    val wallpaperLayer = rememberGraphicsLayer()
    val pagesLayer = rememberGraphicsLayer()
    val homeLayer = rememberGraphicsLayer()
    val hasWallpaper = glass.wallpaperBackdrop && snapshot.image != null
    val base = if (hasWallpaper) listOf(wallpaperLayer) else emptyList()
    val wallpaperOnly = remember(hasWallpaper, snapshot) { Backdrop(base, snapshot) }
    val homeBackdrop = remember(hasWallpaper, snapshot) { Backdrop(base + pagesLayer, snapshot) }
    val overlayBackdrop = remember(hasWallpaper, snapshot) { Backdrop(base + homeLayer, snapshot) }

    // Status bar icons follow the wallpaper brightness.
    LaunchedEffect(lightWallpaper) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = lightWallpaper
            isAppearanceLightNavigationBars = lightWallpaper
        }
    }

    // First run: walk through the HyperOS setup.
    LaunchedEffect(settings.onboardingDone) {
        if (!settings.onboardingDone) openSettings("setup")
    }

    // Home button: close overlays, else go to the first page.
    LaunchedEffect(homePresses) {
        if (homePresses == 0) return@LaunchedEffect
        val hadOverlay = ui.overlayOpen || ui.editMode
        ui.closeAll()
        if (!hadOverlay) pagerState.animateScrollToPage(0)
    }

    BackHandler(enabled = true) {
        if (!ui.closeTop()) scope.launch { pagerState.animateScrollToPage(0) }
    }

    val ctx = HomeContext(
        apps = appsByKey,
        labels = layout.labels,
        badges = badges,
        showLabels = settings.showLabels,
        showBadges = settings.showBadges,
        editMode = ui.editMode,
        lightWallpaper = lightWallpaper,
    )

    fun computeHover(at: Offset): DropTarget? {
        val info = ui.drag.info ?: return null
        val lay = layoutState.value
        val page = pagerState.currentPage
        var best: DropTarget? = null
        var bestDist = Float.MAX_VALUE
        var bestRect: Rect? = null
        var bestItem: com.shivam.glasslauncher.data.HomeItem? = null
        for ((i, r) in ui.drag.dockCells) {
            val d = (r.center - at).getDistance()
            if (d < bestDist && d < r.width * 1.1f) {
                bestDist = d; best = DropTarget.DockCell(i, false); bestRect = r; bestItem = lay.dock.getOrNull(i)
            }
        }
        if (page < homeCountState.value) {
            for ((key, r) in ui.drag.pageCells) {
                if (key.first != page || r.width <= 0f) continue
                val d = (r.center - at).getDistance()
                if (d < bestDist && d < r.width * 1.2f) {
                    bestDist = d; best = DropTarget.Cell(page, key.second, false); bestRect = r
                    bestItem = lay.pages.getOrNull(page)?.getOrNull(key.second)
                }
            }
        }
        val rect = bestRect ?: return null
        val isSelf = when (val b = best) {
            is DropTarget.Cell -> info.from == Slot.Page(b.page, b.index)
            is DropTarget.DockCell -> info.from == Slot.Dock(b.index)
            null -> false
        }
        val canMerge = bestItem != null && !isSelf && info.item is AppItem && bestDist < rect.width * 0.36f
        return when (val b = best) {
            is DropTarget.Cell -> b.copy(merge = canMerge)
            is DropTarget.DockCell -> b.copy(merge = canMerge)
            null -> null
        }
    }

    val callbacks = remember(vm, ui) {
        object : HomeCallbacks {
            override fun launch(entry: com.shivam.glasslauncher.data.AppEntry, bounds: android.graphics.Rect?) {
                if (vm.isLocked(entry)) {
                    ui.lockPrompt = LockPrompt(entry, bounds)
                } else {
                    vm.launchNow(activity, entry, bounds)
                    ui.folder = null
                }
            }
            override fun openFolder(folder: FolderItem, bounds: Rect) { ui.folder = OpenFolder(folder.id, null, bounds) }
            override fun showMenu(target: MenuTarget) { ui.menu = target }
            override fun startDrag(info: DragInfo, at: Offset) {
                ui.menu = null
                ui.drag.start(info, at)
            }
            override fun drag(at: Offset) {
                ui.drag.pointer = at
                val h = computeHover(at)
                if (h != ui.drag.hover) {
                    val merge = (h as? DropTarget.Cell)?.merge == true || (h as? DropTarget.DockCell)?.merge == true
                    if (merge) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    ui.drag.hover = h
                }
            }
            override fun endDrag() {
                val (info, target) = ui.drag.end() ?: return
                if (target != null) vm.move(info.from, target)
            }
            override fun cancelDrag() { ui.drag.end() }
            override fun remove(slot: Slot) { vm.removeFromHome(slot) }
            override fun lockScreen() {
                if (!vm.lockScreen()) {
                    ui.toast.show("Double-tap to lock needs Glass Gestures.", "Set up") { openSettings("check") }
                }
            }
        }
    }

    // While dragging, dwell near a screen edge to flip pages.
    LaunchedEffect(dragging) {
        var dwell = 0
        while (ui.drag.info != null) {
            delay(50)
            val x = ui.drag.pointer.x
            val w = screenWidthPx
            val edge = with(density) { 28.dp.toPx() }
            val dir = when {
                x < edge -> -1
                x > w - edge -> 1
                else -> 0
            }
            dwell = if (dir != 0) dwell + 1 else 0
            if (dwell >= 10) {
                dwell = 0
                val target = (pagerState.currentPage + dir).coerceIn(0, homeCountState.value - 1)
                if (target != pagerState.currentPage) {
                    pagerState.animateScrollToPage(target)
                    callbacks.drag(ui.drag.pointer)
                }
            }
        }
    }

    GlassTheme(style = glass, light = light, fallbackLuminance = snapshot.globalLuminance, backdrop = homeBackdrop) {
        CompositionLocalProvider(LocalIconRenderer provides vm.icons, LocalIconGeneration provides generation) {
            Box(Modifier.fillMaxSize()) {
                WallpaperBackdropLayer(snapshot, wallpaperLayer, hasWallpaper)

                // Fallback base layer when the compositor cannot blur behind the window
                // (battery saver, some HyperOS builds): pre-blurred wallpaper, else solid tinted glass.
                if (!crossWindowBlur && overlayAnim > 0.01f) {
                    val img = snapshot.image
                    if (img != null) {
                        Image(
                            img,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = overlayAnim }.blur(glass.blurRadius.dp),
                        )
                    } else {
                        GlassSurface(
                            Modifier.fillMaxSize().graphicsLayer { alpha = overlayAnim },
                            shape = GlassShape.rounded(0.dp),
                            backdrop = null,
                            role = null,
                        )
                    }
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .blur((overlayAnim * 16f).dp)
                        .recordBackdrop(homeLayer),
                ) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .recordBackdrop(pagesLayer)
                                .swipeDownToSearch(enabled = settings.swipeDownForSpotlight && !dragging && pagerState.currentPage < homePageCount) { origin ->
                                    ui.openSpotlight(origin)
                                },
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = homePageCount + 1,
                                userScrollEnabled = !dragging,
                                key = { it },
                            ) { page ->
                                CompositionLocalProvider(LocalBackdrop provides wallpaperOnly) {
                                    if (page < homePageCount) {
                                        HomeGridPage(
                                            page = page,
                                            items = layout.pages.getOrNull(page) ?: HomeLayout.emptyPage(),
                                            ctx = ctx,
                                            drag = ui.drag,
                                            callbacks = callbacks,
                                            doubleTapToLock = settings.doubleTapToLock,
                                        )
                                    } else {
                                        AppLibrary(
                                            groups = groups,
                                            allApps = visibleApps,
                                            label = { ctx.label(it) },
                                            lightWallpaper = lightWallpaper,
                                            onLaunch = { e, b -> callbacks.launch(e, b) },
                                            onOpenGroup = { g, r -> ui.folder = OpenFolder(null, g.id, r) },
                                            onMenu = { ui.menu = it },
                                        )
                                    }
                                }
                            }
                        }
                        val libraryFraction = {
                            val pos = pagerState.currentPage + pagerState.currentPageOffsetFraction
                            (pos - (homePageCount - 1)).coerceIn(0f, 1f)
                        }
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp).graphicsLayer { alpha = 1f - libraryFraction() },
                            contentAlignment = Alignment.Center,
                        ) {
                            PageDots(
                                homePages = homePageCount,
                                current = pagerState.currentPage,
                                onClick = { origin -> ui.openSpotlight(origin) },
                            )
                        }
                        Dock(
                            items = layout.dock,
                            ctx = ctx,
                            drag = ui.drag,
                            callbacks = callbacks,
                            onBounds = { },
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .graphicsLayer {
                                    val f = libraryFraction()
                                    alpha = 1f - f
                                    translationY = f * 140.dp.toPx()
                                },
                        )
                    }
                }

                // Edit-mode toolbar ("jiggle mode").
                if (ui.editMode) {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        GlassButton("Settings", onClick = { openSettings(null) }, icon = GlassIcons.Gear)
                        GlassButton("Done", onClick = { ui.editMode = false }, prominent = true)
                    }
                }

                // Icon following the finger while dragging.
                ui.drag.info?.let { info ->
                    val size = with(density) { info.iconSizePx.toDp() }
                    val p = ui.drag.pointer - info.touchOffset
                    Box(
                        Modifier
                            .offset { IntOffset(p.x.roundToInt(), p.y.roundToInt()) }
                            .size(size)
                            .graphicsLayer { scaleX = 1.12f; scaleY = 1.12f; alpha = 0.96f },
                    ) {
                        when (val item = info.item) {
                            is AppItem -> info.entry?.let { AppIconImage(it, size) }
                            is FolderItem -> FolderIcon(item, appsByKey, size)
                        }
                    }
                }

                CompositionLocalProvider(LocalBackdrop provides overlayBackdrop) {
                    FolderOverlay(ui, layout, groups, ctx, callbacks)
                    SpotlightOverlay(
                        ui = ui,
                        vm = vm,
                        label = { ctx.label(it) },
                        onLaunch = { e, b -> callbacks.launch(e, b) },
                        onOpenSettings = { openSettings(null) },
                    )
                    ContextMenuOverlay(ui, vm, layout, ctx, callbacks)
                    RenameOverlay(ui, vm)
                    LockPromptOverlay(ui, vm, ctx)
                    GlassToastHost(ui.toast)
                }
            }
        }
    }
}

/** Swipe down anywhere on the home pages opens Spotlight (only unconsumed, mostly vertical drags). */
private fun Modifier.swipeDownToSearch(enabled: Boolean, onSwipe: (Rect) -> Unit): Modifier =
    if (!enabled) this else pointerInput(Unit) {
        val threshold = 56.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var total = Offset.Zero
            while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed || ch.isConsumed) break
                total += ch.positionChange()
                if (total.y < -threshold / 2 || abs(total.x) > threshold) break
                if (total.y > threshold && total.y > abs(total.x) * 1.4f) {
                    ch.consume()
                    val c = down.position
                    onSwipe(Rect(c.x - 40f, c.y - 20f, c.x + 40f, c.y + 20f))
                    break
                }
            }
        }
    }

