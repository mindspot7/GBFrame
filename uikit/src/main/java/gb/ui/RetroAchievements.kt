package gb.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * What the app provides to [RetroAchievements]: the rcheevos client lives in the app's native code (it reads the
 * emulated memory), and the app knows how to hash its own game files.
 */
interface RaBackend {
    fun init(userAgent: String)
    fun login(user: String, secret: String, isToken: Boolean)
    fun logout()
    fun setHardcore(on: Boolean)
    /** Pending events, one per line: kind, a, b, number, url (tab separated). */
    fun pollEvents(): ByteArray
    /** Achievement list of the running game (summary line + one line per achievement). */
    fun achievements(): ByteArray
    fun httpResponse(id: Int, status: Int, body: ByteArray?)
    /** RetroAchievements hash of a game file of the library (for the achievement list of games that aren't running). */
    fun hashGame(path: String): String?
    /** The running game was recognized (e.g. to show a badge in the library). */
    fun onGameRecognized() {}
    /** rcheevos asks for a hard reset (hardcore mode turned on mid-game). */
    fun onResetRequested() {}
}

/** RetroAchievements: login, hardcore mode, unlock notifications and the achievement list (rcheevos in native code). */
object RetroAchievements {
    private lateinit var backend: RaBackend
    data class Notification(val kind: String, val title: String, val text: String, val points: Int, val badgeUrl: String)

    data class Achievement(
        val id: Long, val title: String, val description: String, val points: Int, val unlocked: Boolean,
        val badgeUrl: String, val lockedBadgeUrl: String, val progress: String, val bucket: String,
    )

