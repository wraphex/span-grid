package wraphex.spangrid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A host-shaped policy: light 1×1..4×3, switch 1×1..4×1, everything else 1×1..4×2. */
private val spec = GridSpec(columns = 4, maxRows = 3) { id ->
    when (id.substringBefore('.')) {
        "light" -> SpanBounds(GridSpan(1, 1), GridSpan(4, 3))
        "switch" -> SpanBounds(GridSpan(1, 1), GridSpan(4, 1))
        else -> SpanBounds(GridSpan(1, 1), GridSpan(4, 2))
    }
}

private fun GridSpec.defaultSpan(id: String): GridSpan = when (id.substringBefore('.')) {
    "light" -> GridSpan(columns, 2)
    else -> GridSpan(columns, 1)
}

private fun item(id: String, column: Int, row: Int, span: GridSpan) =
    GridItem.at(id, column, row, span)

class GridBoardTest {

    @Test
    fun `a valid table is left alone`() {
        val items = listOf(
            item("light.a", 0, 0, GridSpan(4, 2)),
            item("switch.b", 0, 2, GridSpan(4, 1)),
            item("switch.c", 0, 3, GridSpan(4, 1))
        )

        assertEquals(items, GridBoard.pack(items, spec))
        assertEquals(items, GridBoard.tidy(items, spec))
    }

    @Test
    fun `a span inside the bounds is kept, even when the handle would not offer it`() {
        // 3 columns is off the rhythm rule, i.e. un-resizable, but it is in bounds: a host declared
        // it, so nothing on the board may quietly turn it into a 4-wide tile
        val stored = item("light.a", 0, 0, GridSpan(3, 2))
        assertEquals(GridSpan(3, 2), GridBoard.pack(listOf(stored), spec).single().span)
        assertEquals(GridSpan(3, 2), GridBoard.tidy(listOf(stored), spec).single().span)
        assertEquals(
            GridSpan(3, 2),
            GridBoard.move(listOf(stored), "light.a", column = 1, row = 0, spec).single().span
        )
    }

    @Test
    fun `a span outside the bounds is pulled back axis by axis`() {
        // 6 columns and 5 rows do not fit the grid, let alone this switch
        val items = GridBoard.pack(listOf(item("switch.a", 0, 0, GridSpan(6, 5))), spec)
        assertEquals(GridSpan(4, 1), items.single().span)
    }

    @Test
    fun `a switch cannot be taller than one row`() {
        val items = GridBoard.pack(listOf(item("switch.a", 0, 0, GridSpan(4, 2))), spec)

        assertEquals(GridSpan(4, 1), items.single().span)
    }

    @Test
    fun `a row that an item cannot have falls back to the nearest allowed one`() {
        val items = GridBoard.pack(listOf(item("switch.a", 0, 0, GridSpan(4, 3))), spec)

        assertEquals(GridSpan(4, 1), items.single().span)
    }

    @Test
    fun `two items on the same cell do not overlap`() {
        val items = GridBoard.pack(
            listOf(
                item("switch.a", 0, 0, GridSpan(4, 1)),
                item("switch.b", 0, 0, GridSpan(4, 1))
            ),
            spec
        )

        assertEquals(0, items[0].row)
        assertEquals(1, items[1].row)
    }

    @Test
    fun `an item hanging off the right edge is re placed`() {
        val items = GridBoard.pack(listOf(item("switch.a", 3, 0, GridSpan(4, 1))), spec)

        assertEquals(0, items.single().column)
    }

    @Test
    fun `tidy closes the holes a shrink leaves behind`() {
        val items = listOf(
            item("switch.a", 0, 0, GridSpan(4, 1)),
            item("switch.b", 0, 3, GridSpan(4, 1)),
            item("switch.c", 0, 7, GridSpan(4, 1))
        )

        val tidied = GridBoard.tidy(items, spec)

        assertEquals(listOf(0, 1, 2), tidied.map { it.row })
        assertEquals(listOf("switch.a", "switch.b", "switch.c"), tidied.map { it.id })
    }

