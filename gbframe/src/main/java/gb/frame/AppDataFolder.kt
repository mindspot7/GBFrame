package gb.frame

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * The selected tree is the authoritative storage, shared by Kotlin and native clients. Data lives in a
 * subfolder named after the app (`<selected>/<appName>`), unless the selected folder already has that name.
 */
class AppDataFolder(context: Context, val appName: String, private val mounts: Map<String, File>, val texturesRelativePath: String? = null) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("gbframe.dataFolder", Context.MODE_PRIVATE)
    private var active = prefs.getString("tree", null)
    var folder by mutableStateOf(active); private set
    var restartRequired by mutableStateOf(false); private set
    /** Called after a folder picked in place of app storage starts being used without a restart (reopen open files). */
    var onOpened: (() -> Unit)? = null
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var selectionNotice by mutableStateOf<String?>(null); private set
    val label: String get() = folder?.let { uri ->
        val tree = Uri.decode(Uri.parse(uri).lastPathSegment.orEmpty()).substringAfter(':').trimEnd('/')
        when {
            tree.isEmpty() -> appName
            tree.substringAfterLast('/').equals(appName, ignoreCase = true) -> tree
            else -> "$tree/$appName"
        }
    } ?: "App storage (default)"

    /** Display the effective storage location, never the source directory used for an import. */
    fun displayPath(file: File): String {
        val path = file.absoluteFile.normalize().path
        val mount = mounts.entries.firstOrNull { path == it.value.absolutePath || path.startsWith(it.value.absolutePath.trimEnd('/') + "/") }
        return if (folder != null && mount != null) {
            "$label/${mount.key}" + path.removePrefix(mount.value.absolutePath)
        } else path
    }

    private fun tree(uri: String): DocumentFile {
        val tree = requireNotNull(DocumentFile.fromTreeUri(app, Uri.parse(uri))) { "Folder unavailable" }
        check(tree.exists() && tree.canRead() && tree.canWrite()) { "Folder unavailable or permission lost. Reconnect storage or choose a folder." }
        return tree
    }

    private fun owner(dir: DocumentFile): String? = dir.findFile(".gbframe-app")?.let { marker ->
        requireNotNull(app.contentResolver.openInputStream(marker.uri)).bufferedReader().use { it.readText() }
    }

    /** The app's own folder inside the selected tree, created on first use. */
    private fun root(uri: String): DocumentFile {
        val tree = tree(uri)
        val root = if (tree.name.equals(appName, ignoreCase = true)) tree
            else tree.findFile(appName)?.also { check(it.isDirectory) { "$appName in the selected folder is not a folder" } }
                ?: requireNotNull(tree.createDirectory(appName)) { "Cannot create $appName folder" }
        owner(root)?.let { require(it == appName) { "Choose a separate folder for $appName" } }
        return root
    }

    /** Opens the picked folder (no scan or copy); files left in app storage after picking it are retired once. */
    fun openAtStartup() {
        val uri = active ?: return
        open(uri)
    }

    private fun open(uri: String) {
        try {
            val root = root(uri)
            if (prefs.getBoolean("retireLocal", false)) {
                retireLegacyFiles(SafDataTree(app, root, mounts.keys))
                check(prefs.edit().remove("retireLocal").commit())
            }
            // One listing of the app folder (each findFile lists it again: ~50 ms per call on a SAF tree)
            val children = root.listFiles().associateBy { it.name }
            val routes = mounts.map { (name, local) ->
                local to (children[name] ?: requireNotNull(root.createDirectory(name)) { "Cannot create $name" })
            }.toMap()
            DataAccess.configure(app, routes) { error = it }
            DataAccess.bindNative(routes.keys.map { it.absoluteFile.normalize().path.trimEnd('/') }.toTypedArray())
            error = null
        } catch (failure: Exception) {
            error = failure.message ?: "Could not open data folder"
            throw IllegalStateException(error, failure) // no silent fallback to stale local saves
        }
    }

    /** Copy only during an explicit folder change; the selected destination takes effect after process restart. */
    suspend fun choose(uri: Uri) = withContext(Dispatchers.IO) {
        busy = true
        selectionNotice = null
        try {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val destination = root(uri.toString())
            val target = SafDataTree(app, destination, mounts.keys)
            val existing = target.files().toSet()
            val staging = File(app.cacheDir, "data-folder-selection").apply { mkdirs() }
            try {
                val current = active
                if (current != null && current != uri.toString()) {
                    val previous = runCatching { root(current) }.getOrNull()
                    if (previous == null) selectionNotice = "The previous folder is unavailable, so its files could not be copied. They remain in that folder. The app will use the files in the newly selected folder."
                    else {
                        val source = SafDataTree(app, previous, mounts.keys)
                        for (path in source.files()) if (path !in existing) {
                            val temp = File.createTempFile("migrate-", ".tmp", staging)
                            try { source.read(path).use { input -> temp.outputStream().use { input.copyTo(it) } }; target.write(path, temp) }
                            finally { temp.delete() }
                        }
                    }
                } else if (active == null) {
                    for ((prefix, local) in mounts) if (local.exists()) local.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { file ->
                        val path = "$prefix/${file.relativeTo(local).invariantSeparatorsPath}"
                        if (path !in existing) target.write(path, file)
                    }
                }
                val marker = File(staging, "marker").apply { writeText(appName) }
                try { target.write(".gbframe-app", marker) } finally { marker.delete() }
            } finally { staging.delete() }
            check(prefs.edit().putString("tree", uri.toString()).putBoolean("retireLocal", active == null).commit())
            folder = uri.toString(); error = null
            if (active == null) {
                // First folder (the setup wizard, or leaving app storage): app storage was just copied into it, so it is
                // routed now instead of after a restart; the app reopens what it keeps open (onOpened), and the local
                // copies are retired at the next start
                active = uri.toString()
                open(uri.toString())
                onOpened?.invoke()
            } else restartRequired = true
        } catch (failure: Exception) { error = failure.message ?: "Could not choose folder" }
        finally { busy = false }
    }

    /** Retire the app-storage copies after a folder was picked, checking each destination. Conflicts go to a recovery archive. */
    private fun retireLegacyFiles(target: DataTree) {
        val remote = target.files().toSet()
        for ((prefix, mounted) in mounts) {
            val local = File(mounted.path) // deliberately bypass DataFile: this is the obsolete physical directory
            if (!local.exists()) continue
            for (file in local.walkTopDown().filter { it.isFile }.toList()) {
                require(file.canonicalPath.startsWith(local.canonicalPath + File.separator)) { "Data path escapes its mount" }
                val path = "$prefix/${file.relativeTo(local).invariantSeparatorsPath}"
                val equal = path in remote && target.read(path).use { external -> file.inputStream().use { original ->
                    val a = ByteArray(65536); val b = ByteArray(65536)
                    var matches = true
                    while (matches) {
                        val n = original.read(a)
                        if (n < 0) { matches = external.read() < 0; break }
                        var got = 0
                        while (got < n) { val k = external.read(b, got, n - got); if (k < 0) break; got += k }
                        matches = got == n && (0 until n).all { a[it] == b[it] }
                    }
                    matches
                } }
                if (!equal) {
                    val recovery = File(app.filesDir, "data-folder-recovery/legacy-${System.currentTimeMillis()}/$path")
                    recovery.parentFile!!.mkdirs()
                    check(!recovery.exists()) { "Legacy recovery already exists: $path" }
                    file.copyTo(recovery)
                }
                check(file.delete()) { "Could not retire old local file: $path" }
            }
            local.walkBottomUp().filter { it.isDirectory }.forEach { it.delete() }
        }
    }

    /** Availability check only. Direct writes are already in the selected folder; there is nothing to synchronize. */
    fun checkAccess(): Boolean = try {
        active?.let {
            root(it)
            if (!restartRequired) check(DataAccess.verifyNativeFileOperations(File(mounts.values.first(), ".gbframe-check-${java.util.UUID.randomUUID()}").path)) { "This folder does not support the file operations required by the emulator" }
        }
        error = null; true
    }
        catch (failure: Exception) { error = failure.message; false }

}

