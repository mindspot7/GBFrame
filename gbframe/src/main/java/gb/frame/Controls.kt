package gb.frame

import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A console button as listed on the Controls page. [id] is the same id the [PadLayout] uses for it; the badge shows
 * [badge] text or [icon] on [color].
 */
data class ControlButton(
    val id: String, val label: String, val badge: String? = null, val icon: ImageVector? = null,
    val color: Color = Color(0xFF47435F),
)

/**
 * What the standard Controls page ([ControlsPage], [ControlsCapturePrompts], [ControlsCapture]) needs from an app: its
 * console's controller drawing and buttons, and where bindings and options live. Everything else (tabs, drawing, rows,
 * the "press a button" prompts, hotkeys) is the kit's, so every app looks and behaves the same. Properties that change
 * while the page is open must be Compose state in the app (read them from `mutableStateOf`).
 */
interface ControlsHost {
    /** The console's short name, e.g. "Saturn": the first card is titled "<console> controller". */
    val console: String
    /** The controller drawn for mapping; its button ids match [buttons]. */
    val pad: PadLayout
    /** Buttons in the order of the mapping list. */
    val buttons: List<ControlButton>
    fun keysFor(id: String): List<Int>
    /** Makes [keyCode] press the console button [id] (and frees the key from any other button). */
    fun bindKey(id: String, keyCode: Int)
    /** The console button waiting for a physical key press, or null. */
    var remapTarget: String?
    fun resetKeyMap()
    val hotkeys: Map<HotkeyAction, Set<Int>>
    fun bindHotkey(action: HotkeyAction, keys: Set<Int>)
    /** The hotkey waiting for a button (or combination), or null. */
    var hotkeyCapture: HotkeyAction?
    /** The keys held so far while capturing a hotkey. */
    var hotkeyCaptureKeys: Set<Int>
    /** Fast-forward hotkey: false = hold, true = toggle. The app saves the change. */
    var fastForwardToggle: Boolean
    /** Show the on-screen controls (they hide by themselves while a controller is connected). The app saves the change. */
    var showTouchControls: Boolean
}

/**
 * The standard Controls page of every app built on the kit (Settings → Controls): Controller / Hotkeys / Touch tabs.
 * [controllerExtras] adds console-specific rows to the first card (e.g. the controller type); the button mapping with the
 * console's controller drawing, the hotkeys and the touch option are the same everywhere.
 */
