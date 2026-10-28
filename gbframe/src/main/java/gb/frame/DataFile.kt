package gb.frame

import android.content.Context
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.system.Os
import android.system.OsConstants
import androidx.annotation.Keep
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileFilter
import java.io.FilenameFilter
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset

/** File-shaped adapter for app-owned data. The path is an identity, never a guessed SAF filesystem path. */
class DataFile : File {
    constructor(path: String) : super(path)
    constructor(parent: String?, child: String) : super(parent, child)
    constructor(parent: File?, child: String) : super(parent, child)
    override fun getParentFile(): DataFile? = parent?.let(::DataFile)
    // Metadata of routed files comes from DataAccess's folder listings (one provider query per folder, cached)
    override fun exists() = if (DataAccess.handles(path)) DataAccess.entry(this) != null else super.exists()
    override fun isFile() = if (DataAccess.handles(path)) DataAccess.entry(this)?.isDir == false else super.isFile()
    override fun isDirectory() = if (DataAccess.handles(path)) DataAccess.entry(this)?.isDir == true else super.isDirectory()
    override fun length() = if (DataAccess.handles(path)) DataAccess.entry(this)?.size ?: 0L else super.length()
    override fun lastModified() = if (DataAccess.handles(path)) DataAccess.entry(this)?.modified ?: 0L else super.lastModified()
    override fun mkdirs(): Boolean = if (DataAccess.handles(path)) DataAccess.directory(this).isDirectory else File(path).mkdirs()
    override fun mkdir(): Boolean = if (DataAccess.handles(path)) DataAccess.directory(this).isDirectory else File(path).mkdir()
    override fun delete(): Boolean = if (DataAccess.handles(path)) DataAccess.delete(this) else super.delete()
    override fun renameTo(dest: File): Boolean = DataAccess.rename(this, dest)
    override fun listFiles(): Array<DataFile>? = if (DataAccess.handles(path)) {
        DataAccess.childNames(this)?.map { DataFile(this, it) }?.toTypedArray()
    } else File(path).listFiles()?.map { DataFile(it.path) }?.toTypedArray()
    override fun listFiles(filter: FileFilter?): Array<DataFile>? = listFiles()?.filter { filter?.accept(it) != false }?.toTypedArray()
    override fun listFiles(filter: FilenameFilter?): Array<DataFile>? = listFiles()?.filter { filter?.accept(this, it.name) != false }?.toTypedArray()
    override fun list(): Array<String>? = listFiles()?.map { it.name }?.toTypedArray()
    fun resolve(child: String) = DataFile(this, child)
    fun inputStream(): InputStream = DataAccess.input(this)
    fun outputStream(): OutputStream = DataAccess.output(this)
    fun readBytes() = inputStream().use { it.readBytes() }
    fun readText(charset: Charset = Charsets.UTF_8) = inputStream().bufferedReader(charset).use { it.readText() }
    fun readLines(charset: Charset = Charsets.UTF_8) = inputStream().bufferedReader(charset).use { it.readLines() }
    fun writeText(text: String, charset: Charset = Charsets.UTF_8) = outputStream().bufferedWriter(charset).use { it.write(text) }
    fun writeBytes(bytes: ByteArray) = outputStream().use { it.write(bytes) }
    fun copyTo(target: File, overwrite: Boolean = false): DataFile {
        val out = DataFile(target.path)
        check(overwrite || !out.exists()) { "File already exists: ${out.name}" }
        inputStream().use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }
    companion object {
        const val separator = "/"
        fun createTempFile(prefix: String, suffix: String?, directory: File?): DataFile {
            val dir = requireNotNull(directory)
            return DataFile(dir, prefix + java.util.UUID.randomUUID() + (suffix ?: ".tmp")).also { it.outputStream().close() }
        }
    }
}

/** Shared SAF routing for Kotlin and native clients. No directory-wide startup scan or local mirror. */
@Keep
object DataAccess {
    private lateinit var context: Context
    private var roots: Map<String, DocumentFile> = emptyMap()
    private var failure: ((String) -> Unit)? = null

