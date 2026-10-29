package com.shivam.glasslauncher.overlay

/** AUTO yields to firmware with a native island; it never modifies an OEM feature flag. */
enum class IslandCompatibility(val title: String) {
    AUTO("Automatic"), SYSTEM("System island"), GLASS("Glass island");

    fun allowsGlass(nativeIsland: Boolean): Boolean = when (this) {
        AUTO -> !nativeIsland
        SYSTEM -> false
        GLASS -> true
    }
}
