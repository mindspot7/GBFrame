package gb.frame

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Coordinates and assets match the user-approved web prototype V12 (1112 x 1415). */
private const val DESIGN_W = 1112f
private const val DESIGN_H = 1415f
private const val BODY_W = 1040f // Alpha-visible controller width in saturn_base.png (x=36..1075).
private const val DPAD_X = 159f
private const val DPAD_Y = 503.5f
private const val DPAD_W = 282f
private const val DPAD_H = 300f
private const val DPAD_CENTER_X = 300f
private const val DPAD_CENTER_Y = 653.5f

/** Uniform fit sized to the visible controller body, using only the transparent side margins as bleed. */
fun saturnControllerV12PortraitArtworkSize(availableWidth: Dp, availableHeight: Dp): DpSize {
    val scale = minOf(availableWidth / BODY_W, availableHeight / DESIGN_H)
    return DpSize(DESIGN_W * scale, DESIGN_H * scale)
}

/** One event on press and one on release; implement emulator key-down/key-up in the host app. */
enum class SaturnButton { L, R, MENU, SAVE, LOAD, X, Y, Z, A, B, C, START, FILTERS }
enum class SaturnDirection { UP, UP_RIGHT, RIGHT, DOWN_RIGHT, DOWN, DOWN_LEFT, LEFT, UP_LEFT }

private data class ControlRect(
    val button: SaturnButton, val x: Float, val y: Float, val w: Float, val h: Float,
    val resId: Int
) {
    fun contains(p: Offset) = p.x >= x && p.x <= x + w && p.y >= y && p.y <= y + h
}

private val controls = listOf(
    ControlRect(SaturnButton.L, 94f, 88f, 232f, 163f, R.drawable.saturn_l),
    ControlRect(SaturnButton.R, 770f, 88f, 244f, 163f, R.drawable.saturn_r),
    ControlRect(SaturnButton.MENU, 463f, 127f, 165f, 77f, R.drawable.saturn_menu),
    ControlRect(SaturnButton.SAVE, 253f, 250f, 92f, 88f, R.drawable.saturn_save),
    ControlRect(SaturnButton.LOAD, 745f, 250f, 92f, 88f, R.drawable.saturn_load),
    ControlRect(SaturnButton.X, 559f, 538f, 133f, 130f, R.drawable.saturn_x),
    ControlRect(SaturnButton.Y, 683f, 473f, 129f, 125f, R.drawable.saturn_y),
    ControlRect(SaturnButton.Z, 818f, 432f, 134f, 125f, R.drawable.saturn_z),
    ControlRect(SaturnButton.A, 575f, 679f, 147f, 146f, R.drawable.saturn_a),
    ControlRect(SaturnButton.B, 713f, 605f, 147f, 144f, R.drawable.saturn_b),
    ControlRect(SaturnButton.C, 864f, 572f, 150f, 147f, R.drawable.saturn_c),
    ControlRect(SaturnButton.START, 455f, 919f, 178f, 85f, R.drawable.saturn_start),
    ControlRect(SaturnButton.FILTERS, 449f, 1080f, 193f, 90f, R.drawable.saturn_filters)
)

private enum class InputTarget { BUTTON, DPAD, FPS, FAST }
private data class Press(val target: InputTarget, val button: SaturnButton? = null, val direction: SaturnDirection? = null)

private fun hitTest(p: Offset): Press? {
    // FPS/FAST interactive hit areas in the original V12 (centerX - 50, y=1104, 200x100).
    if (p.y in 1104f..1204f && p.x in 194f..394f) return Press(InputTarget.FPS)
    if (p.y in 1104f..1204f && p.x in 725f..925f) return Press(InputTarget.FAST)
    if (insideDpad(p)) return Press(InputTarget.DPAD, direction = dpadDirection(p))
    // Buttons are evaluated from the foreground back to avoid overlap errors.
    return controls.asReversed().firstOrNull { it.contains(p) }?.let { Press(InputTarget.BUTTON, button = it.button) }
}