    @Test
    fun `tidy fills a hole instead of leaving it for a later row`() {
        val items = listOf(
            item("sensor.a", 0, 0, GridSpan(2, 1)),
            item("light.b", 0, 1, GridSpan(4, 2)),
            item("sensor.c", 0, 3, GridSpan(2, 1))
        )

        val tidied = GridBoard.tidy(items, spec)

        // c climbs into the two free columns beside a instead of staying below b
        assertEquals(listOf("sensor.a", "sensor.c", "light.b"), tidied.map { it.id })
        assertEquals(listOf(0 to 0, 2 to 0, 0 to 1), tidied.map { it.column to it.row })
    }

    @Test
    fun `tidy is idempotent`() {
        val items = listOf(
            item("sensor.a", 0, 0, GridSpan(2, 1)),
            item("light.b", 0, 1, GridSpan(4, 2)),
            item("sensor.c", 0, 3, GridSpan(2, 1))
        )

        assertEquals(GridBoard.tidy(items, spec), GridBoard.tidy(GridBoard.tidy(items, spec), spec))
    }

    @Test
    fun `tidy packs a two column item beside a full width one`() {
        val items = GridBoard.tidy(
            listOf(
                item("switch.a", 0, 5, GridSpan(4, 1)),
                item("sensor.b", 0, 9, GridSpan(2, 1))
            ),
            spec
        )

        assertEquals(listOf(0 to 0, 0 to 1), items.map { it.column to it.row })
    }

    @Test
    fun `append puts a new item in the first free rect`() {
        val existing = listOf(item("sensor.a", 0, 0, GridSpan(2, 1)))

        val withSwitch = GridBoard.append(existing, "switch.b", spec.defaultSpan("switch.b"), spec)

        assertEquals("switch.b", withSwitch.last().id)
        assertEquals(0 to 1, withSwitch.last().column to withSwitch.last().row)
    }

    @Test
    fun `two narrow items sit side by side without being re packed`() {
        val items = listOf(
            item("sensor.a", 0, 0, GridSpan(2, 1)),
            item("sensor.b", 2, 0, GridSpan(2, 1))
        )

        assertEquals(items, GridBoard.pack(items, spec))
    }

    @Test
    fun `an empty row is dropped and everything below moves up`() {
        val items = listOf(
            item("switch.a", 0, 0, GridSpan(4, 1)),
            item("switch.b", 0, 3, GridSpan(4, 1)),
            item("switch.c", 0, 7, GridSpan(4, 1))
        )

        val compacted = GridBoard.compactRows(items)

        assertEquals(listOf(0, 1, 2), compacted.map { it.row })
        assertEquals(items.map { it.id }, compacted.map { it.id })
    }

    @Test
    fun `shrinking the first item closes the row it emptied`() {
        val stored = listOf(
            item("light.a", 0, 0, GridSpan(4, 1)),
            item("switch.b", 0, 2, GridSpan(4, 1)),
            item("switch.c", 0, 3, GridSpan(4, 1))
        )

        val packed = GridBoard.pack(stored, spec)

        assertEquals(GridSpan(4, 1), packed[0].span)
        assertEquals(listOf(0, 1, 2), packed.map { it.row })
    }

    @Test
    fun `a row that still has an item is kept`() {
        val items = listOf(
            item("sensor.a", 0, 0, GridSpan(2, 1)),
            item("sensor.b", 2, 0, GridSpan(2, 1)),
            item("switch.c", 0, 2, GridSpan(4, 1))
        )

        val compacted = GridBoard.compactRows(items)

        assertEquals(listOf(0, 0, 1), compacted.map { it.row })
    }

    @Test
    fun `an item dropped below a narrow one lands there`() {
        val items = listOf(
            item("switch.a", 0, 0, GridSpan(2, 1)),
            item("switch.b", 2, 0, GridSpan(2, 1)),
            item("switch.c", 0, 1, GridSpan(4, 1))
        )

        val moved = GridBoard.move(items, "switch.b", column = 0, row = 1, spec)

        val b = moved.first { it.id == "switch.b" }
        assertEquals(0 to 1, b.column to b.row)
        // the item that was there made room underneath
        assertEquals(2, moved.first { it.id == "switch.c" }.row)
    }

