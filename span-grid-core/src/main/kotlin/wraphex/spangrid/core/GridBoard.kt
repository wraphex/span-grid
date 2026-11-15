package wraphex.spangrid.core

/**
 * Every span the resize handle may snap to, derived from [bounds] — never hand-listed.
 *
 * The rhythm rule is the only global policy: a tile is 1 column, 2 columns or full width.
 * 3 columns leaves a 1-cell hole and a narrow text column, and 1×2 is a narrow, tall box with
 * nothing to show — so neither is offered. A host that really wants one declares it in the stored
 * table: the board keeps any span inside its [SpanBounds], the handle just never offers it.
 */
fun allowedSpans(
    bounds: SpanBounds,
    gridColumns: Int,
    maxRows: Int
): List<GridSpan> = buildList {
    for (columns in 1..gridColumns) {
        for (rows in 1..maxRows) {
            val span = GridSpan(columns, rows)
            if (span.isWithin(bounds) && span.fitsRhythm(gridColumns)) add(span)
        }
    }
}

/**
 * The allowed span closest to this one (sum of axis distances, larger area wins a tie).
 *
 * What the handle lands on while resizing, so the result is always a member of [allowedSpans]. A
 * stored table is *not* repaired with this: see [coerceToBounds].
 */
fun GridSpan.clampTo(
    bounds: SpanBounds,
    gridColumns: Int,
    maxRows: Int
): GridSpan {
    val allowed = allowedSpans(bounds, gridColumns, maxRows)
    require(allowed.isNotEmpty()) { "no allowed span for $bounds on a $gridColumns column grid" }
    return allowed.minWith(
        compareBy<GridSpan> { it.distanceTo(this) }
            .thenByDescending { it.columns * it.rows }
    )
}

/**
 * The same span pulled inside [bounds] and the grid's limits, axis by axis — what the board applies
 * to a span a host declares.
 *
 * Deliberately not [clampTo]: the rhythm rule is a policy for what the *handle* offers, not for what
 * a table may contain, so a host-declared 3-wide tile survives a drag unchanged.
 */
fun GridSpan.coerceToBounds(bounds: SpanBounds, gridColumns: Int, maxRows: Int): GridSpan = GridSpan(
    columns = columns.coerceIn(bounds.min.columns, maxOf(bounds.min.columns, minOf(bounds.max.columns, gridColumns))),
    rows = rows.coerceIn(bounds.min.rows, maxOf(bounds.min.rows, minOf(bounds.max.rows, maxRows)))
)

/**
 * Placement rules for a board of fixed-column, variable-height items.
 *
 * Pure functions: no Android, no Compose, no persistence. A host keeps its own item type and maps
 * to [GridItem]; the tests here are the proof that a resize cannot overlap a neighbour and that a
 * drop lands where it was aimed.
 */
object GridBoard {

    /**
     * Validate a stored table: clamp every span to its bounds, keep an item where it is if that
     * rect is still free, otherwise re-place it in the first free rect that fits. Empty rows are
     * dropped.
     *
     * A table that is already valid comes out unchanged, so loading a board does not reshuffle it.
     */
    fun pack(items: List<GridItem>, spec: GridSpec): List<GridItem> =
        keepPlaced(items, spec)

    /**
     * Compact the board: every item goes to the **first rect that still fits**, in reading order.
     *
     * First-fit rather than flow: flow honours the list order but never looks back, so a narrow item
     * coming after a full-width one could never fill the gap beside it. Here the gaps *are* the
     * point — "tidy up" means "leave no hole that something could sit in".
     */
    fun tidy(items: List<GridItem>, spec: GridSpec): List<GridItem> {
        val occupied = ArrayList<CellRect>(items.size)
        val placed = ArrayList<GridItem>(items.size)
        for (item in items.sortedWith(ReadingOrder)) {
            val span = item.span.coerceToBounds(spec.boundsOf(item.id), spec.columns, spec.maxRows)
            val (column, row) = firstFree(occupied, span, spec.columns)
            occupied += CellRect(column, row, span)
            placed += item.copy(
                column = column,
                row = row,
                columns = span.columns,
                rows = span.rows
            )
        }
        return placed.sortedWith(ReadingOrder)
    }

