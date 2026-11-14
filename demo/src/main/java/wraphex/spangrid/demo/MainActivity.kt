package wraphex.spangrid.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import wraphex.spangrid.core.GridBoard
import wraphex.spangrid.core.GridItem
import wraphex.spangrid.core.GridSpec
import wraphex.spangrid.core.GridSpan
import wraphex.spangrid.core.SpanBounds
import wraphex.spangrid.core.clampTo
import wraphex.spangrid.compose.GridChrome
import wraphex.spangrid.compose.GridMetrics
import wraphex.spangrid.compose.GridPlacement
import wraphex.spangrid.compose.GridTokens
import wraphex.spangrid.compose.ResizeHandle
import wraphex.spangrid.compose.WidgetGrid
import wraphex.spangrid.compose.gridMetrics
import kotlin.math.roundToInt

/**
 * The smallest host of `span-grid-compose`: a board of placeholder tiles, drag to move, drag the
 * corner to resize, and three buttons that are one line each of `GridBoard`.
 *
 * Everything application-shaped (entity states, edit mode, the delete dialog, the magnetic drag
 * state machine) stays in the host app; this is the library's contract, not its owner.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 36 draws edge to edge whether the app asks or not; DemoScreen keeps the
        // status and navigation bars clear with safeDrawing insets.
        enableEdgeToEdge()
        setContent { DemoContent() }
    }
}

private const val Columns = 4
private const val MaxRows = 3

private val DemoSpec = GridSpec(
    columns = Columns,
    maxRows = MaxRows,
    boundsOf = { SpanBounds(min = GridSpan(1, 1), max = GridSpan(Columns, MaxRows)) }
)

private val InitialLayout: List<GridItem> = listOf(
    GridItem.at("a", 0, 0, GridSpan(4, 1)),
    GridItem.at("b", 0, 1, GridSpan(2, 2)),
    GridItem.at("c", 2, 1, GridSpan(2, 1)),
    GridItem.at("d", 2, 2, GridSpan(2, 1)),
    GridItem.at("e", 0, 3, GridSpan(1, 1)),
    GridItem.at("f", 1, 3, GridSpan(3, 1))
)

/** The whole screen: theme plus background, shared by the activity and the previews. */
@Composable
internal fun DemoContent() {
    DemoTheme {
        Surface(Modifier.fillMaxSize()) { DemoScreen() }
    }
}


/**
 * Light or dark, following the system. Dynamic colour is deliberately not used: a sample should
 * show the same board on every device.
 */
@Composable
private fun DemoTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
    content = content
)

@Composable
private fun DemoScreen() {
    var items by remember { mutableStateOf(InitialLayout) }
    var frame by remember { mutableStateOf(true) }
    var nextId by remember { mutableStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "span-grid",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Frames", style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = frame,
                    onCheckedChange = { frame = it },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            TextButton(onClick = { items = GridBoard.tidy(items, DemoSpec) }) { Text("Tidy") }
            TextButton(
                onClick = {
                    val id = "new${nextId++}"
                    items = GridBoard.append(items, id, GridSpan(1, 1), DemoSpec)
                }
            ) { Text("Add") }
        }

        DemoBoard(items = items, frame = frame, onItems = { items = it })
    }
}

/** A drag in flight: the committed table stays untouched until the finger lifts. */
private data class Dragging(
    val id: String,
    val resize: Boolean,
    val from: GridItem,
    val base: List<GridItem>,
    val delta: Offset,
    val aimedColumn: Int,
    val aimedRow: Int,
    val table: List<GridItem>
)

