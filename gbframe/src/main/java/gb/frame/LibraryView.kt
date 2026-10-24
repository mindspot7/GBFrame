package gb.frame

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

/** Last selected game and save state in the library, remembered between sessions. */
object LibraryView {
    private const val KEY_GAME = "gbframe.libraryLastGame"
    private const val KEY_STATE = "gbframe.libraryLastState"
    private const val KEY_RECENT = "gbframe.libraryRecent"
    private var prefs: SharedPreferences? = null

    /**
     * The game ([HomeGame.key]) and save state ([ResumeEntry.key]) selected last in the library: the home screen
     * opens on them again (after a game, a settings page, the menu, or the next app start) instead of on the first one.
     */
    var lastGame: String? = null
        set(v) { if (field != v) { field = v; prefs?.edit()?.putString(KEY_GAME, v)?.apply() } }
    var lastState: String? = null
        set(v) { if (field != v) { field = v; prefs?.edit()?.putString(KEY_STATE, v)?.apply() } }

    /** Whether the library opens on recent save states rather than all games. */
    private val recentState = mutableStateOf(false)
    var recent: Boolean
        get() = recentState.value
        set(v) {
            if (recentState.value != v) {
                recentState.value = v
                prefs?.edit()?.putBoolean(KEY_RECENT, v)?.apply()
            }
        }

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        lastGame = prefs.getString(KEY_GAME, null)
        lastState = prefs.getString(KEY_STATE, null)
        recent = prefs.getBoolean(KEY_RECENT, false)
    }

    /** Index of the last selected game in [games] (0 when it isn't there). */
    fun gameIndex(games: List<HomeGame>) = games.indexOfFirst { it.key == lastGame }.coerceAtLeast(0)
    /** Index of the last selected save state in [states] (0 when it isn't there). */
    fun stateIndex(states: List<ResumeEntry>) = states.indexOfFirst { it.key == lastState }.coerceAtLeast(0)
}
