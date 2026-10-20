package gb.ui

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf

/**
 * Play time per game, keyed by the game's file name (stays valid when the games folder moves). Call [update] whenever
 * the running game, or its paused state, changes: time is counted only while a game runs unpaused.
 */
object PlayTime {
    private lateinit var prefs: SharedPreferences
    private val times = mutableStateMapOf<String, Long>()
    private var start = 0L
    private var current: String? = null

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        prefs.getString("playTimes", null)?.split("\n")?.forEach { l ->
            val i = l.lastIndexOf('\t')
            if (i > 0) l.substring(i + 1).toLongOrNull()?.let { times[l.substring(0, i)] = it }
        }
    }

    private fun key(path: String) = path.substringAfterLast('/')

    /** Seconds played. */
    fun of(path: String): Long = times[key(path)] ?: 0L

    /** [game] is the running game (null when none); [counting] is true while it runs unpaused. */
    fun update(game: String?, counting: Boolean) {
        val now = System.currentTimeMillis()
        val prev = current
        if (start != 0L && prev != null) {
            val k = key(prev)
            times[k] = (times[k] ?: 0L) + (now - start) / 1000
            prefs.edit().putString("playTimes", times.entries.joinToString("\n") { "${it.key}\t${it.value}" }).apply()
        }
        current = game
        start = if (game != null && counting) now else 0L
    }
}

/** "1h 05m" / "12m". */
fun formatPlayTime(secs: Long): String {
    val h = secs / 3600
    val m = (secs % 3600) / 60
    return if (h > 0) "${h}h ${"%02d".format(m)}m" else "${m}m"
}
