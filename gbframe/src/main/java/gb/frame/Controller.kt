package gb.frame

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/**
 * True when a real game controller is connected (built into a handheld, or paired/wired). A bare joystick-class source
 * is not enough: phones expose sensor, stylus and remote devices like that. It must report gamepad buttons.
 */
fun hasPhysicalController(context: Context): Boolean {
    val im = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    return im.inputDeviceIds.any { id ->
        val d = im.getInputDevice(id) ?: return@any false
        if (d.isVirtual) return@any false
        val gamepad = d.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        val joystick = d.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        (gamepad || joystick) && d.hasKeys(android.view.KeyEvent.KEYCODE_BUTTON_A)[0]
    }
}

/** True while a physical controller (gamepad/joystick) is connected; updates when one is plugged in or removed. */
@Composable
fun rememberHasGamepad(): Boolean {
    val ctx = LocalContext.current
    val im = remember { ctx.getSystemService(Context.INPUT_SERVICE) as InputManager }
    fun check() = hasPhysicalController(ctx)
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
    val compose = androidx.compose.ui.platform.ComposeView(this).apply {
        setContent {
            // Keeps the UI clear of the status/navigation bars while they are visible (touch-only device outside
            // games); the insets are zero when the bars are hidden.
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)
            ) { content() }
        }
    }
    val frame = object : android.widget.FrameLayout(this) {
        override fun dispatchKeyEventPreIme(event: android.view.KeyEvent): Boolean {
            if (isInTouchMode && event.keyCode in TouchModeNavKeys) {
                // Leave touch mode ourselves: the key is consumed here, so Android never does it, and in touch mode Compose
                // refuses to focus clickable items (the menu's first entry stayed unselected after a tap).
                if (event.action == android.view.KeyEvent.ACTION_DOWN) compose.requestFocusFromTouch()
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