    data class GameSummary(
        val title: String, val unlocked: Int, val total: Int, val pointsUnlocked: Int, val pointsTotal: Int,
        val badgeUrl: String, val richPresence: String, val hardcore: Boolean,
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var cacheDir: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val http = Executors.newFixedThreadPool(2)

    var enabled by mutableStateOf(false); private set
    var hardcore by mutableStateOf(false); private set
    var user by mutableStateOf<String?>(null); private set
    var score by mutableStateOf(0); private set // total points (hardcore + softcore)
    var loginPending by mutableStateOf(false); private set
    var loginError by mutableStateOf<String?>(null); private set
    var gameTitle by mutableStateOf<String?>(null); private set
    /** Profile picture of the logged-in user. */
    var avatarUrl by mutableStateOf<String?>(null); private set
    private var userAgent = "GBApp"

    /** Pending on-screen notifications (unlocks, game info, errors). The UI shows and removes them. */
    var notifications by mutableStateOf<List<Notification>>(emptyList())

    fun init(context: Context, appName: String, version: String, backend: RaBackend) {
        this.backend = backend
        prefs = context.getSharedPreferences("retroachievements", Context.MODE_PRIVATE)
        cacheDir = File(context.cacheDir, "ra_badges").apply { mkdirs() }
        enabled = prefs.getBoolean("enabled", false)
        hardcore = prefs.getBoolean("hardcore", false)
        userAgent = "$appName/$version (Android ${Build.VERSION.RELEASE})"
        backend.init(userAgent)
        avatarUrl = prefs.getString("avatar", null)
        backend.setHardcore(enabled && hardcore)
        val savedUser = prefs.getString("user", null)
        val token = prefs.getString("token", null)
        if (enabled && savedUser != null && token != null) {
            loginPending = true
            backend.login(savedUser, token, true)
        }
        scope.launch {
            while (true) {
                poll()
                delay(250)
            }
        }
    }

    fun changeEnabled(on: Boolean) {
        enabled = on
        prefs.edit().putBoolean("enabled", on).apply()
        backend.setHardcore(on && hardcore)
        if (!on) {
            backend.logout()
            user = null
            gameTitle = null
        } else {
            val savedUser = prefs.getString("user", null)
            val token = prefs.getString("token", null)
            if (savedUser != null && token != null) {
                loginPending = true
                backend.login(savedUser, token, true)
            }
        }
    }

    fun changeHardcore(on: Boolean) {
        hardcore = on
        prefs.edit().putBoolean("hardcore", on).apply()
        backend.setHardcore(enabled && on)
    }

    fun login(username: String, password: String) {
        loginError = null
        loginPending = true
        prefs.edit().putString("user", username.trim()).remove("token").apply()
        backend.login(username.trim(), password, false)
    }

    fun logout() {
        backend.logout()
        prefs.edit().remove("token").apply()
        user = null
        score = 0
        gameTitle = null
    }

    /** True when the emulator must block save state loading and cheats. */
    val hardcoreActive get() = enabled && hardcore && user != null

    private fun poll() {
        val text = backend.pollEvents().toString(Charsets.UTF_8)
        if (text.isEmpty()) return
        val add = mutableListOf<Notification>()
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val p = line.split('\t')
            val kind = p.getOrElse(0) { "" }
            val a = p.getOrElse(1) { "" }
            val b = p.getOrElse(2) { "" }
            val n = p.getOrElse(3) { "0" }.toIntOrNull() ?: 0
            val url = p.getOrElse(4) { "" }
            when (kind) {
                "login" -> {
                    user = a; score = n + (p.getOrElse(5) { "0" }.toIntOrNull() ?: 0); loginPending = false; loginError = null
                    avatarUrl = url.ifBlank { "https://media.retroachievements.org/UserPic/$a.png" }
                    prefs.edit().putString("token", b).putString("avatar", avatarUrl).apply()
                    add += Notification("info", "RetroAchievements", "Logged in as $a ($score points)", 0, url)
                }
                "loginfail" -> { loginPending = false; loginError = a; user = null }
                "game" -> { gameTitle = a; backend.onGameRecognized(); add += Notification("game", a, b, 0, url) }
                "gamefail" -> { gameTitle = null; add += Notification("info", "RetroAchievements", a, 0, "") }
                "unlocked" -> add += Notification("unlocked", a, b, n, url)
                "completed" -> add += Notification("completed", a, "All achievements earned!", 0, url)
                "challenge", "progress" -> add += Notification(kind, a, b, 0, url)
                "lbstart" -> add += Notification("leaderboard", "Leaderboard started", a, 0, "")
                "lbfail" -> add += Notification("leaderboard", "Leaderboard failed", a, 0, "")
                "lbsubmit" -> add += Notification("leaderboard", "Leaderboard submitted", "$a: $b", 0, "")
                "error" -> add += Notification("info", "RetroAchievements error", "$a: $b", 0, "")
                "info" -> add += Notification("info", a, b, 0, "")
                "reset" -> backend.onResetRequested()
            }
        }
        if (add.isNotEmpty()) notifications = notifications + add
    }

    fun gameUnloaded() { gameTitle = null }

