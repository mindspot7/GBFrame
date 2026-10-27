package gb.frame

import android.content.SharedPreferences

/** Session selection and the last opened game, remembered separately between sessions. */
object LibraryView {
    private const val KEY_GAME = "gbframe.libraryLastGame"
    private const val KEY_STATE = "gbframe.libraryLastState"
    private const val KEY_OPENED = "gbframe.libraryLastOpenedGame"
    private var prefs: SharedPreferences? = null

    /**
     * The game ([HomeGame.key]) and save state ([ResumeEntry.key]) selected last in the library: the home screen
     * returns to them after a game, settings page, or menu. Startup prefers the last successfully opened game.
     */
    var lastGame: String? = null
        set(v) { if (field != v) { field = v; prefs?.edit()?.putString(KEY_GAME, v)?.apply() } }
    var lastState: String? = null
        set(v) { if (field != v) { field = v; prefs?.edit()?.putString(KEY_STATE, v)?.apply() } }

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        lastGame = prefs.getString(KEY_OPENED, null) ?: prefs.getString(KEY_GAME, null)
        lastState = prefs.getString(KEY_STATE, null)
    }

    /** Call after a game launch succeeds, including fresh starts and save-state resumes. */
    fun gameOpened(key: String) {
        prefs?.edit()?.putString(KEY_OPENED, key)?.apply()
        lastGame = key
    }

    /** Index of the last selected game in [games] (0 when it isn't there). */
    fun gameIndex(games: List<HomeGame>) = games.indexOfFirst { it.key == lastGame }.coerceAtLeast(0)
    /** Index of the last selected save state in [states] (0 when it isn't there). */
    fun stateIndex(states: List<ResumeEntry>) = states.indexOfFirst { it.key == lastState }.coerceAtLeast(0)
}