@Composable
private fun DemoBoard(items: List<GridItem>, frame: Boolean, onItems: (List<GridItem>) -> Unit) {
    val density = LocalDensity.current
    var drag by remember { mutableStateOf<Dragging?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val metrics = remember(maxWidth, density) {
            with(density) {
                gridMetrics(
                    availableWidthPx = (maxWidth - GridTokens.ScreenPadding * 2).roundToPx(),
                    gapPx = GridTokens.Gap.roundToPx(),
                    minCellPx = GridTokens.CellMinHeight.roundToPx(),
                    maxCellPx = GridTokens.CellMaxHeight.roundToPx()
                )
            }
        }
        val table = drag?.table ?: items

        // The same gesture serves both flavours: the body moves, the corner handle resizes.
        fun Modifier.dragBy(item: GridItem, resize: Boolean) = pointerInput(item.id, items) {
            detectDragGestures(
                onDragStart = {
                    drag = Dragging(
                        id = item.id,
                        resize = resize,
                        from = item,
                        base = items,
                        delta = Offset.Zero,
                        aimedColumn = item.column,
                        aimedRow = item.row,
                        table = items
                    )
                },
                onDragEnd = {
                    drag?.let { onItems(it.table) }
                    drag = null
                },
                onDragCancel = { drag = null },
                onDrag = { change, amount ->
                    change.consume()
                    drag = drag?.movedBy(amount, metrics)
                }
            )
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            WidgetGrid(
                placements = table.map { GridPlacement(it.column, it.row, it.span) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = GridTokens.ScreenPadding)
            ) {
                table.forEach { item ->
                    key(item.id) {
                        val dragging = drag?.takeIf { it.id == item.id }
                        // the planted card is drawn at the cell the drag aims at, shifted by the
                        // part of the finger movement that snapping has not eaten yet
                        val residual = if (dragging == null || dragging.resize) {
                            Offset.Zero
                        } else {
                            Offset(
                                metrics.xOf(dragging.from.column) + dragging.delta.x -
                                    metrics.xOf(dragging.aimedColumn),
                                metrics.yOf(dragging.from.row) + dragging.delta.y -
                                    metrics.yOf(dragging.aimedRow)
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .offset {
                                    IntOffset(residual.x.roundToInt(), residual.y.roundToInt())
                                }
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .dragBy(item, resize = false)
                            ) {
                                TileContent(item)
                            }

                            if (frame) {
                                GridChrome(
                                    selected = dragging != null,
                                    corner = 12.dp,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .dragBy(item, resize = true)
                            ) {
                                ResizeHandle()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One drag frame. A move asks the board to drop the item at the aimed cell (the board decides
 * whether that is a swap, a free landing or a push); a resize asks it to grow, clamped to the
 * span the spec allows.
 */
private fun Dragging.movedBy(amount: Offset, metrics: GridMetrics): Dragging {
    val delta = delta + amount
    val columns = (delta.x / metrics.columnStep()).roundToInt()
    val rows = (delta.y / metrics.rowStep()).roundToInt()
    return if (resize) {
        val span = GridSpan(
            columns = from.columns + columns,
            rows = from.rows + rows
        ).clampTo(DemoSpec.boundsOf(id), Columns, MaxRows)
        copy(
            delta = delta,
            table = GridBoard.resize(base, id, span, DemoSpec)
        )
    } else {
        val column = (from.column + columns).coerceIn(0, Columns - from.columns)
        val row = (from.row + rows).coerceAtLeast(0)
        copy(
            delta = delta,
            aimedColumn = column,
            aimedRow = row,
            table = GridBoard.move(base, id, column, row, DemoSpec)
        )
    }
}

/**
 * Placeholder colours, one set per mode.
 *
 * Deliberately not [MaterialTheme.colorScheme] containers: the baseline scheme is monochrome, so
 * six roles collapse into three indistinguishable greys. Text stays on [MaterialTheme] `onSurface`,
 * which is why these only have to read as blocks, not as content.
 */
private val LightTiles = listOf(
    Color(0xFFD3E4FD),
    Color(0xFFFDE2C4),
    Color(0xFFD8F3DC),
    Color(0xFFF3D9FA),
    Color(0xFFFFE0E0),
    Color(0xFFE2E8F0)
)

private val DarkTiles = listOf(
    Color(0xFF2B3550),
    Color(0xFF4A3A22),
    Color(0xFF243D2C),
    Color(0xFF3B2B4A),
    Color(0xFF4A2C33),
    Color(0xFF2E3440)
)

@Composable
private fun tileColors(): List<Color> = if (isSystemInDarkTheme()) DarkTiles else LightTiles
@Composable
private fun TileContent(item: GridItem) {
    val colors = tileColors()
    val color = colors[item.id.hashCode().mod(colors.size)]
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = item.id,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${item.columns} × ${item.rows}",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
