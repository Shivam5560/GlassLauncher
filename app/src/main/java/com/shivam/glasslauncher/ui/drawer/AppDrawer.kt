package com.shivam.glasslauncher.ui.drawer

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.glasslauncher.data.AppEntry
import com.shivam.glasslauncher.data.DrawerCategory
import com.shivam.glasslauncher.data.DrawerFolder
import com.shivam.glasslauncher.data.DrawerItem
import com.shivam.glasslauncher.data.DrawerShortcut
import com.shivam.glasslauncher.data.DrawerTransition
import com.shivam.glasslauncher.data.FolderItem
import com.shivam.glasslauncher.data.HomeLayout
import com.shivam.glasslauncher.data.LauncherSettings
import com.shivam.glasslauncher.ui.LauncherViewModel
import com.shivam.glasslauncher.ui.glass.Contrast
import com.shivam.glasslauncher.ui.glass.GlassIcon
import com.shivam.glasslauncher.ui.glass.GlassIconButton
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassMenuRow
import com.shivam.glasslauncher.ui.glass.GlassMorph
import com.shivam.glasslauncher.ui.glass.GlassScrim
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.glass.LocalGlassContentColor
import com.shivam.glasslauncher.ui.glass.LocalScreenContentColor
import com.shivam.glasslauncher.ui.glass.SurfaceKind
import com.shivam.glasslauncher.ui.glass.currentAccent
import com.shivam.glasslauncher.ui.glass.screenTextShadow
import com.shivam.glasslauncher.ui.home.AppIconImage
import com.shivam.glasslauncher.ui.home.BadgedIcon
import com.shivam.glasslauncher.ui.home.FolderIcon
import com.shivam.glasslauncher.ui.home.HomeCallbacks
import com.shivam.glasslauncher.ui.home.HomeContext
import com.shivam.glasslauncher.ui.home.IconLabel
import com.shivam.glasslauncher.ui.home.LauncherUiState
import com.shivam.glasslauncher.ui.home.MenuTarget
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Sheets of the drawer's ⋮ menu. */
sealed interface DrawerSheet {
    data object Categories : DrawerSheet
    data object Sorting : DrawerSheet
    data class AddFolder(val categoryId: String) : DrawerSheet
    data class AddShortcut(val categoryId: String) : DrawerSheet
}

/** Small name prompts (glass dialog with a text field). */
sealed interface DrawerPrompt {
    data object AddCategory : DrawerPrompt
    data class RenameCategory(val id: String, val current: String) : DrawerPrompt
    data class RenameFolder(val id: String, val current: String) : DrawerPrompt
}

/** State of the app drawer and everything opened from it. */
@Stable
class DrawerUiState {
    var open by mutableStateOf(false)
    var menuAnchor by mutableStateOf<Rect?>(null)
    var sheet by mutableStateOf<DrawerSheet?>(null)
    var prompt by mutableStateOf<DrawerPrompt?>(null)
    /** App whose category is being changed (long-press → Move to category). */
    var moveApp by mutableStateOf<AppEntry?>(null)
    var folder by mutableStateOf<DrawerFolder?>(null)
    var folderOrigin by mutableStateOf<Rect?>(null)
    /** Shortcut long-pressed (offers removal). */
    var shortcutMenu by mutableStateOf<DrawerShortcut?>(null)
    /** Category on screen. */
    var currentCategory by mutableStateOf<String?>(null)

    val menuOpen: Boolean get() = menuAnchor != null

    /** Closes the top-most drawer layer; false when the drawer is closed. */
    fun closeTop(): Boolean = when {
        !open -> false
        prompt != null -> { prompt = null; true }
        moveApp != null -> { moveApp = null; true }
        shortcutMenu != null -> { shortcutMenu = null; true }
        sheet != null -> { sheet = null; true }
        menuAnchor != null -> { menuAnchor = null; true }
        folder != null -> { folder = null; true }
        else -> { open = false; true }
    }

    fun closeAll() {
        prompt = null
        moveApp = null
        shortcutMenu = null
        sheet = null
        menuAnchor = null
        folder = null
        open = false
    }
}

