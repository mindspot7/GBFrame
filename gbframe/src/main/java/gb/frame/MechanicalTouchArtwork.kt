package gb.frame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** Redraw the actual printed control as a small movable layer while it is held. */
@Composable
fun MechanicalArtworkPress(
    artwork: ImageBitmap, x: Float, y: Float, width: Float, height: Float,
    name: String, amount: Float,
) {
    if (amount <= .001f) return
    val left = ((x - width / 2f) * artwork.width).toInt().coerceIn(0, artwork.width - 1)
    val top = ((y - height / 2f) * artwork.height).toInt().coerceIn(0, artwork.height - 1)
    val sourceWidth = (width * artwork.width).toInt().coerceIn(1, artwork.width - left)
    val sourceHeight = (height * artwork.height).toInt().coerceIn(1, artwork.height - top)
    val shape = if (name in setOf("A", "B", "C", "X", "Y", "Z", "save", "load")) CircleShape else RoundedCornerShape(40)
    Canvas(Modifier.fillMaxSize().graphicsLayer {
        scaleX = 1f - .05f * amount
        scaleY = 1f - .05f * amount
        translationY = 2.dp.toPx() * amount
    }.clip(shape)) {
        drawImage(artwork, srcOffset = androidx.compose.ui.unit.IntOffset(left, top),
            srcSize = IntSize(sourceWidth, sourceHeight),
            dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)))
        drawRect(Color.Black.copy(alpha = .08f * amount))
    }
}

