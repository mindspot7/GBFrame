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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
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
private const val DPAD_X = 93f
private const val DPAD_Y = 446.5f
private const val DPAD_W = 414f
private const val DPAD_H = 414f
private const val DPAD_CENTER_X = 300f
private const val DPAD_CENTER_Y = 653.5f

/** Uniform fit sized to the visible controller body, using only the transparent side margins as bleed. */
fun saturnControllerV12PortraitArtworkSize(availableWidth: Dp, availableHeight: Dp): DpSize {
    val scale = minOf(availableWidth / BODY_W, availableHeight / DESIGN_H)
    return DpSize(scale * DESIGN_W, scale * DESIGN_H)
}

/** One event on press and one on release; implement emulator key-down/key-up in the host app. */
enum class SaturnButton { L, R, MENU, SAVE, LOAD, X, Y, Z, A, B, C, START, FILTERS }
enum class SaturnDirection { UP, UP_RIGHT, RIGHT, DOWN_RIGHT, DOWN, DOWN_LEFT, LEFT, UP_LEFT }

private data class ControlRect(
    val button: SaturnButton, val x: Float, val y: Float, val w: Float, val h: Float,
    val resId: Int,
    /** Hit area in design px; it follows the visible cap (and the printed label, for the small top buttons). */
    val hitX: Float, val hitY: Float, val hitW: Float, val hitH: Float,
    /** Visible cap inside the sprite (sprite px): the press mask is cut around it, not around the sprite box. */
    val cap: Cap?,
) {
    fun contains(p: Offset) = p.x >= hitX && p.x <= hitX + hitW && p.y >= hitY && p.y <= hitY + hitH
}

// Sprites are crops of saturn_base.png at x/y (the top band follows the owner's L/FAST/FPS/R art; SAVE/LOAD sit beside
// the SEGA logo; MENU, START and FILTERS share the bottom row). Face-button sprites carry their well on the upper-left, so their hit areas are
// centered on the visible cap instead of the sprite box. L/R sprites have an alpha cut-out of the shoulder cap.
private val controls = listOf(
    ControlRect(SaturnButton.L, 62f, 70f, 256f, 159f, R.drawable.saturn_l, 52f, 58f, 276f, 180f, null),
    ControlRect(SaturnButton.R, 802f, 70f, 255f, 158f, R.drawable.saturn_r, 792f, 58f, 276f, 180f, null),
    ControlRect(SaturnButton.MENU, 201f, 1109f, 165f, 77f, R.drawable.saturn_menu, 211.5f, 1108f, 165f, 77f, Cap(22f, 14f, 142f, 47f)),
    ControlRect(SaturnButton.SAVE, 253f, 268f, 92f, 88f, R.drawable.saturn_save, 248f, 260f, 110f, 143f, Cap(20f, 22f, 59f, 53f)),
    ControlRect(SaturnButton.LOAD, 745f, 268f, 92f, 88f, R.drawable.saturn_load, 753f, 260f, 110f, 143f, Cap(34f, 22f, 58f, 53f)),
    ControlRect(SaturnButton.X, 559f, 538f, 136f, 130f, R.drawable.saturn_x, 572.4f, 542.8f, 133f, 130f, Cap(32.9f, 22.8f, 94f, 94f)),
    ControlRect(SaturnButton.Y, 683f, 473f, 136f, 125f, R.drawable.saturn_y, 700f, 472.5f, 129f, 125f, Cap(36.5f, 16.9f, 90.1f, 90.1f)),
    ControlRect(SaturnButton.Z, 818f, 432f, 139f, 125f, R.drawable.saturn_z, 835.1f, 436.4f, 134f, 125f, Cap(38.8f, 21.6f, 90.6f, 90.6f)),
    ControlRect(SaturnButton.A, 575f, 679f, 159f, 155f, R.drawable.saturn_a, 593.1f, 692.9f, 147f, 146f, Cap(33.5f, 28.8f, 116.2f, 116.2f)),
    ControlRect(SaturnButton.B, 713f, 605f, 160f, 154f, R.drawable.saturn_b, 734.5f, 622.4f, 147f, 144f, Cap(39.8f, 34.2f, 110.3f, 110.3f)),
    ControlRect(SaturnButton.C, 864f, 572f, 162f, 152f, R.drawable.saturn_c, 882.3f, 581.5f, 150f, 147f, Cap(33.9f, 23.6f, 118.7f, 118.7f)),
    ControlRect(SaturnButton.START, 455f, 1086f, 178f, 85f, R.drawable.saturn_start, 467f, 1104f, 178f, 85f, Cap(27f, 36f, 148f, 49f)),
    ControlRect(SaturnButton.FILTERS, 711f, 1080f, 193f, 90f, R.drawable.saturn_filters, 721f, 1101f, 193f, 90f, Cap(27f, 42f, 159f, 48f))
)