/**
 * The auto-categorised app drawer (swipe up from home): one page per category with a big title
 * and Search / Play Store / ⋮ buttons, a 4-column grid that sits at the bottom of the screen when
 * a category has few apps (thumb reach), and a floating glass tab bar to switch categories.
 */
@Composable
fun AppDrawerOverlay(
    ui: LauncherUiState,
    vm: LauncherViewModel,
    layout: HomeLayout,
    settings: LauncherSettings,
    ctx: HomeContext,
    callbacks: HomeCallbacks,
    openSettings: (String?) -> Unit,
    wallpaperLuminance: Float,
) {
    val state = ui.drawer
    val visible = state.open
    val progress = remember { Animatable(0f) }
    var composed by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            composed = true
            progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 360f))
        } else {
            progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 520f))
            composed = false
        }
    }
    if (!composed) return

    val cats = layout.drawer.categories.filter { it.inBar }
    if (cats.isEmpty()) return
    val pages by vm.drawerPages.collectAsState()
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val startIndex = remember {
        when (settings.drawerLaunchView) {
            com.shivam.glasslauncher.data.DrawerLaunchView.LastCategory -> cats.indexOfFirst { it.id == settings.drawerLastCategory }.coerceAtLeast(0)
            com.shivam.glasslauncher.data.DrawerLaunchView.First -> 0
        }
    }
    val pagerState = rememberPagerState(initialPage = startIndex) { cats.size }
    LaunchedEffect(pagerState.settledPage, cats) {
        val c = cats.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
        state.currentCategory = c.id
        if (c.id != settings.drawerLastCategory) vm.setDrawerLastCategory(c.id)
    }

    // Background role: a dim over the blurred home; text colour by contrast against it.
    val bg = settings.glass.role(SurfaceKind.Background)
    val dim = Color(bg.color ?: 0xFF000000.toInt()).copy(alpha = 1f)
    val dimAlpha = bg.opacity ?: 0.32f
    val textColor = bg.contentColor?.let { Color(it) }
        ?: Color(Contrast.pickText(Contrast.glassLuminance(Contrast.gammaToLinear(wallpaperLuminance), dim.toArgb(), dimAlpha)))

    // Pulling down past the top of a category closes the drawer.
    val closeDistance = with(density) { 96.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    val closeOnPull = remember(state) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pull += available.y
                    if (pull > closeDistance) { pull = 0f; state.open = false }
                } else if (consumed.y != 0f) {
                    pull = 0f
                }
                return Offset.Zero
            }
        }
    }

    val close = { state.open = false }
    val launch: (AppEntry, android.graphics.Rect?) -> Unit = { e, b -> callbacks.launch(e, b); state.closeAll() }

    CompositionLocalProvider(LocalScreenContentColor provides textColor) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(dim.copy(alpha = dimAlpha))
                .swipeDownToClose(closeDistance) { close() }
                // A tap on empty space (not on an app, folder or button) goes back to Home.
                .pointerInput(Unit) { detectTapGestures { close() } },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = (1f - progress.value) * size.height * 0.16f },
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { cats[it].id },
                    beyondViewportPageCount = 1,
                    userScrollEnabled = settings.drawerSwipe == com.shivam.glasslauncher.data.DrawerSwipe.Both ||
                        settings.drawerSwipe == com.shivam.glasslauncher.data.DrawerSwipe.Screen,
                ) { index ->
                    val cat = cats[index]
                    Box(Modifier.fillMaxSize().graphicsLayer { pageTransition(settings.drawerTransition, pagerState, index) }) {
                        CategoryPage(
                            category = cat,
                            items = pages[cat.id] ?: emptyList(),
                            ctx = ctx,
                            settings = settings,
                            textColor = textColor,
                            nested = closeOnPull,
                            onSearch = {
                                state.closeAll()
                                ui.openSpotlight(null)
                            },
                            onStore = { vm.actions.playStoreSearch(activity, cat.title) },
                            onMore = { r -> state.menuAnchor = r },
                            onApp = launch,
                            onAppLong = { e, r -> ui.menu = MenuTarget(e, null, null, r, fromDrawer = true) },
                            onFolder = { f, r -> state.folderOrigin = r; state.folder = f },
                            onShortcut = { s, b ->
                                if (!vm.startDrawerShortcut(activity, s, b)) {
                                    ui.toast.show("Shortcuts open only while Glass is your default Home app.")
                                } else {
                                    state.closeAll()
                                }
                            },
                            onShortcutLong = { s -> state.shortcutMenu = s },
                        )
                    }
                }

                CategoryTabBar(
                    cats = cats,
                    pagerState = pagerState,
                    swipeable = settings.drawerSwipe == com.shivam.glasslauncher.data.DrawerSwipe.Both ||
                        settings.drawerSwipe == com.shivam.glasslauncher.data.DrawerSwipe.Bar,
                    onSelect = { i ->
                        scope.launch {
                            if (settings.drawerTransition == DrawerTransition.None) pagerState.scrollToPage(i) else pagerState.animateScrollToPage(i)
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp),
                )
            }
        }

        val currentId = cats.getOrNull(pagerState.currentPage)?.id ?: cats.first().id
        DrawerMenu(state, currentId, openSettings)
        DrawerFolderOverlay(state, ctx, vm, launch, onLong = { e, r -> ui.menu = MenuTarget(e, null, null, r, fromDrawer = true) })
        DrawerSheets(state, vm, layout, ctx, pages)
        DrawerPrompts(state, vm)
    }
}

