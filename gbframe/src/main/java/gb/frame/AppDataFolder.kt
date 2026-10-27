package gb.frame

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.FileObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/** SAF-backed persistent data folder, with a private working copy for path-based native cores. */
class AppDataFolder(context: Context, private val appName: String, private val mounts: Map<String, File>, val texturesRelativePath: String? = null) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("gbframe.dataFolder", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val engine = DataFolderSync(mounts, File(app.filesDir, "data-folder-recovery"))
    private var active = prefs.getString("tree", null)
    private val baseline = mutableMapOf<String, String>()
    private var pendingJob: Job? = null
    private var observers = emptyList<FileObserver>()
    var folder by mutableStateOf(active); private set
    var restartRequired by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    val label: String get() = folder?.let { Uri.decode(Uri.parse(it).lastPathSegment.orEmpty()).substringAfter(':') } ?: "App storage (default)"

    private fun tree(uri: String): DataTree {
        val root = requireNotNull(DocumentFile.fromTreeUri(app, Uri.parse(uri))) { "Folder unavailable" }
        check(root.exists() && root.canRead() && root.canWrite()) { "Folder unavailable or permission lost. Local data is safe." }
        val marker = root.findFile(".gbframe-app")
        if (marker != null) {
            val owner = requireNotNull(app.contentResolver.openInputStream(marker.uri)).bufferedReader().use { it.readText() }
            require(owner == appName) { "Choose a separate folder for $appName" }
        }
        return SafDataTree(app, root, mounts.keys)
    }

    /** Call before creating/loading the native core. Failed restoration retains every local working file. */
    fun restoreAtStartup() = synchronized(lock) {
        val uri = active ?: return@synchronized
        val json = JSONObject(prefs.getString("baseline", "{}") ?: "{}")
        json.keys().forEach { baseline[it] = json.getString(it) }
        runCatching {
            engine.transfer(tree(uri), baseline, pull = true, restoring = prefs.getBoolean("restore", false))
            prefs.edit().putBoolean("restore", false).putString("baseline", JSONObject(baseline.toMap()).toString()).commit()
        }.onFailure {
            // A later restart must not overwrite new offline saves as if this were still an explicit restore.
            prefs.edit().putBoolean("restore", false).putString("baseline", JSONObject(baseline.toMap()).toString()).commit()
            error = (it.message ?: "Could not restore data folder") + ". Local files were kept; choose the folder again to retry restoration."
        }
    }

    /** Selection takes effect at the next process start, so a live core cannot overwrite restored saves. */
    suspend fun choose(uri: Uri) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            busy = true
            try {
                app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                val target = tree(uri.toString())
                // Seed missing files only; existing files in the selected folder are restored after restart.
                val staging = File(app.cacheDir, "data-folder-selection").apply { deleteRecursively(); mkdirs() }
                try {
                    val existing = target.files().toSet()
                    for ((prefix, root) in mounts) if (root.exists()) {
                        root.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { file ->
                            val path = "$prefix/${file.relativeTo(root).invariantSeparatorsPath}"
                            if (path !in existing) target.write(path, file)
                        }
                    }
                    val marker = File(staging, "marker").apply { writeText(appName) }
                    target.write(".gbframe-app", marker)
                } finally { staging.deleteRecursively() }
                check(prefs.edit().putString("tree", uri.toString()).putString("baseline", "{}").putBoolean("restore", true).commit()) { "Could not save folder selection" }
                folder = uri.toString()
                restartRequired = true
                error = null
            } catch (failure: Exception) { error = failure.message ?: "Could not choose folder" }
            finally { busy = false }
        }
    }

    /** Called after completed saves, on background/exit, and by the recursive close-write observers. */
    fun syncNow(): Boolean = synchronized(lock) {
        val uri = active ?: return@synchronized true
        if (restartRequired) return@synchronized true // preserve the newly selected folder until startup restore
        try {
            engine.transfer(tree(uri), baseline, pull = false)
            prefs.edit().putString("baseline", JSONObject(baseline.toMap()).toString()).commit()
            error = null
            true
        } catch (failure: Exception) { error = failure.message ?: "Data folder sync failed"; false }
    }

    fun requestSync() {
        synchronized(lock) {
            pendingJob?.cancel()
            pendingJob = scope.launch { delay(1500); syncNow(); watch() }
        }
    }

    /** Covers, BIOS imports and native backup writes also use the common synchronization path. */
    fun watch() = synchronized(lock) {
        observers.forEach { it.stopWatching() }
        observers = mounts.values.flatMap { root ->
            root.mkdirs()
            root.walkTopDown().filter { it.isDirectory }.map { directory ->
                @Suppress("DEPRECATION")
                object : FileObserver(directory.path, CLOSE_WRITE or MOVED_TO or CREATE) {
                    override fun onEvent(event: Int, path: String?) { if (path != null && !path.endsWith(".tmp")) requestSync() }
                }.also { it.startWatching() }
            }.toList()
        }
    }
}

/** Stages and verifies writes before replacing an existing document, with rollback on rename failure. */
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