@Composable
fun ControlsPage(host: ControlsHost, controllerExtras: (@Composable ColumnScope.() -> Unit)? = null) {
    val p = palette
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Controller" to Icons.Default.SportsEsports, "Hotkeys" to Icons.Default.Keyboard, "Touch" to Icons.Default.TouchApp)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.card).border(p.panelBorder, RoundedCornerShape(20.dp)).padding(4.dp)) {
        tabs.forEachIndexed { i, (label, icon) ->
            val sel = i == tab
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (sel) p.highlight.copy(alpha = .22f) else Color.Transparent)
                    .clickable { tab = i }.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = if (sel) p.highlight else p.dim, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = if (sel) Color.White else p.dim, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
    when (tab) {
        0 -> {
            if (controllerExtras != null) SettingsGroup("${host.console} controller", Icons.Default.SportsEsports) { controllerExtras() }
            SettingsGroup("Button mapping", Icons.Default.Gamepad) {
                Text("Tap a button on the pad or in the list, then press the button on your controller.",
                    style = MaterialTheme.typography.bodySmall, color = p.dim, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PadDiagram(host.pad, host.remapTarget, { host.remapTarget = it },
                        Modifier.widthIn(max = if (host.pad.aspectRatio < 1.3f) 360.dp else 760.dp)
                            .fillMaxWidth().padding(horizontal = 12.dp).aspectRatio(host.pad.aspectRatio))
                }
                Spacer(Modifier.height(8.dp))
                host.buttons.forEachIndexed { i, b ->
                    if (i > 0) SettingsDivider()
                    val keys = host.keysFor(b.id).joinToString(", ") { keyName(it) }
                    ControlButtonRow(b, keys.ifEmpty { "Unassigned" }, selected = host.remapTarget == b.id) { host.remapTarget = b.id }
                }
                SettingsDivider()
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { host.resetKeyMap() }) { Icon(Icons.Default.RestartAlt, null); Spacer(Modifier.width(6.dp)); Text("Reset to defaults") }
                }
            }
        }
        1 -> {
            SettingsGroup("Hotkeys", Icons.Default.Keyboard) {
                Text("Bind one button, or hold several together (a combination) and release. Buttons in a combination are not sent to the game.",
                    style = MaterialTheme.typography.bodySmall, color = p.dim, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                HotkeyAction.entries.forEachIndexed { i, a ->
                    if (i > 0) SettingsDivider()
                    HotkeyRow(host, a, host.hotkeys[a].orEmpty())
                }
            }
            SettingsGroup("Fast-forward", Icons.Default.FastForward) {
                ChoiceRow("Fast-forward hotkey", "Hold: fast while held · Toggle: press once to switch on or off",
                    listOf("Hold", "Toggle"), if (host.fastForwardToggle) 1 else 0) { host.fastForwardToggle = it == 1 }
            }
        }
        else -> {
            SettingsGroup("On-screen controls", Icons.Default.TouchApp) {
                ToggleRow(Icons.Default.TouchApp, "Show touch controls", "Hidden automatically while a controller is connected (handhelds included)",
                    host.showTouchControls) { host.showTouchControls = it }
            }
        }
    }
}

