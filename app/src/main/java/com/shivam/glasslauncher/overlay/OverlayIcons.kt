package com.shivam.glasslauncher.overlay

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Original line-art glyphs for the island, Control Center and Notification Center, drawn on the
 * same 24×24 grid and stroke as ui/glass/GlassIcons (tinted at draw time).
 */
object OverlayIcons {
    private fun icon(name: String, vararg paths: String, fill: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = if (fill) SolidColor(Color.White) else null,
                    stroke = if (fill) null else SolidColor(Color.White),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Wifi = icon("wifi", "M2.5,9.5C7.8,4.8 16.2,4.8 21.5,9.5", "M5.8,12.8C9.3,9.7 14.7,9.7 18.2,12.8", "M9,16C10.7,14.6 13.3,14.6 15,16", "M12,19.3L12,19.4")
    val Bluetooth = icon("bluetooth", "M7,7.5L16.5,16L12,20.5V3.5L16.5,8L7,16.5")
    val Cellular = icon("cellular", "M5,19V16", "M9.5,19V13", "M14,19V10", "M18.5,19V7")
    val Airplane = icon(
        "airplane",
        "M12,2.5C12.9,2.5 13.4,3.4 13.4,4.6V9.4L21,13.8V16L13.4,13.7V18.2L15.6,19.9V21.3L12,20.3L8.4,21.3V19.9L10.6,18.2V13.7L3,16V13.8L10.6,9.4V4.6C10.6,3.4 11.1,2.5 12,2.5Z",
    )
    val Sun = icon(
        "sun",
        "M12,8a4,4 0 1,0 0,8a4,4 0 1,0 0,-8z",
        "M12,2.5V4.5", "M12,19.5V21.5", "M2.5,12H4.5", "M19.5,12H21.5",
        "M5.3,5.3L6.7,6.7", "M17.3,17.3L18.7,18.7", "M5.3,18.7L6.7,17.3", "M17.3,6.7L18.7,5.3",
    )
    val Speaker = icon("speaker", "M4,9.5H7.5L12,5.5V18.5L7.5,14.5H4Z", "M15.5,9C16.6,10.4 16.6,13.6 15.5,15", "M18.3,6.5C20.6,9.3 20.6,14.7 18.3,17.5")
    val Flashlight = icon("flashlight", "M8,3H16V7L14,10.5V21H10V10.5L8,7Z", "M8,7H16", "M12,13.5V15.5")
    val Moon = icon("moon", "M20,14.5C18.9,15 17.7,15.2 16.5,15.2C11.9,15.2 8.8,12.1 8.8,7.5C8.8,6.3 9,5.1 9.5,4C6.2,5.2 4,8.4 4,12C4,16.4 7.6,20 12,20C15.6,20 18.8,17.8 20,14.5Z")
    val RotationLock = icon(
        "rotationLock",
        "M12,3.5A8.5,8.5 0 1,1 4.6,7.8",
        "M4.2,3.8V8.2H8.6",
        "M9.3,12.2h5.4v4.3h-5.4z",
        "M10.4,12.2V11a1.6,1.6 0 0,1 3.2,0V12.2",
    )
    val Clock = icon("clock", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z", "M12,7V12L15.5,14")
    val Timer = icon("timer", "M12,7.5a6.5,6.5 0 1,0 0,13a6.5,6.5 0 1,0 0,-13z", "M12,10.5V14L14.2,15.5", "M10,3H14", "M12,3V7.5")
    val Camera = icon("camera", "M3.5,8H7.5L9,5.5H15L16.5,8H20.5V19H3.5Z", "M12,10a3.5,3.5 0 1,0 0,7a3.5,3.5 0 1,0 0,-7z")
    val Qr = icon("qr", "M4,4h6v6h-6z", "M14,4h6v6h-6z", "M4,14h6v6h-6z", "M14,14h2.5", "M14,17.5V20", "M17.5,17.5H20V14", "M17,20H20")
    val Play = icon("play", "M7.5,4.8L19,12L7.5,19.2Z", fill = true)
    val Pause = icon("pause", "M6.5,5h3.6v14h-3.6z", "M13.9,5h3.6v14h-3.6z", fill = true)
    val Next = icon("next", "M4,6L12,12L4,18Z", "M12,6L20,12L12,18Z", fill = true)
    val Previous = icon("previous", "M20,6L12,12L20,18Z", "M12,6L4,12L12,18Z", fill = true)
    val Note = icon("note", "M9,17.5V6L19,4V15.5", "M9,17.5a2.5,2.5 0 1,1 -5,0a2.5,2.5 0 1,1 5,0z", "M19,15.5a2.5,2.5 0 1,1 -5,0a2.5,2.5 0 1,1 5,0z")
    val Bolt = icon("bolt", "M13.5,2.5L5.5,13.5H11.5L10.5,21.5L18.5,10.5H12.5Z", fill = true)
    val ArrowDown = icon("arrowDown", "M12,4V19", "M6,13L12,19L18,13")
    val ArrowUp = icon("arrowUp", "M12,20V5", "M6,11L12,5L18,11")
    val Navigate = icon("navigate", "M12,3L19,20L12,16.5L5,20Z", fill = true)
    val BellSlash = icon("bellSlash", "M6,16V11a6,6 0 0,1 9.6,-4.8", "M18,11V16L19.5,18H8", "M4,4L20,20", "M10,20.5a2,2 0 0,0 4,0")
    val BellRing = icon("bellRing", "M6,16V11a6,6 0 0,1 12,0V16L19.5,18H4.5Z", "M10,20.5a2,2 0 0,0 4,0", "M3,8C3.5,6 4.5,4.5 6,3.5", "M21,8C20.5,6 19.5,4.5 18,3.5")
    val Vibrate = icon("vibrate", "M8,4h8v16h-8z", "M4.5,8V16", "M19.5,8V16", "M2,10V14", "M22,10V14")
    val Mic = icon("mic", "M12,3a3,3 0 0,0 -3,3V12a3,3 0 0,0 6,0V6a3,3 0 0,0 -3,-3z", "M5.5,11.5C5.5,15 8.4,17.8 12,17.8C15.6,17.8 18.5,15 18.5,11.5", "M12,17.8V21")
    val Reply = icon("reply", "M10,6L4,12L10,18", "M4,12H14C17.3,12 20,14.7 20,18")
    val Send = icon("send", "M4,12L20,4L16,20L12,13Z", "M12,13L20,4")
    val ControlCenter = icon("controls", "M4,7H14", "M18,7H20", "M16,5a2,2 0 1,0 0,4a2,2 0 1,0 0,-4z", "M4,17H6", "M10,17H20", "M8,15a2,2 0 1,0 0,4a2,2 0 1,0 0,-4z")
    val Island = icon("island", "M6,9H18a3,3 0 0,1 0,6H6a3,3 0 0,1 0,-6Z")
    val Stack = icon("stack", "M5,9h14v10h-14z", "M7,6H17", "M9,3.5H15")
}
