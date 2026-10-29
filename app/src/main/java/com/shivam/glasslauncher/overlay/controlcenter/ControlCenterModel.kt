package com.shivam.glasslauncher.overlay.controlcenter

/** Module footprint in grid cells (columns × rows). */
enum class CcSize(val w: Int, val h: Int, val label: String) {
    Small(1, 1, "1×1"),
    Wide(2, 1, "2×1"),
    Large(2, 2, "2×2"),
    /** 1×2, only for the vertical brightness and volume sliders (like iOS). */
    Tall(1, 2, "1×2"),
}

enum class CcGroup { Connectivity, Media, Sliders, Toggles, Shortcuts }

private val Toggle = listOf(CcSize.Small, CcSize.Wide, CcSize.Large)
private val Slider = listOf(CcSize.Tall, CcSize.Small, CcSize.Large)

enum class CcModuleId(val title: String, val group: CcGroup, val defaultSize: CcSize, val sizes: List<CcSize> = Toggle) {
    Wifi("Wi-Fi", CcGroup.Connectivity, CcSize.Small),
    Bluetooth("Bluetooth", CcGroup.Connectivity, CcSize.Small),
    MobileData("Mobile data", CcGroup.Connectivity, CcSize.Small),
    Airplane("Airplane mode", CcGroup.Connectivity, CcSize.Small),
    Music("Music", CcGroup.Media, CcSize.Large),
    Brightness("Brightness", CcGroup.Sliders, CcSize.Tall, Slider),
    Volume("Volume", CcGroup.Sliders, CcSize.Tall, Slider),
    Flashlight("Flashlight", CcGroup.Toggles, CcSize.Small),
    DoNotDisturb("Do Not Disturb", CcGroup.Toggles, CcSize.Small),
    RotationLock("Rotation lock", CcGroup.Toggles, CcSize.Small),
    ScreenTimeout("Screen timeout", CcGroup.Toggles, CcSize.Small),
    Camera("Camera", CcGroup.Shortcuts, CcSize.Small),
    Timer("Timer", CcGroup.Shortcuts, CcSize.Small),
    Calculator("Calculator", CcGroup.Shortcuts, CcSize.Small),
    QrScan("Scan QR code", CcGroup.Shortcuts, CcSize.Small),
}

data class CcModule(val id: CcModuleId, val size: CcSize)

/** A module's place in the grid, in cells. */
data class CcPlacement(val module: CcModule, val col: Int, val row: Int) {
    val right: Int get() = col + module.size.w
    val bottom: Int get() = row + module.size.h
}

/** Layout logic for the user-arrangeable Control Center grid (pure; unit-tested). */
object ControlCenterLayout {

    const val COLUMNS = 4

    val DEFAULT: List<CcModule> = listOf(
        CcModuleId.Wifi, CcModuleId.Bluetooth, CcModuleId.Music, CcModuleId.MobileData, CcModuleId.Airplane,
        CcModuleId.Brightness, CcModuleId.Volume, CcModuleId.Flashlight, CcModuleId.DoNotDisturb,
        CcModuleId.RotationLock, CcModuleId.ScreenTimeout,
        CcModuleId.Camera, CcModuleId.Timer, CcModuleId.Calculator, CcModuleId.QrScan,
    ).map { CcModule(it, it.defaultSize) }

    /** First-fit packing in reading order: each module takes the first free spot it fits. */
    fun pack(modules: List<CcModule>, columns: Int = COLUMNS): List<CcPlacement> {
        val taken = ArrayList<BooleanArray>()
        fun free(c: Int, r: Int, w: Int, h: Int): Boolean {
            if (c + w > columns) return false
            for (y in r until r + h) {
                val row = taken.getOrNull(y) ?: continue
                for (x in c until c + w) if (row[x]) return false
            }
            return true
        }
        fun mark(c: Int, r: Int, w: Int, h: Int) {
            for (y in r until r + h) {
                while (taken.size <= y) taken.add(BooleanArray(columns))
                for (x in c until c + w) taken[y][x] = true
            }
        }
        val out = ArrayList<CcPlacement>(modules.size)
        for (m in modules) {
            val w = m.size.w.coerceAtMost(columns)
            val h = m.size.h
            var r = 0
            placed@ while (true) {
                for (c in 0..columns - w) {
                    if (free(c, r, w, h)) {
                        mark(c, r, w, h)
                        out.add(CcPlacement(m, c, r))
                        break@placed
                    }
                }
                r++
            }
        }
        return out
    }

    fun rows(placements: List<CcPlacement>): Int = placements.maxOfOrNull { it.bottom } ?: 0

    fun move(modules: List<CcModule>, from: Int, to: Int): List<CcModule> {
        if (from !in modules.indices || to !in modules.indices || from == to) return modules
        val list = modules.toMutableList()
        val m = list.removeAt(from)
        list.add(to, m)
        return list
    }

    /** Next size in the module's allowed list (tap-to-resize in edit mode). */
    fun cycleSize(module: CcModule): CcModule {
        val sizes = module.id.sizes
        val next = sizes[(sizes.indexOf(module.size) + 1).mod(sizes.size)]
        return module.copy(size = next)
    }

    fun missing(modules: List<CcModule>): List<CcModuleId> {
        val present = modules.mapTo(HashSet()) { it.id }
        return CcModuleId.entries.filter { it !in present }
    }

    fun serialize(modules: List<CcModule>): String = modules.joinToString(",") { "${it.id.name}:${it.size.name}" }

    /** Tolerant parse: unknown ids are skipped, bad sizes fall back to the default, duplicates dropped. */
    fun parse(text: String?): List<CcModule> {
        if (text.isNullOrBlank()) return DEFAULT
        val seen = HashSet<CcModuleId>()
        return text.split(',').mapNotNull { token ->
            val parts = token.trim().split(':')
            val id = CcModuleId.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: return@mapNotNull null
            if (!seen.add(id)) return@mapNotNull null
            val size = CcSize.entries.firstOrNull { it.name == parts.getOrNull(1) }?.takeIf { it in id.sizes } ?: id.defaultSize
            CcModule(id, size)
        }
    }
}
