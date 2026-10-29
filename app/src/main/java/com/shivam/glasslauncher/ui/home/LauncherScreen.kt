@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.shivam.glasslauncher.ui.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.shivam.glasslauncher.ui.glass.GlassIconButton
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassTheme
import com.shivam.glasslauncher.ui.glass.GlassToastHost
import com.shivam.glasslauncher.ui.glass.LocalBackdrop
import com.shivam.glasslauncher.ui.glass.SnapshotBackdropLayer
import com.shivam.glasslauncher.ui.glass.WallpaperBackdropLayer
import com.shivam.glasslauncher.ui.glass.recordBackdrop
import com.shivam.glasslauncher.ui.glass.rememberCrossWindowBlur
import com.shivam.glasslauncher.ui.glass.rememberDeviceTilt
import com.shivam.glasslauncher.ui.glass.rememberGlassLight
import com.shivam.glasslauncher.ui.library.AppLibrary
import com.shivam.glasslauncher.ui.spotlight.SpotlightOverlay
import com.shivam.glasslauncher.today.TodayView
import com.shivam.glasslauncher.widgets.HomeWidgetsLayer
import com.shivam.glasslauncher.widgets.LocalWidgetEnv
import com.shivam.glasslauncher.widgets.PickerTarget
import com.shivam.glasslauncher.widgets.WidgetEnv
import com.shivam.glasslauncher.widgets.WidgetGlyphs
import com.shivam.glasslauncher.widgets.WidgetHostLifecycle
import com.shivam.glasslauncher.widgets.WidgetOverlays
import com.shivam.glasslauncher.widgets.WidgetUiState
import com.shivam.glasslauncher.widgets.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The launcher: wallpaper (drawn by the system behind the transparent window), the Today view
 * (leftmost page), paged 4x6 grid with widgets, App Library page, glass page-dot pill and dock,
 * plus overlays (folders, Spotlight, menus, widget picker and settings, page manager).
 *
 * Backdrop nesting (no glass ever refracts a layer containing itself):
 *  - glass inside pages (folder icons, drop targets, library) refracts: wallpaper copy
 *  - dock, page dots, edit toolbar refract: wallpaper copy + pages
 *  - overlays refract: wallpaper copy + whole home (pages + dock)
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class) // statusBarsIgnoringVisibility
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
    val widgetUi = remember(activity) { WidgetUiState(activity, scope, ui.toast) }
    val reduced = remember { reducedMotion(activity) }
    // 0 = home at rest, 1 = pushed back behind an opening app (scaled down and blurred).
    val appDepth = remember { androidx.compose.animation.core.Animatable(0f) }
    var appLaunched by remember { androidx.compose.runtime.mutableStateOf(false) }
    // Widgets (icon group) launch apps through the same path as icons, lock check included.
    val launchHolder = remember { arrayOfNulls<(com.shivam.glasslauncher.data.AppEntry, android.graphics.Rect?) -> Unit>(1) }
    val widgetEnv = remember(ui.editMode, reduced) {
        WidgetEnv(
            openSpotlight = { ui.openSpotlight(it) },
            toast = { ui.toast.show(it) },
            toastAction = { text, label, action -> ui.toast.show(text, label, action) },
            openSettings = openSettings,
            editWidget = { widgetUi.editing = it },
            editMode = ui.editMode,
            reducedMotion = reduced,
            launchApp = { e, b -> launchHolder[0]?.invoke(e, b) },
        )
    }

    val dragging = ui.drag.info != null
    val extraPage = dragging && layout.pages.last().any { it != null }
    val homePageCount = layout.pages.size + if (extraPage) 1 else 0
    // Ring: [Today] + home pages + [App Library]. Home page p is ring page p + TODAY_PAGES. With
    // infinite scroll the pager is a long run of copies of the ring, started in the middle.
    val ring = PagerRing(homePageCount + TODAY_PAGES + 1, settings.infiniteScroll)
    val ringState = rememberUpdatedState(ring)
    val pagerState = rememberPagerState(initialPage = ring.start + layout.defaultPage + TODAY_PAGES) { ringState.value.pagerCount }
    val layoutState = rememberUpdatedState(layout)
    val homeCountState = rememberUpdatedState(homePageCount)
    // Ring position (0 = Today) of the settled page, kept across ring size changes.
    val ringPage = ring.of(pagerState.currentPage)
    val lastRing = remember { androidx.compose.runtime.mutableIntStateOf(ringPage) }
    LaunchedEffect(ring) {
        // Pages were added or removed, or the loop toggled: stay on the same ring page.
        val target = ring.start + lastRing.intValue.coerceIn(0, ring.size - 1)
        if (ring.of(pagerState.currentPage) != lastRing.intValue || pagerState.currentPage >= ring.pagerCount) pagerState.scrollToPage(target)
    }
    LaunchedEffect(Unit) {
        // Keyed on the page only, so a ring resize doesn't overwrite the position before the effect above reads it.
        androidx.compose.runtime.snapshotFlow { pagerState.currentPage }.collect { lastRing.intValue = ringState.value.of(it) }
    }
    // The page pill reads "Search" at rest and shows the dots while paging, for a second after.
    var pillDots by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { pagerState.isScrollInProgress }.collectLatest { moving ->
            if (moving) pillDots = true else { delay(1000); pillDots = false }
        }
    }
    suspend fun goTo(ringTarget: Int) = pagerState.animateScrollToPage(ringState.value.nearest(pagerState.currentPage, ringTarget))

    val glass = settings.glass
    val light = rememberGlassLight(glass.tiltLighting)
    val overlayOpen = ui.overlayOpen || widgetUi.open
    val crossWindowBlur = rememberCrossWindowBlur(if (overlayOpen) glass.blurRadius else 0f)
    val overlayAnimState = animateFloatAsState(if (overlayOpen) 1f else 0f, spring(dampingRatio = 1f, stiffness = 300f), label = "overlay")
    val overlayAnim by overlayAnimState
    val lightWallpaper = snapshot.globalLuminance > 0.7f

    val wallpaperLayer = rememberGraphicsLayer()
    val pagesLayer = rememberGraphicsLayer()
    val homeLayer = rememberGraphicsLayer()
    val hasWallpaper = glass.wallpaperBackdrop && snapshot.image != null
    val base = if (hasWallpaper) listOf(wallpaperLayer) else emptyList()
    val wallpaperOnly = remember(hasWallpaper, snapshot) { Backdrop(base, snapshot) }
    val homeBackdrop = remember(hasWallpaper, snapshot) { Backdrop(base + pagesLayer, snapshot) }
    // While an overlay is open the home under it is still, so overlays refract one snapshot of it,
    // kept until the close animation ends. Before the snapshot exists (a frame or two, overlay
    // still nearly transparent) they refract the wallpaper alone, never the live home layer.
    val frozenHomeLayer = rememberGraphicsLayer()
    var frozenHome by remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val overlayGone = !overlayOpen && overlayAnim < 0.01f
    LaunchedEffect(overlayOpen) {
        if (overlayOpen) frozenHome = runCatching { homeLayer.toImageBitmap() }.getOrNull()
    }
    LaunchedEffect(overlayGone) { if (overlayGone) frozenHome = null }
    val frozen = frozenHome != null
    val overlayBackdrop = remember(hasWallpaper, snapshot, frozen) { Backdrop(if (frozen) base + frozenHomeLayer else base, snapshot) }

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
        val hadOverlay = ui.overlayOpen || ui.editMode || widgetUi.open
        ui.closeAll()
        widgetUi.closeAll()
        if (!hadOverlay) goTo(layoutState.value.defaultPage + TODAY_PAGES)
    }

    BackHandler(enabled = true) {
        if (!widgetUi.closeTop() && !ui.closeTop()) {
            scope.launch { goTo(layoutState.value.defaultPage + TODAY_PAGES) }
        }
    }

    // Once both stores are loaded: drop widget instances and Android widget ids nothing uses.
    val widgetStore by widgetUi.repo.state.collectAsState()
    LaunchedEffect(widgetStore != null) {
        if (widgetStore != null) widgetUi.repo.prune(layoutState.value.widgets.map { it.id }.toSet())
    }
    // Phase 5 default home (header, photo and calendar widgets), once the app grid exists.
    LaunchedEffect(widgetStore != null, layout.known.isNotEmpty()) {
        if (widgetStore != null && layoutState.value.known.isNotEmpty()) {
            delay(300)
            widgetUi.seedHomeIfNeeded(layoutState.value)
        }
    }

    // Ultra-immersive: hide the status bar (and the nav bar when Glass gestures replace it) on
    // home and in the drawer; a swipe from the edge reveals them transiently.
    val lifecycleTick = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        lifecycleTick.intValue++
        // Back from an app: the home springs forward out of depth and comes into focus.
        // HyperOS cross-fades our window in for ~150 ms after resume; hold the pushed-back pose
        // through the start of it so the motion is seen, then ease forward (measured on a Turbo 5).
        if (appDepth.value > 0f) scope.launch {
            appDepth.snapTo(1f)
            delay(70)
            appDepth.animateTo(0f, HomeReturn)
        }
    }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_PAUSE) { appLaunched = false }
    LaunchedEffect(settings.immersive, lifecycleTick.intValue) {
        val gesturesOn = com.shivam.glasslauncher.gestures.GlassGestureService.isEnabled(activity)
        ImmersiveMode.apply(activity.window, settings.immersive, hideNavigation = gesturesOn)
    }

    // KLWP and other live wallpapers scroll with the pages through wallpaper offsets. With a
    // static wallpaper the offset stays centred so Glass's wallpaper copy lines up for refraction.
    LaunchedEffect(snapshot.isLive, homePageCount) {
        if (!snapshot.isLive) return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { ringState.value.of(pagerState.currentPage) + pagerState.currentPageOffsetFraction }.collect { pos ->
            WallpaperOffsets.scroll(activity, pos, homePageCount + TODAY_PAGES + 1)
        }
    }

    val ctx = HomeContext(
        apps = appsByKey,
        labels = layout.labels,
        badges = badges,
        showLabels = settings.showLabels,
        showBadges = settings.showBadges,
        editMode = ui.editMode,
        lightWallpaper = lightWallpaper,
        appearance = settings.appearance,
    )

    fun computeHover(at: Offset): DropTarget? {
        val info = ui.drag.info ?: return null
        val lay = layoutState.value
        val page = ringState.value.of(pagerState.currentPage) - TODAY_PAGES
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
        if (page in 0 until homeCountState.value) {
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
                    if (!reduced) {
                        appLaunched = true
                        scope.launch {
                            appDepth.animateTo(1f, androidx.compose.animation.core.tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                            // The launch failed or was blocked (we were never paused): come back.
                            delay(600)
                            if (appLaunched) { appLaunched = false; appDepth.animateTo(0f, HomeReturn) }
                        }
                    }
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

    launchHolder[0] = { e, b -> callbacks.launch(e, b) }

    // Settings → Home gestures.
    fun needsGestures() = ui.toast.show("This gesture needs Glass Gestures.", "Set up") { openSettings("check") }
    fun runAction(b: GestureBinding, at: Offset) {
        val service = com.shivam.glasslauncher.gestures.GlassGestureService.instance
        when (b.action) {
            HomeAction.None -> Unit
            HomeAction.AppDrawer -> ui.drawer.open = true
            HomeAction.Spotlight -> ui.openSpotlight(originRect(at))
            HomeAction.NotificationCenter, HomeAction.ControlCenter -> {
                val kind = if (b.action == HomeAction.ControlCenter) com.shivam.glasslauncher.overlay.PanelKind.ControlCenter else com.shivam.glasslauncher.overlay.PanelKind.NotificationCenter
                if (!com.shivam.glasslauncher.overlay.OverlayRuntime.openPanel(kind)) needsGestures()
            }
            HomeAction.Notifications -> if (service?.globalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) != true) StatusBarPanels.expand(activity, quickSettings = false)
            HomeAction.QuickSettings -> if (service?.globalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) != true) StatusBarPanels.expand(activity, quickSettings = true)
            HomeAction.Recents -> if (service?.globalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS) != true) needsGestures()
            HomeAction.LockScreen -> callbacks.lockScreen()
            HomeAction.EditHome -> ui.editMode = true
            HomeAction.Today -> scope.launch { goTo(0) }
            HomeAction.AppLibrary -> scope.launch { goTo(ringState.value.size - 1) }
            HomeAction.Settings -> openSettings(null)
            HomeAction.LaunchApp -> b.appKey?.let { appsByKey[it] }?.let { callbacks.launch(it, null) } ?: ui.toast.show("That app isn't installed any more")
        }
    }
    val gestureMap = settings.homeGestures
    val gestureMapState = rememberUpdatedState(gestureMap)
    val runActionState = rememberUpdatedState(::runAction)
    val doubleTap = remember { { runActionState.value(HomeGestureMap.of(gestureMapState.value, HomeGesture.DoubleTap), Offset.Zero) } }
    val doubleTapBound = HomeGestureMap.of(gestureMap, HomeGesture.DoubleTap).action != HomeAction.None
    val tilt = rememberDeviceTilt(settings.wallpaperParallax && hasWallpaper && !reduced)

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
                val cur = ringState.value.of(pagerState.currentPage)
                val target = (cur + dir).coerceIn(TODAY_PAGES, homeCountState.value - 1 + TODAY_PAGES)
                if (target != cur) {
                    goTo(target)
                    callbacks.drag(ui.drag.pointer)
                }
            }
        }
    }

    GlassTheme(style = glass, light = light, fallbackLuminance = snapshot.globalLuminance, backdrop = homeBackdrop) {
        CompositionLocalProvider(LocalIconRenderer provides vm.icons, LocalIconGeneration provides generation, LocalWidgetEnv provides widgetEnv) {
            WidgetHostLifecycle()
            Box(Modifier.fillMaxSize()) {
                SnapshotBackdropLayer(frozenHome, frozenHomeLayer)
                WallpaperBackdropLayer(snapshot, wallpaperLayer, hasWallpaper, parallax = if (settings.wallpaperParallax && hasWallpaper && !reduced) ({ tilt.value }) else null)

                // As on iOS the wallpaper blurs behind the App Library and Today view (HyperOS's window blur
                // skips the system wallpaper, so this is our own blurred copy, faded in with the swipe).
                val img = snapshot.image
                if (img != null) {
                    val sideFraction = {
                        val pos = ringState.value.of(pagerState.currentPage) + pagerState.currentPageOffsetFraction
                        maxOf((pos - (homePageCount - 1 + TODAY_PAGES)).coerceIn(0f, 1f), (TODAY_PAGES - pos).coerceIn(0f, 1f))
                    }
                    val showSideBlur by remember { androidx.compose.runtime.derivedStateOf { sideFraction() > 0.01f } }
                    if (showSideBlur) Image(
                        img,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = sideFraction() }.blur(glass.blurRadius.dp),
                    )
                }

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
                        // Read in the layer block, not in composition: animating it redraws, never recomposes.
                        .graphicsLayer {
                            val depth = appDepth.value
                            val r = (overlayAnimState.value * 16f + depth * 14f).dp.toPx()
                            renderEffect = if (r >= 0.5f) androidx.compose.ui.graphics.BlurEffect(r, r, androidx.compose.ui.graphics.TileMode.Clamp) else null
                            clip = r >= 0.5f
                            val sc = 1f - 0.1f * depth
                            scaleX = sc
                            scaleY = sc
                        }
                        .recordBackdrop(homeLayer),
                ) {
                    // Status bar space is kept even while immersive hides it (as on iOS); the dock keeps its own bottom margin.
                    // Pages run full height under the dots and dock (home pages pad for them), so the App Library
                    // and Today view are not cut off by the faded-out dock's space.
                    var chromeHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                    val chromePad = with(LocalDensity.current) { chromeHeight.toDp() }
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .recordBackdrop(pagesLayer)
                                .homeGestures(
                                    enabled = !dragging && !ui.editMode && ringPage in TODAY_PAGES until homePageCount + TODAY_PAGES,
                                    isBound = { g -> HomeGestureMap.of(gestureMapState.value, g).action != HomeAction.None },
                                    onGesture = { g, at -> runActionState.value(HomeGestureMap.of(gestureMapState.value, g), at) },
                                ),
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = ring.beyondViewport,
                                userScrollEnabled = !dragging,
                                key = { it },
                            ) { pagerPage ->
                                val page = ring.of(pagerPage) - TODAY_PAGES
                                val homeAnim = settings.appearance.homeAnimation
                                val isHome = page in 0 until homePageCount
                                Box(Modifier.fillMaxSize().padding(bottom = if (isHome) chromePad else 0.dp).graphicsLayer { if (isHome) homePageTransition(homeAnim, pagerState.currentPage + pagerState.currentPageOffsetFraction - pagerPage) }) {
                                CompositionLocalProvider(LocalBackdrop provides wallpaperOnly) {
                                    if (page < 0) {
                                        TodayView(widgetUi, ui.editMode, { ui.editMode = it }, lightWallpaper)
                                    } else if (page < homePageCount) {
                                        val pageWidgets = layout.widgetsOn(page)
                                        HomeGridPage(
                                            page = page,
                                            items = layout.pages.getOrNull(page) ?: HomeLayout.emptyPage(),
                                            ctx = ctx,
                                            drag = ui.drag,
                                            callbacks = callbacks,
                                            onDoubleTap = if (doubleTapBound) doubleTap else null,
                                            blocked = remember(layout, page) { layout.blockedCells(page) },
                                            widgetLayer = { cellW, cellH -> HomeWidgetsLayer(page, pageWidgets, cellW, cellH, widgetUi, ui.editMode) },
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
                        }
                        // 0 on home pages, 1 on the App Library or the Today view: the dock and dots fade out.
                        val libraryFraction = {
                            val pos = ringState.value.of(pagerState.currentPage) + pagerState.currentPageOffsetFraction
                            maxOf((pos - (homePageCount - 1 + TODAY_PAGES)).coerceIn(0f, 1f), (TODAY_PAGES - pos).coerceIn(0f, 1f))
                        }
                        // Fully on the Library/Today: drop the chrome so it can't catch their touches (its height is kept).
                        val chromeGone by remember { androidx.compose.runtime.derivedStateOf { libraryFraction() > 0.99f } }
                        if (!chromeGone) Column(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .onSizeChanged { chromeHeight = it.height },
                        ) {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp).graphicsLayer { alpha = 1f - libraryFraction() },
                            contentAlignment = Alignment.Center,
                        ) {
                            PageDots(
                                homePages = homePageCount,
                                current = ringPage - TODAY_PAGES,
                                showDots = pillDots,
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
                                .graphicsLayer {
                                    val f = libraryFraction()
                                    alpha = 1f - f
                                    translationY = f * 140.dp.toPx()
                                },
                        )
                        }
                    }
                }

                // Immersive hides the system status bar on home: Glass draws an iOS one around the island
                // (idle island ≈ 132 dp on the Turbo 5; 140 dp keeps a gap).
                if (settings.immersive && !ui.editMode) {
                    IosStatusBar(if (lightWallpaper) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White, islandWidth = 140.dp)
                }

                // Edit-mode toolbar ("jiggle mode"); the Today view has its own buttons.
                if (ui.editMode && ringPage >= TODAY_PAGES) {
                    Row(
                        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility).padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton("Settings", onClick = { openSettings(null) }, icon = GlassIcons.Gear)
                            GlassIconButton(WidgetGlyphs.Pages, onClick = { widgetUi.pageManager = true })
                            // Share your home screen: rendered from our own layers, never a screenshot.
                            GlassIconButton(com.shivam.glasslauncher.ui.drawer.DrawerGlyphs.Share, onClick = {
                                ui.editMode = false
                                scope.launch {
                                    delay(450)
                                    if (!HomeShare.share(activity, homeLayer, snapshot)) ui.toast.show("Couldn't render the home screen")
                                }
                            })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassIconButton(GlassIcons.Plus, onClick = {
                                val home = (ringPage - TODAY_PAGES).coerceIn(0, layout.pages.size - 1)
                                widgetUi.picker = PickerTarget.Home(home)
                            })
                            GlassButton("Done", onClick = { ui.editMode = false }, prominent = true)
                        }
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
                    com.shivam.glasslauncher.ui.drawer.AppDrawerOverlay(ui, vm, layout, settings, ctx, callbacks, openSettings, snapshot.globalLuminance)
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
                    WidgetOverlays(widgetUi, ui, layout)
                    GlassToastHost(ui.toast)
                }
            }
        }
    }
}

