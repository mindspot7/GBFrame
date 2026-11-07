package gb.frame

import android.content.SharedPreferences

/** Session selection and the last opened game, remembered separately between sessions. */
object LibraryView {
    private const val KEY_GAME = "gbframe.libraryLastGame"
    private const val KEY_STATE = "gbframe.libraryLastState"
    private const val KEY_OPENED = "gbframe.libraryLastOpenedGame"
    private const val KEY_RECENT = "gbframe.libraryRecentGames"
    private var prefs: SharedPreferences? = null
    private val recent = androidx.compose.runtime.mutableStateOf<List<String>>(emptyList())
    val recentGames: List<String> get() = recent.value

    /**
     * The game ([HomeGame.key]) and save state ([ResumeEntry.key]) selected last in the library: the home screen
     * returns to them after a game, settings page, or menu. Startup prefers the last successfully opened game.
     */
    // Snapshot state: the shelves place their focus requester on it, so putting a selection back (HomeLibrary, after a
    // dialog) moves the requester to that cover
    private val game = androidx.compose.runtime.mutableStateOf<String?>(null)
    var lastGame: String?
        get() = game.value
        set(v) { if (game.value != v) { game.value = v; prefs?.edit()?.putString(KEY_GAME, v)?.apply() } }
    var lastState: String? = null
        set(v) { if (field != v) { field = v; prefs?.edit()?.putString(KEY_STATE, v)?.apply() } }

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        val saved = runCatching { org.json.JSONArray(prefs.getString(KEY_RECENT, "[]")) }.getOrNull()
        val restored = saved?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty()
        recent.value = restored.ifEmpty { listOfNotNull(prefs.getString(KEY_OPENED, null)) }
        lastGame = prefs.getString(KEY_OPENED, null) ?: prefs.getString(KEY_GAME, null)
        lastState = prefs.getString(KEY_STATE, null)
    }

    /** Call after a game launch succeeds, including fresh starts and save-state resumes. */
    fun gameOpened(key: String) {
        recent.value = (listOf(key) + recent.value.filterNot { it == key }).take(30)
        prefs?.edit()?.putString(KEY_OPENED, key)
            ?.putString(KEY_RECENT, org.json.JSONArray(recent.value).toString())?.apply()
        lastGame = key
    }

    /** Index of the last selected game in [games] (0 when it isn't there). */
    fun gameIndex(games: List<HomeGame>) = games.indexOfFirst { it.key == lastGame }.coerceAtLeast(0)
    /** Index of the last selected save state in [states] (0 when it isn't there). */
    fun stateIndex(states: List<ResumeEntry>) = states.indexOfFirst { it.key == lastState }.coerceAtLeast(0)
}
