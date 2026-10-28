package com.shivam.glasslauncher.shortcuts

import android.content.pm.LauncherApps
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.ui.glass.GlassButton
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassTheme
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.glass.rememberCrossWindowBlur
import com.shivam.glasslauncher.ui.glass.rememberGlassLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles `LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT`: another app (Chrome "Add to Home screen",
 * Contacts, Maps, ...) asks Glass to pin a shortcut. A glass sheet shows it; "Add" accepts the
 * [LauncherApps.PinItemRequest] and stores it in [PinnedShortcutStore].
 */
class PinShortcutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val launcherApps = getSystemService(LauncherApps::class.java)
        val request = try { launcherApps.getPinItemRequest(intent) } catch (e: Exception) { null }
        val info = request?.shortcutInfo
        if (request == null || !request.isValid || request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT || info == null) {
            finish()
            return
        }
        val app = GlassApp.from(this)
        val appLabel = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(info.`package`, 0)).toString()
        } catch (e: Exception) { info.`package` }
        setContent {
            PinSheet(
                label = (info.shortLabel ?: info.longLabel ?: "").toString(),
                from = appLabel,
                loadIcon = {
                    val d = launcherApps.getShortcutIconDrawable(info, resources.displayMetrics.densityDpi)
                    app.icons.renderShortcut(d, 192)
                },
                onAdd = {
                    if (request.isValid && request.accept()) app.polish.shortcuts.add(info)
                    finish()
                },
                onCancel = { finish() },
            )
        }
    }
}

@Composable
private fun PinSheet(label: String, from: String, loadIcon: () -> ImageBitmap?, onAdd: () -> Unit, onCancel: () -> Unit) {
    val app = GlassApp.from(androidx.compose.ui.platform.LocalContext.current)
    val settings = app.settings.state.collectAsState().value ?: return
    val snapshot by app.wallpaper.snapshot.collectAsState()
    val light = rememberGlassLight(false)
    rememberCrossWindowBlur(settings.glass.blurRadius)
    val icon by produceState<ImageBitmap?>(null) { value = withContext(Dispatchers.Default) { loadIcon() } }
    GlassTheme(settings.glass, light, snapshot.globalLuminance, null) {
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.BottomCenter) {
            GlassSurface(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), shape = GlassShape.rounded(32.dp), role = null) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    GlassText("Add to Home Screen", style = GlassType.Title)
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.size(72.dp)) { icon?.let { Image(it, null, Modifier.fillMaxSize()) } }
                    Spacer(Modifier.height(10.dp))
                    GlassText(label, style = GlassType.Headline, maxLines = 2)
                    GlassText("From $from", style = GlassType.Footnote, secondary = true)
                    Spacer(Modifier.height(8.dp))
                    GlassText(
                        "Pinned shortcuts are listed in Settings → Customise → Pinned shortcuts.",
                        style = GlassType.Footnote,
                        secondary = true,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton("Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
                        GlassButton("Add", onClick = onAdd, modifier = Modifier.weight(1f), prominent = true, variant = GlassVariant.Regular)
                    }
                }
            }
        }
    }
}