@Composable
private fun ControlButtonRow(b: ControlButton, keys: String, selected: Boolean, onRemap: () -> Unit) {
    val p = palette
    Row(
        Modifier.fillMaxWidth().background(if (selected) p.highlight.copy(alpha = .12f) else Color.Transparent).clickable(onClick = onRemap)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(b.color), contentAlignment = Alignment.Center) {
            if (b.icon != null) Icon(b.icon, null, tint = Color.White)
            else Text(b.badge ?: b.id, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.width(14.dp))
        Text(b.label, style = MaterialTheme.typography.titleSmall, color = Color.White, modifier = Modifier.weight(1f))
        Text(keys, style = MaterialTheme.typography.bodyMedium, color = if (keys == "Unassigned") p.warn else p.highlight)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.Edit, "Remap", tint = p.dim, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun HotkeyRow(host: ControlsHost, a: HotkeyAction, keys: Set<Int>) {
    val p = palette
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(when (a) {
            HotkeyAction.QuickSave -> Icons.Default.Save
            HotkeyAction.QuickLoad -> Icons.Default.Restore
            HotkeyAction.FastForward -> Icons.Default.FastForward
            HotkeyAction.Menu -> Icons.Default.Menu
        })
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(a.label, style = MaterialTheme.typography.titleSmall, color = Color.White)
            if (keys.isEmpty()) Text("Not set", style = MaterialTheme.typography.bodySmall, color = p.dim)
            else Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                keys.forEachIndexed { i, k ->
                    if (i > 0) Text("+", color = p.dim)
                    Text(keyName(k), color = Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.background(Color.White.copy(alpha = .12f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
        if (keys.isNotEmpty()) TextButton(onClick = { host.bindHotkey(a, emptySet()) }) { Text("Clear") }
        FilledTonalButton(onClick = { host.hotkeyCaptureKeys = emptySet(); host.hotkeyCapture = a }) { Text("Set") }
    }
}

/**
 * Remap / hotkey capture prompts, drawn in the app's own window (not a Dialog) so controller events still reach the
 * Activity, where [ControlsCapture] turns them into bindings. Put it over the settings screen.
 */
@Composable
fun ControlsCapturePrompts(host: ControlsHost) {
    val remap = host.remapTarget
    val hk = host.hotkeyCapture
    if (remap == null && hk == null) return
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .7f)), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.widthIn(max = 460.dp).padding(16.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (remap != null) {
                    Text("Remap ${host.buttons.firstOrNull { it.id == remap }?.label ?: remap}", style = MaterialTheme.typography.titleLarge)
                    PadDiagram(host.pad, remap, { host.remapTarget = it }, Modifier.fillMaxWidth().aspectRatio(host.pad.aspectRatio))
                    Text("Press the button on your controller you want to use for it…", color = palette.dim, textAlign = TextAlign.Center)
                    TextButton(onClick = { host.remapTarget = null }) { Text("Cancel") }
                } else if (hk != null) {
                    Text("Set ${hk.label}", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (host.hotkeyCaptureKeys.isEmpty()) "Press a button, or hold several buttons together, then release."
                        else host.hotkeyCaptureKeys.joinToString(" + ") { keyName(it) },
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { host.hotkeyCapture = null }) { Text("Cancel") }
                }
            }
        }
    }
}

/**
 * Turns physical button presses into bindings while a [ControlsCapturePrompts] prompt is open: the next press becomes the
 * button's key, and for a hotkey every key held until all are released becomes the combination. Call [dispatch] from the
 * Activity's `dispatchKeyEvent` before anything that sends keys to the game or the library.
 */
object ControlsCapture {
    private val held = HashSet<Int>()
    private var capturedBackRelease = false

    /** A remap or hotkey prompt is open (every button belongs to it). */
    fun active(host: ControlsHost) = host.remapTarget != null || host.hotkeyCapture != null

    /** Returns true when the event was consumed by a prompt. */
    fun dispatch(event: KeyEvent, host: ControlsHost): Boolean {
        // Some handhelds report face buttons with a keyboard source; accept any gamepad/keyboard source
        val fromPad = event.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD or InputDevice.SOURCE_KEYBOARD) != 0
        return dispatchKeyEvent(event.keyCode, event.action, event.repeatCount, fromPad, host)
    }

    /** Key-event state machine kept separate so combinations can be covered by local unit tests. */
    internal fun dispatchKeyEvent(keyCode: Int, eventAction: Int, repeatCount: Int, fromPad: Boolean, host: ControlsHost): Boolean {
        if (!active(host)) held.clear()

        // Some handhelds emit Android Back alongside a controller button (including Select). If it escapes the
        // capture prompt, Compose treats the companion event as Back and closes Settings before the combo is saved.
        // Back is not a gameplay hotkey; consume it during capture and its matching release, but don't add it to the bind.
        if (keyCode == KeyEvent.KEYCODE_BACK && (active(host) || capturedBackRelease)) {
            if (eventAction == KeyEvent.ACTION_DOWN) capturedBackRelease = true
            else if (eventAction == KeyEvent.ACTION_UP) capturedBackRelease = false
            return true
        }

        host.remapTarget?.let { target ->
            if (fromPad) {
                if (eventAction == KeyEvent.ACTION_DOWN && repeatCount == 0) {
                    host.bindKey(target, keyCode)
                    host.remapTarget = null
                }
                return true
            }
        }
        host.hotkeyCapture?.let { hotkeyAction ->
            if (fromPad) {
                if (eventAction == KeyEvent.ACTION_DOWN) {
                    held += keyCode
                    host.hotkeyCaptureKeys = host.hotkeyCaptureKeys + keyCode
                } else if (eventAction == KeyEvent.ACTION_UP) {
                    held -= keyCode
                    if (held.isEmpty() && host.hotkeyCaptureKeys.isNotEmpty()) {
                        host.bindHotkey(hotkeyAction, host.hotkeyCaptureKeys)
                        host.hotkeyCapture = null
                        host.hotkeyCaptureKeys = emptySet()
                    }
                }
                return true
            }
        }
        return false
    }
}
