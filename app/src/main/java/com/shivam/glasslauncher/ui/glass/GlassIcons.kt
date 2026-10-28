package com.shivam.glasslauncher.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Small original line-art glyphs (24x24 grid, 1.8px stroke), tinted at draw time. */
object GlassIcons {
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

    val Search = icon("search", "M10.5,4a6.5,6.5 0 1,0 0,13a6.5,6.5 0 1,0 0,-13z", "M15.5,15.5L20,20")
    val Close = icon("close", "M6,6L18,18", "M18,6L6,18")
    val ChevronRight = icon("chevronRight", "M9,5L16,12L9,19")
    val ChevronLeft = icon("chevronLeft", "M15,5L8,12L15,19")
    val Minus = icon("minus", "M6,12L18,12")
    val Plus = icon("plus", "M12,6L12,18", "M6,12L18,12")
    val Check = icon("check", "M5,12.5L10,17.5L19,7")
    val Phone = icon(
        "phone",
        "M6.5,3.5L9.5,3.5L11,7.5L9,9C10,11.2 12.8,14 15,15L16.5,13L20.5,14.5L20.5,17.5C20.5,19 19.2,20.3 17.6,20.2C10.6,19.6 4.4,13.4 3.8,6.4C3.7,4.8 5,3.5 6.5,3.5Z",
    )
    val Chat = icon("chat", "M12,4C7.3,4 4,7 4,11C4,13.1 5,15 6.6,16.2L6,20L9.8,18C10.5,18.2 11.2,18.3 12,18.3C16.7,18.3 20,15.3 20,11.2C20,7 16.7,4 12,4Z")
    val Gear = icon(
        "gear",
        "M12,9a3,3 0 1,0 0,6a3,3 0 1,0 0,-6z",
        "M12,2.8L13.6,5.2L16.4,4.6L17,7.4L19.6,8.6L18.6,11.3L20.2,13.6L17.8,15.1L17.6,18L14.8,18L12.9,20.2L11,18.3L8.2,18.6L7.7,15.8L5.2,14.6L6.2,11.9L4.6,9.6L7,8.1L7.2,5.2L10,5.4Z",
    )
    val Lock = icon("lock", "M6,11h12v9h-12z", "M8.5,11V8a3.5,3.5 0 0,1 7,0V11")
    val EyeOff = icon("eyeOff", "M3,12C5,8 8.3,6 12,6C15.7,6 19,8 21,12C19,16 15.7,18 12,18C8.3,18 5,16 3,12Z", "M4,4L20,20")
    val Info = icon("info", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z", "M12,11L12,16.5", "M12,7.6L12,7.7")
    val Trash = icon("trash", "M5,7L19,7", "M9,7V4.8h6V7", "M7,7L8,20h8L17,7")
    val Pencil = icon("pencil", "M4,20L5,15.5L15.5,5L19,8.5L8.5,19Z", "M13.5,7L17,10.5")
    val Grid = icon("grid", "M5,5h5v5h-5z", "M14,5h5v5h-5z", "M5,14h5v5h-5z", "M14,14h5v5h-5z")
    val Globe = icon("globe", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z", "M3,12L21,12", "M12,3C9.5,6 9.5,18 12,21", "M12,3C14.5,6 14.5,18 12,21")
    val Calculator = icon("calc", "M6,3h12v18h-12z", "M8.5,6.5h7v3h-7z", "M9,13L9,13.1", "M12,13L12,13.1", "M15,13L15,13.1", "M9,17L9,17.1", "M12,17L12,17.1", "M15,17L15,17.1")
    val Ruler = icon("ruler", "M3,15L15,3L21,9L9,21Z", "M7,11L9,13", "M10,8L12,10", "M13,5L15,7")
    val Person = icon("person", "M12,4a4,4 0 1,0 0,8a4,4 0 1,0 0,-8z", "M4.5,20.5C5.5,16.8 8.4,14.8 12,14.8C15.6,14.8 18.5,16.8 19.5,20.5")
    val Sparkle = icon("sparkle", "M12,3L13.8,10.2L21,12L13.8,13.8L12,21L10.2,13.8L3,12L10.2,10.2Z")
    val Hand = icon("hand", "M8,13V6.5a1.5,1.5 0 0,1 3,0V12", "M11,11V5a1.5,1.5 0 0,1 3,0V11.5", "M14,11.5V6.5a1.5,1.5 0 0,1 3,0V14C17,18 14.5,21 11,21C8,21 6,19 5,16L3.8,12.8a1.4,1.4 0 0,1 2.6,-1L8,15")
    val Palette = icon("palette", "M12,3C7,3 3,6.8 3,11.5C3,15.5 6,18 9,18C10.5,18 11,19 11,20C11,20.6 11.5,21 12,21C17,21 21,17 21,12C21,7 17,3 12,3Z", "M7.5,11L7.5,11.1", "M10,7.5L10,7.6", "M14.5,7.5L14.5,7.6", "M17,11L17,11.1")
    val Bell = icon("bell", "M6,16V11a6,6 0 0,1 12,0V16L19.5,18H4.5Z", "M10,20.5a2,2 0 0,0 4,0")
    val Home = icon("home", "M4,11L12,4L20,11", "M6,9.5V20H18V9.5")
}
