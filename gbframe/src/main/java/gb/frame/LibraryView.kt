package gb.frame

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
    private const val KEY_GAME = "gbframe.libraryLastGame"
    private const val KEY_STATE = "gbframe.libraryLastState"
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

    /**
     * The game ([HomeGame.key]) and save state ([ResumeEntry.key]) selected last in the library: the home screen
     * opens on them again (after a game, a settings page, the menu, or the next app start) instead of on the first one.
     */
    var lastGame: String? = null
        set(v) { if (field != v) { field = v; prefs?.edit()?.putString(KEY_GAME, v)?.apply() } }
    var lastState: String? = null
        set(v) { if (field != v) { field = v; prefs?.edit()?.putString(KEY_STATE, v)?.apply() } }

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        recent.value = prefs.getBoolean(KEY, false)
        lastGame = prefs.getString(KEY_GAME, null)
        lastState = prefs.getString(KEY_STATE, null)
    }

    /** Index of the last selected game in [games] (0 when it isn't there). */
    fun gameIndex(games: List<HomeGame>) = games.indexOfFirst { it.key == lastGame }.coerceAtLeast(0)
    /** Index of the last selected save state in [states] (0 when it isn't there). */
    fun stateIndex(states: List<ResumeEntry>) = states.indexOfFirst { it.key == lastState }.coerceAtLeast(0)

    fun toggle() { recent.value = !recent.value }
}
