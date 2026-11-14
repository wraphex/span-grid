package wraphex.spangrid.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import wraphex.spangrid.core.GridBoard
import wraphex.spangrid.core.GridItem
import wraphex.spangrid.core.GridSpan
import wraphex.spangrid.core.GridSpec
import wraphex.spangrid.core.clampTo

/**
 * How a board feels: the magnet band, the edge scroll and the settle duration.
 *
 * Only a resize needs a magnet: it draws a free-following card, and a two-row item has just two
 * lines to land on, so a tight band would be invisible. A move is drawn by the board itself at its
 * target cell, where the half-cell flip is the whole threshold.
 */
data class GridStyle(
    val snapEnterSize: Dp = 32.dp,
    val snapExitSize: Dp = 44.dp,
    val edgeScroll: Dp = 72.dp,
    val scrollStep: Dp = 16.dp,
    /** How long the dropped shell takes to settle onto its landing rect. */
    val settleMillis: Int = 160
)

/** The per-item actions a selected tile offers in the three free corners. */
enum class GridItemAction { Delete, Replace, Add }

/** What a drag in flight is doing. */
enum class DragMode { Move, Resize }

/** What a finished drag asks the host to store. */
data class DragOutcome(
    val id: String,
    val column: Int,
    val row: Int,
    val span: GridSpan
)

/**
 * One short animation after a drop: the lifted shell slides (and resizes) from where the finger
 * left it onto the rect it was committed to. Without it the card teleports the last few pixels of
 * every drag, which is exactly the part the eye is watching.
 */
data class Settling(
    val id: String,
    val mode: DragMode,
    val from: Rect,
    val to: Rect,
    val span: GridSpan
)

/**
 * A drag in flight. The stored table is untouched until release.
 *
 * [table] is what the board renders while dragging: for a move it has the item at the drop cell and
 * the others pushed down, for a resize it stays the committed table and only [span] changes.
 * [visual] is the lifted shell's unsnapped rect, [grab] is where inside the item the finger
 * started, [dropColumn]/[dropRow] the cell it is aimed at.
 */
data class DragPreview(
    val id: String,
    val mode: DragMode,
    val start: GridItem,
    val span: GridSpan,
    val table: List<GridItem>,
    val dropColumn: Int,
    val dropRow: Int,
    val grab: Offset,
    val visual: Rect,
    val snapX: Boolean = false,
    val snapY: Boolean = false
)

/**
 * The drag state machine of an editable board: hit testing, the free rect that follows the finger,
 * the magnet that pulls it onto the landing rect, and the drop cell.
 *
 * A `remember`ed instance, not local functions inside a composable: the gesture coroutine started
 * by `awaitEachGesture` never ends, so anything it captured from the composition would go stale
 * after the first commit — which is why the table is passed in on every call instead of captured.
 */