/** Stages and verifies writes before replacing an existing document, with rollback on rename failure. */
/** The app's mounted folders in a data tree: relative file paths, read and write. */
private interface DataTree {
    fun files(): List<String>
    fun read(path: String): InputStream
    fun write(path: String, source: File)
}

private class SafDataTree(private val context: Context, private val root: DocumentFile, private val roots: Set<String>) : DataTree {
    private fun document(path: String, createParents: Boolean = false): Pair<DocumentFile, String> {
        val parts = path.split('/')
        require(parts.none { it.isBlank() || it == "." || it == ".." || '\\' in it }) { "Invalid data path" }
        var parent = root
        for (part in parts.dropLast(1)) parent = parent.findFile(part)
            ?: (if (createParents) parent.createDirectory(part) else null) ?: error("Missing folder: $part")
        return parent to parts.last()
    }
    override fun files(): List<String> = buildList {
        fun scan(dir: DocumentFile, prefix: String) {
            check(dir.canRead()) { "Cannot read data folder" }
            dir.listFiles().forEach { child ->
                val name = requireNotNull(child.name)
                if (prefix.isEmpty() && name !in roots) return@forEach
                require(name != "." && name != ".." && '/' !in name && '\\' !in name)
                if (name.startsWith(".gbframe-")) return@forEach
                val path = prefix + name
                if (child.isDirectory) scan(child, "$path/") else if (child.isFile) add(path)
            }
        }
        scan(root, "")
    }
    override fun read(path: String): InputStream {
        val (parent, name) = document(path)
        return requireNotNull(context.contentResolver.openInputStream(requireNotNull(parent.findFile(name)).uri))
    }
    fun delete(path: String) {
        val (parent, name) = document(path)
        parent.findFile(name)?.let { check(it.delete()) { "Could not remove $path" } }
    }
    /** Remove the folders left empty under [name] after a move; folders that still hold files stay. */
    fun pruneEmpty(name: String) {
        fun prune(dir: DocumentFile): Boolean = dir.listFiles().all { it.isDirectory && prune(it) } && dir.delete()
        root.findFile(name)?.takeIf { it.isDirectory }?.let(::prune)
    }
    override fun write(path: String, source: File) {
        val (parent, name) = document(path, createParents = true)
        val id = java.util.UUID.randomUUID().toString()
        val temp = requireNotNull(parent.createFile("application/octet-stream", ".gbframe-$id.tmp")) { "Cannot write folder" }
        var previous: DocumentFile? = null
        try {
            requireNotNull(context.contentResolver.openOutputStream(temp.uri, "wt")).use { output -> source.inputStream().use { it.copyTo(output) } }
            requireNotNull(context.contentResolver.openInputStream(temp.uri)).use { input ->
                source.inputStream().use { original ->
                    val a = ByteArray(65536); val b = ByteArray(65536)
                    while (true) {
                        val count = original.read(a)
                        if (count < 0) { check(input.read() < 0); break }
                        var read = 0
                        while (read < count) { val n = input.read(b, read, count - read); check(n > 0) { "Incomplete data write" }; read += n }
                        check(a.copyOf(count).contentEquals(b.copyOf(count))) { "Data verification failed" }
                    }
                }
            }
            previous = parent.findFile(name)
            if (previous != null) check(previous.renameTo(".gbframe-$id.bak")) { "Provider cannot safely replace $name" }
            if (!temp.renameTo(name)) {
                previous?.renameTo(name)
                error("Provider cannot safely save $name")
            }
            previous?.delete()
        } finally { if (temp.name != name) temp.delete() }
    }
}
