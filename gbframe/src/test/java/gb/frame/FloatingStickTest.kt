package gb.frame

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingStickTest {
    @Test
    fun firstTouchBecomesTheCentre() {
        val s = FloatingStick(100f)
        s.down(Offset(300f, 400f))
        assertEquals(Offset(300f, 400f), s.center)
        assertEquals(Offset.Zero, s.vector)
    }

    @Test
    fun vectorIsNormalizedAndKnobClampedToRadius() {
        val s = FloatingStick(100f, follow = false)
        s.down(Offset(0f, 0f))
        s.move(Offset(50f, 0f))
        assertEquals(.5f, s.vector.x, 1e-4f)
        s.move(Offset(0f, -400f))
        assertEquals(-1f, s.vector.y, 1e-4f)
        assertEquals(100f, s.knob.getDistance(), 1e-3f)
        assertEquals(Offset(0f, 0f), s.center) // without follow the base stays put
    }

    @Test
    fun followDragsTheCentreAlongPastTheRadius() {
        val s = FloatingStick(100f, follow = true)
        s.down(Offset(0f, 0f))
        s.move(Offset(250f, 0f))
        assertEquals(Offset(150f, 0f), s.center)
        assertEquals(1f, s.vector.x, 1e-4f)
        // Coming back moves the knob inside the new base right away
        s.move(Offset(150f, 0f))
        assertEquals(0f, s.vector.x, 1e-4f)
    }

    @Test
    fun releaseResetsToIdle() {
        val s = FloatingStick(100f)
        s.down(Offset(10f, 10f)); s.move(Offset(90f, 10f)); s.up()
        assertFalse(s.active)
        assertEquals(Offset.Zero, s.vector)
    }

    @Test
    fun directionsUseDeadZoneAndEightSectors() {
        assertTrue(stickDirections(Offset(.1f, .1f)).isEmpty())
        assertEquals(setOf(TouchDirection.RIGHT), stickDirections(Offset(1f, 0f)))
        assertEquals(setOf(TouchDirection.UP), stickDirections(Offset(0f, -1f)))
        assertEquals(setOf(TouchDirection.RIGHT, TouchDirection.DOWN), stickDirections(Offset(.7f, .7f)))
        assertEquals(setOf(TouchDirection.LEFT, TouchDirection.UP), stickDirections(Offset(-.7f, -.7f)))
    }
}
