package com.shivam.glasslauncher.ui.glass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.shivam.glasslauncher.R

val LocalGlassStyle = staticCompositionLocalOf { GlassStyle() }
val LocalBackdrop = compositionLocalOf<Backdrop?> { null }
val LocalGlassLight = staticCompositionLocalOf<State<Offset>> { mutableStateOf(DefaultLight) }
val LocalGlassDarkMode = staticCompositionLocalOf { true }
/** Colour for text and glyphs on the nearest enclosing glass (adapts to the glass tone). */
val LocalGlassContentColor = compositionLocalOf { Color.White }
/** Luminance used when a surface has no backdrop sampler (0 dark .. 1 light). */
val LocalFallbackLuminance = staticCompositionLocalOf { 0.3f }

object GlassColors {
    val Accent = Color(0xFF0A84FF)
    val Green = Color(0xFF30D158)
    val Red = Color(0xFFFF453A)
    val Orange = Color(0xFFFF9F0A)
    val Badge = Color(0xFFFF3B30)
    val SecondaryOnDark = Color(0x99EBEBF5)
    val SecondaryOnLight = Color(0x993C3C43)
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** iOS-like type ramp in Inter, with slightly tightened tracking like SF Pro. */
object GlassType {
    val LargeTitle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.6).sp)
    val Title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.4).sp)
    val Headline = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = (-0.3).sp)
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 17.sp, letterSpacing = (-0.3).sp)
    val Callout = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, letterSpacing = (-0.2).sp)
    val Subhead = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = (-0.05).sp)
    val Footnote = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp)
    val Caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.05.sp)
    val IconLabel = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, letterSpacing = (-0.05).sp)
}

/**
 * Root theme for every screen. Provides the glass style, tilt light, dark-mode flag, backdrop and
 * Material typography in Inter (Material is only used for its typography/colour plumbing; every
 * visible surface is a [GlassSurface]).
 */
@Composable
fun GlassTheme(
    style: GlassStyle,
    light: State<Offset>,
    fallbackLuminance: Float,
    backdrop: Backdrop?,
    darkMode: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val typography = Typography(
        displayLarge = GlassType.LargeTitle,
        headlineMedium = GlassType.Title,
        titleLarge = GlassType.Title,
        titleMedium = GlassType.Headline,
        bodyLarge = GlassType.Body,
        bodyMedium = GlassType.Callout,
        bodySmall = GlassType.Footnote,
        labelLarge = GlassType.Headline,
        labelMedium = GlassType.Subhead,
        labelSmall = GlassType.Caption,
    )
    val scheme = if (darkMode) {
        darkColorScheme(primary = GlassColors.Accent, background = Color.Transparent, surface = Color.Transparent)
    } else {
        lightColorScheme(primary = GlassColors.Accent, background = Color.Transparent, surface = Color.Transparent)
    }
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(
            LocalGlassStyle provides style,
            LocalGlassLight provides light,
            LocalGlassDarkMode provides darkMode,
            LocalFallbackLuminance provides fallbackLuminance,
            LocalBackdrop provides backdrop,
            LocalGlassContentColor provides Color.White,
        ) {
            content()
        }
    }
}
