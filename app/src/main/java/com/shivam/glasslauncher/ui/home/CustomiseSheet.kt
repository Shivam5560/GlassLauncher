package com.shivam.glasslauncher.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.data.LauncherSettings
import com.shivam.glasslauncher.settings.IconStyleGroup
import com.shivam.glasslauncher.ui.glass.GlassListGroup
import com.shivam.glasslauncher.ui.glass.GlassSlider
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassSwitchRow
import com.shivam.glasslauncher.widgets.GlassSheet

/**
 * iOS "Edit → Customise" for the Home Screen: icon size, labels and icon appearance, changed live
 * over the home pages behind the sheet. Size is saved when the slider is let go, not every frame.
 */
@Composable
fun CustomiseSheet(visible: Boolean, settings: LauncherSettings, onDismiss: () -> Unit) {
    val app = GlassApp.from(LocalContext.current)
    GlassSheet(visible = visible, title = "Customise", onDismiss = onDismiss, heightFraction = 0.55f) {
        Column(Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState())) {
            var scale by remember(settings.appearance.iconScale) { mutableFloatStateOf(settings.appearance.iconScale) }
            GlassListGroup {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row {
                        GlassText("Icon size", Modifier.weight(1f), style = GlassType.Callout)
                        GlassText("${(scale * 100).toInt()}%", style = GlassType.Callout, secondary = true)
                    }
                    GlassSlider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = 0.7f..1.3f,
                        onValueChangeFinished = { app.settings.setAppearance(settings.appearance.copy(iconScale = scale)) },
                    )
                }
                GlassSwitchRow("Show app names", settings.showLabels, { app.settings.setShowLabels(it) })
            }
            Spacer(Modifier.height(12.dp))
            IconStyleGroup()
        }
    }
}