/** Swipe down on unconsumed space (the fitted, bottom-aligned pages and the header) closes. */
private fun Modifier.swipeDownToClose(threshold: Float, onClose: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var total = Offset.Zero
        while (true) {
            val ev = awaitPointerEvent()
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed || ch.isConsumed) break
            total += ch.positionChange()
            if (total.y < -threshold / 3 || abs(total.x) > threshold / 2) break
            if (total.y > threshold && total.y > abs(total.x) * 1.5f) {
                ch.consume()
                onClose()
                break
            }
        }
    }
}

/** Category page transition (Settings → App drawer → Transition). */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.pageTransition(t: DrawerTransition, pager: PagerState, page: Int) {
    val o = (pager.currentPage - page) + pager.currentPageOffsetFraction
    val a = abs(o).coerceIn(0f, 1f)
    when (t) {
        DrawerTransition.Fade, DrawerTransition.None -> {
            // Pages stay in place and cross-fade (None: tabs jump without animating).
            translationX = o * size.width
            alpha = 1f - a
        }
        DrawerTransition.Depth -> {
            val s = 1f - 0.14f * a
            scaleX = s
            scaleY = s
            alpha = 1f - 0.7f * a
        }
        DrawerTransition.Slide -> Unit
    }
}

@Composable
private fun CategoryPage(
    category: DrawerCategory,
    items: List<DrawerItem>,
    ctx: HomeContext,
    settings: LauncherSettings,
    textColor: Color,
    nested: NestedScrollConnection,
    onSearch: () -> Unit,
    onStore: () -> Unit,
    onMore: (Rect) -> Unit,
    onApp: (AppEntry, android.graphics.Rect?) -> Unit,
    onAppLong: (AppEntry, Rect) -> Unit,
    onFolder: (DrawerFolder, Rect) -> Unit,
    onShortcut: (DrawerShortcut, android.graphics.Rect?) -> Unit,
    onShortcutLong: (DrawerShortcut) -> Unit,
) {
    val ap = settings.appearance
    val iconSize = 58.dp * ap.iconScale.coerceIn(0.7f, 1.25f)
    val labelLines = if (!ap.drawerLabels) 0 else if (ap.twoLineLabels) 2 else 1
    val cellH = iconSize + (ap.labelSize * 1.35f * labelLines).dp + 22.dp
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomReserve = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 92.dp
    val header: @Composable () -> Unit = {
        CategoryHeader(category.title, textColor, onSearch, onStore, onMore)
    }
    val cell: @Composable (DrawerItem) -> Unit = { item ->
        DrawerCell(item, ctx, iconSize, ap.drawerLabels, ap.labelSize, ap.twoLineLabels, textColor, onApp, onAppLong, onFolder, onShortcut, onShortcutLong)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rows = (items.size + 3) / 4
        val headerH = 76.dp
        val emptyH = if (items.isEmpty()) 72.dp else 0.dp
        val fits = top + headerH + cellH * rows + emptyH + bottomReserve + 12.dp <= maxHeight
        if (fits) {
            // Few apps: title and grid sit at the bottom, within thumb reach.
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.Bottom) {
                header()
                if (items.isEmpty()) {
                    GlassText(
                        "Nothing here yet. Long-press an app and choose Move to category.",
                        Modifier.padding(horizontal = 12.dp, vertical = 12.dp).height(48.dp),
                        style = GlassType.Callout.copy(shadow = screenTextShadow(textColor)),
                        color = textColor.copy(alpha = 0.8f),
                    )
                }
                items.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { item -> Box(Modifier.weight(1f).height(cellH), contentAlignment = Alignment.TopCenter) { cell(item) } }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(bottomReserve))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize().nestedScroll(nested),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = top + 8.dp, bottom = bottomReserve),
            ) {
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
                items(items, key = { it.key }) { item ->
                    Box(Modifier.height(cellH), contentAlignment = Alignment.TopCenter) { cell(item) }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(title: String, textColor: Color, onSearch: () -> Unit, onStore: () -> Unit, onMore: (Rect) -> Unit) {
    var moreBounds by remember { mutableStateOf<Rect?>(null) }
    Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassText(
            title,
            Modifier.weight(1f),
            style = GlassType.LargeTitle.copy(fontSize = 34.sp, shadow = screenTextShadow(textColor)),
            color = textColor,
            maxLines = 1,
        )
        GlassIconButton(GlassIcons.Search, onClick = onSearch, size = 40.dp)
        Spacer(Modifier.width(8.dp))
        GlassIconButton(DrawerGlyphs.Store, onClick = onStore, size = 40.dp)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.onGloballyPositioned { moreBounds = it.boundsInWindow() }) {
            GlassIconButton(DrawerGlyphs.More, onClick = { moreBounds?.let(onMore) }, size = 40.dp)
        }
    }
}

