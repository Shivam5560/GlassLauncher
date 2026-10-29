package com.shivam.glasslauncher.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Gestures on empty home space (Settings → Home gestures). Horizontal swipes belong to the pager. */
enum class HomeGesture(val title: String) {
    SwipeUp("Swipe up"),
    SwipeDown("Swipe down"),
    DoubleTap("Double tap"),
    TwoFingerUp("Two-finger swipe up"),
    TwoFingerDown("Two-finger swipe down"),
    PinchIn("Pinch in"),
    PinchOut("Pinch out"),
    RotateRight("Rotate right"),
    RotateLeft("Rotate left"),
}

/** What a [HomeGesture] does. [needsService] actions go through Glass Gestures (accessibility). */
enum class HomeAction(val title: String, val needsService: Boolean = false) {
    None("Nothing"),
    AppDrawer("App drawer"),
    Spotlight("Spotlight"),
    NotificationCenter("Notification Center", true),
    ControlCenter("Control Center", true),
    Notifications("System notifications"),
    QuickSettings("System quick settings"),
    Recents("Recent apps", true),
    LockScreen("Lock screen", true),
    EditHome("Edit Home Screen"),
    Today("Today view"),
    AppLibrary("App Library"),
    Settings("Glass settings"),
    LaunchApp("Open an app"),
}

/** One gesture's action; [appKey] is set for [HomeAction.LaunchApp]. */
data class GestureBinding(val action: HomeAction, val appKey: String? = null)

object HomeGestureMap {
    val defaults: Map<HomeGesture, GestureBinding> = mapOf(
        HomeGesture.SwipeUp to GestureBinding(HomeAction.AppDrawer),
        HomeGesture.SwipeDown to GestureBinding(HomeAction.Spotlight),
        HomeGesture.DoubleTap to GestureBinding(HomeAction.LockScreen),
        HomeGesture.TwoFingerDown to GestureBinding(HomeAction.NotificationCenter),
        HomeGesture.TwoFingerUp to GestureBinding(HomeAction.ControlCenter),
        HomeGesture.PinchIn to GestureBinding(HomeAction.EditHome),
    )

    /** "Gesture=Action[|appKey]" lines. */
    fun encode(map: Map<HomeGesture, GestureBinding>): String =
        map.entries.joinToString("\n") { (g, b) -> "${g.name}=${b.action.name}" + (b.appKey?.let { "|$it" } ?: "") }

    fun decode(s: String?): Map<HomeGesture, GestureBinding>? {
        if (s == null) return null
        val out = HashMap<HomeGesture, GestureBinding>()
        for (line in s.lineSequence()) {
            val (g, rest) = line.split('=', limit = 2).takeIf { it.size == 2 } ?: continue
            val gesture = HomeGesture.entries.firstOrNull { it.name == g } ?: continue
            val parts = rest.split('|', limit = 2)
            val action = HomeAction.entries.firstOrNull { it.name == parts[0] } ?: continue
            out[gesture] = GestureBinding(action, parts.getOrNull(1))
        }
        return out
    }

    fun of(map: Map<HomeGesture, GestureBinding>, g: HomeGesture) = map[g] ?: GestureBinding(HomeAction.None)
}

/**
 * Vertical swipes and two-finger gestures on the home pages. One-finger drags only count when
 * mostly vertical and unconsumed, so icons, widgets and the pager keep theirs. Once a second finger
 * lands, its events are consumed in the Initial pass so the pager does not scroll during a pinch.
 */
fun Modifier.homeGestures(
    enabled: Boolean,
    isBound: (HomeGesture) -> Boolean,
    onGesture: (HomeGesture, Offset) -> Unit,
): Modifier = if (!enabled) this else pointerInput(Unit) {
    val swipe = 56.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var total = Offset.Zero
        var multi = false
        var zoom = 1f
        var rotation = 0f
        var pan = Offset.Zero
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = ev.changes.count { it.pressed }
            if (pressed == 0) break
            if (pressed >= 2) {
                multi = true
                zoom *= ev.calculateZoom()
                rotation += ev.calculateRotation()
                pan += ev.calculateCentroid(useCurrent = true) - ev.calculateCentroid(useCurrent = false)
                ev.changes.forEach { if (it.positionChange() != Offset.Zero) it.consume() }
                val fired = when {
                    zoom < 0.72f -> HomeGesture.PinchIn
                    zoom > 1.35f -> HomeGesture.PinchOut
                    rotation > 35f -> HomeGesture.RotateRight
                    rotation < -35f -> HomeGesture.RotateLeft
                    pan.y < -swipe * 1.4f && abs(pan.y) > abs(pan.x) -> HomeGesture.TwoFingerUp
                    pan.y > swipe * 1.4f && abs(pan.y) > abs(pan.x) -> HomeGesture.TwoFingerDown
                    else -> null
                }
                if (fired != null) {
                    if (isBound(fired)) onGesture(fired, down.position)
                    // Swallow the rest of this gesture.
                    do { awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } } while (currentEvent.changes.any { it.pressed })
                    break
                }
                continue
            }
            if (multi) continue
            // Same event, Main pass: children (icon drags, widgets) have had their turn.
            val main = awaitPointerEvent(PointerEventPass.Main)
            val ch = main.changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed) break
            if (ch.isConsumed) break
            total += ch.positionChange()
            if (abs(total.x) > swipe) break
            val g = when {
                total.y > swipe && total.y > abs(total.x) * 1.4f -> HomeGesture.SwipeDown
                total.y < -swipe && -total.y > abs(total.x) * 1.4f -> HomeGesture.SwipeUp
                else -> null
            }
            if (g != null) {
                if (isBound(g)) {
                    ch.consume()
                    onGesture(g, down.position)
                }
                break
            }
        }
    }
}

/** Small rect around a touch point, used as the morph origin for Spotlight. */
fun originRect(at: Offset) = Rect(at.x - 40f, at.y - 20f, at.x + 40f, at.y + 20f)
