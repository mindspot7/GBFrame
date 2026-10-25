package gb.frame

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Library menu state shared by every app built on the kit. It survives opening a settings page (the library screen is
 * rebuilt on return) and is read by [HomeKeys] and [LibraryBack], which implement the controller behavior of the library.
 */
object MenuState {
    val open = mutableStateOf(false)
    /** "Confirm Quit?" is open; A confirms from the Activity ([HomeKeys]), which works even when nothing has focus yet. */
    val quitOpen = mutableStateOf(false)
    var onQuit: (() -> Unit)? = null
    /** The menu entry whose page is open (the app's own key, e.g. its settings page enum); the menu focuses it on return. */
    var selected by mutableStateOf<Any?>(null)
    /** The library is on screen (not a settings page or a game): [HomeKeys] handles its buttons and lost focus there. */
    @Volatile var libraryShown = false
    /** Something inside the library screen has the focus (a tap or a closed menu can leave nothing focused). */
    @Volatile var hasFocus = false
    /** Bumped to put the focus back on the library. */
    val refocus = mutableIntStateOf(0)
    /** Something inside the open main menu has the focus (opened with a tap, the focus can stay on the library behind). */
    @Volatile var menuHasFocus = false
    /** Bumped to put the focus back on the open menu. */
    val menuRefocus = mutableIntStateOf(0)
}