    @Test
    fun `dropping an item one row down swaps it with the one below`() {
        val items = listOf(
            item("switch.a", 0, 0, GridSpan(2, 1)),
            item("switch.b", 0, 1, GridSpan(4, 1))
        )

        val moved = GridBoard.move(items, "switch.a", column = 0, row = 1, spec)

        assertEquals(listOf("switch.b", "switch.a"), moved.map { it.id })
        assertEquals(listOf(0, 1), moved.map { it.row })
    }

    @Test
    fun `dropping a tall item onto its neighbour swaps them`() {
        val items = listOf(
            item("light.a", 0, 0, GridSpan(4, 2)),
            item("light.b", 0, 2, GridSpan(4, 2))
        )

        // aimed one row down: the item is halfway over its neighbour, which is the swap
        val moved = GridBoard.move(items, "light.a", column = 0, row = 1, spec)

        assertEquals(listOf("light.b", "light.a"), moved.map { it.id })
        assertEquals(listOf(0, 2), moved.map { it.row })
    }

    @Test
    fun `dropping onto a same sized tile only trades the two slots`() {
        val items = listOf(
            item("sensor.a", 0, 0, GridSpan(4, 1)),
            item("sensor.b", 0, 1, GridSpan(4, 1)),
            item("sensor.c", 0, 2, GridSpan(4, 1))
        )

        val moved = GridBoard.move(items, "sensor.a", column = 0, row = 2, spec)

        val byId = moved.associateBy { it.id }
        assertEquals(0 to 2, byId.getValue("sensor.a").column to byId.getValue("sensor.a").row)
        assertEquals(0 to 0, byId.getValue("sensor.c").column to byId.getValue("sensor.c").row)
        // the tile in between is untouched
        assertEquals(items[1], byId.getValue("sensor.b"))
    }

    @Test
    fun `dropping onto the neighbour one row down swaps the two`() {
        val items = listOf(
            item("sensor.a", 0, 0, GridSpan(4, 1)),
            item("sensor.b", 0, 1, GridSpan(4, 1)),
            item("sensor.c", 0, 2, GridSpan(4, 1))
        )

        val moved = GridBoard.move(items, "sensor.a", column = 0, row = 1, spec)

        val byId = moved.associateBy { it.id }
        assertEquals(0 to 1, byId.getValue("sensor.a").column to byId.getValue("sensor.a").row)
        assertEquals(0 to 0, byId.getValue("sensor.b").column to byId.getValue("sensor.b").row)
        assertEquals(items[2], byId.getValue("sensor.c"))
    }

    @Test
    fun `dragging the last item onto the first trades their slots`() {
        val items = listOf(
            item("sensor.a", 0, 0, GridSpan(4, 1)),
            item("sensor.b", 0, 1, GridSpan(4, 1)),
            item("sensor.c", 0, 2, GridSpan(4, 1))
        )

        val moved = GridBoard.move(items, "sensor.c", column = 0, row = 0, spec)

        val byId = moved.associateBy { it.id }
        assertEquals(0 to 0, byId.getValue("sensor.c").column to byId.getValue("sensor.c").row)
        assertEquals(0 to 2, byId.getValue("sensor.a").column to byId.getValue("sensor.a").row)
        assertEquals(items[1], byId.getValue("sensor.b"))
    }

    @Test
    fun `a two column grid only moves the two cards that trade slots`() {
        val items = listOf(
            item("light.a", 0, 0, GridSpan(4, 2)),
            item("light.b", 0, 2, GridSpan(2, 2)),
            item("light.c", 2, 2, GridSpan(2, 2)),
            item("light.d", 0, 4, GridSpan(2, 2)),
            item("light.e", 2, 4, GridSpan(2, 2))
        )

        // d (bottom left) dragged onto b (above it, same column)
        val moved = GridBoard.move(items, "light.d", column = 0, row = 2, spec)

        val byId = moved.associateBy { it.id }
        assertEquals(0 to 2, byId.getValue("light.d").column to byId.getValue("light.d").row)
        assertEquals(0 to 4, byId.getValue("light.b").column to byId.getValue("light.b").row)
        // the whole other column never moves
        assertEquals(items.first { it.id == "light.c" }, byId.getValue("light.c"))
        assertEquals(items.first { it.id == "light.e" }, byId.getValue("light.e"))
    }

