package gb.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Where the directional control sits: fixed in the artwork, or appearing under the thumb (CONVENTIONS "Touch"). */
enum class TouchDpadMode { STATIC, DYNAMIC }

/**
 * Floating joystick maths, free of any UI: the first touch becomes the centre, the knob follows the finger up to
 * [radius], and the output vector is the displacement divided by [radius] (each axis in -1..1). With [follow], a finger
 * that goes past [radius] drags the centre along, so the stick keeps up with the thumb.
 */
class FloatingStick(val radius: Float, val follow: Boolean = true) {
    /** Centre of the base while held, null while idle. */
    var center: Offset? = null
        private set
    /** Knob displacement from [center], never longer than [radius]. */
    var knob: Offset = Offset.Zero
        private set
    /** Normalized output: x right, y down, each in -1..1; (0, 0) while idle. */
    val vector: Offset get() = if (center == null || radius <= 0f) Offset.Zero else knob / radius
    val active: Boolean get() = center != null

    fun down(p: Offset) {
        center = p
        knob = Offset.Zero
    }

    fun move(p: Offset) {
        val c = center ?: return
        var d = p - c
        val len = hypot(d.x, d.y)
        if (len > radius && len > 0f) {
            val clamped = d * (radius / len)
            if (follow) center = p - clamped // the base slides along the same direction as the finger
            d = clamped
        }
        knob = d
    }

    fun up() {
        center = null
        knob = Offset.Zero
    }
}

/**
 * The 8-way digital directions of a stick [vector] (as [FloatingStick.vector]): nothing inside [deadZone], otherwise
 * the 45° sector the vector points into (diagonals report two directions).
 */
fun stickDirections(vector: Offset, deadZone: Float = .3f): Set<TouchDirection> {
    if (hypot(vector.x, vector.y) < deadZone) return emptySet()
    val sector = ((atan2(vector.y, vector.x) + 2 * PI) / (PI / 4)).roundToInt() % 8
    return when (sector) {
        0 -> setOf(TouchDirection.RIGHT)
        1 -> setOf(TouchDirection.RIGHT, TouchDirection.DOWN)
        2 -> setOf(TouchDirection.DOWN)
        3 -> setOf(TouchDirection.DOWN, TouchDirection.LEFT)
        4 -> setOf(TouchDirection.LEFT)
        5 -> setOf(TouchDirection.LEFT, TouchDirection.UP)
        6 -> setOf(TouchDirection.UP)
        else -> setOf(TouchDirection.UP, TouchDirection.RIGHT)
    }
}

/**
 * Touch zone for a dynamic directional control. A touch anywhere inside it places the stick there and [content] draws
 * the base at `center` with the knob moved by `knob` (both in this zone's pixels). Idle it draws nothing, except at
 * [restCenter] (when given) until the first touch, so the player sees where the control is before using it. It reports
 * the 8-way [onDirections] (haptic tick on each change) and the analog [onVector] (each axis -1..1, (0, 0) on release).
 * [radiusPx] is the stick's travel.
 */
@Composable
fun FloatingDPadZone(
    modifier: Modifier,
    radiusPx: Float,
    hapticsEnabled: Boolean = true,
    follow: Boolean = true,
    onDirections: (Set<TouchDirection>) -> Unit,
    onVector: (Offset) -> Unit = {},
    restCenter: Offset? = null,
    content: @Composable BoxScope.(center: Offset, knob: Offset) -> Unit,
) {
    var touched by remember { mutableStateOf(false) }
    val stick = remember(radiusPx, follow) { FloatingStick(radiusPx, follow) }
    var center by remember { mutableStateOf<Offset?>(null) }
    var knob by remember { mutableStateOf(Offset.Zero) }
    val haptic = LocalHapticFeedback.current
    val currentDirections by rememberUpdatedState(onDirections)
    val currentVector by rememberUpdatedState(onVector)
    Box(
        modifier.pointerInput(stick, hapticsEnabled) {
            awaitPointerEventScope {
                var owner: PointerId? = null
                var last = emptySet<TouchDirection>()
                fun publish() {
                    center = stick.center
                    knob = stick.knob
                    currentVector(stick.vector)
                    val dirs = stickDirections(stick.vector)
                    if (dirs != last) {
                        if (hapticsEnabled && dirs.isNotEmpty()) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        last = dirs
                        currentDirections(dirs)
                    }
                }
                while (true) {
                    val event = awaitPointerEvent()
                    for (change in event.changes) {
                        when {
                            owner == null && change.pressed && !change.previousPressed -> {
                                owner = change.id
                                touched = true
                                stick.down(change.position)
                                change.consume()
                                publish()
                            }
                            change.id == owner && change.pressed -> {
                                stick.move(change.position)
                                change.consume()
                                publish()
                            }
                            change.id == owner && !change.pressed -> {
                                owner = null
                                stick.up()
                                change.consume()
                                publish()
                            }
                        }
                    }
                }
            }
        },
    ) {
        (center ?: restCenter?.takeIf { !touched })?.let { content(it, if (center != null) knob else Offset.Zero) }
    }
}
