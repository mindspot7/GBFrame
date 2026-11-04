package gb.frame

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.system.Os
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Game files read through the Storage Access Framework (no broad storage permission, Google Play compliant).
 * Apps extend it with their file extensions and games folder, e.g.
 * `object Storage : SafGames(setOf("cue", "chd", "iso"), { Emulator.gamesDir })`.
 *
 * Games are read through the Storage Access Framework: the user picks the games folder once (a persisted tree URI)
 * and no broad storage permission is needed.
 *
 * Games are identified by a virtual path, `saf:/<folders>/<file name>` relative to the games folder, so file names keep
 * working as keys (play time, save state folders, widescreen hacks). To load a disc, its files are opened as file
 * descriptors and exposed to the emulator core as symlinks to `/proc/self/fd/N` in a staging folder, so the core's
 * path-based loaders (including CUE sheets that name their BIN files) work unchanged. A loader that reads through the
 * descriptor itself (the app's CHD, ISO and CUE/BIN loaders) never reopens the link by path, which some devices refuse; a
 * CHD or ISO is copied to the cache only as a fallback.
 */
private const val TAG = "GBFrame-SAF"

/** A game found in the games folder: [path] is a virtual `saf:/` path (or a plain file path). */
data class Game(val title: String, val path: String)

/** Byte-based extraction progress for an archived disc. [fraction] is null when the format has no reliable total size. */
data class DiscArchiveProgress(val completedBytes: Long, val totalBytes: Long?) {
    val fraction: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { (completedBytes.toDouble() / it).toFloat().coerceIn(0f, 1f) }
}

/**
 * A disc opened for the emulator: [path] is a real file path for the core; close it when the game ends. [stage] is its
 * own folder of /proc/self/fd links, deleted on close, so opening another disc (a disc swap, a RetroAchievements hash)
 * never removes or redirects the links of the disc a game is using.
 */
class OpenDisc(
    val path: String,
    private val fds: List<android.os.ParcelFileDescriptor>,
    private val stage: File? = null,
    private val onClose: (() -> Unit)? = null,
) : AutoCloseable {
    /** Number of SAF descriptors retained for native readers using /proc/self/fd links. */
    val descriptorCount: Int get() = fds.size
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // Links first: once a descriptor is closed its number can be reused by any other file
        stage?.let { runCatching { it.deleteRecursively() } }
        fds.forEach { runCatching { it.close() } }
        runCatching { onClose?.invoke() }
    }
}

