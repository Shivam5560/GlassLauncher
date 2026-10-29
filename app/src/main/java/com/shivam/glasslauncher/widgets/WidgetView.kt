package com.shivam.glasslauncher.widgets

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.ui.glass.GlassButton
import com.shivam.glasslauncher.ui.glass.GlassIcon
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.glass.LocalGlassContentColor

/** What widgets may ask of the launcher around them. */
@Stable
class WidgetEnv(
    val openSpotlight: (Rect?) -> Unit,
    val toast: (String) -> Unit,
    val toastAction: (String, String, () -> Unit) -> Unit,
    val openSettings: (String?) -> Unit,
    val editWidget: (String) -> Unit,
    val editMode: Boolean,
    val reducedMotion: Boolean,
)

val LocalWidgetEnv = compositionLocalOf<WidgetEnv?> { null }

/** Continuous-corner widget outline (iOS widgets use about 22pt corners). */
val WidgetShape = GlassShape.rounded(24.dp)

/** True when the user turned animations off (Developer options or Remove animations). */
fun reducedMotion(context: Context): Boolean =
    try { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f } catch (e: Exception) { false }

/** The Regular glass panel every widget sits on. */
@Composable
fun WidgetFrame(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: androidx.compose.ui.unit.Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        shape = WidgetShape,
        variant = GlassVariant.Regular,
        onClick = onClick,
        role = null,
    ) {
        Box(Modifier.fillMaxSize().padding(padding), content = content)
    }
}

/** Renders any widget instance at [size] (home placement and the Today view decide the size). */
@Composable
fun WidgetContent(inst: WidgetInstance, modifier: Modifier = Modifier, size: WidgetSize = inst.size) {
    when (inst.kind) {
        WidgetKind.SMART_STACK -> SmartStackWidget(inst, size, modifier)
        WidgetKind.MUSIC -> MusicWidget(size, modifier)
        WidgetKind.WEATHER -> WeatherWidget(inst, size, modifier)
        WidgetKind.CALENDAR -> CalendarWidget(size, modifier)
        WidgetKind.CLOCK -> ClockWidget(inst, size, modifier)
        WidgetKind.BATTERY -> BatteryWidget(size, modifier)
        WidgetKind.PHOTOS -> PhotosWidget(inst, size, modifier)
        WidgetKind.NOTES -> NotesWidget(inst, size, modifier)
        WidgetKind.SEARCH -> SearchBarWidget(modifier)
        WidgetKind.APP_WIDGET -> AppWidgetContent(inst, modifier)
    }
}

/**
 * The fallback panel a widget shows when it lacks a permission or data: a glyph, one line of
 * reason and a glass button that fixes it (docs/CONSTRAINTS.md: tell the user in-app).
 */
@Composable
fun WidgetPrompt(
    icon: ImageVector,
    text: String,
    action: String?,
    onAction: () -> Unit,
    size: WidgetSize,
    modifier: Modifier = Modifier,
    secondaryAction: String? = null,
    onSecondary: () -> Unit = {},
) {
    WidgetFrame(modifier) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GlassIcon(icon, size = if (size == WidgetSize.SMALL) 26.dp else 30.dp)
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.Text(
                text,
                style = if (size == WidgetSize.SMALL) GlassType.Footnote else GlassType.Callout,
                color = LocalGlassContentColor.current.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = if (size == WidgetSize.SMALL) 3 else 2,
            )
            if (action != null) {
                Spacer(Modifier.height(8.dp))
                GlassButton(action, onClick = onAction, prominent = true)
            }
            if (secondaryAction != null && size != WidgetSize.SMALL) {
                Spacer(Modifier.height(6.dp))
                GlassButton(secondaryAction, onClick = onSecondary)
            }
        }
    }
}

/** A runtime permission request; on a permanent denial, offers App info instead. */
@Composable
fun rememberPermissionRequest(permission: String, onGranted: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val env = LocalWidgetEnv.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            onGranted()
        } else {
            val activity = context as? Activity
            val canAskAgain = activity?.shouldShowRequestPermissionRationale(permission) == true
            if (!canAskAgain) env?.toastAction?.invoke("Allow it in App info → Permissions", "Open") { openAppDetails(context) }
        }
    }
    return { launcher.launch(permission) }
}

fun openAppDetails(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Exception) { }
}

/** Opens the notification access screen for Glass (music widget, badges). */
fun openNotificationAccess(context: Context) {
    val activity = context as? Activity
    val intents = com.shivam.glasslauncher.settings.SystemChecks.notificationIntents(context)
    if (activity != null) {
        com.shivam.glasslauncher.settings.SystemChecks.launchFirst(activity, intents)
    } else {
        intents.firstOrNull()?.let { try { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { } }
    }
}

/**
 * Long-press on a widget (outside edit mode), watched in the Initial pass so the widget's own
 * buttons keep working; once it fires, the rest of the gesture is consumed so nothing clicks.
 */
fun Modifier.widgetLongPress(enabled: Boolean, onLongPress: () -> Unit): Modifier =
    if (!enabled) this else pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            val endedEarly: Boolean? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var ended = false
                while (!ended) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val ch = ev.changes.firstOrNull { it.id == down.id }
                    ended = ch == null || !ch.pressed || (ch.position - down.position).getDistance() > slop
                }
                true
            }
            if (endedEarly == null) {
                onLongPress()
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    ev.changes.forEach { it.consume() }
                    if (ev.changes.none { it.pressed }) break
                }
            }
        }
    }