    /** Achievement list for the running game (blocks briefly on the emulation thread). */
    suspend fun achievements(): Pair<GameSummary?, List<Achievement>> = withContext(Dispatchers.IO) {
        val text = backend.achievements().toString(Charsets.UTF_8)
        var summary: GameSummary? = null
        val list = mutableListOf<Achievement>()
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val p = line.split('\t')
            if (p[0] == "#") {
                summary = GameSummary(
                    p.getOrElse(1) { "" }, p.getOrElse(2) { "0" }.toInt(), p.getOrElse(3) { "0" }.toInt(),
                    p.getOrElse(4) { "0" }.toInt(), p.getOrElse(5) { "0" }.toInt(), p.getOrElse(6) { "" },
                    p.getOrElse(7) { "" }, p.getOrElse(8) { "0" } == "1",
                )
            } else if (p.size >= 9) {
                list += Achievement(p[0].toLongOrNull() ?: 0, p[1], p[2], p[3].toIntOrNull() ?: 0, p[4] == "1", p[5], p[6], p[7], p[8])
            }
        }
        summary to list
    }

    // ---- Badge images (cached on disk) ----
    private fun request(params: String): org.json.JSONObject? = runCatching {
        val c = URL("https://retroachievements.org/dorequest.php?$params").openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        c.setRequestProperty("User-Agent", userAgent)
        org.json.JSONObject(c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
    }.getOrNull()

    /**
     * Achievements of any game in the library (not only the running one), with the user's unlocks. Returns a null
     * summary when the game is not recognized or has no achievements.
     */
    suspend fun gameAchievements(path: String): Pair<GameSummary?, List<Achievement>> = withContext(Dispatchers.IO) {
        val none = null to emptyList<Achievement>()
        val u = user ?: return@withContext none
        val t = prefs.getString("token", null) ?: return@withContext none
        val hash = backend.hashGame(path) ?: return@withContext none
        val gameId = request("r=gameid&m=$hash")?.optLong("GameID", 0L) ?: 0L
        if (gameId <= 0) return@withContext none
        val enc = { s: String -> java.net.URLEncoder.encode(s, "UTF-8") }
        val patch = request("r=patch&u=${enc(u)}&t=${enc(t)}&g=$gameId")?.optJSONObject("PatchData") ?: return@withContext none
        // Unlocks (softcore and hardcore) come from the session endpoint used by current rcheevos clients
        val unlocked = HashSet<Long>()
        request("r=startsession&u=${enc(u)}&t=${enc(t)}&g=$gameId&h=0&m=$hash&l=${enc(userAgent)}")?.let { r ->
            for (key in listOf("Unlocks", "HardcoreUnlocks")) {
                val arr = r.optJSONArray(key) ?: continue
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i)
                    unlocked += o?.optLong("ID") ?: arr.optLong(i)
                }
            }
        }
        val list = mutableListOf<Achievement>()
        val arr = patch.optJSONArray("Achievements")
        for (i in 0 until (arr?.length() ?: 0)) {
            val a = arr!!.getJSONObject(i)
            if (a.optInt("Flags", 3) != 3) continue // unofficial
            val badge = a.optString("BadgeName")
            val id = a.optLong("ID")
            list += Achievement(id, a.optString("Title"), a.optString("Description"), a.optInt("Points"), id in unlocked,
                "https://media.retroachievements.org/Badge/$badge.png", "https://media.retroachievements.org/Badge/${badge}_lock.png", "",
                if (id in unlocked) "Unlocked" else "Locked")
        }
        if (list.isEmpty()) return@withContext none
        list.sortBy { !it.unlocked }
        val icon = patch.optString("ImageIconURL").ifBlank { "https://media.retroachievements.org" + patch.optString("ImageIcon") }
        GameSummary(patch.optString("Title"), list.count { it.unlocked }, list.size, list.filter { it.unlocked }.sumOf { it.points },
            list.sumOf { it.points }, icon, "", false) to list
    }

    private val memCache = HashMap<String, ImageBitmap>()

    suspend fun image(url: String): ImageBitmap? {
        if (url.isBlank()) return null
        memCache[url]?.let { return it }
        return withContext(Dispatchers.IO) {
            val name = MessageDigest.getInstance("MD5").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
            val f = File(cacheDir, "$name.img")
            val bytes = if (f.exists()) f.readBytes() else runCatching {
                (URL(url).openConnection() as HttpURLConnection).run {
                    connectTimeout = 10000; readTimeout = 15000
                    setRequestProperty("User-Agent", userAgent)
                    inputStream.use { it.readBytes() }
                }
            }.getOrNull()?.also { f.writeBytes(it) }
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }?.also { memCache[url] = it }
    }

    // ---- HTTP for rcheevos (called from native code) ----
    fun httpRequest(id: Int, url: String, post: String?, contentType: String?, userAgent: String) {
        http.execute {
            var status = -1
            var body = ByteArray(0)
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", userAgent)
                if (post != null) {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", contentType ?: "application/x-www-form-urlencoded")
                    conn.outputStream.use { it.write(post.toByteArray()) }
                }
                status = conn.responseCode
                body = (if (status >= 400) conn.errorStream else conn.inputStream)?.use { it.readBytes() } ?: ByteArray(0)
            } catch (_: Exception) {
                status = -1
            }
            backend.httpResponse(id, status, body)
        }
    }
}