/** Circular hitbox centered on the replacement disk and covering the original bezel's full touch area. */
private fun insideDpad(p: Offset): Boolean {
    val nx = (p.x - DPAD_CENTER_X) / (319f / 2f)
    val ny = (p.y - DPAD_CENTER_Y) / (331f / 2f)
    return nx * nx + ny * ny <= 1f
}
private fun dpadDirection(p: Offset): SaturnDirection? {
    val dx = (p.x - DPAD_CENTER_X) / (319f / 2f)
    val dy = (p.y - DPAD_CENTER_Y) / (331f / 2f)
    val radius = hypot(dx, dy)
    if (radius > 1.02f || radius < .20f) return null
    val angle = (atan2(dy, dx) + 2 * PI).rem(2 * PI)
    val sector = (angle / (PI / 4)).roundToInt() % 8
    return arrayOf(
        SaturnDirection.RIGHT, SaturnDirection.DOWN_RIGHT, SaturnDirection.DOWN,
        SaturnDirection.DOWN_LEFT, SaturnDirection.LEFT, SaturnDirection.UP_LEFT,
        SaturnDirection.UP, SaturnDirection.UP_RIGHT
    )[sector]
}

private fun SaturnDirection.vector(): Pair<Int, Int> = when (this) {
    SaturnDirection.UP -> 0 to -1
    SaturnDirection.UP_RIGHT -> 1 to -1
    SaturnDirection.RIGHT -> 1 to 0
    SaturnDirection.DOWN_RIGHT -> 1 to 1
    SaturnDirection.DOWN -> 0 to 1
    SaturnDirection.DOWN_LEFT -> -1 to 1
    SaturnDirection.LEFT -> -1 to 0
    SaturnDirection.UP_LEFT -> -1 to -1
}

/**
 * Drop-in visual Saturn controller. Requires Compose foundation/runtime/ui and the bundled drawables.
 * The host controls FPS/FAST state and receives button and D-pad events.
 *
 * Do not route MENU/SAVE/LOAD/FAST into the emulation core without mapping them explicitly.
 */
