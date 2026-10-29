package com.shivam.glasslauncher.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.data.AppEntry
import com.shivam.glasslauncher.search.FuzzyMatcher
import com.shivam.glasslauncher.ui.LibraryGroup
import com.shivam.glasslauncher.ui.glass.GlassButton
import com.shivam.glasslauncher.ui.glass.GlassIndexScrubber
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassTextField
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.home.AppIconImage
import com.shivam.glasslauncher.ui.home.IconLabel
import com.shivam.glasslauncher.ui.home.MenuTarget
import kotlinx.coroutines.launch

/**
 * App Library: the page after the last home page. Auto-categorised glass tiles, a glass search
 * field, and (while searching) an alphabetical list with a glass A–Z scrubber.
 */
@Composable
fun AppLibrary(
    groups: List<LibraryGroup>,
    allApps: List<AppEntry>,
    label: (AppEntry) -> String,
    lightWallpaper: Boolean,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onOpenGroup: (LibraryGroup, Rect) -> Unit,
    onMenu: (MenuTarget) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var listMode by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) listMode = true },
                placeholder = "App Library",
                variant = GlassVariant.Clear,
            )
            if (listMode) {
                Spacer(Modifier.width(8.dp))
                GlassButton("Cancel", onClick = { query = ""; listMode = false; keyboard?.hide() })
            }
        }
        Spacer(Modifier.height(14.dp))
        if (listMode) {
            AlphabeticalList(allApps, query, label, onLaunch, onMenu)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                items(groups, key = { it.id }) { group ->
                    LibraryTile(group, label, lightWallpaper, onLaunch, onOpenGroup, onMenu)
                }
                item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/**
 * A category tile, as on iOS: clear glass with three large icons filling its quarters and, when
 * there are more apps, four small ones in the last quarter that open the full group.
 */
@Composable
private fun LibraryTile(
    group: LibraryGroup,
    label: (AppEntry) -> String,
    lightWallpaper: Boolean,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onOpenGroup: (LibraryGroup, Rect) -> Unit,
    onMenu: (MenuTarget) -> Unit,
) {
    val tileCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).onGloballyPositioned { tileCoords[0] = it },
            shape = GlassShape.rounded(34.dp),
            variant = GlassVariant.Clear,
            role = null,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(TilePad)) {
                val icon = (maxWidth - TileGap) / 2
                Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
                    for (r in 0 until 2) Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                        for (c in 0 until 2) {
                            val i = r * 2 + c
                            Box(Modifier.size(icon), contentAlignment = Alignment.Center) {
                                if (i == 3 && group.apps.size > 4) {
                                    MiniGrid(group.apps.drop(3).take(4), icon) {
                                        tileCoords[0]?.boundsInWindow()?.let { onOpenGroup(group, it) }
                                    }
                                } else {
                                    group.apps.getOrNull(i)?.let { LibraryIcon(it, icon, onLaunch, onMenu) }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        IconLabel(group.title, false)
    }
}

/** Four small icons in one quarter of a tile; tapping anywhere on it opens the group. */
@Composable
private fun MiniGrid(apps: List<AppEntry>, size: Dp, onClick: () -> Unit) {
    val gap = size * 0.12f
    val small = (size - gap) / 2
    Column(
        Modifier.size(size).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        for (r in 0 until 2) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            for (c in 0 until 2) {
                Box(Modifier.size(small)) { apps.getOrNull(r * 2 + c)?.let { AppIconImage(it, small) } }
            }
        }
    }
}

private val TilePad = 12.dp
private val TileGap = 10.dp

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LibraryIcon(entry: AppEntry, size: Dp, onLaunch: (AppEntry, android.graphics.Rect?) -> Unit, onMenu: (MenuTarget) -> Unit) {
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val haptics = LocalHapticFeedback.current
    // Just the icon, as on iOS (no glass behind it: the tile is the glass).
    Box(
        Modifier
            .size(size)
            .onGloballyPositioned { coords[0] = it }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    val b = coords[0]?.boundsInWindow()
                    onLaunch(entry, b?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) })
                },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    coords[0]?.boundsInWindow()?.let { onMenu(MenuTarget(entry, null, null, it)) }
                },
            ),
    ) {
        AppIconImage(entry, size)
    }
}

@Composable
private fun AlphabeticalList(
    apps: List<AppEntry>,
    query: String,
    label: (AppEntry) -> String,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onMenu: (MenuTarget) -> Unit,
) {
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps
            .map { it to FuzzyMatcher.score(query, it.searchTarget) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }
    fun letterOf(e: AppEntry): String {
        val c = label(e).firstOrNull()?.uppercaseChar() ?: '#'
        return if (c.isLetter()) c.toString() else "#"
    }
    val sections = remember(filtered, query) {
        if (query.isNotBlank()) listOf("" to filtered) else filtered.groupBy { letterOf(it) }.toSortedMap().toList()
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val letterIndex = remember(sections) {
        var i = 0
        sections.associate { (l, list) -> l to i.also { i += 1 + list.size } }
    }
    Row(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = listState, contentPadding = PaddingValues(bottom = 300.dp)) {
            sections.forEach { (letter, list) ->
                item(key = "h$letter") {
                    if (letter.isNotEmpty()) {
                        GlassText(letter, Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp), style = GlassType.Headline)
                    }
                }
                items(list, key = { it.key }) { e ->
                    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).onGloballyPositioned { coords[0] = it },
                        shape = GlassShape.Row,
                        variant = GlassVariant.Clear,
                        onClick = {
                            val b = coords[0]?.boundsInWindow()
                            onLaunch(e, b?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) })
                        },
                        onLongClick = { coords[0]?.boundsInWindow()?.let { onMenu(MenuTarget(e, null, null, it)) } },
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIconImage(e, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            GlassText(label(e), style = GlassType.Body, maxLines = 1)
                        }
                    }
                }
            }
        }
        if (query.isBlank() && sections.size > 1) {
            Spacer(Modifier.width(6.dp))
            GlassIndexScrubber(
                letters = sections.map { it.first },
                onLetter = { l -> letterIndex[l]?.let { idx -> scope.launch { listState.scrollToItem(idx) } } },
                modifier = Modifier.fillMaxHeight(0.75f).align(Alignment.CenterVertically),
            )
        }
    }
}
