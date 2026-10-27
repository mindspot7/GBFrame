package gb.frame

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Provider-independent data transfer. A failed write must leave the previous destination intact. */
interface DataTree {
    fun files(): List<String>
    fun read(path: String): InputStream
    fun write(path: String, source: File)
}

/** Only explicitly mounted app data is shared; preferences, credentials and caches stay private. */
class DataFolderSync(private val mounts: Map<String, File>, private val recovery: File) {
    init { require(mounts.keys.all { it.matches(Regex("[A-Za-z0-9_-]+")) }) }

    private fun local(path: String): File? {
        val parts = path.split('/')
        val root = mounts[parts.first()] ?: return null
        require(parts.size >= 2 && parts.none { it.isBlank() || it == "." || it == ".." || '\\' in it }) { "Invalid data path" }
        val file = File(root, parts.drop(1).joinToString("/"))
        require(file.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "Invalid data path" }
        return file
    }

    private fun hash(input: InputStream): String = input.use {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = it.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    /** Pull only before core initialization. Afterwards send completed local writes, never replace live core files. */
    fun transfer(tree: DataTree, baseline: MutableMap<String, String>, pull: Boolean, restoring: Boolean = false) {
        val remote = tree.files().toSet()
        for (path in remote.sorted()) {
            val target = local(path) ?: continue
            if (!pull) continue
            val remoteHash = hash(tree.read(path))
            val localHash = target.takeIf { it.isFile }?.let { hash(it.inputStream()) }
            if (remoteHash == localHash) { baseline[path] = remoteHash; continue }
            // Never replace an unsynchronized local save after a crash or unavailable SD card.
            if (!restoring && localHash != null && localHash != baseline[path]) continue
            target.parentFile!!.mkdirs()
            val temp = File.createTempFile("gbframe-", ".tmp", target.parentFile)
            try {
                tree.read(path).use { input -> temp.outputStream().use { input.copyTo(it) } }
                check(hash(temp.inputStream()) == remoteHash) { "Data changed during restore: $path" }
                if (target.isFile) {
                    val saved = File(recovery, "${System.currentTimeMillis()}/$path")
                    saved.parentFile!!.mkdirs()
                    target.copyTo(saved)
                }
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                baseline[path] = remoteHash
            } finally { temp.delete() }
        }
        for ((prefix, root) in mounts) {
            if (!root.exists()) continue
            root.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { file ->
                val path = "$prefix/${file.relativeTo(root).invariantSeparatorsPath}"
                local(path) // reject symlinks escaping a mount
                val digest = hash(file.inputStream())
                if (path !in remote || baseline[path] != digest) {
                    // Keep an externally edited version before an offline/local edit replaces it.
                    if (path in remote) {
                        val previous = hash(tree.read(path))
                        if (previous != digest && previous != baseline[path]) {
                            val saved = File(recovery, "${System.currentTimeMillis()}/external/$path")
                            saved.parentFile!!.mkdirs()
                            tree.read(path).use { input -> saved.outputStream().use { input.copyTo(it) } }
                        }
                    }
                    val snapshot = File.createTempFile("snapshot-", ".tmp", recovery.apply { mkdirs() })
                    try {
                        file.copyTo(snapshot, overwrite = true)
                        check(hash(snapshot.inputStream()) == digest) { "Data still being written: $path" }
                        tree.write(path, snapshot)
                        check(hash(tree.read(path)) == digest) { "Could not verify $path" }
                        baseline[path] = digest
                    } finally { snapshot.delete() }
                }
            }
        }
    }
}
