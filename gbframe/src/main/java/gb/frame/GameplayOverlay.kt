package gb.frame

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Insets for anything drawn over the game: only the display cutout. A game always runs immersive, so the system bars
 * show only for a moment (while the game screen opens, or after a swipe); padding by them (`safeDrawingPadding`) moved
 * the overlays down by the status bar's height in that moment.
 */
@Composable
fun Modifier.gameOverlayInsets(): Modifier = windowInsetsPadding(WindowInsets.displayCutout)

/**
 * What every app draws over the running game, in the same places: the menu button (top left) with the optional
 * fast-forward button and the Skip Engine indicator next to it, the performance overlay (top right, drawn by [perf]
 * with the modifier it receives), RetroAchievements notifications and a short [message] (top center).
 * Call it inside the game screen's full-size Box, after the picture.
 */
@Composable
fun BoxScope.GameplayOverlayChrome(
    onMenu: () -> Unit,
    showFastForwardButton: Boolean,
    fastForward: Boolean,
    onFastForward: () -> Unit,
    perf: @Composable (Modifier) -> Unit,
    skipping: Boolean = false,
    message: String? = null,
) {
    Row(Modifier.align(Alignment.TopStart).gameOverlayInsets().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = onMenu,
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = .45f), contentColor = Color.White),
        ) { Icon(Icons.Default.Menu, "Menu") }
        if (showFastForwardButton) {
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = onFastForward,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (fastForward) Color(0xFFE8C26A).copy(alpha = .85f) else Color.Black.copy(alpha = .45f),
                    contentColor = if (fastForward) Color.Black else Color.White),
            ) { Icon(Icons.Default.FastForward, "Fast-forward") }
        }
        // Skip Engine running at maximum speed (a load, transition or save wait)
        if (skipping && !fastForward) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.FastForward, "Skipping", tint = Color(0xFFE8C26A), modifier = Modifier.size(22.dp))
        }
    }
    perf(Modifier.align(Alignment.TopEnd).gameOverlayInsets().padding(10.dp))
    RaNotifications(Modifier.align(Alignment.TopCenter).gameOverlayInsets().padding(top = 56.dp))
    message?.let { msg ->
        Surface(
            shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = .7f),
            modifier = Modifier.align(Alignment.TopCenter).gameOverlayInsets().padding(top = 12.dp),
        ) { Text(msg, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
    }
}
