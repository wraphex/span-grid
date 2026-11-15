package wraphex.spangrid.compose

import org.junit.Assert.assertEquals
import org.junit.Test
import wraphex.spangrid.core.GridSpan

/**
 * The geometry the layout and the drag handling both read: a disagreement here is a drop that lands
 * next to the finger.
 */
class GridMetricsTest {

    private val gap = 8
    private val minCell = 72
    private val maxCell = 112

    /** 360 dp phone, 16 dp of screen padding on each side, 1 dp = 1 px. */
    private fun phone() = gridMetrics(availableWidthPx = 328, gapPx = gap, minCellPx = minCell, maxCellPx = maxCell)

    @Test
    fun `a phone gets square 76 dp cells`() {
        val metrics = phone()
        assertEquals(76, metrics.cellWidthPx)
        assertEquals(76, metrics.cellHeightPx)
        assertEquals(84, metrics.columnStep())
        assertEquals(84, metrics.rowStep())
    }

    @Test
    fun `cells and gaps add up`() {
        val metrics = phone()
        assertEquals(0, metrics.widthOf(GridSpan(0, 1)))
        assertEquals(76, metrics.widthOf(GridSpan(1, 1)))
        assertEquals(160, metrics.widthOf(GridSpan(2, 1)))
        assertEquals(328, metrics.widthOf(GridSpan(4, 1)))
        assertEquals(160, metrics.heightOf(GridSpan(1, 2)))
        assertEquals(328, metrics.heightOf(GridSpan(1, 4)))
        assertEquals(84, metrics.xOf(1))
        assertEquals(84, metrics.yOf(1))
    }

    @Test
    fun `a wide screen stops growing rows but not columns`() {
        val tablet = gridMetrics(availableWidthPx = 1000, gapPx = gap, minCellPx = minCell, maxCellPx = maxCell)
        assertEquals(244, tablet.cellWidthPx)
        // clamped: a row never becomes a billboard
        assertEquals(maxCell, tablet.cellHeightPx)
    }

    @Test
    fun `a narrow screen stops shrinking rows at the floor`() {
        val narrow = gridMetrics(availableWidthPx = 200, gapPx = gap, minCellPx = minCell, maxCellPx = maxCell)
        assertEquals(44, narrow.cellWidthPx)
        assertEquals(minCell, narrow.cellHeightPx)
    }
}
