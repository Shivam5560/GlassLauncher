package com.shivam.glasslauncher.ui.drawer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.ui.glass.GlassIcons

/**
 * Original line-art glyphs for drawer categories and the drawer header (24x24 grid, 1.8px stroke,
 * tinted at draw time), in the same style as [GlassIcons].
 */
object DrawerGlyphs {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Media = icon("media", "M4,5.5h16v13h-16z", "M10,9V15L15,12Z")
    val Social = icon("social", "M9,11a3,3 0 1,0 0,-6a3,3 0 1,0 0,6z", "M3.5,19C4.3,15.8 6.4,14 9,14C11.6,14 13.7,15.8 14.5,19", "M15.5,5.3a3,3 0 0,1 0,5.4", "M17,14.3C18.8,14.9 20,16.6 20.5,19")
    val Games = icon("games", "M7,8h10a4,4 0 0,1 4,4v1a4,4 0 0,1 -7,2.6h-4A4,4 0 0,1 3,13v-1a4,4 0 0,1 4,-4z", "M7.5,10.5V13.5", "M6,12H9", "M15.5,11L15.5,11.1", "M17.5,13L17.5,13.1")
    val Utilities = icon("utilities", "M14.5,4.5a4.5,4.5 0 0,0 -4.2,6.1L4.5,16.4a1.9,1.9 0 0,0 2.7,2.7l5.8,-5.8a4.5,4.5 0 0,0 6.1,-4.2L16.5,11.5L13.5,10.5L12.5,7.5Z")
    val Star = icon("star", "M12,3.5L14.6,9L20.5,9.6L16,13.6L17.3,19.5L12,16.5L6.7,19.5L8,13.6L3.5,9.6L9.4,9Z")
    val Heart = icon("heart", "M12,19.5C12,19.5 4,14.8 4,9.3A4.3,4.3 0 0,1 12,7A4.3,4.3 0 0,1 20,9.3C20,14.8 12,19.5 12,19.5Z")
    val Briefcase = icon("briefcase", "M4,8h16v11h-16z", "M9,8V5.5h6V8", "M4,13H20")
    val Folder = icon("folder", "M3.5,6.5h6l2,2h9v10h-17z")
    val Bolt = icon("boltLine", "M13,2.5L5.5,13.5H11.5L10.5,21.5L18.5,10H12.5Z")
    val Book = icon("book", "M5,4.5h6a2,2 0 0,1 2,2V20a2,2 0 0,0 -2,-2H5Z", "M19,4.5h-4a2,2 0 0,0 -2,2V20a2,2 0 0,1 2,-2h4Z")
    val Cart = icon("cart", "M3.5,4.5H6L8,15.5H18L20,8H7", "M9.5,19.5L9.5,19.6", "M16.5,19.5L16.5,19.6")
    val Camera = icon("camera", "M4,8h4l1.5,-2.5h5L16,8h4v11h-16z", "M12,10.5a3,3 0 1,0 0,6a3,3 0 1,0 0,-6z")
    val Music = icon("music", "M9,18V6L19,4V16", "M9,18a2.5,2.5 0 1,1 -5,0a2.5,2.5 0 1,1 5,0z", "M19,16a2.5,2.5 0 1,1 -5,0a2.5,2.5 0 1,1 5,0z")
    val Store = icon("store", "M5,8h14l-1,12h-12z", "M9,10V7a3,3 0 0,1 6,0V10")
    val More = icon("more", "M12,5.5L12,5.6", "M12,12L12,12.1", "M12,18.5L12,18.6")
    val Link = icon("link", "M10,14L14,10", "M8.5,11.5L6.5,13.5a3,3 0 0,0 4,4L12.5,15.5", "M15.5,12.5L17.5,10.5a3,3 0 0,0 -4,-4L11.5,8.5")
    val Sort = icon("sort", "M5,7H19", "M7,12H17", "M9.5,17H14.5")
    val Share = icon("share", "M12,15V4", "M8,8L12,4L16,8", "M6,12V19.5H18V12")

    /** Glyph names a user can pick for their own categories. */
    val pickable = listOf("star", "heart", "briefcase", "folder", "bolt", "book", "cart", "camera", "music", "games", "media", "social")

    fun of(name: String): ImageVector = when (name) {
        "internet" -> GlassIcons.Globe
        "media" -> Media
        "social" -> Social
        "games" -> Games
        "utilities" -> Utilities
        "settings" -> GlassIcons.Gear
        "other" -> GlassIcons.Grid
        "heart" -> Heart
        "briefcase" -> Briefcase
        "folder" -> Folder
        "bolt" -> Bolt
        "book" -> Book
        "cart" -> Cart
        "camera" -> Camera
        "music" -> Music
        else -> Star
    }
}
