package gb.frame

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.system.Os
import android.util.Log
import java.io.File
import java.security.MessageDigest

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
 * descriptor itself (the app's CHD loader) never reopens the link by path, which some devices refuse; ISO files are still
 * copied to the cache.
 */
/** A game found in the games folder: [path] is a virtual `saf:/` path (or a plain file path). */
data class Game(val title: String, val path: String)

/** A disc opened for the emulator: [path] is a real file path for the core; close it when the game ends. */
class OpenDisc(val path: String, private val fds: List<android.os.ParcelFileDescriptor>) : AutoCloseable {
    override fun close() { fds.forEach { runCatching { it.close() } } }
}

open class SafGames(private val discExt: Set<String>, private val gamesDir: () -> String) {
    private val PREFIX = "saf:/"

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

    /** File name (with extension) of a game path. */
    fun fileName(path: String) = path.substringAfterLast('/')

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

    /** Lists the disc images in the games folder (and up to [maxDepth] levels of subfolders). */
    fun scan(dir: String, maxDepth: Int = 3): List<Game> {
        if (!dir.startsWith("content://")) return scanFiles(dir, maxDepth) // plain folders (tests)
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
        return out.sortedBy { it.title.lowercase() }
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
     * A CHD is read by the core straight through the descriptor (no copy). [stageCopy] makes it copy the CHD to the app
     * cache instead, for devices whose core can't read that way (the caller retries with it when loading fails).
     */
    fun open(path: String, stageCopy: Boolean = false): OpenDisc? {
        Log.i("GBSat-SAF", "open start path=" + path + "; folderConfigured=" + gamesDir().startsWith("content://"))
        if (!path.startsWith(PREFIX) && File(path).canRead()) return OpenDisc(path, emptyList())
        val tree = tree() ?: run { Log.e("GBSat-SAF", "no SAF tree URI"); return null }
        val located = locate(path) ?: run { Log.e("GBSat-SAF", "locate failed path=" + path); return null }
        val (_, siblings) = located
        val name = fileName(path)
        val main = siblings.firstOrNull { !it.isDir && it.name == name } ?: run { Log.e("GBSat-SAF", "main document missing name=" + name); return null }
        Log.i("GBSat-SAF", "located name=" + name + "; providerSize=" + main.size + "; siblingCount=" + siblings.size)
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
            // GDI sheets (Dreamcast) name their track files: <track> <lba> <type> <sector size> <file> <offset>
            val text = runCatching {
                appContext.contentResolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, main.docId))
                    ?.use { it.readBytes().toString(Charsets.ISO_8859_1) }
            }.getOrNull().orEmpty()
            Regex("(?m)^\\s*\\d+\\s+\\d+\\s+\\d+\\s+\\d+\\s+(\"[^\"]+\"|\\S+)").findAll(text).forEach { m ->
                val ref = m.groupValues[1].trim('"')
                wanted += ref.lowercase(); linkName[ref.lowercase()] = ref
            }
        }
        if (ext == "mds") siblings.filter { it.name.endsWith(".mdf", true) }.forEach { wanted += it.name.lowercase() }

        // ISO loaders open their input with native path-based APIs, and reopening a /proc/self/fd symlink by path is
        // refused on some devices (Anbernic RG DS: "file not found"). Stage a real file in the app cache, retaining the
        // original extension for loader selection. A CHD doesn't need it: the core reads it through the descriptor
        // itself (no reopen), so it is copied only when [stageCopy] asks for it (fallback).
        if (ext == "iso" || (ext == "chd" && stageCopy)) {
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
                    Log.e("GBSat-SAF", "cache copy failed name=$name; error=${it.message}")
                    return null
                }
                Log.i("GBSat-SAF", "cached disc name=$name; size=$copied; path=${staged.path}")
            } else {
                Log.i("GBSat-SAF", "reusing cached disc name=$name; size=${staged.length()}")
            }
            return OpenDisc(staged.path, emptyList())
        }

        val stage = File(appContext.cacheDir, "disc").apply { deleteRecursively(); mkdirs() }
        val fds = mutableListOf<ParcelFileDescriptor>()
        for (c in siblings) {
            if (c.isDir || c.name.lowercase() !in wanted) continue
            val pfd = runCatching {
                appContext.contentResolver.openFileDescriptor(DocumentsContract.buildDocumentUriUsingTree(tree, c.docId), "r")
            }.getOrNull() ?: continue
            fds += pfd
            runCatching { Os.symlink("/proc/self/fd/${pfd.fd}", File(stage, linkName[c.name.lowercase()] ?: c.name).path) }
        }
        if (fds.isEmpty()) { Log.e("GBSat-SAF", "no referenced files opened name=" + name + "; wanted=" + wanted.joinToString()); return null }
        val stagedMain = File(stage, main.name).path
        Log.i("GBSat-SAF", "staged path=" + stagedMain + "; openedDescriptors=" + fds.size + "; mainExists=" + File(stage, main.name).exists())
        return OpenDisc(stagedMain, fds)
    }

    /**
     * Deletes the CHD copies earlier versions kept in the app cache (they can be gigabytes). Call it once a CHD has loaded
     * through its descriptor, i.e. when the copies are known to be unnecessary on this device.
     */
    fun clearChdCopies() {
        runCatching {
            File(appContext.cacheDir, "disc-cache").listFiles { f -> f.name.endsWith(".chd") || f.name.endsWith(".chd.tmp") }
                ?.forEach { it.delete() }
        }
    }
}
