package gb.frame

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlsCaptureTest {
    @Test
    fun selectR2CombinationIgnoresCompanionBackAndSavesTheChord() {
        val host = Host().apply { hotkeyCapture = HotkeyAction.FastForward }

        assertTrue(send(host, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.ACTION_DOWN))
        assertTrue(send(host, KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN))
        assertTrue(send(host, KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.ACTION_DOWN))
        assertTrue(send(host, KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.ACTION_UP))
        assertTrue(send(host, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.ACTION_UP))
        // The companion Back release can arrive after the last Select release completed capture.
        assertTrue(send(host, KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP))

        assertEquals(HotkeyAction.FastForward to setOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_R2), host.binding)
        assertFalse(ControlsCapture.active(host))
    }

    @Test
    fun cancelingCaptureClearsKeysStillHeldByThePreviousPrompt() {
        val host = Host().apply { hotkeyCapture = HotkeyAction.FastForward }

        assertTrue(send(host, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.ACTION_DOWN))
        host.hotkeyCapture = null
        assertFalse(send(host, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.ACTION_UP))

        host.hotkeyCapture = HotkeyAction.FastForward
        host.hotkeyCaptureKeys = emptySet()
        assertTrue(send(host, KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.ACTION_DOWN))
        assertTrue(send(host, KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.ACTION_UP))

        assertEquals(HotkeyAction.FastForward to setOf(KeyEvent.KEYCODE_BUTTON_R2), host.binding)
    }

    private fun send(host: Host, keyCode: Int, action: Int) =
        ControlsCapture.dispatchKeyEvent(keyCode, action, repeatCount = 0, fromPad = true, host = host)

    private class Host : ControlsHost {
        override val console = "Test"
        override val pad: PadLayout get() = error("Controller layout is unused in capture tests")
        override val buttons = emptyList<ControlButton>()
        override fun keysFor(id: String) = emptyList<Int>()
        override fun bindKey(id: String, keyCode: Int) = Unit
        override var remapTarget: String? = null
        private var storedHotkeys = emptyMap<HotkeyAction, Set<Int>>()
        override val hotkeys: Map<HotkeyAction, Set<Int>> get() = storedHotkeys
        var binding: Pair<HotkeyAction, Set<Int>>? = null
            private set
        override fun bindHotkey(action: HotkeyAction, keys: Set<Int>) {
            binding = action to keys
            storedHotkeys = storedHotkeys + (action to keys)
        }
        override var hotkeyCapture: HotkeyAction? = null
        override var hotkeyCaptureKeys: Set<Int> = emptySet()
        override var fastForwardToggle = false
        override var showTouchControls = true
        override fun resetKeyMap() = Unit
    }
}
