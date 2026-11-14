package wraphex.spangrid.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import wraphex.spangrid.core.GridSpan

/** Geometry of a board: cell size bounds, gaps and the edit-mode handle. */
object GridTokens {
    /**
     * The default column count, and the number `span-grid-core`'s span rhythm assumes.
     *
     * A host may pass a different one to [WidgetGrid], but it has to pass the same number to
     * `GridSpec` — the board draws cells, the rules count them, and nothing reconciles the two.
     */
    const val Columns = 4

    val Gap = 8.dp
    val ScreenPadding = 16.dp
    val CellMinHeight = 72.dp
    val CellMaxHeight = 112.dp

    /** Space kept free at the top and bottom of the scroll container, so nothing hugs the bar. */
    val ContentVerticalPadding = 8.dp

    /** Edit-mode resize handle: 28 dp drawn, 48 dp touch target (bottom-right corner). */
    val HandleTouchSize = 48.dp

    /** Edit-mode corner action (delete / replace / add): 36 dp squares, three corners. */
    val ActionSize = 36.dp

    /** Gap between an action button and the tile edge. */
    val ActionInset = 6.dp
}

/** Where one child sits: absolute cell coordinates, same shape as the stored rect. */
data class GridPlacement(val column: Int, val row: Int, val span: GridSpan)

/** Pixel geometry of one grid instance; the layout and the drag handling must agree on it. */
class GridMetrics(
    val columns: Int,
    val cellWidthPx: Int,
    val cellHeightPx: Int,
    val gapPx: Int
) {
    fun xOf(column: Int): Int = column * (cellWidthPx + gapPx)
    fun yOf(row: Int): Int = row * (cellHeightPx + gapPx)
    fun widthOf(span: GridSpan): Int =
        if (span.columns <= 0) 0 else span.columns * cellWidthPx + (span.columns - 1) * gapPx

    /** Height of [rows] cells including the gaps between them (0 rows = 0). */
    fun heightOf(rows: Int): Int =
        if (rows <= 0) 0 else rows * cellHeightPx + (rows - 1) * gapPx

    fun heightOf(span: GridSpan): Int = heightOf(span.rows)

    /** Size one cell plus its trailing gap — a span of n cells is `n * step - gap`. */
    fun columnStep(): Int = cellWidthPx + gapPx

    fun rowStep(): Int = cellHeightPx + gapPx
}

/**
 * Square-ish cells: `cellW = (W - (n - 1) * gap) / n`, `cellH = cellW` clamped to 72..112 dp.
 *
 * [availableWidthPx] is the grid width, i.e. the screen minus its horizontal padding.
 */
fun gridMetrics(
    availableWidthPx: Int,
    gapPx: Int,
    minCellPx: Int,
    maxCellPx: Int,
    columns: Int = GridTokens.Columns
): GridMetrics {
    val cellWidth = (availableWidthPx - gapPx * (columns - 1)) / columns
    return GridMetrics(
        columns = columns,
        cellWidthPx = cellWidth,
        cellHeightPx = cellWidth.coerceIn(minCellPx, maxCellPx),
        gapPx = gapPx
    )
}

/**
 * Places children at absolute cells. `LazyVerticalGrid` cannot do row spans and the staggered
 * one only has full-row spans, so this is hand-written — which is what a launcher does anyway.
 *
 * Children are measured with fixed constraints (that is why every tile has to ellipsize), and
 * [reservedRows] pins the height during an edit-mode drag so growing a preview cannot move the
 * scroll offset under the finger.
 *
 * [columns] must equal the `GridSpec.columns` the host feeds to `GridBoard`: this layout places
 * cells and knows nothing about the rules that counted them.
 */
@Composable
fun WidgetGrid(
    placements: List<GridPlacement>,
    modifier: Modifier = Modifier,
    reservedRows: Int? = null,
    columns: Int = GridTokens.Columns,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        require(measurables.size == placements.size) {
            "WidgetGrid got ${measurables.size} children for ${placements.size} placements"
        }
        val gapPx = GridTokens.Gap.roundToPx()
        val metrics = gridMetrics(
            availableWidthPx = constraints.maxWidth,
            gapPx = gapPx,
            minCellPx = GridTokens.CellMinHeight.roundToPx(),
            maxCellPx = GridTokens.CellMaxHeight.roundToPx(),
            columns = columns
        )
        val placeables = measurables.mapIndexed { index, measurable ->
            val span = placements[index].span
            measurable.measure(
                Constraints.fixed(metrics.widthOf(span), metrics.heightOf(span))
            )
        }
        // only ~10 tiles, so laziness is not worth the complexity
        val usedRows = placements.maxOfOrNull { it.row + it.span.rows } ?: 0
        val rows = maxOf(usedRows, reservedRows ?: 0)
        val height = metrics.heightOf(rows) + GridTokens.ContentVerticalPadding.roundToPx() * 2
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                val placement = placements[index]
                placeable.place(
                    x = metrics.xOf(placement.column),
                    y = metrics.yOf(placement.row) + GridTokens.ContentVerticalPadding.roundToPx()
                )
            }
        }
    }
}
