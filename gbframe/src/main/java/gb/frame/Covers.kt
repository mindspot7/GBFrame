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
import gb.frame.DataFile as StoredFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Box art for the library. Order: user-chosen image -> cached download -> libretro-thumbnails
 * (thumbnails.libretro.com, matched by the disc image's file name) -> placeholder.
 */
object Covers {
    /** Width divided by height, explicitly selected by the consuming app for its system. */
    var frameAspectRatio: Float = Float.NaN
        private set
    private lateinit var dir: StoredFile
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

    /** Reuse the library title and configured thumbnail system for manual image search. */
    fun webSearchQuery(title: String): String = "$title $system box art"

    /**
     * @param system libretro-thumbnails system name (folder of https://thumbnails.libretro.com)
     * @param aliases extra title aliases (normalized western title -> normalized name used by the thumbnails)
     * @param frameAspectRatio target width/height ratio selected from the majority of this system's box-art images
     */
    fun init(
        dataDir: File,
        system: String,
        aliases: Map<String, String> = emptyMap(),
        frameAspectRatio: Float,
    ) {
        require(frameAspectRatio.isFinite() && frameAspectRatio in 0.35f..2.5f) {
            "Box-art frame aspect ratio must be between 0.35 and 2.5"
        }
        this.system = system
        this.extraAliases = aliases
        this.frameAspectRatio = frameAspectRatio
        memory.evictAll()
        dir = StoredFile(dataDir, "covers").apply { mkdirs(); StoredFile(this, "custom").mkdirs() }
    }

    private fun safe(title: String) = title.replace(Regex("[&*/:`<>?\\\\|\"]"), "_")
    private fun custom(title: String) = StoredFile(dir, "custom/${safe(title)}.img")
    private fun cached(title: String) = StoredFile(dir, "${safe(title)}.png")
    private fun miss(title: String) = StoredFile(dir, "${safe(title)}.miss5")

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

    private fun decode(f: StoredFile): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        f.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val largestSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (largestSide / (sample * 2) >= maxDimension) sample *= 2
        val decoded = f.inputStream().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null

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

        // Normalize every downloaded or custom cover to the system's shared majority-aspect frame so each library layout
        // receives one consistent size.
        val frameWidth = (maxDimension * frameAspectRatio).toInt().coerceAtLeast(1)
        val frameHeight = maxDimension
        val frame = android.graphics.Bitmap.createBitmap(frameWidth, frameHeight, android.graphics.Bitmap.Config.ARGB_8888)
        frame.eraseColor(android.graphics.Color.rgb(17, 19, 26))
        android.graphics.Canvas(frame).drawBitmap(
            result, null,
            android.graphics.RectF(0f, 0f, frameWidth.toFloat(), frameHeight.toFloat()),
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        )
        result.recycle()
        return frame.asImageBitmap()
    }

    private fun download(name: String, to: StoredFile): Boolean = runCatching {
        val url = URL(base + Uri.encode(name + ".png"))
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 15000
        if (c.responseCode != 200) return@runCatching false
        val tmp = StoredFile(to.path + ".tmp")
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
        val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "Could not open image" }
        input.use { saveCustom(title, it) }
    }

    /** Downloads only the image explicitly selected by the user; never executes page content. */
    suspend fun webImage(url: String): ByteArray = withContext(Dispatchers.IO) {
        val limit = 10 * 1024 * 1024
        val bytes = if (url.startsWith("data:image/")) {
            require(url.length <= limit * 2) { "Image is too large" }
            require(url.substringBefore(',').endsWith(";base64")) { "Unsupported image" }
            android.util.Base64.decode(url.substringAfter(','), android.util.Base64.DEFAULT)
        } else {
            var next = URL(url)
            var result: ByteArray? = null
            for (redirect in 0..5) {
                require(next.protocol in listOf("https", "http")) { "Unsupported image address" }
                val connection = next.openConnection() as HttpURLConnection
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 10000; connection.readTimeout = 15000
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                    if (connection.responseCode in 300..399) {
                        next = URL(next, requireNotNull(connection.getHeaderField("Location")))
                        continue
                    }
                    require(connection.responseCode == 200) { "Image download failed" }
                    require(connection.contentLengthLong <= limit) { "Image is too large" }
                    result = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val size = input.read(buffer)
                            if (size < 0) break
                            require(output.size() + size <= limit) { "Image is too large" }
                            output.write(buffer, 0, size)
                        }
                        output.toByteArray()
                    }
                    break
                } finally { connection.disconnect() }
            }
            requireNotNull(result) { "Too many redirects" }
        }
        require(bytes.size <= limit) { "Image is too large" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 64_000_000) {
            "This address does not contain a supported image"
        }
        bytes
    }

    suspend fun setCustomWeb(title: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        bytes.inputStream().use { saveCustom(title, it) }
    }

    private fun saveCustom(title: String, input: java.io.InputStream) {
        val destination = custom(title)
        val temp = StoredFile.createTempFile("cover-", ".tmp", destination.parentFile)
        try {
            temp.outputStream().use { input.copyTo(it) }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            temp.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
            check(temp.renameTo(destination)) { "Could not save cover" }
        } finally { temp.delete() }
        memory.remove(title)
    }
}
