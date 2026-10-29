package com.shivam.glasslauncher.widgets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.ui.glass.ResumedLoop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.LocalGlassStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** How long each photo stays before the next one fades in. */
private const val PHOTO_MS = 45_000

/**
 * Rotates through photos the user picked with the Photo Picker (no storage permission), with a
 * short crossfade only when the photo changes. A resting photo does not continuously render frames.
 */
@Composable
fun PhotosWidget(inst: WidgetInstance, size: WidgetSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val env = LocalWidgetEnv.current
    val scope = rememberCoroutineScope()
    val version = inst[WidgetKeys.PHOTO_COUNT] ?: "0"
    val pick = rememberPhotoPicker(inst.id) { n -> env?.toast?.invoke(if (n > 0) "$n photo${if (n > 1) "s" else ""} added" else "No photos added") }
    val files by produceState(emptyList<File>(), inst.id, version) {
        value = withContext(Dispatchers.IO) { PhotoStore.files(context, inst.id) }
    }
    if (files.isEmpty()) {
        WidgetPrompt(
            icon = WidgetGlyphs.Photo,
            text = if (size == WidgetSize.SMALL) "Pick photos to show" else "Pick photos to show here. Glass only sees the ones you pick.",
            action = "Choose",
            onAction = pick,
            size = size,
            modifier = modifier,
        )
        return
    }
    val reduced = env?.reducedMotion ?: reducedMotion(context)
    val index = remember(files) { mutableIntStateOf(0) }
    ResumedLoop(files) {
        while (files.size > 1) {
            delay(PHOTO_MS.toLong())
            index.intValue = (index.intValue + 1) % files.size
        }
    }
    val file = files[index.intValue.coerceIn(0, files.size - 1)]
    val maxSide = if (size == WidgetSize.SMALL) 600 else 1200
    val bitmap by produceState<ImageBitmap?>(null, file, maxSide) {
        value = PhotoStore.load(file, maxSide)?.asImageBitmap()
    }

    WidgetFrame(modifier, padding = 3.dp, onClick = {
        if (files.size > 1) scope.launch { index.intValue = (index.intValue + 1) % files.size }
    }) {
        val innerShape = GlassShape.rounded((LocalGlassStyle.current.widgetCornerRadius - 3f).coerceAtLeast(0f).dp)
        Crossfade(targetState = bitmap, animationSpec = tween(if (reduced) 0 else 650), label = "photo", modifier = Modifier.fillMaxSize().clip(innerShape)) { img ->
            if (img != null) Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Launches the Photo Picker and imports the picks into the widget's storage. */
@Composable
fun rememberPhotoPicker(widgetId: String, onDone: (Int) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { WidgetRepository.get(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val n = PhotoStore.import(context, widgetId, uris)
            // Bump the config so every view of this widget reloads its photos.
            repo.setConfig(widgetId, WidgetKeys.PHOTO_COUNT, "$n-${System.currentTimeMillis()}")
            onDone(n)
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}