    /**
     * Drop one item at a cell, i.e. what a body drag does.
     *
     * - the aimed rect is covered by tiles that fit back into the rect this item vacates → the two
     *   **regions trade places** (one card for one card, a 2×2 for the two 1×2 that filled it, …);
     *   every other tile stays exactly where it is;
     * - the aimed rect is free → it lands there and nothing else moves;
     * - anything else is in the way → it takes the spot and that item is pushed down.
     *
     * Swap rather than insertion on purpose: on a narrow board an insertion drags the tiles of the
     * neighbouring column along.
     */
    fun move(items: List<GridItem>, id: String, column: Int, row: Int, spec: GridSpec): List<GridItem> {
        val target = items.firstOrNull { it.id == id } ?: return items
        val span = target.span.coerceToBounds(spec.boundsOf(id), spec.columns, spec.maxRows)
        val moved = target.copy(
            column = column.coerceIn(0, (spec.columns - span.columns).coerceAtLeast(0)),
            row = row.coerceAtLeast(0),
            columns = span.columns,
            rows = span.rows
        )
        val rest = items.filter { it.id != id }
        val overlapped = rest.filter { it.rect.overlaps(moved.rect) }

        // one tile of the same size: the half-overlap case, i.e. dragging a card one row down
        val single = overlapped.singleOrNull()?.takeIf { it.span == span }
        if (single != null) {
            val settled = rest.map { item ->
                if (item.id == single.id) {
                    item.copy(column = target.column, row = target.row)
                } else {
                    item
                }
            }
            return (settled + moved.copy(column = single.column, row = single.row))
                .sortedWith(ReadingOrder)
        }

        val aimedArea = moved.columns * moved.rows
        val exactlyCovered = overlapped.isNotEmpty() &&
            overlapped.all { moved.rect.contains(it.rect) } &&
            overlapped.sumOf { it.columns * it.rows } == aimedArea
        if (exactlyCovered) {
            // the tiles that were there take the rect this one just left, if they fit
            val repacked = packInto(target.rect, overlapped)?.associateBy { it.id }
            if (repacked != null) {
                val settled = rest.map { repacked[it.id] ?: it }
                return (settled + moved).sortedWith(ReadingOrder)
            }
        }

        if (overlapped.isEmpty()) {
            return compactRows(rest + moved).sortedWith(ReadingOrder)
        }

        val placed = ArrayList<GridItem>(items.size)
        placed += moved
        for (item in compactRows(rest.sortedWith(ReadingOrder))) placed += pushBelow(item, placed)
        return compactRows(placed).sortedWith(ReadingOrder)
    }

    /**
     * Resize one item and **push whatever it now overlaps downwards**, instead of rejecting it.
     *
     * A packed board has no free row to grow into, and "you cannot grow" is a worse answer than
     * "the rows below move": columns never change, nothing overlaps, and the height grows by
     * exactly what the drag asked for.
     */
    fun resize(items: List<GridItem>, id: String, span: GridSpan, spec: GridSpec): List<GridItem> {
        val target = items.firstOrNull { it.id == id } ?: return items
        val clamped = span.coerceToBounds(spec.boundsOf(id), spec.columns, spec.maxRows)
        val resized = target.copy(
            column = target.column.coerceIn(0, (spec.columns - clamped.columns).coerceAtLeast(0)),
            columns = clamped.columns,
            rows = clamped.rows
        )
        // the dragged item wins, so it is placed first and everything else yields to it
        val placed = ArrayList<GridItem>(items.size)
        placed += resized
        for (item in items) {
            if (item.id == id) continue
            placed += pushBelow(item, placed)
        }
        val byId = placed.associateBy { it.id }
        return compactRows(items.map { byId.getValue(it.id) })
    }

    /**
     * Drops rows that hold nothing and pulls everything below them up.
     *
     * A hole inside a row is legitimate (a 2-column item leaves room for a 2-column neighbour);
     * an empty *row* is only ever a leftover, so it is never kept.
     */
    fun compactRows(items: List<GridItem>): List<GridItem> {
        if (items.isEmpty()) return items
        val occupiedRows = items.flatMapTo(sortedSetOf()) { item -> item.row until item.row + item.rows }
        val shift = HashMap<Int, Int>(occupiedRows.size)
        occupiedRows.forEachIndexed { index, row -> shift[row] = index }
        return items.map { item ->
            val row = shift.getValue(item.row)
            if (row == item.row) item else item.copy(row = row)
        }
    }

    /** Add one item at [defaultSpan] in the first rect that fits. */
    fun append(
        items: List<GridItem>,
        id: String,
        defaultSpan: GridSpan,
        spec: GridSpec
    ): List<GridItem> {
        val span = defaultSpan.coerceToBounds(spec.boundsOf(id), spec.columns, spec.maxRows)
        val added = GridItem.at(id, column = 0, row = 0, span = span)
        return keepPlaced(items + added, spec)
    }

    /**
     * Insert [item] at its rect and push whatever it overlaps downwards.
     *
     * The difference to [move] is intent: a drop asks "where can this go", an insert says "put it
     * here, make room". That is what "add a tile below this one" needs — a full-width tile has no
     * free row to drop into on a packed board, so searching for one sends it to the very end.
     */
    fun insert(items: List<GridItem>, item: GridItem, spec: GridSpec): List<GridItem> {
        val span = item.span.coerceToBounds(spec.boundsOf(item.id), spec.columns, spec.maxRows)
        val added = item.copy(
            column = item.column.coerceIn(0, (spec.columns - span.columns).coerceAtLeast(0)),
            row = item.row.coerceAtLeast(0),
            columns = span.columns,
            rows = span.rows
        )
        val placed = ArrayList<GridItem>(items.size + 1)
        placed += added
        for (other in items.sortedWith(ReadingOrder)) {
            if (other.id == added.id) continue
            placed += pushBelow(other, placed)
        }
        return compactRows(placed).sortedWith(ReadingOrder)
    }

