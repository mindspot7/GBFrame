package gb.frame

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.system.Os
import java.io.File

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
 * path-based loaders (including CUE sheets that name their BIN files) work unchanged.
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

    private data class Child(val docId: String, val name: String, val isDir: Boolean)

    private fun children(tree: Uri, docId: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val out = mutableListOf<Child>()
        runCatching {
            appContext.contentResolver.query(uri, arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    out += Child(c.getString(0), c.getString(1) ?: "", c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR)
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


    /** Opens a game's disc image and the files it references (BIN tracks, IMG/SUB, MDF). */
    fun open(path: String): OpenDisc? {
        if (!path.startsWith(PREFIX) && File(path).canRead()) return OpenDisc(path, emptyList())
        val tree = tree() ?: return null
        val (_, siblings) = locate(path) ?: return null
        val name = fileName(path)
        val main = siblings.firstOrNull { !it.isDir && it.name == name } ?: return null
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
        if (fds.isEmpty()) return null
        return OpenDisc(File(stage, main.name).path, fds)
    }
}
