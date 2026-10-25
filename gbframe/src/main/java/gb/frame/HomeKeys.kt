package gb.frame

import android.view.KeyEvent

/**
 * Controller buttons of the library, the same in every app built on the kit:
 * **X** opens/closes the main menu, **Start** cycles the theme (Showcase, Grid, All in One), **Select** toggles the
 * library fullscreen, **A** confirms and **B** goes back ([LibraryBack]); **Y** opens the game options (handled by the
 * covers). The Activity calls [dispatch] first thing in `dispatchKeyEvent`, because Compose key handlers only see events
 * while something inside them is focused.
 */
object HomeKeys {
    private val dpadKeys = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER,
    )

    /**
     * Returns true when the event was consumed. [enabled] must be false while a game runs or the app is capturing the
     * next button (remapping): then every button belongs to the game or to the capture.
     */
    fun dispatch(event: KeyEvent, enabled: Boolean = true): Boolean {
        if (!enabled) return false
        val menu = MenuState
        // Quit dialog: A confirms (right after start nothing may have focus, so the dialog's button can't take the press)
        if (menu.quitOpen.value) {
            if (event.keyCode == KeyEvent.KEYCODE_BUTTON_A || event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                event.keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                if (event.action == KeyEvent.ACTION_UP) {
                    menu.quitOpen.value = false
                    menu.onQuit?.invoke()
                }
                return true
            }
            return false
        }
        if (!menu.libraryShown) return false
        val pressed = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        when (event.keyCode) {
            // Select is the console-style shortcut for switching the library between Default and FullScreen
            KeyEvent.KEYCODE_BUTTON_SELECT -> {
                if (pressed) {
                    HomeTheme.fullscreen.value = !HomeTheme.fullscreen.value
                    // Reclaim focus after fullscreen changes the library's layout; keep the menu selection if it is open
                    if (menu.open.value) menu.menuRefocus.intValue++ else menu.refocus.intValue++
                }
                return true
            }
            KeyEvent.KEYCODE_BUTTON_X -> {
                if (pressed) {
                    if (!menu.open.value) menu.selected = null // a fresh menu starts on its first entry
                    menu.open.value = !menu.open.value
                }
                return true
            }
            KeyEvent.KEYCODE_BUTTON_START -> {
                // The library takes the focus back on the selected game by itself when its layout changes (HomeLibrary),
                // and with the menu open the focus stays on the menu entry.
                if (pressed) HomeTheme.cycleStyle()
                return true
            }
        }
        // A controller key that arrives while the focus is lost (after a tap, when a menu closed, or a menu opened by
        // touch while the library behind it kept the focus) first puts the focus back where it belongs (the open menu,
        // else the library), so the controller never gets stuck.
        val focusLost = if (menu.open.value) !menu.menuHasFocus else !menu.hasFocus
        if (focusLost && (event.keyCode in dpadKeys || event.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                event.keyCode == KeyEvent.KEYCODE_BUTTON_Y)
        ) {
            if (pressed) {
                if (menu.open.value) menu.menuRefocus.intValue++ else menu.refocus.intValue++
            }
            return true
        }
        return false
    }
}

/** What Back (the controller's B) does in the library; call it from the app's `BackHandler`. */
object LibraryBack {
    /**
     * Closes the innermost thing that is open: the quit dialog, then whatever the app shows over the library
     * ([closeOverlay] returns true when it closed something), then the main menu; from the library itself it asks to quit.
     */
    fun handle(closeOverlay: () -> Boolean = { false }) {
        val menu = MenuState
        when {
            menu.quitOpen.value -> menu.quitOpen.value = false
            closeOverlay() -> Unit
            menu.open.value -> menu.open.value = false
            else -> menu.quitOpen.value = true
        }
    }
}
