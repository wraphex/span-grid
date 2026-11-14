package wraphex.spangrid.compose

/** One axis after magnetic snapping. */
internal data class SnapResult(val value: Float, val snapped: Boolean)

/**
 * Magnetism for one axis: the shell follows the finger until the landing position is within
 * [enterRadius], then it is pinned to it. It releases again past [exitRadius], which is larger so a
 * hand tremor on the boundary cannot make it flicker in and out.
 */
internal fun snapAxis(
    free: Float,
    target: Float,
    wasSnapped: Boolean,
    enterRadius: Float,
    exitRadius: Float
): SnapResult {
    val distance = kotlin.math.abs(free - target)
    val snapped = if (wasSnapped) distance <= exitRadius else distance <= enterRadius
    return SnapResult(value = if (snapped) target else free, snapped = snapped)
}