@Composable
private fun DrawerCell(
    item: DrawerItem,
    ctx: HomeContext,
    iconSize: Dp,
    labels: Boolean,
    labelSize: Float,
    twoLines: Boolean,
    textColor: Color,
    onApp: (AppEntry, android.graphics.Rect?) -> Unit,
    onAppLong: (AppEntry, Rect) -> Unit,
    onFolder: (DrawerFolder, Rect) -> Unit,
    onShortcut: (DrawerShortcut, android.graphics.Rect?) -> Unit,
    onShortcutLong: (DrawerShortcut) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun bounds(): Rect? = coords[0]?.boundsInWindow()
    fun androidBounds() = bounds()?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
    val light = textColor != Color.White
    val label: String
    val iconContent: @Composable () -> Unit
    val tap: () -> Unit
    val longPress: () -> Unit
    when (item) {
        is DrawerItem.App -> {
            val e = ctx.apps[item.key] ?: return
            label = ctx.label(e)
            iconContent = {
                BadgedIcon(iconSize, if (ctx.showBadges) ctx.badges[e.packageName] ?: 0 else 0, false, {}, 0f) { AppIconImage(e, iconSize) }
            }
            tap = { onApp(e, androidBounds()) }
            longPress = { bounds()?.let { onAppLong(e, it) } }
        }
        is DrawerItem.Folder -> {
            val f = item.folder
            label = f.name
            iconContent = { FolderIcon(FolderItem(f.id, f.name, f.apps), ctx.apps, iconSize) }
            tap = { bounds()?.let { onFolder(f, it) } }
            longPress = { bounds()?.let { onFolder(f, it) } }
        }
        is DrawerItem.Shortcut -> {
            val s = item.shortcut
            val e = ctx.apps[s.appKey] ?: return
            label = s.label
            iconContent = {
                Box(Modifier.size(iconSize)) {
                    AppIconImage(e, iconSize)
                    GlassSurface(
                        Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp).size(iconSize * 0.42f),
                        shape = GlassShape.Circle,
                        variant = GlassVariant.Clear,
                        tintOverride = Color(0xFF1C1C1E),
                        contentAlignment = Alignment.Center,
                        role = null,
                    ) { GlassIcon(DrawerGlyphs.Link, size = iconSize * 0.24f, tint = Color.White) }
                }
            }
            tap = { onShortcut(s, androidBounds()) }
            longPress = { onShortcutLong(s) }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
        Box(
            Modifier
                .size(iconSize)
                .onGloballyPositioned { coords[0] = it }
                .pointerInput(item.key) {
                    detectTapGestures(
                        onTap = { tap() },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            longPress()
                        },
                    )
                },
        ) { iconContent() }
        if (labels) {
            Spacer(Modifier.height(5.dp))
            IconLabel(label, light, Modifier.width(iconSize + 22.dp), sizeSp = labelSize, twoLines = twoLines)
        }
    }
}

