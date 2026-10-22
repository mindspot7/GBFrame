package gb.frame

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Offline RetroAchievements (casual mode only, like RAOfflineProxy): the app's own HTTP layer for rcheevos keeps the
 * server answers it needs to start a game (login, game id, achievement set, session) while online, answers from that
 * cache when there is no connection, and queues casual unlocks made offline. The queue is sent when the connection is
 * back, with the time since each unlock (`o`) so the site records when it really happened. Hardcore never works offline.
 */
internal object RaOffline {
    private lateinit var prefs: SharedPreferences
    private lateinit var dir: File
    private val lock = Any()
    @Volatile private var syncing = false

    /** Answers kept for offline use (request kinds). */
    private val cached = setOf("login2", "gameid", "achievementsets", "patch", "startsession")

    fun init(prefs: SharedPreferences, cacheRoot: File) {
        this.prefs = prefs
        dir = File(cacheRoot, "ra_offline").apply { mkdirs() }
    }

    fun parse(post: String?): Map<String, String> = post.orEmpty().split('&').filter { '=' in it }.associate {
        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }

    private fun key(p: Map<String, String>): String? {
        val r = p["r"] ?: return null
        if (r !in cached) return null
        // Volatile parameters (token, password, client version) are left out
        val parts = listOf("u", "g", "m", "h").mapNotNull { k -> p[k]?.let { "$k=$it" } }
        return (listOf(r) + parts).joinToString("|")
    }

    private fun file(key: String) = File(dir, md5(key) + ".json")

    /** Called with every successful online answer: keeps the ones needed offline. */
    fun store(p: Map<String, String>, body: ByteArray) {
        val k = key(p) ?: return
        val ok = runCatching { JSONObject(String(body)).optBoolean("Success", true) }.getOrDefault(false)
        if (ok) runCatching { file(k).writeBytes(body) }
    }

    /** Answer for a request that could not reach the server, or null to report the failure. */
    fun offlineAnswer(p: Map<String, String>, hardcore: Boolean): ByteArray? {
        if (hardcore) return null // hardcore requires a live connection
        return when (p["r"]) {
            "ping" -> """{"Success":true}""".toByteArray()
            "awardachievement" -> {
                if (p["h"] == "1") return null
                val id = p["a"]?.toLongOrNull() ?: return null
                enqueue(id, p["u"].orEmpty(), p["m"].orEmpty())
                val score = prefs.getInt("score", 0)
                """{"Success":true,"Score":$score,"SoftcoreScore":0,"AchievementID":$id,"AchievementsRemaining":-1}""".toByteArray()
            }
            "startsession" -> {
                val k = key(p) ?: return null
                val body = runCatching { file(k).readText() }.getOrNull() ?: return null
                // Unlocks made offline are not in the cached session yet
                val json = JSONObject(body)
                val unlocks = json.optJSONArray("Unlocks") ?: JSONArray().also { json.put("Unlocks", it) }
                val have = (0 until unlocks.length()).map { unlocks.getJSONObject(it).optLong("ID") }.toSet()
                queue().forEach { q ->
                    if (q.getLong("a") !in have) unlocks.put(JSONObject().put("ID", q.getLong("a")).put("When", q.getLong("when") / 1000))
                }
                json.put("ServerNow", System.currentTimeMillis() / 1000)
                json.toString().toByteArray()
            }
            else -> key(p)?.let { k -> runCatching { file(k).readBytes() }.getOrNull() }
        }
    }

    // ---- Queue of casual unlocks made offline ----
    private fun queue(): List<JSONObject> = synchronized(lock) {
        val arr = runCatching { JSONArray(prefs.getString("offlineQueue", "[]")) }.getOrDefault(JSONArray())
        (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    private fun save(list: List<JSONObject>) = synchronized(lock) {
        prefs.edit().putString("offlineQueue", JSONArray(list).toString()).apply()
    }

    private fun enqueue(id: Long, user: String, hash: String) = synchronized(lock) {
        val list = queue().toMutableList()
        if (list.none { it.getLong("a") == id && it.getString("u") == user }) {
            list += JSONObject().put("a", id).put("u", user).put("m", hash).put("when", System.currentTimeMillis())
            save(list)
        }
    }

    val pending: Int get() = queue().size

    /**
     * Sends the queued unlocks (call when the connection works again). [post] performs one request and returns the
     * HTTP status and body (-1 when offline). Returns how many were accepted by the site.
     */
    fun sync(token: String, post: (String) -> Pair<Int, ByteArray>): Int {
        if (syncing) return 0
        syncing = true
        var sent = 0
        try {
            for (q in queue()) {
                val id = q.getLong("a"); val user = q.getString("u")
                val o = ((System.currentTimeMillis() - q.getLong("when")) / 1000).coerceAtLeast(1)
                // Same validation as rcheevos' award request with seconds_since_unlock
                val v = md5("$id${user}0$id$o")
                val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
                val body = "r=awardachievement&u=${enc(user)}&t=${enc(token)}&a=$id&h=0" +
                    (q.optString("m").takeIf { it.isNotEmpty() }?.let { "&m=${enc(it)}" } ?: "") + "&o=$o&v=$v"
                val (status, resp) = post(body)
                if (status < 0 || status >= 500) break // still offline: keep the rest for later
                // Accepted, already unlocked or refused: either way it is done
                val json = runCatching { JSONObject(String(resp)) }.getOrNull()
                if (json?.optBoolean("Success") == true) sent++
                synchronized(lock) { save(queue().filterNot { it.getLong("a") == id && it.getString("u") == user }) }
            }
        } finally {
            syncing = false
        }
        return sent
    }

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
