package gb.frame

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max

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
    val circular = name in setOf("A", "B", "C", "X", "Y", "Z", "save", "load")
    val shape = if (circular) CircleShape else RoundedCornerShape(40)
    Box(Modifier.fillMaxSize()) {
        // Recess the printed button first, so moving the button layer never leaves a bright duplicate behind.
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * .5f, size.height * .5f)
            if (circular) {
                val radius = size.minDimension * if (name == "save" || name == "load") .31f else .44f
                drawCircle(Color.Black.copy(alpha = .58f * amount), radius, center + Offset(0f, size.minDimension * .06f * amount))
                drawCircle(Color.Black.copy(alpha = .27f * amount), radius * .90f, center)
                drawCircle(
                    Color.White.copy(alpha = .16f * amount), radius * .84f, center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.1.dp.toPx()),
                )
            } else {
                val insetX = size.width * .06f * amount
                val insetY = size.height * .06f * amount
                val topLeft = Offset(insetX, insetY + size.height * .035f * amount)
                val buttonSize = androidx.compose.ui.geometry.Size(size.width - insetX * 2f, size.height - insetY * 2f)
                val corner = androidx.compose.ui.geometry.CornerRadius(size.minDimension * .38f)
                drawRoundRect(Color.Black.copy(alpha = .58f * amount), topLeft + Offset(0f, size.height * .05f * amount), buttonSize, corner)
                drawRoundRect(Color.Black.copy(alpha = .24f * amount), topLeft, buttonSize, corner)
                drawRoundRect(
                    Color.White.copy(alpha = .16f * amount), topLeft, buttonSize, corner,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.1.dp.toPx()),
                )
            }
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer {
            translationY = 2.dp.toPx() * amount
        }.clip(shape)) {
            drawImage(artwork, srcOffset = androidx.compose.ui.unit.IntOffset(left, top),
                srcSize = IntSize(sourceWidth, sourceHeight),
                dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)))
            drawRect(Color.Black.copy(alpha = .08f * amount))
        }
    }
}

/** Directional disk feedback for printed Saturn artwork; the bezel stays stationary while the disk tilts on its pivot. */
@Composable
fun MechanicalArtworkDPad(
    artwork: ImageBitmap,
    centerX: Float,
    centerY: Float,
    sourceSizeFraction: Float,
    activeDirections: Set<TouchDirection>,
    modifier: Modifier = Modifier,
) {
    val up by animateFloatAsState(if (TouchDirection.UP in activeDirections) 1f else 0f, tween(85), label = "mechanical dpad up")
    val down by animateFloatAsState(if (TouchDirection.DOWN in activeDirections) 1f else 0f, tween(85), label = "mechanical dpad down")
    val left by animateFloatAsState(if (TouchDirection.LEFT in activeDirections) 1f else 0f, tween(85), label = "mechanical dpad left")
    val right by animateFloatAsState(if (TouchDirection.RIGHT in activeDirections) 1f else 0f, tween(85), label = "mechanical dpad right")
    val dx = right - left
    val dy = down - up
    val progress = max(max(up, down), max(left, right))
    val sourceSize = (sourceSizeFraction * artwork.width).toInt().coerceIn(1, minOf(artwork.width, artwork.height))
    val sourceLeft = (centerX * artwork.width - sourceSize / 2f).toInt().coerceIn(0, artwork.width - sourceSize)
    val sourceTop = (centerY * artwork.height - sourceSize / 2f).toInt().coerceIn(0, artwork.height - sourceSize)

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            if (progress > .001f) {
                // This dark socket covers the still artwork beneath the displaced disk.
                drawCircle(Color.Black.copy(alpha = .46f * progress), size.minDimension * .40f, Offset(size.width * .5f, size.height * .5f))
                drawCircle(Color.White.copy(alpha = .10f * progress), size.minDimension * .36f,
                    Offset(size.width * .5f, size.height * .5f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer {
            translationX = dx * size.width * .0115f * progress
            translationY = dy * size.height * .015f * progress + size.height * .005f * progress
            rotationX = -5f * dy * progress
            rotationY = 5f * dx * progress
            cameraDistance = 12f * density
        }.clip(CircleShape)) {
            if (progress <= .001f) return@Canvas
            drawImage(artwork, srcOffset = androidx.compose.ui.unit.IntOffset(sourceLeft, sourceTop),
                srcSize = IntSize(sourceSize, sourceSize),
                dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)))
            drawCircle(Color.Black.copy(alpha = .07f * progress))
        }
    }
}

