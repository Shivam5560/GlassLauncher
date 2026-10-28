package com.shivam.glasslauncher.widgets

import kotlin.math.abs

/** A block of grid cells: column, row, width and height in cells. */
data class GridRect(val col: Int, val row: Int, val w: Int, val h: Int) {
    val right: Int get() = col + w
    val bottom: Int get() = row + h

    fun overlaps(o: GridRect): Boolean = col < o.right && o.col < right && row < o.bottom && o.row < bottom

    fun contains(c: Int, r: Int): Boolean = c in col until right && r in row until bottom

    fun fits(cols: Int, rows: Int): Boolean = col >= 0 && row >= 0 && w >= 1 && h >= 1 && right <= cols && bottom <= rows

    /** Cell indices (row-major) covered by this rect on a grid [cols] wide. */
    fun cells(cols: Int): List<Int> = buildList {
        for (r in row until bottom) for (c in col until right) add(r * cols + c)
    }
}

/**
 * Pure placement maths for the free-placement home grid: widgets own rectangles of cells and must
 * not overlap each other; icons fill the remaining cells and reflow around widgets (see
 * `LayoutOps` for the icon side). Free of Android types so it is unit-tested.
 */
object GridPlacement {

    /** Clamps [rect] so it lies inside a [cols] x [rows] grid, shrinking it if it is too big. */
    fun clamp(rect: GridRect, cols: Int, rows: Int): GridRect {
        val w = rect.w.coerceIn(1, cols)
        val h = rect.h.coerceIn(1, rows)
        return GridRect(rect.col.coerceIn(0, cols - w), rect.row.coerceIn(0, rows - h), w, h)
    }

    fun collides(rect: GridRect, others: Collection<GridRect>): Boolean = others.any { it.overlaps(rect) }

    fun canPlace(rect: GridRect, others: Collection<GridRect>, cols: Int, rows: Int): Boolean =
        rect.fits(cols, rows) && !collides(rect, others)

    /** Row-major mask of the cells covered by [rects]. */
    fun blockedMask(rects: Collection<GridRect>, cols: Int, rows: Int): BooleanArray {
        val mask = BooleanArray(cols * rows)
        for (r in rects) for (i in r.cells(cols)) if (i in mask.indices) mask[i] = true
        return mask
    }

    /**
     * First free spot (reading order) for a [w] x [h] widget that avoids [others], preferring spots
     * that cover the fewest [occupied] icon cells so as few icons as possible have to move.
     */
    fun firstFree(w: Int, h: Int, others: Collection<GridRect>, cols: Int, rows: Int, occupied: BooleanArray? = null): GridRect? {
        var best: GridRect? = null
        var bestCost = Int.MAX_VALUE
        for (r in 0..rows - h) for (c in 0..cols - w) {
            val rect = GridRect(c, r, w, h)
            if (collides(rect, others)) continue
            val cost = if (occupied == null) 0 else rect.cells(cols).count { occupied.getOrElse(it) { false } }
            if (cost < bestCost) {
                best = rect
                bestCost = cost
                if (cost == 0) return best
            }
        }
        return best
    }

    /**
     * The free position for [rect] nearest to where it was requested (Manhattan distance, ties in
     * reading order), or null when no position on the grid fits.
     */
    fun nearestFree(rect: GridRect, others: Collection<GridRect>, cols: Int, rows: Int): GridRect? {
        val wanted = clamp(rect, cols, rows)
        if (!collides(wanted, others)) return wanted
        var best: GridRect? = null
        var bestDist = Int.MAX_VALUE
        for (r in 0..rows - wanted.h) for (c in 0..cols - wanted.w) {
            val candidate = GridRect(c, r, wanted.w, wanted.h)
            if (collides(candidate, others)) continue
            val d = abs(c - wanted.col) + abs(r - wanted.row)
            if (d < bestDist) {
                best = candidate
                bestDist = d
            }
        }
        return best
    }

    /**
     * Snaps a widget being dragged: the top-left cell nearest to the dragged position
     * ([xCells], [yCells] in fractional cells) within the grid.
     */
    fun snap(xCells: Float, yCells: Float, w: Int, h: Int, cols: Int, rows: Int): GridRect {
        val c = Math.round(xCells).coerceIn(0, (cols - w).coerceAtLeast(0))
        val r = Math.round(yCells).coerceIn(0, (rows - h).coerceAtLeast(0))
        return GridRect(c, r, w.coerceAtMost(cols), h.coerceAtMost(rows))
    }

    /**
     * Resizing with a handle: the new span from fractional cell deltas, limited to [minW]..[maxW]
     * and [minH]..[maxH], the grid edge, and (when [allowed] is not null) snapped to the nearest
     * allowed span, e.g. the iOS sizes 2x2, 4x2 and 4x4.
     */
    fun resize(
        rect: GridRect,
        dwCells: Float,
        dhCells: Float,
        cols: Int,
        rows: Int,
        minW: Int = 1,
        minH: Int = 1,
        maxW: Int = cols,
        maxH: Int = rows,
        allowed: List<Pair<Int, Int>>? = null,
    ): GridRect {
        val limitW = minOf(maxW, cols - rect.col).coerceAtLeast(1)
        val limitH = minOf(maxH, rows - rect.row).coerceAtLeast(1)
        val rawW = rect.w + dwCells
        val rawH = rect.h + dhCells
        if (allowed != null) {
            val fitting = allowed.filter { (w, h) -> w <= limitW && h <= limitH }
            val pick = fitting.minByOrNull { (w, h) -> (w - rawW) * (w - rawW) + (h - rawH) * (h - rawH) }
                ?: return rect
            return rect.copy(w = pick.first, h = pick.second)
        }
        val w = Math.round(rawW).coerceIn(minW.coerceAtMost(limitW), limitW)
        val h = Math.round(rawH).coerceIn(minH.coerceAtMost(limitH), limitH)
        return rect.copy(w = w, h = h)
    }
}
