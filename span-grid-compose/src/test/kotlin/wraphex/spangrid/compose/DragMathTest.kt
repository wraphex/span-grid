package wraphex.spangrid.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class DragMathTest {

    // a 76 dp cell with an 8 dp gap at 1x: one step is 84 px
    private val step = 84
    private val gap = 8
    private val pad = 8f

    @Test
    fun `a cell flips at half a step`() {
        assertEquals(1, dropIndex(free = 1.4f * step, step = step, maxIndex = 10))
        assertEquals(2, dropIndex(free = 1.6f * step, step = step, maxIndex = 10))
        // exactly halfway is the far cell: the eye reads "over the neighbour" from there on
        assertEquals(1, dropIndex(free = 1.5f * step - 1f, step = step, maxIndex = 10))
        assertEquals(2, dropIndex(free = 1.5f * step, step = step, maxIndex = 10))
    }

    @Test
    fun `a drop never leaves the board`() {
        // dragged left of the board, or past the last column a two-column item still fits in
        assertEquals(0, dropIndex(free = -200f, step = step, maxIndex = 2))
        assertEquals(2, dropIndex(free = 4000f, step = step, maxIndex = 2))
    }

    @Test
    fun `an item wider than the board drops at the first column`() {
        assertEquals(0, dropIndex(free = 500f, step = step, maxIndex = 4 - 4))
        assertEquals(0, dropIndex(free = 500f, step = step, maxIndex = -3))
    }

    @Test
    fun `a row index is measured below the board's top padding`() {
        assertEquals(0, dropIndex(free = pad, step = step, origin = pad, maxIndex = 99))
        assertEquals(1, dropIndex(free = pad + step, step = step, origin = pad, maxIndex = 99))
        // above the padding is still row 0, not a negative row
        assertEquals(0, dropIndex(free = 0f, step = step, origin = pad, maxIndex = 99))
    }

    @Test
    fun `an edge counts whole cells, gap included`() {
        // one 76 dp cell plus the 8 dp gap it trails is exactly one step
        assertEquals(1, cellsForEdge(edgePx = 1f * step - gap, step = step, gap = gap))
        assertEquals(2, cellsForEdge(edgePx = 2f * step - gap, step = step, gap = gap))
        assertEquals(3, cellsForEdge(edgePx = 3f * step - gap, step = step, gap = gap))
        // an edge just short of the next cell stays on this one
        assertEquals(2, cellsForEdge(edgePx = 2f * step - gap - 2f, step = step, gap = gap))
    }

    @Test
    fun `nothing scrolls in the middle of the board`() {
        assertEquals(
            0f,
            autoScrollDelta(
                targetTop = 500f, targetBottom = 700f, scrollOffset = 0,
                viewportHeight = 2000, edge = 72f, step = 16f
            ),
            0.01f
        )
    }

    @Test
    fun `a target in the bottom band scrolls down by at most one step`() {
        // target bottom 1950, view bottom 2000, band 72: 1950 is inside it
        assertEquals(
            22f,
            autoScrollDelta(
                targetTop = 1700f, targetBottom = 1950f, scrollOffset = 0,
                viewportHeight = 2000, edge = 72f, step = 100f
            ),
            0.01f
        )
        // deep inside the band the raw overshoot is 62 px, more than this step: it is capped
        assertEquals(
            50f,
            autoScrollDelta(
                targetTop = 1900f, targetBottom = 1990f, scrollOffset = 0,
                viewportHeight = 2000, edge = 72f, step = 50f
            ),
            0.01f
        )
    }

    @Test
    fun `a target in the top band scrolls up by at most one step`() {
        assertEquals(
            -16f,
            autoScrollDelta(
                targetTop = 60f, targetBottom = 200f, scrollOffset = 100,
                viewportHeight = 2000, edge = 72f, step = 16f
            ),
            0.01f
        )
        // dragged far above the viewport: still one step, never a jump
        assertEquals(
            -16f,
            autoScrollDelta(
                targetTop = -500f, targetBottom = 0f, scrollOffset = 100,
                viewportHeight = 2000, edge = 72f, step = 16f
            ),
            0.01f
        )
    }

    @Test
    fun `an unmeasured viewport does not scroll`() {
        assertEquals(
            0f,
            autoScrollDelta(
                targetTop = 0f, targetBottom = 10f, scrollOffset = 0,
                viewportHeight = 0, edge = 72f, step = 16f
            ),
            0.01f
        )
    }
}
