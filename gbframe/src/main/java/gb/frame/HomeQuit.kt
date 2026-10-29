package gb.frame

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * The library's Exit dialog, the same in every app. B/Back in the library opens it ([LibraryBack.handle]), the main menu's
 * Quit tile too (`MenuState.quitOpen.value = true`); [HomeLibrary] keeps the selected game while it is open.
 *
 * ```
 * Box(Modifier.fillMaxSize().homeQuitKeys(onQuit)) {
 *     … library, menu, overlays …
 *     HomeQuitDialog(onQuit) // last, over everything
 * }
 * ```
 */
@Composable
fun HomeQuitDialog(onQuit: () -> Unit) {
    MenuState.onQuit = onQuit // A from the Activity (HomeKeys) confirms even when nothing has the focus yet
    if (!MenuState.quitOpen.value) return
    // Drawn in the app window (not a separate dialog window), so the Activity's A/B controller mapping applies
    ConfirmDialog(title = "Exit", message = "Are you sure you want to exit?", confirm = "Exit",
        onConfirm = { MenuState.quitOpen.value = false; onQuit() }, onCancel = { MenuState.quitOpen.value = false })
}

/**
 * While the Exit dialog is open it owns the controller: A confirms, B/Back cancels. Both act on the key release and the
 * press is consumed too, so the same press can't also reach the library or the system Back. Put it on the library's
 * root, after the app's own preview handlers that must win (e.g. ignoring keys while a game is opening).
 */
fun Modifier.homeQuitKeys(onQuit: () -> Unit): Modifier = onPreviewKeyEvent { e ->
    if (!MenuState.quitOpen.value) return@onPreviewKeyEvent false
    val up = e.type == KeyEventType.KeyUp
    when (e.key) {
        Key.ButtonA, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> if (up) { MenuState.quitOpen.value = false; onQuit() }
        Key.ButtonB, Key.Back, Key.Escape -> if (up) MenuState.quitOpen.value = false
        else -> {}
    }
    true
}
