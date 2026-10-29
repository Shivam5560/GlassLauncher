package com.shivam.glasslauncher.widgets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.ui.glass.ResumedLoop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** How long each photo stays before the next one fades in. */
private const val PHOTO_MS = 45_000

/**
 * Rotates through photos the user picked with the Photo Picker (no storage permission), with a
 * gentle Ken Burns drift that stops when the system's animations are turned off.
 */
@Composable
fun PhotosWidget(inst: WidgetInstance, size: WidgetSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val env = LocalWidgetEnv.current
    val scope = rememberCoroutineScope()
    val version = inst[WidgetKeys.PHOTO_COUNT] ?: "0"
    val pick = rememberPhotoPicker(inst.id) { n -> env?.toast?.invoke(if (n > 0) "$n photo${if (n > 1) "s" else ""} added" else "No photos added") }
    val files by produceState(emptyList<File>(), inst.id, version) {
        value = PhotoStore.files(context, inst.id)
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

    WidgetFrame(modifier, padding = 0.dp, onClick = {
        if (files.size > 1) scope.launch { index.intValue = (index.intValue + 1) % files.size }
    }) {
        Crossfade(targetState = bitmap, animationSpec = tween(900), label = "photo", modifier = Modifier.fillMaxSize().clip(WidgetShape)) { img ->
            if (img != null) KenBurnsImage(img, file.name, reduced)
        }
    }
}

@Composable
private fun KenBurnsImage(img: ImageBitmap, key: String, reduced: Boolean) {
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key, reduced) {
        if (!reduced) t.animateTo(1f, tween(PHOTO_MS + 2_000, easing = LinearEasing))
    }
    // Alternate the drift direction per photo so consecutive photos don't move the same way.
    val dir = if (key.hashCode() % 2 == 0) 1f else -1f
    Image(
        img,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            val p = if (reduced) 0f else t.value
            val s = 1.04f + 0.10f * p
            scaleX = s
            scaleY = s
            translationX = dir * (p - 0.5f) * size.width * 0.05f
            translationY = (0.5f - p) * size.height * 0.03f
        },
    )
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
