package wraphex.spangrid.compose

import kotlin.math.roundToInt

/**
 * The arithmetic behind a drag: which cell it lands on, how many cells the handle spans, how fast
 * the board scrolls at its edge.
 *
 * Kept out of [GridDragState] so it can be tested with plain numbers — no `Density`, no Compose, no
 * gesture. Callers convert dp to pixels first.
 */

/**
 * The index [free] snaps to, given the cell [step] and the coordinate [origin] of index 0.
 *
 * It flips at **half a cell**, so an item is already "between the two" when it is halfway over its
 * neighbour. [maxIndex] is the last index that still fits (columns minus span); a negative value
 * means nothing fits and 0 is the answer.
 */
internal fun dropIndex(free: Float, step: Int, origin: Float = 0f, maxIndex: Int): Int =
    ((free - origin) / step).roundToInt().coerceIn(0, maxIndex.coerceAtLeast(0))

/**
 * How many cells an edge of [edgePx] covers. `n` cells are `n * step - gap`, so the trailing gap is
 * added back before dividing — otherwise every edge reads one cell short near its far end.
 */
internal fun cellsForEdge(edgePx: Float, step: Int, gap: Int): Int =
    ((edgePx + gap) / step).roundToInt()

/**
 * How far the board scrolls for a drag whose target sits inside the [edge] band at either end of the
 * viewport: at most one [step] per frame, 0 in the dead zone.
 *
 * The overshoot past the band is what makes a fast drag scroll faster, and the cap is what keeps it
 * from jumping a whole row per frame.
 */
internal fun autoScrollDelta(
    targetTop: Float,
    targetBottom: Float,
    scrollOffset: Int,
    viewportHeight: Int,
    edge: Float,
    step: Float
): Float {
    if (viewportHeight <= 0) return 0f
    val viewBottom = scrollOffset + viewportHeight
    return when {
        targetBottom > viewBottom - edge ->
            minOf(targetBottom - (viewBottom - edge), step)
        targetTop < scrollOffset + edge ->
            maxOf(targetTop - (scrollOffset + edge), -step)
        else -> 0f
    }
}
