package gb.frame

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** True while a physical controller (gamepad/joystick) is connected; updates when one is plugged in or removed. */
@Composable
fun rememberHasGamepad(): Boolean {
    val ctx = LocalContext.current
    val im = remember { ctx.getSystemService(Context.INPUT_SERVICE) as InputManager }
    fun check() = im.inputDeviceIds.any { id ->
        val d = im.getInputDevice(id) ?: return@any false
        !d.isVirtual && (d.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            d.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
    }
    var has by remember { mutableStateOf(check()) }
    DisposableEffect(im) {
        val l = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(id: Int) { has = check() }
            override fun onInputDeviceRemoved(id: Int) { has = check() }
            override fun onInputDeviceChanged(id: Int) { has = check() }
        }
        im.registerInputDeviceListener(l, null)
        onDispose { im.unregisterInputDeviceListener(l) }
    }
    return has
}

/**
 * Like `setContent`, but the first D-pad press after a touch already moves the focus. Normally Android spends that press
 * leaving "touch mode" (ViewRootImpl consumes it before the app sees it), so after a tap two presses were needed to
 * move. Here D-pad keys that arrive in touch mode are sent straight to the Activity's key handling.
 * Call it on a ComponentActivity (its setContentView sets up the lifecycle owners Compose needs).
 */
fun android.app.Activity.setControllerContent(content: @androidx.compose.runtime.Composable () -> Unit) {
    val activity = this
    val compose = androidx.compose.ui.platform.ComposeView(this).apply { setContent(content) }
    val frame = object : android.widget.FrameLayout(this) {
        override fun dispatchKeyEventPreIme(event: android.view.KeyEvent): Boolean {
            if (isInTouchMode && event.keyCode in TouchModeNavKeys) {
                activity.dispatchKeyEvent(event)
                return true
            }
            return super.dispatchKeyEventPreIme(event)
        }
    }
    frame.addView(compose, android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT))
    setContentView(frame)
}

private val TouchModeNavKeys = setOf(
    android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN, android.view.KeyEvent.KEYCODE_DPAD_LEFT,
    android.view.KeyEvent.KEYCODE_DPAD_RIGHT, android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER,
)