    @Test
    fun `a 2x2 dropped onto two 1x2 tiles trades the whole region`() {
        val items = listOf(
            item("light.a", 0, 0, GridSpan(2, 2)),
            item("sensor.b", 2, 0, GridSpan(1, 2)),
            item("sensor.c", 3, 0, GridSpan(1, 2))
        )

        val moved = GridBoard.move(items, "light.a", column = 2, row = 0, spec)

        val byId = moved.associateBy { it.id }
        assertEquals(2 to 0, byId.getValue("light.a").column to byId.getValue("light.a").row)
        // the two narrow tiles take the 2x2 rect that was just vacated
        assertEquals(0 to 0, byId.getValue("sensor.b").column to byId.getValue("sensor.b").row)
        assertEquals(1 to 0, byId.getValue("sensor.c").column to byId.getValue("sensor.c").row)
    }

    @Test
    fun `a tile that pokes out of the aimed rect is pushed instead of traded`() {
        val items = listOf(
            item("switch.b", 0, 0, GridSpan(4, 1)),
            item("light.a", 0, 1, GridSpan(2, 2))
        )

        // the switch is 4 wide, the aimed rect only 2: it cannot be "what was there"
        val moved = GridBoard.move(items, "light.a", column = 0, row = 0, spec)

        val byId = moved.associateBy { it.id }
        assertEquals(0 to 0, byId.getValue("light.a").column to byId.getValue("light.a").row)
        assertEquals(2, byId.getValue("switch.b").row)
    }

    @Test
    fun `a drop into a free cell leaves the others alone`() {
        val items = listOf(
            item("switch.a", 0, 0, GridSpan(2, 1)),
            item("switch.b", 2, 0, GridSpan(2, 1))
        )

        val moved = GridBoard.move(items, "switch.b", column = 0, row = 1, spec)

        assertEquals(items[0], moved.first { it.id == "switch.a" })
    }

    @Test
    fun `a drop orders the table by position`() {
        val items = listOf(
            item("switch.a", 0, 0, GridSpan(4, 1)),
            item("switch.b", 0, 1, GridSpan(4, 1))
        )

        val moved = GridBoard.move(items, "switch.b", column = 0, row = 0, spec)

        assertEquals(listOf("switch.b", "switch.a"), moved.map { it.id })
        assertEquals(listOf(0, 1), moved.map { it.row })
    }

    @Test
    fun `a drop outside the grid is clamped`() {
        val items = listOf(item("switch.a", 0, 0, GridSpan(2, 1)))

        assertEquals(2, GridBoard.move(items, "switch.a", column = 9, row = 0, spec).single().column)
        assertEquals(0, GridBoard.move(items, "switch.a", column = -3, row = -1, spec).single().row)
    }

    @Test
    fun `growing an item pushes the items below it down`() {
        val items = listOf(
            item("light.a", 0, 0, GridSpan(4, 1)),
            item("light.b", 0, 1, GridSpan(4, 2)),
            item("switch.c", 0, 3, GridSpan(4, 1))
        )

        val resized = GridBoard.resize(items, "light.a", GridSpan(4, 2), spec)

        assertEquals(listOf(0, 2, 4), resized.map { it.row })
        assertEquals(
            listOf(GridSpan(4, 2), GridSpan(4, 2), GridSpan(4, 1)),
            resized.map { it.span }
        )
    }

    @Test
    fun `shrinking pulls the items below back up`() {
        val items = listOf(
            item("light.a", 0, 0, GridSpan(4, 2)),
            item("light.b", 0, 2, GridSpan(4, 2))
        )

        val resized = GridBoard.resize(items, "light.a", GridSpan(4, 1), spec)

        assertEquals(listOf(0, 1), resized.map { it.row })
    }