/** Home coming back from an app (about 320 ms, decelerating). */
private val HomeReturn = androidx.compose.animation.core.tween<Float>(320, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))

/** Pages before the first home page in the pager (the Today view). */
private const val TODAY_PAGES = 1

/**
 * Maps pager pages to ring pages (0 = Today, then home pages, last = App Library). Without
 * [infinite] they are the same; with it the pager holds [LOOPS] copies of the ring.
 */
@androidx.compose.runtime.Immutable
internal data class PagerRing(val size: Int, val infinite: Boolean) {
    val pagerCount = if (infinite) size * LOOPS else size
    val start = if (infinite) size * (LOOPS / 2) else 0
    /** Pages kept composed on each side; in a loop, never so many that one page shows up twice. */
    val beyondViewport = if (infinite) (size - 1) / 2 else size
    fun of(pager: Int) = if (infinite) Math.floorMod(pager - start, size) else pager
    /** The pager page showing [ringTarget] closest to [from]. */
    fun nearest(from: Int, ringTarget: Int): Int {
        if (!infinite) return ringTarget
        var d = Math.floorMod(ringTarget - of(from), size)
        if (d > size / 2) d -= size
        return (from + d).coerceIn(0, pagerCount - 1)
    }
    companion object { const val LOOPS = 2000 }
}

/** Home page transition (Settings → Appearance → Home animation); [o] is the page's offset from the centre. */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.homePageTransition(anim: com.shivam.glasslauncher.data.HomeAnimation, o: Float) {
    val a = abs(o).coerceIn(0f, 1f)
    when (anim) {
        com.shivam.glasslauncher.data.HomeAnimation.Slide -> Unit
        com.shivam.glasslauncher.data.HomeAnimation.Fade -> {
            translationX = o * size.width
            alpha = 1f - a
        }
        com.shivam.glasslauncher.data.HomeAnimation.Depth -> {
            val sc = 1f - 0.12f * a
            scaleX = sc
            scaleY = sc
            alpha = 1f - 0.5f * a
        }
        com.shivam.glasslauncher.data.HomeAnimation.Cube -> {
            cameraDistance = 14f * density
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (o > 0f) 1f else 0f, 0.5f)
            rotationY = -o.coerceIn(-1f, 1f) * 50f
        }
    }
}
