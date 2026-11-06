package gb.frame

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntRect
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

/** Source artwork dimensions for one physical slide switch. Coordinates are in pixels of [artwork]. */
data class MechanicalArtworkToggleSpec(
    val track: IntRect,
    val thumbOffCenter: Offset,
    val thumbOnCenter: Offset,
    val blankSampleCenter: Offset,
    val thumbRadius: Float,
)

/**
 * Artwork-based mechanical slider. It moves the real thumb from [artwork] rather than drawing a vector switch.
 * When the controller panel is already behind it, [sourceArea] maps this overlay to the original image coordinates;
 * otherwise [drawTrack] paints the source track for compact layouts without the full panel.
 */
@Composable
fun MechanicalArtworkSlideToggle(
    artwork: ImageBitmap,
    spec: MechanicalArtworkToggleSpec,
    sourceArea: Rect,
    isOn: Boolean,
    isPressed: Boolean,
    drawTrack: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val position by animateFloatAsState(
        if (isOn) 1f else 0f,
        spring(dampingRatio = .72f, stiffness = 480f),
        label = "mechanical toggle detent",
    )
    val press by animateFloatAsState(
        if (isPressed) 1f else 0f,
        spring(dampingRatio = .68f, stiffness = 620f),
        label = "mechanical toggle press",
    )
    Canvas(modifier) {
        if (drawTrack) {
            val trackShape = Path().apply {
                addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(size.height * .5f)))
            }
            clipPath(trackShape) {
                drawImage(
                    image = artwork,
                    srcOffset = androidx.compose.ui.unit.IntOffset(spec.track.left, spec.track.top),
                    srcSize = IntSize(spec.track.width, spec.track.height),
                    dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }

        if (position > .001f || press > .001f) {
            val scaleX = size.width / sourceArea.width.coerceAtLeast(1f)
            val scaleY = size.height / sourceArea.height.coerceAtLeast(1f)
            fun map(source: Offset) = Offset(
                (source.x - sourceArea.left) * scaleX,
                (source.y - sourceArea.top) * scaleY,
            )

            val off = map(spec.thumbOffCenter)
            val sample = spec.blankSampleCenter
            val radius = spec.thumbRadius
            val sampleLeft = (sample.x - radius).toInt().coerceIn(0, artwork.width - 1)
            val sampleTop = (sample.y - radius).toInt().coerceIn(0, artwork.height - 1)
            val sampleSize = (radius * 2f).toInt().coerceIn(1, minOf(artwork.width - sampleLeft, artwork.height - sampleTop))
            val offRadiusX = radius * scaleX
            val offRadiusY = radius * scaleY
            val patch = Path().apply {
                addOval(Rect(off.x - offRadiusX, off.y - offRadiusY, off.x + offRadiusX, off.y + offRadiusY))
            }
            clipPath(patch) {
                drawImage(
                    image = artwork,
                    srcOffset = androidx.compose.ui.unit.IntOffset(sampleLeft, sampleTop),
                    srcSize = IntSize(sampleSize, sampleSize),
                    dstOffset = androidx.compose.ui.unit.IntOffset(
                        (off.x - offRadiusX).toInt(),
                        (off.y - offRadiusY).toInt(),
                    ),
                    dstSize = IntSize(
                        (offRadiusX * 2f).toInt().coerceAtLeast(1),
                        (offRadiusY * 2f).toInt().coerceAtLeast(1),
                    ),
                    filterQuality = FilterQuality.Medium,
                )
            }

            val clampedPosition = position.coerceIn(0f, 1f)
            val centerX = spec.thumbOffCenter.x +
                (spec.thumbOnCenter.x - spec.thumbOffCenter.x) * clampedPosition
            val center = map(Offset(centerX, spec.thumbOffCenter.y)) + Offset(0f, 2.dp.toPx() * press)
            val pressedScale = 1f - .025f * press
            val radiusX = radius * scaleX * pressedScale
            val radiusY = radius * scaleY * pressedScale
            val thumbClip = Path().apply {
                addOval(Rect(center.x - radiusX, center.y - radiusY, center.x + radiusX, center.y + radiusY))
            }
            val thumbLeft = (spec.thumbOffCenter.x - radius).toInt().coerceIn(0, artwork.width - 1)
            val thumbTop = (spec.thumbOffCenter.y - radius).toInt().coerceIn(0, artwork.height - 1)
            val thumbSize = (radius * 2f).toInt().coerceIn(1, minOf(artwork.width - thumbLeft, artwork.height - thumbTop))
            clipPath(thumbClip) {
                drawImage(
                    image = artwork,
                    srcOffset = androidx.compose.ui.unit.IntOffset(thumbLeft, thumbTop),
                    srcSize = IntSize(thumbSize, thumbSize),
                    dstOffset = androidx.compose.ui.unit.IntOffset(
                        (center.x - radiusX).toInt(),
                        (center.y - radiusY).toInt(),
                    ),
                    dstSize = IntSize(
                        (radiusX * 2f).toInt().coerceAtLeast(1),
                        (radiusY * 2f).toInt().coerceAtLeast(1),
                    ),
                    filterQuality = FilterQuality.Medium,
                )
                drawOval(
                    color = Color.Black.copy(alpha = .08f * press),
                    topLeft = Offset(center.x - radiusX, center.y - radiusY),
                    size = androidx.compose.ui.geometry.Size(radiusX * 2f, radiusY * 2f),
                )
            }
        }
    }
}

