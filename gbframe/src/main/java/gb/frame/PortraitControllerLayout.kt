package gb.frame

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Vertical placement of a printed controller artwork below the game on portrait phones (CONVENTIONS "Portrait
 * gameplay"). The area it gets runs from the bottom of the game picture to the bottom of the screen.
 *
 * - The controller's body ends at the navigation bar's edge. Its height counts even while the bar is hidden in a game,
 *   so a swipe that shows it never covers the pad.
 * - [marginTop] and [marginBottom] are the artwork's transparent rows above and below the body, as fractions of its
 *   height: they are not counted as space, so the body (not the image box) touches the navigation bar.
 * - An optional [logo] sits between the game and the controller, [gap] away from both. It takes the height left over,
 *   up to [maxLogoHeight]; the controller gives up at most [minLogoHeight] for it.
 * - [logoVerticalOffset] adjusts the visual center for artwork whose transparent edges differ from its visible shape.
 *
 * [artworkSize] fits the artwork into a width and height (for example GBSat's saturnControllerV12PortraitArtworkSize);
 * [controller] draws it with the modifier it receives.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortraitControllerLayout(
    modifier: Modifier,
    artworkSize: (Dp, Dp) -> DpSize,
    marginTop: Float,
    marginBottom: Float,
    logo: Painter? = null,
    logoDescription: String? = null,
    gap: Dp = 10.dp,
    minLogoHeight: Dp = 26.dp,
    maxLogoHeight: Dp = 36.dp,
    logoVerticalOffset: Dp = 0.dp,
    controller: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val navBar = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
        val withLogo = logo != null && maxHeight > 300.dp
        val reserved = if (withLogo) gap * 2 + minLogoHeight else 0.dp
        // The artwork's bottom margin extends below the navigation edge. The top of the visible body therefore
        // depends on the entire artwork above that edge, including its transparent bottom margin.
        val artBudget = (maxHeight - navBar - reserved).coerceAtLeast(0.dp)
        val art = artworkSize(maxWidth, artBudget / (1f - marginTop))
        val bodyTop = maxHeight - navBar - art.height * (1f - marginTop)
        if (withLogo) {
            val logoHeight = (bodyTop - gap * 2).coerceIn(0.dp, maxLogoHeight)
            if (logoHeight >= 18.dp) {
                val logoTop = ((bodyTop - logoHeight) / 2 + logoVerticalOffset)
                    .coerceIn(0.dp, (bodyTop - logoHeight).coerceAtLeast(0.dp))
                Image(
                    painter = logo!!,
                    contentDescription = logoDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
                        .padding(top = logoTop).height(logoHeight),
                )
            }
        }
        controller(
            Modifier.align(Alignment.BottomCenter).padding(bottom = navBar).offset(y = art.height * marginBottom)
                .requiredSize(art),
        )
    }
}
