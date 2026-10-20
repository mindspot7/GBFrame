package gb.ui

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
