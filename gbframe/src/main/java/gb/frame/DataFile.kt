package gb.frame

import android.content.Context
import android.os.ParcelFileDescriptor
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
    override fun exists() = DataAccess.document(this)?.exists() ?: if (DataAccess.handles(path)) false else super.exists()
    override fun isFile() = DataAccess.document(this)?.isFile ?: if (DataAccess.handles(path)) false else super.isFile()
    override fun isDirectory() = DataAccess.document(this)?.isDirectory ?: if (DataAccess.handles(path)) false else super.isDirectory()
    override fun length() = DataAccess.document(this)?.length() ?: if (DataAccess.handles(path)) 0L else super.length()
    override fun lastModified() = DataAccess.document(this)?.lastModified() ?: if (DataAccess.handles(path)) 0L else super.lastModified()
    override fun mkdirs(): Boolean = if (DataAccess.handles(path)) DataAccess.directory(this).isDirectory else File(path).mkdirs()
    override fun mkdir(): Boolean = if (DataAccess.handles(path)) DataAccess.directory(this).isDirectory else File(path).mkdir()
    override fun delete(): Boolean = if (DataAccess.handles(path)) DataAccess.document(this)?.delete() ?: false else super.delete()
    override fun renameTo(dest: File): Boolean = DataAccess.rename(this, dest)
    override fun listFiles(): Array<DataFile>? = if (DataAccess.handles(path)) {
        DataAccess.document(this)?.takeIf { it.isDirectory }?.listFiles()?.map { DataFile(this, requireNotNull(it.name)) }?.toTypedArray()
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
    fun configure(app: Context, mounts: Map<File, DocumentFile>, onError: (String) -> Unit) {
        context = app.applicationContext
        roots = mounts.entries.sortedByDescending { it.key.absoluteFile.normalize().path.length }.associate { it.key.absoluteFile.normalize().path.trimEnd('/') to it.value }
        failure = onError
    }
    private fun route(path: String): Pair<DocumentFile, List<String>>? {
        val root = roots.entries.firstOrNull { path == it.key || path.startsWith(it.key + "/") } ?: return null
        val parts = path.removePrefix(root.key).trim('/').split('/').filter { it.isNotEmpty() }
        require(parts.none { it == "." || it == ".." || '\\' in it }) { "Invalid app data path" }
        return root.value to parts
    }
    @JvmStatic fun handles(path: String) = roots.keys.any { path == it || path.startsWith(it + "/") }
    internal fun document(file: File): DocumentFile? {
        val (root, parts) = route(file.absolutePath) ?: return null
        var doc = root
        for (part in parts) doc = doc.findFile(part) ?: return null
        return doc
    }
    internal fun directory(file: File): DocumentFile {
        val (root, parts) = requireNotNull(route(file.absolutePath))
        var dir = root
        for (part in parts) dir = dir.findFile(part) ?: requireNotNull(dir.createDirectory(part)) { "Cannot create data folder" }
        check(dir.isDirectory) { "Not a folder: ${file.name}" }
        return dir
    }
    private fun writable(file: File): DocumentFile = document(file) ?: requireNotNull(directory(requireNotNull(file.parentFile)).createFile("application/octet-stream", file.name)) { "Cannot create ${file.name}" }
    internal fun input(file: File): InputStream = if (handles(file.absolutePath))
        requireNotNull(context.contentResolver.openInputStream(requireNotNull(document(file)) { "Missing data file: ${file.name}" }.uri))
        else file.inputStream()
    internal fun output(file: File): OutputStream = if (handles(file.absolutePath))
        requireNotNull(context.contentResolver.openOutputStream(writable(file).uri, "wt")) else file.outputStream()
    internal fun rename(source: File, destination: File): Boolean {
        if (!handles(source.absolutePath) && !handles(destination.absolutePath)) return File(source.path).renameTo(destination)
        check(handles(source.absolutePath) && handles(destination.absolutePath)) { "Cannot move data across storage providers" }
        check(source.parent == destination.parent) { "Cross-directory data rename requires explicit migration" }
        val src = document(source) ?: return false
        val previous = document(destination)
        val backup = ".gbframe-${java.util.UUID.randomUUID()}.bak"
        if (previous != null && !previous.renameTo(backup)) return false
        if (!src.renameTo(destination.name)) { previous?.renameTo(destination.name); return false }
        previous?.delete()
        return true
    }
    @JvmStatic external fun bindNative(roots: Array<String>)
    /** Explicit diagnostics, on demand; creates and removes only its own uniquely named test files. */
    @JvmStatic external fun verifyNativeFileOperations(path: String): Boolean
    @JvmStatic fun openFd(path: String, flags: Int): Int = guarded(-1) {
        val file = File(path)
        val writing = flags and OsConstants.O_ACCMODE != OsConstants.O_RDONLY
        val doc = if (flags and OsConstants.O_CREAT != 0) writable(file) else document(file) ?: return@guarded -1
        val mode = if (!writing) "r" else if (flags and OsConstants.O_APPEND != 0) "wa" else if (flags and OsConstants.O_TRUNC != 0) "rwt" else "rw"
        val descriptor = requireNotNull(context.contentResolver.openFileDescriptor(doc.uri, mode))
        try {
            Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_CUR) // never accept a pipe for native random access
            descriptor.detachFd()
        } finally { descriptor.close() }
    }
    @JvmStatic fun info(path: String): LongArray? = guarded(null) {
        val doc = document(File(path)) ?: return@guarded null
        longArrayOf(if (doc.isDirectory) 1 else 0, doc.length(), doc.lastModified())
    }
    @JvmStatic fun children(path: String): Array<String>? = guarded(null) {
        document(File(path))?.takeIf { it.isDirectory }?.listFiles()?.map { requireNotNull(it.name) }?.toTypedArray()
    }
    @JvmStatic fun makeDirectory(path: String): Boolean = guarded(false) { directory(File(path)).isDirectory }
    @JvmStatic fun remove(path: String): Boolean = guarded(false) { document(File(path))?.delete() ?: false }
    @JvmStatic fun move(from: String, to: String): Boolean = guarded(false) { rename(File(from), File(to)) }
    private inline fun <T> guarded(default: T, action: () -> T): T = try { action() } catch (error: Exception) {
        failure?.invoke(error.message ?: "Data folder access failed")
        default
    }
}
