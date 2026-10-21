package gb.ui

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

/**
 * Library layout remembered between sessions: all games (box art grid) or recent save states ("Resume" view).
 * The library opens in the layout the user used last.
 *
 * ```
 * LibraryView.init(prefs)                  // once, at startup
 * var recent by LibraryView.recent         // read/write from Compose; every change is saved
 * LibraryView.toggle()                     // e.g. on Select
 * ```
 */
object LibraryView {
    private const val KEY = "gbui.libraryRecent"
    private var prefs: SharedPreferences? = null

    /** true = recent save states, false = all games. */
    val recent = object : androidx.compose.runtime.MutableState<Boolean> {
        private val state = mutableStateOf(false)
        override var value: Boolean
            get() = state.value
            set(v) {
                if (state.value == v) return
                state.value = v
                prefs?.edit()?.putBoolean(KEY, v)?.apply()
            }
        override fun component1() = value
        override fun component2(): (Boolean) -> Unit = { value = it }
    }

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        recent.value = prefs.getBoolean(KEY, false)
    }

    fun toggle() { recent.value = !recent.value }
}
