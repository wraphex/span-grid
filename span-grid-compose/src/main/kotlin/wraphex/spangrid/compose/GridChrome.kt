package wraphex.spangrid.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The edit-mode chrome of an item: a dashed frame, nothing else.
 *
 * The host keeps drawing the tile's real content underneath (dimmed and inert); this only marks
 * the item as editable, so no content is duplicated or replaced in edit mode.
 */
@Composable
fun GridChrome(
    selected: Boolean,
    corner: Dp,
    modifier: Modifier = Modifier,
    invalid: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {}
) {
    val outline = when {
        invalid -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = modifier.dashedBorder(
            color = outline,
            corner = corner,
            width = if (selected) 2.dp else 1.dp
        ),
        content = content
    )
}

/** The snapped landing rect: a dashed frame with no fill, drawn under the lifted shell. */
@Composable
fun TargetOutline(color: Color, corner: Dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.dashedBorder(color, corner, width = 2.dp))
}

/** The resize handle: a round button with 48 dp of touch target. */
@Composable
fun ResizeHandle(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier.size(GridTokens.HandleTouchSize),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(percent = 50)),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

/** Dashed outline for the edit-mode shell. Compose has no dashed border, so draw it. */
private fun Modifier.dashedBorder(color: Color, corner: Dp, width: Dp) = drawBehind {
    val stroke = width.toPx()
    val inset = stroke / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(corner.toPx(), corner.toPx()),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(10.dp.toPx(), 8.dp.toPx())
            )
        )
    )
}