/** Floating glass category bar: one glyph per category, the active one underlined. */
@Composable
private fun CategoryTabBar(cats: List<DrawerCategory>, pagerState: PagerState, swipeable: Boolean, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(modifier.widthIn(max = 380.dp), shape = GlassShape.Capsule, kind = SurfaceKind.Panel, variant = GlassVariant.Regular, role = null) {
        Row(
            (if (swipeable) {
                // Drag across the icons to move through categories, one per ~46dp.
                Modifier.pointerInput(cats.size) {
                    var acc = 0f
                    detectHorizontalDragGestures(onDragStart = { acc = 0f }) { change, dx ->
                        change.consume()
                        acc += dx
                        val step = 46.dp.toPx()
                        while (kotlin.math.abs(acc) >= step) {
                            val next = (pagerState.targetPage + if (acc > 0) 1 else -1).coerceIn(0, cats.lastIndex)
                            acc -= if (acc > 0) step else -step
                            if (next != pagerState.targetPage) onSelect(next)
                        }
                    }
                }
            } else Modifier.horizontalScroll(rememberScrollState())).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val content = LocalGlassContentColor.current
            val accent = currentAccent()
            cats.forEachIndexed { i, c ->
                val active = pagerState.targetPage == i
                val line by animateDpAsState(if (active) 16.dp else 0.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "tabLine")
                Column(
                    Modifier
                        .size(width = 46.dp, height = 46.dp)
                        .clip(GlassShape.Capsule)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    GlassIcon(DrawerGlyphs.of(c.glyph), size = 22.dp, tint = content.copy(alpha = if (active) 1f else 0.55f))
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(line).height(3.dp).clip(GlassShape.Capsule).background(accent))
                }
            }
        }
    }
}

