package wraphex.spangrid.demo

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * Previews live in the debug source set: they are a development tool, so the release build carries
 * neither these functions nor the `ui-tooling-preview` dependency.
 *
 * A preview canvas never shrinks to its content — it is exactly the size the annotation asks for —
 * so the height has to be stated. It is derived rather than tuned: the board's own geometry at
 * [PreviewWidth], plus the header. Change the starting layout or `GridTokens` and these follow.
 */
private const val PreviewWidth = 384

/** `cellW = (W - 2 * ScreenPadding - (columns - 1) * Gap) / columns`, clamped by `GridTokens`. */
private const val PreviewCell =
    (PreviewWidth - 2 * 16 - 3 * 8) / 4

/** Rows of `InitialLayout`, their gaps, the board's vertical padding, and the header row. */
private const val PreviewHeight =
    4 * PreviewCell + 3 * 8 + 2 * 8 + 52

@Preview(name = "Light", widthDp = PreviewWidth, heightDp = PreviewHeight, showBackground = true)
@Composable
private fun DemoLightPreview() = DemoContent()

@Preview(
    name = "Dark",
    widthDp = PreviewWidth,
    heightDp = PreviewHeight,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun DemoDarkPreview() = DemoContent()