    @Test
    fun `a resize keeps the list order and the columns`() {
        val items = listOf(
            item("sensor.a", 0, 0, GridSpan(2, 1)),
            item("sensor.b", 2, 0, GridSpan(2, 1)),
            item("switch.c", 0, 1, GridSpan(4, 1))
        )

        val resized = GridBoard.resize(items, "sensor.a", GridSpan(2, 2), spec)

        assertEquals(listOf("sensor.a", "sensor.b", "switch.c"), resized.map { it.id })
        assertEquals(listOf(0, 2, 0), resized.map { it.column })
        // the 2-column neighbour stays on row 0, the full-width item below gets pushed
        assertEquals(listOf(0, 0, 2), resized.map { it.row })
        assertEquals(3, resized.last().row + resized.last().rows)
    }

    @Test
    fun `resizing the last item just appends a row`() {
        val items = listOf(
            item("switch.a", 0, 0, GridSpan(4, 1)),
            item("light.b", 0, 1, GridSpan(4, 1))
        )

        val resized = GridBoard.resize(items, "light.b", GridSpan(4, 2), spec)

        assertEquals(listOf(0, 1), resized.map { it.row })
        assertEquals(GridSpan(4, 2), resized.last().span)
    }

    @Test
    fun `fits rejects overlaps and the right edge`() {
        val neighbour = item("switch.a", 0, 0, GridSpan(4, 1))

        assertFalse(GridBoard.fits(item("sensor.b", 0, 0, GridSpan(2, 1)), listOf(neighbour), 4))
        assertTrue(GridBoard.fits(item("sensor.b", 0, 1, GridSpan(2, 1)), listOf(neighbour), 4))
        assertFalse(GridBoard.fits(item("sensor.b", 3, 1, GridSpan(2, 1)), listOf(neighbour), 4))
        assertTrue(GridBoard.fits(item("sensor.b", 2, 1, GridSpan(2, 1)), listOf(neighbour), 4))
    }

    @Test
    fun `an item does not collide with itself when validated`() {
        val moving = item("switch.a", 0, 0, GridSpan(4, 1))
        val below = item("switch.b", 0, 1, GridSpan(4, 1))

        assertTrue(GridBoard.fits(moving, listOf(moving, below), 4))
    }

    @Test
    fun `allowed spans come from bounds plus the rhythm rule`() {
        val light = allowedSpans(spec.boundsOf("light.a"), spec.columns, spec.maxRows)
        assertEquals(
            setOf(
                GridSpan(1, 1),
                GridSpan(2, 1), GridSpan(2, 2), GridSpan(2, 3),
                GridSpan(4, 1), GridSpan(4, 2), GridSpan(4, 3)
            ),
            light.toSet()
        )

        val switch = allowedSpans(spec.boundsOf("switch.a"), spec.columns, spec.maxRows)
        assertEquals(setOf(GridSpan(1, 1), GridSpan(2, 1), GridSpan(4, 1)), switch.toSet())

        val plain = allowedSpans(spec.boundsOf("sensor.a"), spec.columns, spec.maxRows)
        assertTrue(plain.none { it.columns == 1 && it.rows > 1 })
        assertTrue(plain.none { it.columns == 3 })
    }

    @Test
    fun `clamp picks the nearest allowed span`() {
        assertEquals(
            GridSpan(4, 2),
            GridSpan(3, 2).clampTo(spec.boundsOf("light.a"), spec.columns, spec.maxRows)
        )
        // a switch may not be 2 rows: (2,1) is one step away, (1,1) and (4,1) are further
        assertEquals(
            GridSpan(2, 1),
            GridSpan(2, 2).clampTo(spec.boundsOf("switch.a"), spec.columns, spec.maxRows)
        )
        assertEquals(
            GridSpan(1, 1),
            GridSpan(1, 3).clampTo(spec.boundsOf("switch.a"), spec.columns, spec.maxRows)
        )
    }
}
