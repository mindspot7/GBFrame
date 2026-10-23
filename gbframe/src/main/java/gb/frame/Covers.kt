package gb.frame

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Box art for the library. Order: user-chosen image -> cached download -> libretro-thumbnails
 * (thumbnails.libretro.com, matched by the disc image's file name) -> placeholder.
 */
object Covers {
    private lateinit var dir: File
    private val memory = LruCache<String, ImageBitmap>(64)

    /** libretro-thumbnails system folder, e.g. "Sega - Saturn", "Sony - PlayStation". */
    private var system = "Sega - Saturn"
    private val base get() = "https://thumbnails.libretro.com/" + Uri.encode(system) + "/Named_Boxarts/"

    /**
     * @param system libretro-thumbnails system name (folder of https://thumbnails.libretro.com)
     * @param aliases extra title aliases (normalized western title -> normalized name used by the thumbnails)
     */
    fun init(dataDir: File, system: String, aliases: Map<String, String> = emptyMap()) {
        this.system = system
        this.extraAliases = aliases
        dir = File(dataDir, "covers").apply { mkdirs(); File(this, "custom").mkdirs() }
    }

    private fun safe(title: String) = title.replace(Regex("[&*/:`<>?\\\\|\"]"), "_")
    private fun custom(title: String) = File(dir, "custom/${safe(title)}.img")
    private fun cached(title: String) = File(dir, "${safe(title)}.png")
    private fun miss(title: String) = File(dir, "${safe(title)}.miss3")

    /**
     * Widest cover worth keeping: half the screen's short side (the focused cover in the shelf or grid is smaller), so
     * covers aren't kept on the GPU at full size and shrunk on every frame (88 covers took ~33 MB of textures and most of
     * the Mali-G52's frame time while scrolling). 720x720 -> 360 px, 1280x960 -> 480 px.
     */
    private val maxWidth: Int by lazy {
        val m = android.content.res.Resources.getSystem().displayMetrics
        (minOf(m.widthPixels, m.heightPixels) / 2).coerceIn(256, 600)
    }

    private fun decode(f: File): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        if (bmp.width <= maxWidth) return bmp.asImageBitmap()
        // Filtered once here (background thread) instead of by the GPU on every frame
        val h = (bmp.height.toLong() * maxWidth / bmp.width).toInt().coerceAtLeast(1)
        val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, maxWidth, h, true)
        if (scaled !== bmp) bmp.recycle()
        return scaled.asImageBitmap()
    }

    private fun download(name: String, to: File): Boolean = runCatching {
        val url = URL(base + Uri.encode(safe(name) + ".png"))
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 15000
        if (c.responseCode != 200) return@runCatching false
        val tmp = File(to.path + ".tmp")
        c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        tmp.renameTo(to)
    }.getOrDefault(false)

    private var extraAliases: Map<String, String> = emptyMap()
    @Volatile private var index: List<String>? = null

    // Cleans a title for matching: drops region/disc tags (even unbalanced ones such as "(CD1 (BR)"), disc markers,
    // punctuation and articles, and spells "&" as "and".
    private fun norm(t: String) = t
        .replace(Regex("\\([^)]*\\)?|\\[[^]]*]?"), " ")
        .replace(Regex("(?i)\\b(cd|disc|disk|dvd)\\s*\\d+\\b"), " ")
        .replace("&", " and ")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\b(the|a|an)\\b"), " ")
        .replace(Regex("\\s+"), " ").trim()


    // Lists the thumbnail server's file names once, so titles can be matched even when the disc image name differs.
    private fun loadIndex(): List<String> {
        index?.let { return it }
        val list = runCatching {
            val c = URL(base).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 20000
            if (c.responseCode != 200) return@runCatching emptyList<String>()
            val html = c.inputStream.bufferedReader().readText()
            Regex("href=\"([^\"]+\\.png)\"").findAll(html).map { Uri.decode(it.groupValues[1]).removeSuffix(".png") }.toList()
        }.getOrDefault(emptyList())
        if (list.isNotEmpty()) index = list
        return list
    }

    private fun words(s: String) = s.split(" ").filter { it.isNotEmpty() }.toSet()

    /**
     * Finds the closest name on the thumbnail server: exact match after cleaning first (also through the alias list),
     * then the best word overlap. Prefers USA, then Europe, then Japan, and the first disc.
     */
    private fun fuzzy(title: String): String? {
        var want = norm(title)
        if (want.isEmpty()) return null
        extraAliases[want]?.let { want = it }
        val all = loadIndex()
        if (all.isEmpty()) return null
        fun pick(c: List<String>) = c.sortedWith(compareBy(
            { if ("(USA" in it) 0 else if ("(Europe" in it) 1 else if ("(Japan" in it) 2 else 3 },
            { if ("Disc 1" in it || "Disc" !in it) 0 else 1 },
        )).firstOrNull()
        pick(all.filter { norm(it) == want })?.let { return it }
        // Word overlap (Jaccard); the candidate must share the first word and most of the words
        val w = words(want)
        val first = want.substringBefore(' ')
        var best: String? = null
        var bestScore = 0.0
        for (name in all) {
            val n = norm(name)
            if (!n.startsWith(first)) continue
            val nw = words(n)
            val score = (w intersect nw).size.toDouble() / (w union nw).size
            if (score > bestScore || (score == bestScore && best != null && pick(listOf(best, name)) == name)) { best = name; bestScore = score }
        }
        return if (bestScore >= 0.6) best else null
    }

    suspend fun load(title: String): ImageBitmap? = withContext(Dispatchers.IO) {
        memory.get(title)?.let { return@withContext it }
        val img = custom(title).takeIf { it.exists() }?.let(::decode)
            ?: cached(title).takeIf { it.exists() }?.let(::decode)
            ?: run {
                if (miss(title).exists()) return@run null
                val stripped = title.replace(Regex("(?i)\\s*\\((Disc|Disk|CD)\\s*\\d+\\)?"), "").trim()
                val ok = download(title, cached(title)) || (stripped != title && download(stripped, cached(title))) ||
                    (fuzzy(title)?.let { download(it, cached(title)) } ?: false)
                if (ok) decode(cached(title)) else { miss(title).writeText("1"); null }
            }
        if (img != null) memory.put(title, img)
        img
    }

    suspend fun setCustom(context: Context, title: String, uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { i -> custom(title).outputStream().use { o -> i.copyTo(o) } }
        memory.remove(title)
    }
}