    /** Whether [candidate] sits inside the grid and touches none of [others]. */
    fun fits(candidate: GridItem, others: List<GridItem>, columns: Int): Boolean {
        val rect = candidate.rect
        if (rect.column < 0 || rect.row < 0) return false
        if (rect.column + candidate.columns > columns) return false
        return others.none { it.id != candidate.id && it.rect.overlaps(rect) }
    }

    private fun keepPlaced(items: List<GridItem>, spec: GridSpec): List<GridItem> {
        val placed = ArrayList<GridItem>(items.size)
        val occupied = ArrayList<CellRect>(items.size)
        for (item in items) {
            val span = item.span.coerceToBounds(spec.boundsOf(item.id), spec.columns, spec.maxRows)
            val stored = CellRect(item.column, item.row, span)
            val keep = stored.column >= 0 &&
                stored.column + span.columns <= spec.columns &&
                stored.row >= 0 &&
                occupied.none { it.overlaps(stored) }
            val next = if (keep) {
                item.copy(columns = span.columns, rows = span.rows)
            } else {
                val (column, row) = firstFree(occupied, span, spec.columns)
                item.copy(column = column, row = row, columns = span.columns, rows = span.rows)
            }
            occupied += CellRect(next.column, next.row, span)
            placed += next
        }
        return compactRows(placed)
    }

    /**
     * Packs [tiles] into [area] by first fit, or null when they do not fit.
     *
     * Used by the region swap: the tiles that were dropped on have to be able to live in the rect
     * the dragged one left behind, otherwise the drop would leave a hole or an overlap.
     */
    private fun packInto(area: CellRect, tiles: List<GridItem>): List<GridItem>? {
        val placed = ArrayList<GridItem>(tiles.size)
        val occupied = ArrayList<CellRect>(tiles.size)
        for (tile in tiles) {
            var spot: Pair<Int, Int>? = null
            for (row in area.row..area.bottom - tile.rows) {
                for (column in area.column..area.right - tile.columns) {
                    val candidate = CellRect(column, row, tile.span)
                    if (occupied.none { it.overlaps(candidate) }) {
                        spot = column to row
                        break
                    }
                }
                if (spot != null) break
            }
            val at = spot ?: return null
            occupied += CellRect(at.first, at.second, tile.span)
            placed += tile.copy(column = at.first, row = at.second)
        }
        return placed
    }

    /** Keeps [item]'s column, moves it down until it clears every item already placed. */
    private fun pushBelow(item: GridItem, placed: List<GridItem>): GridItem {
        var row = item.row
        while (true) {
            val rect = CellRect(item.column, row, item.span)
            val clash = placed
                .filter { it.rect.overlaps(rect) }
                .maxByOrNull { it.row + it.rows }
                ?: return item.copy(row = row)
            row = clash.row + clash.rows
        }
    }

    /** Row by row from the top; the row below the last occupied cell is always free. */
    private fun firstFree(
        occupied: List<CellRect>,
        span: GridSpan,
        gridColumns: Int
    ): Pair<Int, Int> {
        val lastRow = occupied.maxOfOrNull { it.row + it.span.rows } ?: 0
        for (row in 0..lastRow) {
            for (column in 0..gridColumns - span.columns) {
                val candidate = CellRect(column, row, span)
                if (occupied.none { it.overlaps(candidate) }) return column to row
            }
        }
        return 0 to lastRow
    }
}

private val GridItem.rect: CellRect get() = CellRect(column, row, span)

/** Reading order: top to bottom, then left to right. */
private val ReadingOrder = compareBy<GridItem>({ it.row }, { it.column })

private data class CellRect(val column: Int, val row: Int, val span: GridSpan) {

    val right: Int get() = column + span.columns
    val bottom: Int get() = row + span.rows

    /** True when [other] lies completely inside this rect. */
    fun contains(other: CellRect): Boolean =
        other.column >= column && other.right <= right &&
            other.row >= row && other.bottom <= bottom

    fun overlaps(other: CellRect): Boolean =
        column < other.column + other.span.columns &&
            other.column < column + span.columns &&
            row < other.row + other.span.rows &&
            other.row < row + span.rows
}

private fun GridSpan.isWithin(bounds: SpanBounds): Boolean =
    columns >= bounds.min.columns && columns <= bounds.max.columns &&
        rows >= bounds.min.rows && rows <= bounds.max.rows

private fun GridSpan.fitsRhythm(gridColumns: Int): Boolean = when (columns) {
    gridColumns -> true
    1 -> rows == 1
    2 -> true
    else -> false
}

private fun GridSpan.distanceTo(other: GridSpan): Int =
    kotlin.math.abs(columns - other.columns) + kotlin.math.abs(rows - other.rows)
