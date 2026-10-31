package gb.frame

import android.content.Context
import org.json.JSONObject

/**
 * Recommended settings per game and device class ([DeviceTier]), shipped with the app (assets/game_profiles.json), so heavy games
 * start with the configuration that tested best on similar hardware.
 *
 * Priority when a game starts: the user's own choice for that game > this database > the global settings.
 *
 * ```json
 * {
 *   "games": [
 *     {
 *       "titles": ["VIRTUA FIGHTER 2"],          // disc/ROM header titles (matched case-insensitively, trimmed)
 *       "ids": ["GS-9079", "MK-81041"],           // optional product codes
 *       "all":   { "renderer": "cpu" },           // any device
 *       "entry": { "frameSkip": true },           // little cores only (e.g. Rockchip RK3566)
 *       "low":   { "threadedAudio": false },      // 2 fast cores or fewer (e.g. Unisoc T618)
 *       "mid":   { },                             // 3+ fast cores, modest GPU (e.g. Unisoc T820)
 *       "high":  { }                              // 3+ fast cores, strong GPU (e.g. Snapdragon 865)
 *     }
 *   ]
 * }
 * ```
 * Only "all" and the device's own class apply. Keys are app-defined: GBFrame provides lookup and priority, while each
 * emulator maps its measured settings into its own core options (for example Saturn renderer=cpu|gpu, Dreamcast
 * renderer=opengl|vulkan).
 */
object GameProfiles {
    private var games: List<JSONObject> = emptyList()

    /** Loads the database from the app's assets (missing file = empty database). */
    fun init(context: Context, asset: String = "game_profiles.json") {
        games = runCatching {
            val text = context.assets.open(asset).use { it.readBytes().toString(Charsets.UTF_8) }
            val arr = JSONObject(text).getJSONArray("games")
            (0 until arr.length()).map { arr.getJSONObject(it) }
        }.getOrDefault(emptyList())
    }

    private fun norm(s: String) = s.trim().replace(Regex("\\s+"), " ").uppercase()

    /** Recommended settings for a game (by header title and/or product code), merged "all" + device class. */
    fun lookup(title: String?, id: String?, tier: DeviceTier.Tier = DeviceTier.detect().tier): Map<String, Any> {
        val t = title?.let(::norm)
        val i = id?.let(::norm)
        val game = games.firstOrNull { g ->
            fun list(key: String) = g.optJSONArray(key)?.let { a -> (0 until a.length()).map { norm(a.getString(it)) } }.orEmpty()
            (t != null && t in list("titles")) || (i != null && i in list("ids"))
        } ?: return emptyMap()
        val out = LinkedHashMap<String, Any>()
        for (section in listOf("all", tier.key)) {
            game.optJSONObject(section)?.let { o -> o.keys().forEach { k -> out[k] = o.get(k) } }
        }
        return out
    }
}
