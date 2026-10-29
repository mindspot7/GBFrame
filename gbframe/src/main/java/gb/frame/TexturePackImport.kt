package gb.frame

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import com.github.junrar.Archive
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/** Shared texture archive importer. It stages and validates every archive before writing to the routed data folder. */
@Composable
fun TexturePackImportRow(textureFolder: File) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !busy) scope.launch {
            busy = true
            message = null
            try {
                val imported = withContext(Dispatchers.IO) { importTextureArchive(context, uri, textureFolder) }
                message = "Imported $imported texture files"
            } catch (failure: Exception) {
                message = failure.message ?: "Could not import texture package"
            } finally { busy = false }
        }
    }
    ActionRow(Icons.Default.FolderZip, "Import texture pack", message ?: "ZIP, 7z or RAR into data/textures/<game ID>",
        if (busy) "Importing…" else "Import", { if (!busy) picker.launch(arrayOf("application/zip", "application/x-7z-compressed", "application/vnd.rar", "application/octet-stream")) })
}

private fun importTextureArchive(context: android.content.Context, uri: Uri, textureFolder: File): Int {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "texture-pack.zip"
    val extension = name.substringAfterLast('.', "").lowercase()
    require(extension in setOf("zip", "7z", "rar")) { "Choose a ZIP, 7z or RAR archive" }
    val staging = File(context.cacheDir, "texture-import-${java.util.UUID.randomUUID()}").apply { check(mkdirs()) }
    val archiveFile = File(staging, "package.$extension")
    val extracted = File(staging, "files").apply { mkdirs() }
    try {
        resolver.openInputStream(uri)?.use { input -> archiveFile.outputStream().use { input.copyBoundedTo(it, MAX_UNPACKED_BYTES) } }
            ?: error("Cannot read selected archive")
        var count = 0
        var total = 0L
        val flatPackName = name.substringBeforeLast('.', name).replace(Regex("[^A-Za-z0-9._-]"), "_")
        fun save(entryName: String, directory: Boolean, copy: ((FileOutputStream) -> Unit)?) {
            if (directory) return
            require(++count <= MAX_FILES) { "Texture package contains too many files" }
            val parts = entryName.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
            require(parts.isNotEmpty() && parts.none { it == ".." || ':' in it }) { "Archive contains an unsafe file path" }
            val normalized = parts.dropWhile { it.equals("textures", true) }
            val path = (if (normalized.isEmpty()) parts else normalized).toMutableList()
            if (path.size == 1) path.add(0, flatPackName)
            val destination = File(extracted, path.joinToString(File.separator))
            check(destination.canonicalPath.startsWith(extracted.canonicalPath + File.separator)) { "Archive contains an unsafe file path" }
            destination.parentFile?.mkdirs()
            FileOutputStream(destination).use { out -> copy?.invoke(out) }
            total += destination.length()
            require(total <= MAX_UNPACKED_BYTES) { "Texture package exceeds the 8 GiB import limit" }
        }
        when (extension) {
            "zip" -> ZipInputStream(archiveFile.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    save(entry.name, entry.isDirectory) { out -> zip.copyBoundedTo(out, MAX_UNPACKED_BYTES - total) }
                    zip.closeEntry()
                }
            }
            "7z" -> SevenZFile.builder().setFile(archiveFile).setMaxMemoryLimitKiB(256 * 1024).get().use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    save(entry.name, entry.isDirectory) { out -> zip.copyBoundedTo(out, MAX_UNPACKED_BYTES - total) }
                }
            }
            "rar" -> Archive(archiveFile).use { rar ->
                while (true) {
                    val entry = rar.nextFileHeader() ?: break
                    val entryName = entry.fileName
                    save(entryName, entry.isDirectory) { out -> rar.extractFile(entry, out) }
                }
            }
        }
        require(count > 0) { "No texture files found in archive" }
        extracted.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(extracted).invariantSeparatorsPath
            val target = DataFile(textureFolder, relative)
            target.parentFile?.mkdirs()
            file.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        }
        return count
    } finally { staging.deleteRecursively() }
}

private fun java.io.InputStream.copyBoundedTo(out: FileOutputStream, remaining: Long) {
    val buffer = ByteArray(64 * 1024)
    var copied = 0L
    while (true) {
        val size = read(buffer)
        if (size < 0) break
        copied += size
        require(copied <= remaining) { "Texture package exceeds the 8 GiB import limit" }
        out.write(buffer, 0, size)
    }
}

private fun SevenZFile.copyBoundedTo(out: FileOutputStream, remaining: Long) {
    val buffer = ByteArray(64 * 1024)
    var copied = 0L
    while (true) {
        val size = read(buffer)
        if (size <= 0) break
        copied += size
        require(copied <= remaining) { "Texture package exceeds the 8 GiB import limit" }
        out.write(buffer, 0, size)
    }
}

private const val MAX_FILES = 50_000
private const val MAX_UNPACKED_BYTES = 8L * 1024 * 1024 * 1024
