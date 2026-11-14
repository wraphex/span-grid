package wraphex.spangrid.core

/** A size in cells. */
data class GridSpan(val columns: Int, val rows: Int)

/** What one item may become: the resize handle snaps inside this, the pack pass clamps to it. */
data class SpanBounds(val min: GridSpan, val max: GridSpan)

/**
 * One item on a grid: which, where, and how big.
 *
 * Absolute cell coordinates, like a home screen — not a list index. The board functions return a
 * new list; an item is never mutated.
 */
data class GridItem(
    val id: String,
    val column: Int,
    val row: Int,
    val columns: Int,
    val rows: Int
) {
    val span: GridSpan get() = GridSpan(columns, rows)

    companion object {
        fun at(id: String, column: Int, row: Int, span: GridSpan) = GridItem(
            id = id,
            column = column,
            row = row,
            columns = span.columns,
            rows = span.rows
        )
    }
}

/**
 * The grid shape plus the one policy the board cannot know: how large a given item is allowed to
 * get. [boundsOf] is called for every clamp, so a host can decide per item type.
 */
data class GridSpec(
    val columns: Int,
    val maxRows: Int,
    val boundsOf: (id: String) -> SpanBounds
)
