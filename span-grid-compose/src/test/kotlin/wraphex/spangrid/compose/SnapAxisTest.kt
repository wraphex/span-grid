package wraphex.spangrid.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapAxisTest {

    @Test
    fun `an axis snaps only when the target is close`() {
        val far = snapAxis(free = 200f, target = 100f, wasSnapped = false, 16f, 28f)
        assertEquals(200f, far.value, 0.01f)
        assertEquals(false, far.snapped)

        val near = snapAxis(free = 110f, target = 100f, wasSnapped = false, 16f, 28f)
        assertEquals(100f, near.value, 0.01f)
        assertEquals(true, near.snapped)
    }

    @Test
    fun `a snapped axis keeps holding until it is pulled far enough`() {
        // still holding inside the wider release radius
        assertEquals(100f, snapAxis(120f, 100f, true, 16f, 28f).value, 0.01f)
        // released past it
        val released = snapAxis(140f, 100f, true, 16f, 28f)
        assertEquals(140f, released.value, 0.01f)
        assertEquals(false, released.snapped)
    }
}