class GridDragState(
    private val metrics: GridMetrics,
    private val spec: GridSpec,
    density: Density,
    val style: GridStyle = GridStyle(),
    private val haptic: () -> Unit = {}
) {
    private val padPx = with(density) { GridTokens.ContentVerticalPadding.toPx() }
    private val handlePx = with(density) { GridTokens.HandleTouchSize.toPx() }
    private val actionPx = with(density) { GridTokens.ActionSize.toPx() }
    private val actionInsetPx = with(density) { GridTokens.ActionInset.toPx() }
    private val snapEnterSize = with(density) { style.snapEnterSize.toPx() }
    private val snapExitSize = with(density) { style.snapExitSize.toPx() }
    private val edgeScrollPx = with(density) { style.edgeScroll.toPx() }
    private val scrollStepPx = with(density) { style.scrollStep.toPx() }

    var preview by mutableStateOf<DragPreview?>(null)
        private set

    var settling by mutableStateOf<Settling?>(null)
        private set

    /** Set by the board; only used to decide when a drag needs to scroll. */
    var viewportHeightPx: Int = 0

    /** Which item sits under a grid-space point, from the table as drawn. */
    fun itemAt(items: List<GridItem>, point: Offset): GridItem? =
        items.firstOrNull { item ->
            val left = metrics.xOf(item.column).toFloat()
            val top = metrics.yOf(item.row) + padPx
            point.x >= left &&
                point.x < left + metrics.widthOf(item.span) &&
                point.y >= top &&
                point.y < top + metrics.heightOf(item.span)
        }

    /**
     * Which corner action sits under [point], if any: delete top-start, replace top-end, add
     * bottom-start (the resize handle keeps bottom-end). The nearest centre wins when the zones
     * overlap on a one-row tile.
     */
    fun actionAt(item: GridItem, point: Offset): GridItemAction? {
        val rect = rectOf(item)
        val size = actionPx
        val inset = actionInsetPx
        fun zone(left: Float, top: Float) = Rect(Offset(left, top), Size(size, size))
        return listOf(
            GridItemAction.Delete to zone(rect.left + inset, rect.top + inset),
            GridItemAction.Replace to zone(rect.right - inset - size, rect.top + inset),
            GridItemAction.Add to zone(rect.left + inset, rect.bottom - inset - size)
        )
            .filter { (_, zone) -> zone.contains(point) }
            .minByOrNull { (_, zone) -> (point - zone.center).getDistanceSquared() }
            ?.first
    }

    /** The handle-sized square in the bottom-right corner of [item]. */
    fun onHandle(item: GridItem, point: Offset): Boolean {
        val right = metrics.xOf(item.column) + metrics.widthOf(item.span).toFloat()
        val bottom = metrics.yOf(item.row) + padPx + metrics.heightOf(item.span)
        return point.x >= right - handlePx && point.y >= bottom - handlePx
    }

    fun start(item: GridItem, mode: DragMode, pointer: Offset, table: List<GridItem>) {
        val topLeft = Offset(
            metrics.xOf(item.column).toFloat(),
            metrics.yOf(item.row) + padPx
        )
        preview = DragPreview(
            id = item.id,
            mode = mode,
            start = item,
            span = item.span,
            table = table,
            dropColumn = item.column,
            dropRow = item.row,
            grab = pointer - topLeft,
            visual = Rect(
                offset = topLeft,
                size = Size(
                    metrics.widthOf(item.span).toFloat(),
                    metrics.heightOf(item.span).toFloat()
                )
            )
        )
    }

    /**
     * [pointer] is in grid coordinates and the board does not move under the finger, so the target
     * is a direct function of it — no accumulated delta, no feedback loop with the shell's own
     * position. [scrollBy] performs the edge scroll of a drag that reaches the viewport border.
     */
    fun update(
        pointer: Offset,
        table: List<GridItem>,
        scrollOffset: Int,
        scrollBy: (Float) -> Unit
    ) {
        val current = preview ?: return
        val free = freeRectFor(current, pointer)
        val next = when (current.mode) {
            DragMode.Move -> {
                val column = dropColumnFor(free, current.span)
                val row = dropRowFor(free)
                current.copy(
                    dropColumn = column,
                    dropRow = row,
                    // re-placed only when the target cell changes, so the others move in a step,
                    // not on every pixel
                    table = if (column == current.dropColumn && row == current.dropRow) {
                        current.table
                    } else {
                        GridBoard.move(table, current.id, column, row, spec)
                    }
                )
            }
            DragMode.Resize -> {
                // the span comes from where the *edges* are: a two-row item flips to one row
                // halfway between the two heights, which is where the eye expects it
                val left = metrics.xOf(current.start.column).toFloat()
                val top = metrics.yOf(current.start.row) + padPx
                val columns = ((pointer.x - left + metrics.gapPx) / metrics.columnStep())
                    .roundToInt()
                val rows = ((pointer.y - top + metrics.gapPx) / metrics.rowStep()).roundToInt()
                current.copy(
                    span = GridSpan(columns, rows).clampTo(
                        spec.boundsOf(current.id),
                        spec.columns,
                        spec.maxRows
                    )
                )
            }
        }

        // magnetism, against where it would actually land (only a resize draws its own card)
        val land = rectOf(targetOf(next))
        val dragged = when (next.mode) {
            // a move is drawn by the board at its target cell plus the residual between that cell
            // and the finger, so the free rect is what keeps it under the finger
            DragMode.Move -> next.copy(visual = free)
            DragMode.Resize -> {
                val w = snapAxis(
                    free.width, land.width, current.snapX, snapEnterSize, snapExitSize
                )
                val h = snapAxis(
                    free.height, land.height, current.snapY, snapEnterSize, snapExitSize
                )
                next.copy(
                    visual = Rect(free.topLeft, Size(w.value, h.value)),
                    snapX = w.snapped,
                    snapY = h.snapped
                )
            }
        }
        if (next.dropColumn != current.dropColumn ||
            next.dropRow != current.dropRow ||
            next.span != current.span
        ) {
            haptic()
        }
        preview = dragged
        autoScroll(dragged, scrollOffset, scrollBy)
    }

    /** Clears the drag and says what to store, or null when there is nothing to commit. */
    fun finish(): DragOutcome? {
        val finished = preview ?: return null
        preview = null
        val target = targetOf(finished)
        val landing = rectOf(target)
        // Both modes settle, differently: a resize slides the lifted card onto its landing rect,
        // a move lets the card's residual offset (the part it was following the finger with) spring
        // back to zero, because the card itself is already drawn at the target cell.
        if (landing != finished.visual) {
            settling = Settling(
                id = finished.id,
                mode = finished.mode,
                from = finished.visual,
                to = landing,
                span = target.span
            )
        }
        return DragOutcome(
            id = finished.id,
            column = target.column,
            row = target.row,
            span = finished.span
        )
    }

    fun clearSettling() {
        settling = null
    }

    fun cancel() {
        preview = null
        settling = null
    }

    /** Frame rect of one item, in board coordinates. */
    fun rectOf(item: GridItem): Rect = Rect(
        offset = Offset(
            metrics.xOf(item.column).toFloat(),
            metrics.yOf(item.row) + padPx
        ),
        size = Size(
            metrics.widthOf(item.span).toFloat(),
            metrics.heightOf(item.span).toFloat()
        )
    )

    /**
     * The lifted shell's rect, in pixels and **not** snapped: it follows the finger one pixel at a
     * time, the way a home screen widget does. The magnet decides where it settles.
     */
    private fun freeRectFor(preview: DragPreview, pointer: Offset): Rect {
        if (preview.mode == DragMode.Resize) {
            val bounds = spec.boundsOf(preview.id)
            val left = metrics.xOf(preview.start.column).toFloat()
            val top = metrics.yOf(preview.start.row) + padPx
            val minWidth = metrics.widthOf(GridSpan(bounds.min.columns, 1)).toFloat()
            val maxWidth = metrics.widthOf(GridSpan(bounds.max.columns, 1)).toFloat()
            val minHeight = metrics.heightOf(GridSpan(1, bounds.min.rows)).toFloat()
            val maxHeight = metrics.heightOf(GridSpan(1, bounds.max.rows)).toFloat()
            return Rect(
                offset = Offset(left, top),
                size = Size(
                    width = (pointer.x - left).coerceIn(minWidth, maxWidth),
                    height = (pointer.y - top).coerceIn(minHeight, maxHeight)
                )
            )
        }
        val width = metrics.widthOf(preview.span).toFloat()
        val height = metrics.heightOf(preview.span).toFloat()
        val boardWidth = metrics.widthOf(GridSpan(metrics.columns, 1)).toFloat()
        return Rect(
            offset = Offset(
                x = (pointer.x - preview.grab.x)
                    .coerceIn(0f, (boardWidth - width).coerceAtLeast(0f)),
                y = (pointer.y - preview.grab.y).coerceAtLeast(0f)
            ),
            size = Size(width, height)
        )
    }

    /**
     * The aimed cell, from the shell's top-left. It flips at **half a cell**, so an item is already
     * "between the two" when it is halfway over its neighbour — together with the remove-first drop
     * in [GridBoard.move] that is what makes a half-row drag a swap.
     */
    private fun dropColumnFor(free: Rect, span: GridSpan): Int =
        (free.left / metrics.columnStep()).roundToInt()
            .coerceIn(0, (metrics.columns - span.columns).coerceAtLeast(0))

    private fun dropRowFor(free: Rect): Int =
        ((free.top - padPx) / metrics.rowStep()).roundToInt().coerceAtLeast(0)

    private fun autoScroll(preview: DragPreview, scrollOffset: Int, scrollBy: (Float) -> Unit) {
        if (viewportHeightPx == 0) return
        val target = targetOf(preview)
        val top = metrics.yOf(target.row) + padPx
        val bottom = metrics.yOf(target.row) + metrics.heightOf(target.span) + padPx
        val viewBottom = scrollOffset + viewportHeightPx
        val delta = when {
            bottom > viewBottom - edgeScrollPx ->
                minOf(bottom - (viewBottom - edgeScrollPx), scrollStepPx)
            top < scrollOffset + edgeScrollPx ->
                maxOf(top - (scrollOffset + edgeScrollPx), -scrollStepPx)
            else -> 0f
        }
        if (delta != 0f) scrollBy(delta)
    }
}

/** Where the dragged item would land if released now. */
fun targetOf(preview: DragPreview): GridItem =
    if (preview.mode == DragMode.Move) {
        preview.table.firstOrNull { it.id == preview.id } ?: preview.start
    } else {
        preview.start.copy(columns = preview.span.columns, rows = preview.span.rows)
    }
