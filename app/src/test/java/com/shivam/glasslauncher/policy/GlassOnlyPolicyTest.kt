package com.shivam.glasslauncher.policy

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Lint-style guard: every visible surface must go through `ui/glass` (GlassSurface and the glass
 * controls). Fails the build if a plain Material or framework surface sneaks in anywhere else.
 */
class GlassOnlyPolicyTest {

    private val banned = listOf(
        "androidx.compose.material3.Surface",
        "androidx.compose.material3.Card",
        "androidx.compose.material3.ElevatedCard",
        "androidx.compose.material3.OutlinedCard",
        "androidx.compose.material3.AlertDialog",
        "androidx.compose.material3.BasicAlertDialog",
        "androidx.compose.material3.ModalBottomSheet",
        "androidx.compose.material3.BottomSheetScaffold",
        "androidx.compose.material3.Snackbar",
        "androidx.compose.material3.SnackbarHost",
        "androidx.compose.material3.Scaffold",
        "androidx.compose.material3.TopAppBar",
        "androidx.compose.material3.NavigationBar",
        "androidx.compose.material3.Button",
        "androidx.compose.material3.TextButton",
        "androidx.compose.material3.OutlinedButton",
        "androidx.compose.material3.FilledTonalButton",
        "androidx.compose.material3.IconButton",
        "androidx.compose.material3.Switch",
        "androidx.compose.material3.Slider",
        "androidx.compose.material3.TextField",
        "androidx.compose.material3.OutlinedTextField",
        "androidx.compose.material3.SearchBar",
        "androidx.compose.material3.DropdownMenu",
        "androidx.compose.material3.ListItem",
        "androidx.compose.material3.Chip",
        "androidx.compose.material3.FilterChip",
        "androidx.compose.material3.SegmentedButton",
        "androidx.compose.ui.window.Dialog",
        "androidx.compose.ui.window.Popup",
        "android.app.AlertDialog",
        "android.app.Dialog",
        "android.widget.Toast",
        "androidx.compose.material3.*",
    )

    @Test
    fun noPlainSurfacesOutsideGlassPackage() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory }
            ?: error("source root not found from ${File(".").absolutePath}")
        val violations = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.invariantSeparatorsPath.contains("/ui/glass/") }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    val t = line.trim()
                    val hit = banned.firstOrNull { b -> t == "import $b" || t.startsWith("import $b ") || t.contains("$b(") }
                    hit?.let { "${file.path}:${i + 1}: $it — use GlassSurface / ui.glass controls" }
                }
            }
            .toList()
        assertTrue("Plain surfaces found:\n" + violations.joinToString("\n"), violations.isEmpty())
    }
}
