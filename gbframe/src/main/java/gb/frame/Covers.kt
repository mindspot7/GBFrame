package gb.frame

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
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
    private const val COVER_RATIO = 0.66f
    private lateinit var dir: File
    // Bound the cache by bytes, not entry count: a handful of tall covers can otherwise exhaust low-memory devices.
    private val cacheKb = (Runtime.getRuntime().maxMemory() / 1024L / 40L).toInt().coerceIn(2048, 8 * 1024)
    private val memory = object : LruCache<String, ImageBitmap>(cacheKb) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            (value.asAndroidBitmap().allocationByteCount / 1024).coerceAtLeast(1)
    }
    // Avoid decoding/downloading many covers at once while the library first appears or scrolls.
    private val loadSlots = Semaphore(2)

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
    private fun miss(title: String) = File(dir, "${safe(title)}.miss5")

    /**
     * Widest cover worth keeping: half the screen's short side (the focused cover in the shelf or grid is smaller), so
     * covers aren't kept on the GPU at full size and shrunk on every frame (88 covers took ~33 MB of textures and most of
     * the Mali-G52's frame time while scrolling). 720x720 -> 360 px, 1280x960 -> 480 px.
     */
    // A low heap implies a low-end handheld: keep covers smaller to reduce both decoded RAM and GPU texture use.
    private val maxDimension: Int by lazy {
        val m = android.content.res.Resources.getSystem().displayMetrics
        val screenLimit = (minOf(m.widthPixels, m.heightPixels) / 2).coerceIn(256, 600)
        val heapLimit = when {
            Runtime.getRuntime().maxMemory() <= 256L * 1024 * 1024 -> 320
            Runtime.getRuntime().maxMemory() <= 384L * 1024 * 1024 -> 384
            else -> 480
        }
        minOf(screenLimit, heapLimit)
    }

    private fun decode(f: File): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val largestSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (largestSide / (sample * 2) >= maxDimension) sample *= 2
        val decoded = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply {
            inSampleSize = sample
        }) ?: return null

        // Keep decoded covers small on low-memory devices before normalizing their aspect ratio.
        var result = decoded
        val largestDecodedSide = maxOf(decoded.width, decoded.height)
        if (largestDecodedSide > maxDimension) {
            val scale = maxDimension.toFloat() / largestDecodedSide
            val w = (decoded.width * scale).toInt().coerceAtLeast(1)
            val h = (decoded.height * scale).toInt().coerceAtLeast(1)
            result = android.graphics.Bitmap.createScaledBitmap(decoded, w, h, true)
            if (result !== decoded) decoded.recycle()
        }

        // Every Saturn box art uses the same 2:3-ish canvas. Fit the original art inside it without stretching or
        // cropping, so shelf, grid, and AIO have consistent cover widths/heights even when scans vary in shape.
        val frameWidth = (maxDimension * COVER_RATIO).toInt().coerceAtLeast(1)
        val frameHeight = maxDimension
        val frame = android.graphics.Bitmap.createBitmap(frameWidth, frameHeight, android.graphics.Bitmap.Config.ARGB_8888)
        frame.eraseColor(android.graphics.Color.rgb(17, 19, 26))
        val scale = minOf(frameWidth.toFloat() / result.width, frameHeight.toFloat() / result.height)
        val drawWidth = result.width * scale
        val drawHeight = result.height * scale
        val left = (frameWidth - drawWidth) / 2f
        val top = (frameHeight - drawHeight) / 2f
        android.graphics.Canvas(frame).drawBitmap(
            result, null,
            android.graphics.RectF(left, top, left + drawWidth, top + drawHeight),
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        )
        result.recycle()
        return frame.asImageBitmap()
    }

    private fun download(name: String, to: File): Boolean = runCatching {
        val url = URL(base + Uri.encode(name + ".png"))
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
        // Preserve possessive names such as Steam-Heart's as the same words as "Steam Hearts".
        .replace(Regex("(?i)'s\\b"), "s")
        .replace(Regex("(?i)\\b(cd|disc|disk|dvd)\\s*\\d+\\b"), " ")
        .replace("&", " and ")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\b(the|a|an)\\b"), " ")
        .replace(Regex("\\s+"), " ").trim()


    // Lists the thumbnail server's file names once, so titles can be matched even when the disc image name differs.
    @Synchronized
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
        extraAliases[want]?.let { want = norm(it) }
        val all = loadIndex()
        if (all.isEmpty()) return null
        fun pick(c: List<String>) = c.sortedWith(compareBy(
            { if ("(USA" in it) 0 else if ("(Europe" in it) 1 else if ("(Japan" in it) 2 else 3 },
            { if ("Disc 1" in it || "Disc" !in it) 0 else 1 },
        )).firstOrNull()
        val normalized = all.associateBy(::norm)
        normalized[want]?.let { return it }

        // Patched ROM filenames often append tags such as (BR V1.0), (DUB), [Translation]. Compare
        // the clean base title too, and allow the candidate to contain all meaningful title words.
        val baseWant = norm(title.replace(Regex("\\s*[\\[(].*$"), " ")
            .replace(Regex("(?i)\\b(pt[- ]?br|br|dub|undub|translation|translated|patch|v\\d+(?:\\.\\d+)*)\\b"), " "))
        if (baseWant.isNotBlank()) normalized[baseWant]?.let { return it }

        val w = words(baseWant.ifBlank { want })
        if (w.isEmpty()) return null
        var best: String? = null
        var bestScore = 0.0
        for (name in all) {
            val n = norm(name)
            val nw = words(n)
            if (nw.isEmpty() || w.intersect(nw).isEmpty()) continue
            val intersection = (w intersect nw).size.toDouble()
            val union = (w union nw).size.toDouble()
            val jaccard = intersection / union
            val containment = intersection / minOf(w.size, nw.size).toDouble()
            val score = maxOf(jaccard, containment * 0.92)
            if (score > bestScore || (score == bestScore && best != null && pick(listOf(best, name)) == name)) {
                best = name
                bestScore = score
            }
        }
        val threshold = if (w.size <= 2) 0.72 else 0.58
        return if (bestScore >= threshold) best else null
    }

    suspend fun load(title: String): ImageBitmap? = withContext(Dispatchers.IO) {
        memory.get(title)?.let { return@withContext it }
        loadSlots.withPermit {
            memory.get(title)?.let { return@withPermit it }
            val img = custom(title).takeIf { it.exists() }?.let(::decode)
                ?: cached(title).takeIf { it.exists() }?.let(::decode)
                ?: run {
                    // Versioned negative cache invalidates misses from older matching logic.
                    if (miss(title).exists()) return@run null
                    val stripped = title.replace(Regex("(?i)\\s*\\((Disc|Disk|CD)\\s*\\d+\\)?"), "").trim()
                    val baseTitle = title.replace(Regex("\\s*[\\[(].*$"), "").trim()
                    val candidates = listOf(title, stripped, baseTitle).filter { it.isNotBlank() }.distinct()
                    var downloaded = false
                    for (candidate in candidates) {
                        if (download(candidate, cached(title))) { downloaded = true; break }
                    }
                    if (!downloaded) {
                        val match = fuzzy(title)
                        if (match != null) downloaded = download(match, cached(title))
                    }
                    if (downloaded) decode(cached(title))
                    else {
                        // Don't cache transient network/index failures; allow retry when connectivity returns.
                        if (index?.isNotEmpty() == true) miss(title).writeText("1")
                        null
                    }
                }
            if (img != null) memory.put(title, img)
            img
        }
    }

    suspend fun setCustom(context: Context, title: String, uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { i -> custom(title).outputStream().use { o -> i.copyTo(o) } }
        memory.remove(title)
    }
}