/** Slide switches of the top band: the knob sprite slides inside the empty printed track (knob right = on). */
private class SlideSwitch(val offX: Float, val y: Float, val w: Float, val h: Float, val hit: Rect)
private const val SWITCH_TRAVEL = 16.1f
private val fastSwitch = SlideSwitch(386f, 142f, 105f, 44f, Rect(358f, 75f, 533f, 206f))
private val fpsSwitch = SlideSwitch(605f, 142f, 105f, 44f, Rect(578f, 75f, 753f, 206f))

private data class Cap(val x: Float, val y: Float, val w: Float, val h: Float)

/** Rim of the button's dark border kept around the cap, in sprite px. */
private const val CAP_RIM = 7f

/**
 * Mask around the visible cap plus its rim. The sprites are square crops of the base art with the well on the
 * upper-left, so a circle on the sprite box showed an offset disc of the sprite's background behind each button.
 */
private fun capMask(c: ControlRect, cap: Cap, round: Boolean) = GenericShape { size, _ ->
    val kx = size.width / c.w
    val ky = size.height / c.h
    if (round) {
        val r = maxOf(cap.w, cap.h) / 2f + CAP_RIM
        val cx = cap.x + cap.w / 2f
        val cy = cap.y + cap.h / 2f
        addOval(Rect((cx - r) * kx, (cy - r) * ky, (cx + r) * kx, (cy + r) * ky))
    } else {
        val rect = Rect((cap.x - CAP_RIM) * kx, (cap.y - CAP_RIM) * ky, (cap.x + cap.w + CAP_RIM) * kx, (cap.y + cap.h + CAP_RIM) * ky)
        addRoundRect(RoundRect(rect, CornerRadius(rect.height / 2f)))
    }
}

private enum class InputTarget { BUTTON, DPAD, FPS, FAST }
private data class Press(val target: InputTarget, val button: SaturnButton? = null, val direction: SaturnDirection? = null)

private fun hitTest(p: Offset): Press? {
    // FAST/FPS switches: the track and its printed label.
    if (fpsSwitch.hit.contains(p)) return Press(InputTarget.FPS)
    if (fastSwitch.hit.contains(p)) return Press(InputTarget.FAST)
    if (insideDpad(p)) return Press(InputTarget.DPAD, direction = dpadDirection(p))
    // Buttons are evaluated from the foreground back to avoid overlap errors.
    return controls.asReversed().firstOrNull { it.contains(p) }?.let { Press(InputTarget.BUTTON, button = it.button) }
}

/** Circular hit area matches the directional disk, inside the larger visible bezel. */
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
                // Pointer-id ownership ensures simultaneous buttons, diagonals and L/R work.
                awaitEachGesture {
                    // Read the size on every gesture: the pointer coroutine outlives resizes (system bars hiding after
                    // the game opens grow the artwork), and a scale captured once left every hit area offset from the art.
                    val scaleX = size.width / DESIGN_W
                    val scaleY = size.height / DESIGN_H
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
                c.cap == null -> null // L/R sprites have intrinsic transparency: never rectangularly clip them.
                else -> capMask(c, c.cap, round = isFace || isRound)
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
        // Keep the supplied outer bezel stationary; only the directional disk below moves on press.
        Image(
            painter = painterResource(R.drawable.gb_saturn_v12_dpad_bezel), contentDescription = null,
            Modifier.offset(x = sx * DPAD_X, y = sy * DPAD_Y)
                .size(sx * DPAD_W, sy * DPAD_H),
            contentScale = ContentScale.Fit
        )
        Image(
            painter = painterResource(R.drawable.gb_saturn_v12_dpad_disk), contentDescription = null,
            Modifier.offset(x = sx * DPAD_X, y = sy * DPAD_Y)
                .size(sx * DPAD_W, sy * DPAD_H)
                .graphicsLayer {
                    translationX = padX
                    translationY = padY
                    rotationX = -5f * dy * dpadPressed
                    rotationY = 5f * dx * dpadPressed
                    cameraDistance = 12f * density
                },
            contentScale = ContentScale.Fit
        )

        // The base image has both tracks empty; each knob slides inside its own.
        val fpsTravel by animateFloatAsState(if (fpsEnabled) SWITCH_TRAVEL else 0f, tween(140), label = "fps toggle")
        val fastTravel by animateFloatAsState(if (fastEnabled) SWITCH_TRAVEL else 0f, tween(140), label = "fast toggle")
        for ((switch, travel, res) in listOf(
            Triple(fpsSwitch, fpsTravel, R.drawable.saturn_knob_fps),
            Triple(fastSwitch, fastTravel, R.drawable.saturn_knob_fast),
        )) {
            Image(
                painter = painterResource(res), contentDescription = null,
                modifier = Modifier.offset(x = sx * (switch.offX + travel), y = sy * switch.y)
                    .size(sx * switch.w, sy * switch.h), contentScale = ContentScale.FillBounds
            )
        }
    }
}
