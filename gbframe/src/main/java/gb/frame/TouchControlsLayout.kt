package gb.frame

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Screen metrics for a touch overlay. The overlay stays transparent and never takes space from the game view. */
@Immutable
data class TouchControlsLayoutMetrics(
    val screenWidth: Dp,
    val screenHeight: Dp,
    val scale: Float,
    val edgePadding: Dp,
    val compact: Boolean,
)

/**
 * Full-window transparent touch layer shared by GBFrame apps. [referenceWidth] and [referenceHeight] describe the
 * app's unscaled control layout; the kit scales it to fit and supplies safe edge spacing for small screens.
 */
@Composable
fun TouchControlsOverlay(
    modifier: Modifier = Modifier,
    referenceWidth: Dp,
    referenceHeight: Dp,
    content: @Composable BoxScope.(TouchControlsLayoutMetrics) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val scale = minOf(1f, maxWidth / referenceWidth, maxHeight / referenceHeight).coerceAtLeast(0.35f)
        val compact = maxWidth < 600.dp || maxHeight < 680.dp
        val edgePadding = when {
            maxWidth < 400.dp || maxHeight < 400.dp -> 4.dp
            compact -> 8.dp
            else -> 16.dp
        }
        content(TouchControlsLayoutMetrics(maxWidth, maxHeight, scale, edgePadding, compact))
    }
}