@Composable
fun SaturnControllerV12(
    modifier: Modifier = Modifier,
    fpsEnabled: Boolean = false,
    fastEnabled: Boolean = false,
    hapticsEnabled: Boolean = true,
    onButton: (SaturnButton, Boolean) -> Unit,
    onDirection: (SaturnDirection, Boolean) -> Unit,
    onFpsChange: (Boolean) -> Unit,
    onFastChange: (Boolean) -> Unit
) {
    val down = remember { mutableStateMapOf<SaturnButton, Boolean>() }
    var direction by remember { mutableStateOf<SaturnDirection?>(null) }
    val currentOnButton by rememberUpdatedState(onButton)
    val currentOnDirection by rememberUpdatedState(onDirection)
    val currentOnFpsChange by rememberUpdatedState(onFpsChange)
    val currentOnFastChange by rememberUpdatedState(onFastChange)
    val currentFps by rememberUpdatedState(fpsEnabled)
    val currentFast by rememberUpdatedState(fastEnabled)
    val haptic = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = modifier.aspectRatio(DESIGN_W / DESIGN_H)
            .pointerInput(hapticsEnabled) {
                val scaleX = size.width / DESIGN_W
                val scaleY = size.height / DESIGN_H
                // Pointer-id ownership ensures simultaneous buttons, diagonals and L/R work.
                awaitEachGesture {
                    val held = mutableMapOf<PointerId, Press>()
                    fun reconcile() {
                        val newButtons = held.values.mapNotNull { it.button }.toSet()
                        for (button in SaturnButton.entries) {
                            val now = button in newButtons
                            if ((down[button] == true) != now) {
                                if (now) down[button] = true else down.remove(button)
                                currentOnButton(button, now)
                            }
                        }
                        val newDir = held.values.firstOrNull { it.target == InputTarget.DPAD }?.direction
                        if (newDir != direction) {
                            direction?.let { currentOnDirection(it, false) }
                            direction = newDir
                            newDir?.let {
                                currentOnDirection(it, true)
                                if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                    try {
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                val p = Offset(change.position.x / scaleX, change.position.y / scaleY)
                                if (change.changedToDown()) {
                                    hitTest(p)?.let {
                                        held[change.id] = it
                                        change.consume()
                                        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                } else if (change.changedToUp()) {
                                    val original = held.remove(change.id)
                                    val released = hitTest(p)
                                    when (original?.target) {
                                        InputTarget.FPS -> if (released?.target == InputTarget.FPS) currentOnFpsChange(!currentFps)
                                        InputTarget.FAST -> if (released?.target == InputTarget.FAST) currentOnFastChange(!currentFast)
                                        else -> Unit
                                    }
                                    if (original != null) change.consume()
                                } else if (change.pressed) {
                                    val old = held[change.id]
                                    if (old?.target == InputTarget.DPAD) {
                                        held[change.id] = old.copy(direction = dpadDirection(p))
                                        change.consume()
                                    } else if (old != null) {
                                        change.consume()
                                    }
                                }
                            }
                            reconcile()
                        } while (event.changes.any { it.pressed })
                    } finally {
                        held.clear()
                        reconcile()
                    }
                }
            }
    ) {
        val sx = maxWidth / DESIGN_W
        val sy = maxHeight / DESIGN_H
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        Image(
            painter = painterResource(R.drawable.saturn_base), contentDescription = "Saturn controller",
            modifier = Modifier.size(maxWidth, maxHeight), contentScale = ContentScale.FillBounds
        )

        for (c in controls) {
            val pressed = down[c.button] == true
            val isFace = c.button in setOf(SaturnButton.X, SaturnButton.Y, SaturnButton.Z,
                SaturnButton.A, SaturnButton.B, SaturnButton.C)
            val isRound = c.button == SaturnButton.SAVE || c.button == SaturnButton.LOAD
            val isPill = c.button in setOf(SaturnButton.MENU, SaturnButton.START, SaturnButton.FILTERS)
            val depth = if (isFace || isRound || isPill || c.button == SaturnButton.L || c.button == SaturnButton.R) .04f else .02f
            val targetY = if (pressed) c.h * depth * (heightPx / DESIGN_H) else 0f
            val ty by animateFloatAsState(targetY, tween(105), label = "${c.button} depression")
            val mask = when {
                isFace || isRound -> CircleShape
                isPill -> RoundedCornerShape(percent = 50)
                else -> null // L/R sprites have intrinsic transparency: never rectangularly clip them.
            }
            val m = Modifier
                .offset(x = sx * c.x, y = sy * c.y)
                .size(width = sx * c.w, height = sy * c.h)
                .graphicsLayer { translationY = ty }
                .let { if (mask != null) it.clip(mask) else it }
            Image(
                painter = painterResource(c.resId), contentDescription = null,
                modifier = m, contentScale = ContentScale.FillBounds
            )
        }

        val dpVec = direction?.vector()
        val dx = dpVec?.first ?: 0
        val dy = dpVec?.second ?: 0
        val dpadPressed by animateFloatAsState(if (direction == null) 0f else 1f, tween(85), label = "dpad press")
        val padX by animateFloatAsState(if (direction != null) dx * 319f * .0115f * widthPx / DESIGN_W else 0f, tween(85), label = "dpad x")
        val padY by animateFloatAsState(if (direction != null) (1.5f + dy * .75f) / 100f * 331f * heightPx / DESIGN_H else 0f, tween(85), label = "dpad y")
        // The base artwork has a transparent cutout here. Draw the original D-pad sprite as a
        // separate layer so its physical artwork moves with the press animation, with no duplicate
        // stationary D-pad left underneath it.
        Image(
            painter = painterResource(R.drawable.saturn_dpad), contentDescription = null,
            Modifier.offset(x = sx * DPAD_X, y = sy * DPAD_Y)
                .size(sx * DPAD_W, sy * DPAD_H)
                // The source sprite is rectangular and has the controller panel in its corners.
                // Clip to the D-pad's oval bezel so its background corners do not cover gameplay.
                .clip(CircleShape)
                .graphicsLayer {
                    translationX = padX
                    translationY = padY
                    rotationX = -5f * dy * dpadPressed
                    rotationY = 5f * dx * dpadPressed
                    cameraDistance = 12f * density
                },
            // Keep the supplied 143x152 crop proportional inside the design hit region.
            contentScale = ContentScale.Fit
        )

        // The base image already includes BOTH empty tracks. Only move one knob per toggle.
        val fpsTravel by animateFloatAsState(if (fpsEnabled) 94.08f else 0f, tween(175), label = "fps toggle")
        val fastTravel by animateFloatAsState(if (fastEnabled) 94.08f else 0f, tween(175), label = "fast toggle")
        Image(
            painter = painterResource(R.drawable.saturn_knob_fps), contentDescription = null,
            modifier = Modifier.offset(x = sx * (202f + fpsTravel), y = sy * 1111f)
                .size(sx * 84f, sy * 84f), contentScale = ContentScale.FillBounds
        )
        Image(
            painter = painterResource(R.drawable.saturn_knob_fast), contentDescription = null,
            modifier = Modifier.offset(x = sx * (733f + fastTravel), y = sy * 1111f)
                .size(sx * 84f, sy * 84f), contentScale = ContentScale.FillBounds
        )
    }
}
