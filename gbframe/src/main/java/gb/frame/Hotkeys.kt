package gb.frame

import android.view.KeyEvent

/** Emulator hotkeys (the same set in every app of the family). */
enum class HotkeyAction(val label: String) {
    QuickSave("Quick save"), QuickLoad("Quick load"), FastForward("Fast-forward"), Menu("Open menu"),
}

/** Human-readable name of a keycode, with L3/R3 spelled the way players expect. */
fun keyName(code: Int): String = when (code) {
    KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
    KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
    KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
    KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
    KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
    KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
    KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
    KeyEvent.KEYCODE_BUTTON_MODE -> "Mode"
    else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").removePrefix("BUTTON_")
}

/**
 * Hotkey detection over physical gamepad/keyboard keys. A binding is a set of keys: one key = "button" hotkey,
 * several keys = "combination" hotkey (all must be held). Keys that take part in a triggered combination are
 * hidden from the game until released, so a combo like L1+R1+Start does not also press those console buttons.
 *
 * ```
 * Hotkeys.configure(bindings = { myHotkeys }, run = { action, down -> ... })
 * ```
 */
object Hotkeys {
    private val pressed = HashSet<Int>()
    private val active = HashSet<HotkeyAction>()
    private val suppressed = HashSet<Int>()
    private var bindings: () -> Map<HotkeyAction, Set<Int>> = { emptyMap() }
    private var run: (HotkeyAction, Boolean) -> Unit = { _, _ -> }

    fun configure(bindings: () -> Map<HotkeyAction, Set<Int>>, run: (HotkeyAction, Boolean) -> Unit) {
        this.bindings = bindings
        this.run = run
    }

    fun isSuppressed(code: Int) = code in suppressed
    fun isMenuKey(code: Int) = code in bindings()[HotkeyAction.Menu].orEmpty()
    fun suppressedKeys(): Set<Int> = suppressed.toSet()

    /**
     * Returns true if the key was consumed by a hotkey: a single-button binding, or the release of a key that took part
     * in a triggered combination. That release must be consumed too: an unhandled key up makes Android send the key's
     * fallback (L3 → DPAD_CENTER in Generic.kcm), which clicked the focused item of the menu the combo had just opened.
     */
    fun onKey(code: Int, down: Boolean): Boolean {
        val wasSuppressed = code in suppressed
        if (down) pressed += code else { pressed -= code; suppressed -= code }
        var consumed = false
        val all = bindings()
        for (action in HotkeyAction.entries) {
            val binding = all[action].orEmpty()
            if (binding.isEmpty()) continue
            val satisfied = pressed.containsAll(binding)
            if (satisfied && action !in active) {
                active += action
                if (binding.size > 1) suppressed += binding
                run(action, true)
            } else if (!satisfied && action in active) {
                active -= action
                run(action, false)
            }
            if (binding.size == 1 && code in binding) consumed = true
        }
        return consumed || (!down && wasSuppressed)
    }

    fun reset() { pressed.clear(); active.clear(); suppressed.clear() }
}
