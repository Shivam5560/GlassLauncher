package com.shivam.glasslauncher.polish

import android.content.Context
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font as GoogleFontFont
import com.shivam.glasslauncher.R
import com.shivam.glasslauncher.ui.glass.Inter

/**
 * The font picker's catalogue.
 *
 * Bundled fonts are SIL OFL fonts shipped in `res/font` (licences in the repo root). Downloadable
 * fonts come from Google Fonts through the Play Services font provider; every downloadable family
 * lists bundled Inter after it, so text renders in Inter while a font downloads and stays in Inter
 * if Play Services is missing or the download fails (docs/CONSTRAINTS.md, "Font picker").
 */
object Fonts {

    const val DEFAULT_ID = "inter"
    private const val PROVIDER_AUTHORITY = "com.google.android.gms.fonts"
    private const val PROVIDER_PACKAGE = "com.google.android.gms"

    data class Entry(val id: String, val name: String, val bundled: Boolean, val licence: String)

    val bundled = listOf(
        Entry("system", "System font", true, "The font your phone uses (MiSans on HyperOS)"),
        Entry("inter", "Inter", true, "© The Inter Project Authors · OFL 1.1"),
        Entry("manrope", "Manrope", true, "© The Manrope Project Authors · OFL 1.1"),
        Entry("nunito", "Nunito", true, "© The Nunito Project Authors · OFL 1.1"),
        Entry("space_grotesk", "Space Grotesk", true, "© The Space Grotesk Project Authors · OFL 1.1"),
        Entry("lora", "Lora", true, "© The Lora Project Authors · OFL 1.1"),
    )

    /** Google Fonts families offered for download (all open-licensed, served by Play Services). */
    val downloadable = listOf(
        "Poppins", "Outfit", "Figtree", "Plus Jakarta Sans", "Sora", "Urbanist", "Lexend", "Rubik",
        "Work Sans", "Quicksand", "DM Sans", "Playfair Display", "JetBrains Mono", "Comfortaa",
    ).map { Entry("gf:$it", it, false, "Google Fonts · downloaded by Play Services") }

    val all get() = bundled + downloadable

    fun entry(id: String): Entry = all.firstOrNull { it.id == id } ?: bundled.first()

    private val provider = GoogleFont.Provider(
        providerAuthority = PROVIDER_AUTHORITY,
        providerPackage = PROVIDER_PACKAGE,
        certificates = R.array.com_google_android_gms_fonts_certs,
    )

    /** Whether the Play Services font provider exists on this phone (no network check). */
    fun downloadsAvailable(context: Context): Boolean = try {
        context.packageManager.resolveContentProvider(PROVIDER_AUTHORITY, 0) != null
    } catch (e: Exception) {
        false
    }

    private val weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

    @OptIn(ExperimentalTextApi::class)
    private fun variable(res: Int) = FontFamily(weights.map { w ->
        Font(res, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    })

    /** Resolves [id] to a family; downloadable ones fall back to the bundled fonts. */
    fun family(context: Context, id: String): FontFamily = when (id) {
        "inter" -> Inter
        "system" -> FontFamily.SansSerif
        "manrope" -> variable(R.font.manrope)
        "nunito" -> variable(R.font.nunito)
        "space_grotesk" -> variable(R.font.space_grotesk)
        "lora" -> variable(R.font.lora)
        else -> {
            val name = id.removePrefix("gf:")
            if (!id.startsWith("gf:") || !downloadsAvailable(context)) {
                Inter
            } else {
                val google = GoogleFont(name)
                FontFamily(
                    weights.map { w -> GoogleFontFont(googleFont = google, fontProvider = provider, weight = w) } +
                        listOf(
                            Font(R.font.inter_regular, FontWeight.Normal),
                            Font(R.font.inter_medium, FontWeight.Medium),
                            Font(R.font.inter_semibold, FontWeight.SemiBold),
                            Font(R.font.inter_bold, FontWeight.Bold),
                        ),
                )
            }
        }
    }
}