/** The ⋮ menu: add shortcut / folder / category, category bar, sorting, hidden apps. */
@Composable
private fun DrawerMenu(state: DrawerUiState, currentCategory: String, openSettings: (String?) -> Unit) {
    val anchor = com.shivam.glasslauncher.ui.home.rememberLast(state.menuAnchor) ?: return
    var progress by remember { mutableFloatStateOf(0f) }
    val visible = state.menuAnchor != null
    Box(Modifier.fillMaxSize()) {
        if (visible || progress > 0.01f) GlassScrim(progress) { state.menuAnchor = null }
        Box(
            Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val margin = 12.dp.roundToPx()
                        val below = anchor.bottom + margin + p.height < constraints.maxHeight - 24.dp.roundToPx()
                        val y = if (below) anchor.bottom.roundToInt() + margin else (anchor.top.roundToInt() - margin - p.height).coerceAtLeast(margin)
                        val x = (anchor.right.roundToInt() - p.width).coerceIn(margin, (constraints.maxWidth - p.width - margin).coerceAtLeast(margin))
                        p.place(x, y)
                    }
                },
        ) {
            GlassMorph(visible = visible, origin = anchor, onProgress = { progress = it }) {
                GlassSurface(Modifier.width(250.dp), shape = GlassShape.rounded(24.dp), role = null, kind = SurfaceKind.Panel) {
                    Column(Modifier.padding(6.dp)) {
                        fun go(block: () -> Unit) { state.menuAnchor = null; block() }
                        GlassMenuRow("Add shortcut", DrawerGlyphs.Link) { go { state.sheet = DrawerSheet.AddShortcut(currentCategory) } }
                        GlassMenuRow("Add folder", DrawerGlyphs.Folder) { go { state.sheet = DrawerSheet.AddFolder(currentCategory) } }
                        GlassMenuRow("Add category", GlassIcons.Plus) { go { state.prompt = DrawerPrompt.AddCategory } }
                        GlassMenuRow("Category bar", GlassIcons.Grid) { go { state.sheet = DrawerSheet.Categories } }
                        GlassMenuRow("Sorting", DrawerGlyphs.Sort) { go { state.sheet = DrawerSheet.Sorting } }
                        GlassMenuRow("Hidden apps", GlassIcons.EyeOff) { go { openSettings("hidden") } }
                    }
                }
            }
        }
    }
}

/** A drawer folder: its apps on a glass panel growing out of the folder icon. */
@Composable
private fun DrawerFolderOverlay(
    state: DrawerUiState,
    ctx: HomeContext,
    vm: LauncherViewModel,
    launch: (AppEntry, android.graphics.Rect?) -> Unit,
    onLong: (AppEntry, Rect) -> Unit,
) {
    val current = state.folder
    val folder = com.shivam.glasslauncher.ui.home.rememberLast(current) ?: return
    var progress by remember { mutableFloatStateOf(0f) }
    val entries = folder.apps.mapNotNull { ctx.apps[it] }
    val labels = ctx.appearance.folderLabels
    Box(Modifier.fillMaxSize()) {
        if (current != null || progress > 0.01f) GlassScrim(progress) { state.folder = null }
        GlassMorph(visible = current != null, origin = state.folderOrigin, modifier = Modifier.fillMaxSize(), onProgress = { progress = it }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp)) {
                GlassSurface(
                    shape = GlassShape.Capsule,
                    variant = GlassVariant.Clear,
                    onClick = { state.prompt = DrawerPrompt.RenameFolder(folder.id, folder.name) },
                ) {
                    GlassText(folder.name, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = GlassType.Title, maxLines = 1)
                }
                Spacer(Modifier.height(16.dp))
                GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.rounded(34.dp), role = null, kind = SurfaceKind.Panel) {
                    Column(Modifier.padding(16.dp)) {
                        entries.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { e ->
                                    val coords = remember(e.key) { arrayOfNulls<LayoutCoordinates>(1) }
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .padding(vertical = 8.dp)
                                            .onGloballyPositioned { coords[0] = it }
                                            .pointerInput(e.key) {
                                                detectTapGestures(
                                                    onTap = {
                                                        val b = coords[0]?.boundsInWindow()
                                                        launch(e, b?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) })
                                                    },
                                                    onLongPress = { coords[0]?.boundsInWindow()?.let { onLong(e, it) } },
                                                )
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        AppIconImage(e, 54.dp)
                                        if (labels) {
                                            Spacer(Modifier.height(4.dp))
                                            GlassText(ctx.label(e), style = GlassType.IconLabel, maxLines = 1)
                                        }
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            com.shivam.glasslauncher.ui.glass.GlassButton(
                                "Remove folder",
                                onClick = {
                                    vm.updateDrawer { com.shivam.glasslauncher.data.DrawerOps.removeFolder(it, folder.id) }
                                    state.folder = null
                                },
                                destructive = true,
                                icon = GlassIcons.Trash,
                            )
                        }
                    }
                }
            }
        }
    }
}