open class SafGames(
    private val discExt: Set<String>,
    private val gamesDir: () -> String,
    /** Disc-image extensions accepted inside ZIP, 7z and RAR packages. Empty disables archive game loading. */
    private val archiveDiscExt: Set<String> = emptySet(),
) {
    private val PREFIX = "saf:/"
    private val archiveCacheLock = Any()
    private val pinnedArchiveCaches = HashMap<String, Int>()
    @Volatile private var lastArchiveError: String? = null

    private companion object {
        const val MAX_ARCHIVE_UNPACKED_BYTES = 8L * 1024 * 1024 * 1024
        const val ARCHIVE_CACHE_BUDGET_BYTES = 8L * 1024 * 1024 * 1024
    }

    private lateinit var appContext: Context
    fun init(context: Context) { appContext = context.applicationContext }

    private fun tree(): Uri? = gamesDir().takeIf { it.startsWith("content://") }?.let(Uri::parse)

    /** Keeps read access to the picked games folder across restarts. */
    fun takeFolder(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Readable name of the games folder for the settings screen. */
    fun folderLabel(dir: String): String = if (dir.startsWith("content://")) {
        runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(dir)) }.getOrNull()
            ?.let { id -> id.substringAfter(':').ifBlank { id.substringBefore(':') } }?.ifBlank { null } ?: "Selected folder"
    } else dir

    /** File name (with extension) of a game path, including document providers with opaque URI paths. */
    fun fileName(path: String): String {
        if (path.startsWith("content://", ignoreCase = true)) {
            val uri = runCatching { Uri.parse(path) }.getOrNull()
            val displayName = runCatching {
                uri?.let { appContext.contentResolver.query(it, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) }
                    ?.use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf(String::isNotBlank) else null }
            }.getOrNull()
            if (displayName != null) return displayName
            val id = uri?.let { runCatching { DocumentsContract.getDocumentId(it) }.getOrNull() ?: it.lastPathSegment }
            return id?.substringAfterLast('/')?.substringAfterLast(':') ?: path.substringAfterLast('/')
        }
        if (path.startsWith("file://", ignoreCase = true)) {
            return runCatching { Uri.parse(path).path?.substringAfterLast('/') }.getOrNull() ?: path.substringAfterLast('/')
        }
        return path.substringAfterLast('/')
    }

    private data class Child(
        val docId: String, val name: String, val isDir: Boolean,
        val size: Long = -1L, val lastModified: Long = -1L,
    )

    private fun children(tree: Uri, docId: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val out = mutableListOf<Child>()
        runCatching {
            appContext.contentResolver.query(uri, arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    out += Child(
                        c.getString(0), c.getString(1) ?: "",
                        c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                        if (c.isNull(3)) -1L else c.getLong(3),
                        if (c.isNull(4)) -1L else c.getLong(4),
                    )
                }
            }
        }
        return out
    }

    /**
     * The list from the last [scan] of [dir] (kept across restarts), to show the library at once while the folder is
     * scanned again; empty when it was never scanned.
     */
    fun cachedScan(dir: String): List<Game> {
        val prefs = appContext.getSharedPreferences("gbframe.games", Context.MODE_PRIVATE)
        if (prefs.getString("dir", null) != dir) return emptyList()
        return prefs.getString("games", null).orEmpty().split('\n').mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) null else Game(line.substring(0, tab), line.substring(tab + 1))
        }
    }

    @Volatile private var lastScan: Pair<String, Long>? = null

    /**
     * Whether [dir] should be scanned again: not scanned in this process yet, or more than [maxAgeMs] ago. The library
     * comes back on screen after every game; walking the whole folder each time cost CPU for a list that rarely changes.
     */
    fun scanStale(dir: String, maxAgeMs: Long = 5 * 60_000L): Boolean {
        val last = lastScan ?: return true
        return last.first != dir || android.os.SystemClock.elapsedRealtime() - last.second > maxAgeMs
    }

    /** Lists the disc images in the games folder (and up to [maxDepth] levels of subfolders). */
    fun scan(dir: String, maxDepth: Int = 3): List<Game> = scanFolder(dir, maxDepth, keepFirstDisc = true).also { games ->
        lastScan = dir to android.os.SystemClock.elapsedRealtime()
        val text = games.joinToString("\n") { it.title + "\t" + it.path }
        val prefs = appContext.getSharedPreferences("gbframe.games", Context.MODE_PRIVATE)
        if (prefs.getString("dir", null) != dir || prefs.getString("games", null) != text) {
            prefs.edit().putString("dir", dir).putString("games", text).apply()
        }
    }

    /** Lists every supported disc image, including later discs hidden from the main library list. */
    fun scanAllDiscs(dir: String, maxDepth: Int = 3): List<Game> =
        scanFolder(dir, maxDepth, keepFirstDisc = false)

    /** Shared frontend contract: match an indexed game first, then use its granted file URI/path if supported. */
    fun resolveLaunchRequest(
        request: GameLaunchRequest,
        cachedGames: List<Game>,
        refresh: () -> List<Game>,
    ): Game? {
        val supported = discExt + archiveDiscExt
        val contentSingleFile = supported - setOf("cue", "gdi", "mds", "ccd", "lst", "dat", "bin")
        return LaunchIntent.resolveOrRefresh(request, cachedGames, refresh, supported, contentSingleFile)
    }

    private fun scanFolder(dir: String, maxDepth: Int, keepFirstDisc: Boolean): List<Game> {
        if (!dir.startsWith("content://")) {
            val games = scanFiles(dir, maxDepth)
            return if (keepFirstDisc) keepFirstDiscOnly(games) else games
        }
        val tree = tree() ?: return emptyList()
        val out = mutableListOf<Game>()
        fun walk(docId: String, rel: String, depth: Int) {
            for (c in children(tree, docId)) {
                if (c.isDir) { if (depth < maxDepth) walk(c.docId, "$rel${c.name}/", depth + 1) }
                else if (c.name.substringAfterLast('.', "").lowercase() in discExt) {
                    out += Game(c.name.substringBeforeLast('.'), "$PREFIX$rel${c.name}")
                }
            }
        }
        walk(DocumentsContract.getTreeDocumentId(tree), "", 0)
        return (if (keepFirstDisc) keepFirstDiscOnly(out) else out).sortedBy { it.title.lowercase() }
    }

    private fun scanFiles(dir: String, maxDepth: Int): List<Game> {
        val out = mutableListOf<Game>()
        fun walk(f: File, depth: Int) {
            f.listFiles()?.forEach { c ->
                if (c.isDirectory && depth < maxDepth) walk(c, depth + 1)
                else if (c.isFile && c.extension.lowercase() in discExt) out += Game(c.nameWithoutExtension, c.absolutePath)
            }
        }
        walk(File(dir), 0)
        return out.sortedBy { it.title.lowercase() }
    }

    /** Finds a game's folder and file in the games folder. Older paths (absolute) are looked up by file name. */
    private fun locate(path: String): Pair<String, List<Child>>? {
        val tree = tree() ?: return null
        val root = DocumentsContract.getTreeDocumentId(tree)
        if (path.startsWith(PREFIX)) {
            val parts = path.removePrefix(PREFIX).split('/')
            var dir = root
            for (p in parts.dropLast(1)) dir = children(tree, dir).firstOrNull { it.isDir && it.name == p }?.docId ?: return null
            return dir to children(tree, dir)
        }
        val name = fileName(path)
        fun find(docId: String, depth: Int): Pair<String, List<Child>>? {
            val kids = children(tree, docId)
            if (kids.any { !it.isDir && it.name == name }) return docId to kids
            if (depth >= 3) return null
            return kids.filter { it.isDir }.firstNotNullOfOrNull { find(it.docId, depth + 1) }
        }
        return find(root, 0)
    }


    /**
     * Opens a game's disc image and the files it references (BIN tracks, IMG/SUB, MDF).
     * The core reads every format straight through the descriptor (no copy). [stageCopy] makes a CHD or ISO be copied to the
     * app cache instead, for devices where that fails (the caller retries with it when loading fails).
     */
    fun open(
        path: String,
        stageCopy: Boolean = false,
        onArchiveProgress: (DiscArchiveProgress) -> Unit = {},
    ): OpenDisc? {
        lastArchiveError = null
        Log.i(TAG, "open start path=" + path + "; folderConfigured=" + gamesDir().startsWith("content://"))
        val extension = fileName(path).substringAfterLast('.', "").lowercase()
        if (extension in GameDiscArchive.extensions) {
            if (archiveDiscExt.isEmpty()) return null
            return openArchive(path, extension, onArchiveProgress)
        }
        if (!path.startsWith(PREFIX) && File(path).canRead()) return OpenDisc(path, emptyList())
        if (path.startsWith("content://", ignoreCase = true)) return openExternalDocument(path, stageCopy)
        val tree = tree() ?: run { Log.e(TAG, "no SAF tree URI"); return null }
        val located = locate(path) ?: run { Log.e(TAG, "locate failed path=" + path); return null }
        val (_, siblings) = located
        val name = fileName(path)
        val main = siblings.firstOrNull { !it.isDir && it.name == name } ?: run { Log.e(TAG, "main document missing name=" + name); return null }
        Log.i(TAG, "located name=" + name + "; providerSize=" + main.size + "; siblingCount=" + siblings.size)
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.').lowercase()
        val wanted = mutableSetOf(name.lowercase())
        val linkName = HashMap<String, String>() // lower-case file name -> name the core will look for
        siblings.filter { !it.isDir && it.name.substringBeforeLast('.').equals(base, true) }.forEach { wanted += it.name.lowercase() }
        if (ext == "cue") {
            // CUE sheets name their track files: FILE "Track 01.bin" BINARY
            val text = runCatching {
                appContext.contentResolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, main.docId))
                    ?.use { it.readBytes().toString(Charsets.ISO_8859_1) }
            }.getOrNull().orEmpty()
            Regex("(?im)^\\s*FILE\\s+\"?(.+?)\"?\\s+\\S+\\s*$").findAll(text).forEach { m ->
                val ref = m.groupValues[1].replace('\\', '/').substringAfterLast('/')
                wanted += ref.lowercase(); linkName[ref.lowercase()] = ref
            }
        }
        if (ext == "gdi") {
            // GDI sheets (Dreamcast) name their track files: <track> <lba> <type> <sector size> <file> <offset>; fields are
            // matched within one line, so the first line (the track count) can't join the next
            val text = runCatching {
                appContext.contentResolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, main.docId))
                    ?.use { it.readBytes().toString(Charsets.ISO_8859_1) }
            }.getOrNull().orEmpty()
            Regex("(?m)^[ \t]*\\d+[ \t]+\\d+[ \t]+\\d+[ \t]+\\d+[ \t]+(\"[^\"]+\"|\\S+)").findAll(text).forEach { m ->
                val ref = m.groupValues[1].trim('"')
                wanted += ref.lowercase(); linkName[ref.lowercase()] = ref
            }
        }
        if (ext == "mds") siblings.filter { it.name.endsWith(".mdf", true) }.forEach { wanted += it.name.lowercase() }

        // Reopening a /proc/self/fd symlink by path is refused on some devices (Anbernic RG DS: "file not found"), so the
        // core's loaders read these links through the descriptor itself (no reopen). A real file in the app cache,
        // retaining the original extension for loader selection, is only the fallback when [stageCopy] asks for it.
        if (stageCopy && (ext == "chd" || ext == "iso")) {
            val source = DocumentsContract.buildDocumentUriUsingTree(tree, main.docId)
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(path.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }.take(24)
            val cacheDir = File(appContext.cacheDir, "disc-cache").apply { mkdirs() }
            val stamp = "${main.size}-${main.lastModified}"
            val staged = File(cacheDir, "${digest}-${stamp}.${ext}")
            if (!staged.isFile || (main.size > 0L && staged.length() != main.size)) {
                val temp = File(cacheDir, staged.name + ".tmp")
                val copied = runCatching {
                    appContext.contentResolver.openInputStream(source)?.use { input ->
                        temp.outputStream().buffered().use { output -> input.copyTo(output, 1024 * 1024) }
                    } ?: error("Document provider returned no input stream")
                    val actual = temp.length()
                    if (actual <= 0L || (main.size > 0L && actual != main.size)) {
                        error("Copied size mismatch: expected=${main.size}, actual=$actual")
                    }
                    if (staged.exists()) staged.delete()
                    if (!temp.renameTo(staged)) error("Could not finalize cached disc image")
                    actual
                }.getOrElse {
                    temp.delete()
                    Log.e(TAG, "cache copy failed name=$name; error=${it.message}")
                    return null
                }
                Log.i(TAG, "cached disc name=$name; size=$copied; path=${staged.path}")
            } else {
                Log.i(TAG, "reusing cached disc name=$name; size=${staged.length()}")
            }
            return OpenDisc(staged.path, emptyList())
        }

        val stage = newStage()
        val fds = mutableListOf<ParcelFileDescriptor>()
        for (c in siblings) {
            if (c.isDir || c.name.lowercase() !in wanted) continue
            val pfd = runCatching {
                appContext.contentResolver.openFileDescriptor(DocumentsContract.buildDocumentUriUsingTree(tree, c.docId), "r")
            }.getOrNull() ?: continue
            fds += pfd
            runCatching { Os.symlink("/proc/self/fd/${pfd.fd}", File(stage, linkName[c.name.lowercase()] ?: c.name).path) }
        }
        if (fds.isEmpty()) {
            Log.e(TAG, "no referenced files opened name=" + name + "; wanted=" + wanted.joinToString())
            stage.deleteRecursively()
            return null
        }
        val stagedMain = File(stage, main.name).path
        Log.i(TAG, "staged path=" + stagedMain + "; openedDescriptors=" + fds.size + "; mainExists=" + File(stage, main.name).exists())
        return OpenDisc(stagedMain, fds, stage)
    }

    /** Open a one-file game supplied by a frontend's temporary content URI grant. */
    private fun openExternalDocument(path: String, stageCopy: Boolean): OpenDisc? {
        val uri = runCatching { Uri.parse(path) }.getOrNull() ?: return null
        val name = fileName(path).replace(Regex("[/\\\\]"), "_")
        val extension = name.substringAfterLast('.', "").lowercase()
        val size = runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
            } ?: -1L
        }.getOrDefault(-1L)

        if (stageCopy && (extension == "chd" || extension == "iso")) {
            return copyExternalDocument(uri, name, size)
        }

        val descriptor = runCatching { appContext.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
        if (descriptor == null) {
            // Some document providers expose a stream but not a seekable descriptor. CHD/ISO can still be staged.
            if (extension == "chd" || extension == "iso") return copyExternalDocument(uri, name, size)
            lastArchiveError = "The frontend did not grant read access to $name."
            Log.e(TAG, "content URI descriptor denied name=$name")
            return null
        }

        val stage = newStage()
        val stagedMain = File(stage, name)
        val linked = runCatching { Os.symlink("/proc/self/fd/${descriptor.fd}", stagedMain.path) }.isSuccess
        if (!linked) {
            descriptor.close()
            stage.deleteRecursively()
            lastArchiveError = "Could not stage $name from the frontend URI."
            return null
        }
        Log.i(TAG, "opened frontend document name=$name; size=$size; descriptor=${descriptor.fd}")
        return OpenDisc(stagedMain.path, listOf(descriptor), stage)
    }

    private fun copyExternalDocument(uri: Uri, name: String, size: Long): OpenDisc? {
        val extension = name.substringAfterLast('.', "").lowercase()
        val identity = "${uri}|$size"
        val digest = sha256(identity)
        val cacheDir = File(appContext.cacheDir, "disc-cache").apply { mkdirs() }
        val staged = File(cacheDir, "$digest-${size.coerceAtLeast(0)}.$extension")
        if (!staged.isFile || (size > 0L && staged.length() != size)) {
            val temp = File(cacheDir, staged.name + ".tmp")
            val copied = runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().buffered().use { output -> input.copyTo(output, 1024 * 1024) }
                } ?: error("The frontend URI returned no input stream")
                val actual = temp.length()
                if (actual <= 0L || (size > 0L && actual != size)) error("The shared file download was incomplete")
                if (staged.exists()) staged.delete()
                if (!temp.renameTo(staged)) error("Could not finalize the cached game file")
                actual
            }.getOrElse {
                temp.delete()
                lastArchiveError = "Could not copy $name from the frontend: ${it.message ?: "access denied"}"
                Log.e(TAG, lastArchiveError!!, it)
                return null
            }
            Log.i(TAG, "cached frontend document name=$name; size=$copied")
        }
        return OpenDisc(staged.path, emptyList())
    }

    /** Result of [openAndLoad]: the open disc when loading worked (the caller closes it when the game ends), else the error. */
    class DiscLoad(val disc: OpenDisc?, val error: String?)

    /**
     * Opens [path] and hands the disc to the core with [load] (it returns an error message, or null when the core loaded
     * it). The core reads straight through the descriptor; if that fails for a CHD or ISO, loading is retried once from a
     * copy in the app cache (devices whose core can't read that way), and when it works without the copy, copies kept by
     * earlier versions are deleted. The same for every app: only [load] is the app's own.
     */
    fun openAndLoad(
        path: String,
        onArchiveProgress: (DiscArchiveProgress) -> Unit = {},
        load: (OpenDisc) -> String?,
    ): DiscLoad {
        var disc = open(path, onArchiveProgress = onArchiveProgress)
            ?: return DiscLoad(null, lastArchiveError ?: "Unable to access ${fileName(path)}. Check file permission and companion tracks.")
        var error = load(disc)
        val copyable = isCopyable(path)
        var copied = false
        if (error != null && copyable) {
            android.util.Log.w("GBFrame-Disc", "Descriptor read failed; retrying from a cached copy: $path")
            disc.close()
            open(path, stageCopy = true)?.let { copy ->
                disc = copy
                copied = true
                error = load(copy)
            }
        }
        if (error == null && copyable && !copied) clearDiscCopies()
        if (error != null) {
            disc.close()
            return DiscLoad(null, error)
        }
        return DiscLoad(disc, null)
    }

    /** A specific game-archive validation/read error from the most recent [open], when available. */
    fun lastOpenError(): String? = lastArchiveError

    /** Runs [attempt] on the opened disc and closes it; like [openAndLoad], a CHD/ISO that yields null is retried from a copy. */
    fun <T : Any> useDisc(path: String, attempt: (OpenDisc) -> T?): T? {
        open(path)?.use(attempt)?.let { return it }
        if (!isCopyable(path)) return null
        return open(path, stageCopy = true)?.use(attempt)
    }

    private var stageSeq = 0
    private var staleStagesCleared = false

    /** A new folder for one disc's links. Folders left by an earlier process (no disc of it is open) go on first use. */
    @Synchronized
    private fun newStage(): File {
        val root = File(appContext.cacheDir, "disc")
        if (!staleStagesCleared) { root.deleteRecursively(); staleStagesCleared = true }
        return File(root, (++stageSeq).toString()).apply { deleteRecursively(); mkdirs() }
    }

    private fun isCopyable(path: String) = path.endsWith(".chd", ignoreCase = true) || path.endsWith(".iso", ignoreCase = true)

    /**
     * Deletes the CHD/ISO copies earlier versions kept in the app cache (they can be gigabytes). Call it once a disc has
     * loaded through its descriptor, i.e. when the copies are known to be unnecessary on this device.
     */
    fun clearDiscCopies() {
        runCatching {
            File(appContext.cacheDir, "disc-cache").listFiles { f ->
                val name = f.name.lowercase()
                name.endsWith(".chd") || name.endsWith(".iso") || name.endsWith(".chd.tmp") || name.endsWith(".iso.tmp")
            }?.forEach { it.delete() }
        }
    }

    private data class ArchiveSource(
        val file: File,
        val extension: String,
        val identity: String,
        val size: Long,
        val modified: Long,
        val temporary: Boolean,
    )

    private fun openArchive(
        path: String,
        extension: String,
        onProgress: (DiscArchiveProgress) -> Unit,
    ): OpenDisc? {
        lastArchiveError = null
        val source = try { archiveSource(path, extension) } catch (failure: Exception) {
            lastArchiveError = "Could not read ${fileName(path)}: ${failure.message ?: "access denied"}"
            Log.e(TAG, lastArchiveError!!, failure)
            return null
        } ?: run {
            lastArchiveError = "Could not find ${fileName(path)} in the selected games folder."
            return null
        }
        val key = sha256(source.identity)
        try {
            synchronized(archiveCacheLock) {
                val root = File(appContext.cacheDir, "zip-games")
                if (!root.exists() && !root.mkdirs()) throw DiscArchiveException("Could not create the game archive cache.")
                val target = File(root, key)
                val marker = File(target, ".gbframe-zip.properties")
                val primary: String
                val unpackedBytes: Long
                if (cacheMatches(marker, source)) {
                    val metadata = Properties().apply { marker.inputStream().use(::load) }
                    primary = metadata.getProperty("primary")
                    unpackedBytes = metadata.getProperty("bytes").toLongOrNull() ?: 0L
                    val cachedDisc = File(target, primary)
                    if (!cachedDisc.canonicalPath.startsWith(target.canonicalPath + File.separator) || !cachedDisc.isFile) {
                        target.deleteRecursively()
                        throw DiscArchiveException("The cached game archive is incomplete.")
                    }
                    marker.setLastModified(System.currentTimeMillis())
                    Log.i(TAG, "reusing game archive cache name=${fileName(path)} bytes=$unpackedBytes")
                } else {
                    target.deleteRecursively()
                    val temp = File(root, "$key.tmp")
                    temp.deleteRecursively()
                    val extracted = GameDiscArchive.extract(
                        source.file, source.extension, temp, archiveDiscExt, MAX_ARCHIVE_UNPACKED_BYTES,
                    ) { completed, total -> onProgress(DiscArchiveProgress(completed, total)) }
                    Properties().apply {
                        setProperty("identity", source.identity)
                        setProperty("extension", source.extension)
                        setProperty("size", source.size.toString())
                        setProperty("modified", source.modified.toString())
                        setProperty("primary", extracted.primaryPath)
                        setProperty("bytes", extracted.unpackedBytes.toString())
                    }.let { props -> File(temp, ".gbframe-zip.properties").outputStream().use { props.store(it, null) } }
                    if (!temp.renameTo(target)) {
                        temp.deleteRecursively()
                        throw DiscArchiveException("Could not finalize the game archive cache.")
                    }
                    primary = extracted.primaryPath
                    unpackedBytes = extracted.unpackedBytes
                    Log.i(TAG, "extracted game archive name=${fileName(path)} format=$extension bytes=$unpackedBytes")
                }
                pinnedArchiveCaches[key] = (pinnedArchiveCaches[key] ?: 0) + 1
                trimArchiveCacheLocked()
                val disc = File(target, primary)
                return OpenDisc(disc.path, emptyList(), onClose = {
                    synchronized(archiveCacheLock) {
                        val count = pinnedArchiveCaches[key] ?: 0
                        if (count <= 1) pinnedArchiveCaches.remove(key) else pinnedArchiveCaches[key] = count - 1
                        File(target, ".gbframe-zip.properties").setLastModified(System.currentTimeMillis())
                        trimArchiveCacheLocked()
                    }
                })
            }
        } catch (failure: Exception) {
            lastArchiveError = failure.message ?: "Could not extract ${fileName(path)}."
            File(File(appContext.cacheDir, "zip-games"), "$key.tmp").deleteRecursively()
            Log.e(TAG, "game archive open failed name=${fileName(path)} error=$lastArchiveError", failure)
            return null
        } finally {
            if (source.temporary) source.file.delete()
        }
    }

    private fun archiveSource(path: String, extension: String): ArchiveSource? {
        if (path.startsWith("content://", ignoreCase = true)) {
            val uri = Uri.parse(path)
            val size = runCatching {
                appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
                } ?: -1L
            }.getOrDefault(-1L)
            val identity = "$path|$size"
            val archiveDir = File(appContext.cacheDir, "zip-input").apply { mkdirs() }
            val staged = File.createTempFile(sha256(identity), ".$extension", archiveDir)
            try {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    staged.outputStream().buffered().use { output -> input.copyTo(output, 1024 * 1024) }
                } ?: run { staged.delete(); return null }
                if (size > 0L && staged.length() != size) error("The shared archive download was incomplete")
            } catch (failure: Exception) {
                staged.delete()
                throw failure
            }
            return ArchiveSource(staged, extension, identity, staged.length(), 0L, true)
        }
        if (!path.startsWith(PREFIX)) {
            val file = File(path)
            if (!file.isFile || !file.canRead()) return null
            val canonical = file.canonicalPath
            val identity = "$canonical|${file.length()}|${file.lastModified()}"
            return ArchiveSource(file, extension, identity, file.length(), file.lastModified(), false)
        }
        val tree = tree() ?: return null
        val located = locate(path) ?: return null
        val name = fileName(path)
        val main = located.second.firstOrNull { !it.isDir && it.name == name } ?: return null
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, main.docId)
        val size = main.size.coerceAtLeast(0L)
        val modified = main.lastModified.coerceAtLeast(0L)
        val identity = "${path}|$size|$modified"
        val archiveDir = File(appContext.cacheDir, "zip-input").apply { mkdirs() }
        val staged = File.createTempFile(sha256(identity), ".$extension", archiveDir)
        try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                staged.outputStream().buffered().use { output -> input.copyTo(output, 1024 * 1024) }
            } ?: run { staged.delete(); return null }
            if (size > 0 && staged.length() != size) throw DiscArchiveException("The archive download was incomplete.")
        } catch (failure: Exception) {
            staged.delete()
            throw failure
        }
        return ArchiveSource(staged, extension, identity, staged.length(), modified, true)
    }

    private fun cacheMatches(marker: File, source: ArchiveSource): Boolean {
        if (!marker.isFile) return false
        return runCatching {
            val props = Properties().apply { marker.inputStream().use(::load) }
            props.getProperty("identity") == source.identity &&
                props.getProperty("extension", "zip") == source.extension &&
                props.getProperty("size") == source.size.toString() &&
                props.getProperty("modified") == source.modified.toString() &&
                props.getProperty("primary").orEmpty().isNotBlank() &&
                (props.getProperty("bytes").toLongOrNull() ?: -1L) >= 0
        }.getOrDefault(false)
    }

    private fun trimArchiveCacheLocked() {
        val root = File(appContext.cacheDir, "zip-games")
        root.listFiles()?.filter { it.isDirectory && it.name.endsWith(".tmp") }?.forEach { it.deleteRecursively() }
        File(appContext.cacheDir, "zip-input").listFiles()?.filter {
            it.isFile && System.currentTimeMillis() - it.lastModified() > 24L * 60 * 60 * 1000
        }?.forEach { it.delete() }
        val entries = root.listFiles()?.filter { it.isDirectory }.orEmpty()
        var total = entries.sumOf { dir ->
            runCatching {
                val marker = File(dir, ".gbframe-zip.properties")
                Properties().apply { marker.inputStream().use(::load) }.getProperty("bytes").toLongOrNull() ?: 0L
            }.getOrDefault(0L)
        }
        entries.sortedBy { File(it, ".gbframe-zip.properties").lastModified() }.forEach { dir ->
            if (total <= ARCHIVE_CACHE_BUDGET_BYTES) return
            if (dir.name in pinnedArchiveCaches) return@forEach
            val bytes = runCatching {
                Properties().apply { File(dir, ".gbframe-zip.properties").inputStream().use(::load) }
                    .getProperty("bytes").toLongOrNull() ?: 0L
            }.getOrDefault(0L)
            if (dir.deleteRecursively()) total -= bytes
        }
        root.listFiles()?.filter { it.isFile && (it.name.endsWith(".tmp") || it.name.endsWith(".zip")) }
            ?.forEach { if (it.name.endsWith(".tmp")) it.delete() }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