    /** A file or folder of a routed path, from its folder's listing. */
    class Entry(val doc: DocumentFile, val isDir: Boolean, val size: Long, val modified: Long)
    private class Listing(val at: Long, val entries: Map<String, Entry>)
    // Folder listings (folder document URI -> children), one provider query each: a lookup through DocumentFile.findFile
    // listed every folder of the path again on each exists/length/lastModified (the save-state menu took ~100 queries).
    // Dropped on every change made here, and after a few seconds for changes made outside the app.
    private val lock = Any()
    private val listings = HashMap<String, Listing>()
    private const val LISTING_TTL_MS = 5000L
    private fun invalidate() = synchronized(lock) { listings.clear() }
    private fun list(dir: DocumentFile): Map<String, Entry> {
        val key = dir.uri.toString()
        val now = android.os.SystemClock.uptimeMillis()
        synchronized(lock) { listings[key]?.takeIf { now - it.at < LISTING_TTL_MS }?.let { return it.entries } }
        val parentId = DocumentsContract.getDocumentId(dir.uri)
        val out = HashMap<String, Entry>()
        context.contentResolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(dir.uri, parentId),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val doc = requireNotNull(DocumentFile.fromTreeUri(context, DocumentsContract.buildDocumentUriUsingTree(dir.uri, id)))
                check(DocumentsContract.getDocumentId(doc.uri) == id) { "Data folder lookup failed" }
                out[name] = Entry(doc, c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                    if (c.isNull(3)) 0L else c.getLong(3), if (c.isNull(4)) 0L else c.getLong(4))
            }
        }
        synchronized(lock) { listings[key] = Listing(now, out) }
        return out
    }
    internal fun entry(file: File): Entry? {
        val (root, parts) = route(file.absolutePath) ?: return null
        if (parts.isEmpty()) return Entry(root, true, 0L, 0L)
        var dir = root
        for ((i, part) in parts.withIndex()) {
            val e = list(dir)[part] ?: return null
            if (i == parts.lastIndex) return e
            if (!e.isDir) return null
            dir = e.doc
        }
        return null
    }
    internal fun childNames(file: File): List<String>? = entry(file)?.takeIf { it.isDir }?.let { list(it.doc).keys.toList() }
    internal fun delete(file: File): Boolean {
        val doc = document(file) ?: return false
        invalidate()
        return doc.delete().also { invalidate() }
    }

    fun configure(app: Context, mounts: Map<File, DocumentFile>, onError: (String) -> Unit) {
        context = app.applicationContext
        // Longest first: a mount inside another mount's folder (data/bios inside data) wins
        invalidate()
        roots = mounts.mapKeys { it.key.absoluteFile.normalize().path.trimEnd('/') }.toList().sortedByDescending { it.first.length }.toMap()
        failure = onError
    }
    private fun route(path: String): Pair<DocumentFile, List<String>>? {
        val root = roots.entries.firstOrNull { path == it.key || path.startsWith(it.key + "/") } ?: return null
        val parts = path.removePrefix(root.key).trim('/').split('/').filter { it.isNotEmpty() }
        require(parts.none { it == "." || it == ".." || '\\' in it }) { "Invalid app data path" }
        return root.value to parts
    }
    @JvmStatic fun handles(path: String) = roots.keys.any { path == it || path.startsWith(it + "/") }
    internal fun document(file: File): DocumentFile? = entry(file)?.doc
    internal fun directory(file: File): DocumentFile {
        val (root, parts) = requireNotNull(route(file.absolutePath))
        var dir = root
        for (part in parts) {
            val e = list(dir)[part]
            dir = if (e != null) {
                check(e.isDir) { "Not a folder: ${file.name}" }
                e.doc
            } else {
                invalidate()
                requireNotNull(dir.createDirectory(part)) { "Cannot create data folder" }.also { invalidate() }
            }
        }
        return dir
    }
    private fun writable(file: File): DocumentFile {
        invalidate() // its size and time change
        return document(file) ?: requireNotNull(directory(requireNotNull(file.parentFile)).createFile("application/octet-stream", file.name)) { "Cannot create ${file.name}" }.also { invalidate() }
    }
    internal fun input(file: File): InputStream = if (handles(file.absolutePath))
        requireNotNull(context.contentResolver.openInputStream(requireNotNull(document(file)) { "Missing data file: ${file.name}" }.uri))
        else file.inputStream()
    internal fun output(file: File): OutputStream = if (handles(file.absolutePath)) {
        val stream = requireNotNull(context.contentResolver.openOutputStream(writable(file).uri, "wt"))
        object : java.io.FilterOutputStream(stream) {
            override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
            override fun close() {
                try { super.close() } finally { invalidate() }
            }
        }
    } else file.outputStream()
    internal fun rename(source: File, destination: File): Boolean {
        if (!handles(source.absolutePath) && !handles(destination.absolutePath)) return File(source.path).renameTo(destination)
        check(handles(source.absolutePath) && handles(destination.absolutePath)) { "Cannot move data across storage providers" }
        check(source.parent == destination.parent) { "Cross-directory data rename requires explicit migration" }
        val src = document(source) ?: return false
        val previous = document(destination)
        invalidate()
        val backup = ".gbframe-${java.util.UUID.randomUUID()}.bak"
        if (previous != null && !previous.renameTo(backup)) return false
        if (!src.renameTo(destination.name)) { previous?.renameTo(destination.name); return false }
        previous?.delete()
        invalidate()
        return true
    }
    @JvmStatic external fun bindNative(roots: Array<String>)
    /** Explicit diagnostics, on demand; creates and removes only its own uniquely named test files. */
    @JvmStatic external fun verifyNativeFileOperations(path: String): Boolean
    @JvmStatic fun openFd(path: String, flags: Int): Int = guarded(-1) {
        val file = File(path)
        val writing = flags and OsConstants.O_ACCMODE != OsConstants.O_RDONLY
        if (writing) invalidate()
        val doc = if (flags and OsConstants.O_CREAT != 0) writable(file) else document(file) ?: return@guarded -1
        val mode = if (!writing) "r" else if (flags and OsConstants.O_APPEND != 0) "wa" else if (flags and OsConstants.O_TRUNC != 0) "rwt" else "rw"
        val descriptor = requireNotNull(context.contentResolver.openFileDescriptor(doc.uri, mode))
        try {
            Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_CUR) // never accept a pipe for native random access
            descriptor.detachFd()
        } finally { descriptor.close() }
    }
    @JvmStatic fun info(path: String): LongArray? = guarded(null) {
        val e = entry(File(path)) ?: return@guarded null
        longArrayOf(if (e.isDir) 1 else 0, e.size, e.modified)
    }
    @JvmStatic fun children(path: String): Array<String>? = guarded(null) { childNames(File(path))?.toTypedArray() }
    @JvmStatic fun makeDirectory(path: String): Boolean = guarded(false) { directory(File(path)).isDirectory }
    @JvmStatic fun remove(path: String): Boolean = guarded(false) { delete(File(path)) }
    @JvmStatic fun move(from: String, to: String): Boolean = guarded(false) { rename(File(from), File(to)) }
    private inline fun <T> guarded(default: T, action: () -> T): T = try { action() } catch (error: Exception) {
        failure?.invoke(error.message ?: "Data folder access failed")
        default
    }
}
