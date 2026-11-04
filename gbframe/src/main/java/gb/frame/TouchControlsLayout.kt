package gb.frame

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Shared screen arrangement for touch gameplay. */
enum class TouchControlsPlacement {
    /** 4:3 and square devices keep the whole game view and draw transparent controls above it. */
    FULLSCREEN_OVERLAY,
    /** Tall phones put the game at the top and the controller below it. */
    PORTRAIT_BOTTOM,
    /** Widescreen landscape devices put the game in the center and controls in side panels. */
    LANDSCAPE_SIDES,
    /** Touch controls are disabled; the game view fills the device. */
    FULLSCREEN,
}

/** A game surface rectangle in the same units passed to [TouchControlsLayoutPlanner.plan]. */
@Immutable
data class TouchControlsVideoBounds(val left: Float, val top: Float, val width: Float, val height: Float)

/** Pure responsive layout result shared by Compose overlays and native game-surface containers. */
@Immutable
data class TouchControlsLayoutPlan(
    val placement: TouchControlsPlacement,
    val video: TouchControlsVideoBounds,
)

object TouchControlsLayoutPlanner {
    /** Handheld-style target ratios for the shared full-screen overlay treatment. */
    @JvmStatic
    fun usesResponsiveLayout(width: Float, height: Float): Boolean {
        if (width <= 0f || height <= 0f) return false
        val ratio = max(width, height) / min(width, height)
        return ratio <= 1.10f ||
            abs(ratio - (4f / 3f)) <= 0.10f ||
            abs(ratio - (16f / 9f)) <= 0.10f
    }

    /**
     * [width] and [height] can be pixels or dp, but must use the same unit. Aspect is the intended landscape game
     * ratio (normally 4:3 or 16:9). The returned video bounds use that same unit.
     */
    @JvmStatic
    fun plan(width: Float, height: Float, videoAspect: Float = 4f / 3f, controlsEnabled: Boolean = true): TouchControlsLayoutPlan {
        if (width <= 0f || height <= 0f) {
            return TouchControlsLayoutPlan(TouchControlsPlacement.FULLSCREEN, TouchControlsVideoBounds(0f, 0f, width, height))
        }
        if (!controlsEnabled) {
            return TouchControlsLayoutPlan(TouchControlsPlacement.FULLSCREEN, TouchControlsVideoBounds(0f, 0f, width, height))
        }

        val longSide = max(width, height)
        val shortSide = min(width, height)
        val deviceRatio = longSide / shortSide
        val square = deviceRatio <= 1.10f
        val fourThree = abs(deviceRatio - (4f / 3f)) <= 0.10f
        val aspect = videoAspect.takeIf { it.isFinite() && it in 1f..2.4f } ?: (4f / 3f)
        if (square) {
            // Keep a 4:3 (or selected widescreen) image centered on square displays. The overlay still
            // spans the full screen, so transparent controls remain easy to reach without covering a
            // top-aligned, letterboxed image.
            val videoWidth = min(width, height * aspect)
            val videoHeight = videoWidth / aspect
            return TouchControlsLayoutPlan(
                TouchControlsPlacement.FULLSCREEN_OVERLAY,
                TouchControlsVideoBounds((width - videoWidth) * 0.5f, (height - videoHeight) * 0.5f, videoWidth, videoHeight),
            )
        }
        if (fourThree) {
            return TouchControlsLayoutPlan(
                TouchControlsPlacement.FULLSCREEN_OVERLAY,
                TouchControlsVideoBounds(0f, 0f, width, height),
            )
        }

        // Console output remains landscape even when a user selects Fullscreen on a portrait phone.
        if (height > width) {
            val videoHeight = min(width / aspect, height * 0.55f)
            val videoWidth = videoHeight * aspect
            return TouchControlsLayoutPlan(
                TouchControlsPlacement.PORTRAIT_BOTTOM,
                TouchControlsVideoBounds((width - videoWidth) * 0.5f, 0f, videoWidth, videoHeight),
            )
        }

        // Reserve side room for the controller while keeping the game image at its selected aspect ratio.
        // Leave enough room for reachable side controls on phones held landscape without shrinking the game view too far.
        val videoWidth = min(width * 0.62f, height * 0.96f * aspect)
        val videoHeight = videoWidth / aspect
        return TouchControlsLayoutPlan(
            TouchControlsPlacement.LANDSCAPE_SIDES,
            TouchControlsVideoBounds((width - videoWidth) * 0.5f, (height - videoHeight) * 0.5f, videoWidth, videoHeight),
        )
    }
}

/** Screen metrics for a touch overlay. The overlay stays transparent and never takes space from the game view. */
@Immutable
data class TouchControlsLayoutMetrics(
    val screenWidth: Dp,
    val screenHeight: Dp,
    val scale: Float,
    val edgePadding: Dp,
    val compact: Boolean,
    val nearSquare: Boolean,
    val plan: TouchControlsLayoutPlan,
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
    videoAspectRatio: Float = 4f / 3f,
    controlsEnabled: Boolean = true,
    content: @Composable BoxScope.(TouchControlsLayoutMetrics) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val scale = minOf(1f, maxWidth / referenceWidth, maxHeight / referenceHeight).coerceAtLeast(0.35f)
        val compact = maxWidth < 600.dp || maxHeight < 680.dp
        val nearSquare = maxWidth.value > 0f && maxHeight.value > 0f &&
            maxWidth.value / maxHeight.value in 0.9f..1.1f
        val edgePadding = when {
            maxWidth < 400.dp || maxHeight < 400.dp -> 4.dp
            compact -> 8.dp
            else -> 16.dp
        }
        val plan = TouchControlsLayoutPlanner.plan(maxWidth.value, maxHeight.value, videoAspectRatio, controlsEnabled)
        content(TouchControlsLayoutMetrics(maxWidth, maxHeight, scale, edgePadding, compact, nearSquare, plan))
    }
}
