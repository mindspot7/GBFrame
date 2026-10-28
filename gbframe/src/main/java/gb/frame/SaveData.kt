package gb.frame

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Save data conventions shared by the apps:
 * - each game's saves and save states live in a folder named after the game's file ([gameFolderName]);
 * - Export copies only the saves and save states to a "<App> backup" folder; Import restores a backup or an older data
 *   folder (also one level down).
 */
object SaveData {
    /** Maximum number of numbered/manual save-state slots exposed by GB-family apps. */
    const val MANUAL_SAVE_STATE_SLOTS = 5

    /** Folder name for a game: its file name without extension, minus characters folders can't hold. */
    fun gameFolderName(gamePath: String): String =
        gamePath.substringAfterLast('/').substringBeforeLast('.').replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "game" }

    /** Copies [folders] of [dataDir] into a "[backupName]" folder inside the picked tree. Returns an error or null. */
    fun export(context: Context, dataDir: File, tree: Uri, backupName: String, folders: List<String> = listOf(DataLayout.SAVES, DataLayout.SAVESTATES)): String? =
        runCatching {
            val root = DocumentFile.fromTreeUri(context, tree) ?: error("Folder not available")
            val out = root.findFile(backupName) ?: root.createDirectory(backupName) ?: error("Can't create the backup folder")
            fun copy(src: File, dst: DocumentFile) {
                src.listFiles()?.forEach { f ->
                    if (f.isDirectory) copy(f, dst.findFile(f.name) ?: dst.createDirectory(f.name) ?: return@forEach)
                    else {
                        dst.findFile(f.name)?.delete()
                        val doc = dst.createFile("application/octet-stream", f.name) ?: return@forEach
                        context.contentResolver.openOutputStream(doc.uri)?.use { o -> f.inputStream().use { it.copyTo(o) } }
                    }
                }
            }
            folders.forEach { n ->
                val src = File(dataDir, n)
                if (src.exists()) copy(src, out.findFile(n) ?: out.createDirectory(n) ?: return@forEach)
            }
        }.exceptionOrNull()?.let { "Export failed: ${it.message}" }

    /**
     * Copies the [folders] found in the picked tree (or one level down) into [dataDir], replacing existing files.
     * Returns an error or null.
     */
    fun import(context: Context, dataDir: File, tree: Uri, folders: List<String>): String? = runCatching {
        var root = DocumentFile.fromTreeUri(context, tree) ?: error("Folder not available")
        if (root.listFiles().none { it.isDirectory && it.name in folders }) {
            root = root.listFiles().firstOrNull { d -> d.isDirectory && d.listFiles().any { it.isDirectory && it.name in folders } }
                ?: error("No saved data found in this folder")
        }
        fun copy(src: DocumentFile, dst: File) {
            dst.mkdirs()
            src.listFiles().forEach { f ->
                val name = f.name ?: return@forEach
                if (f.isDirectory) copy(f, File(dst, name))
                else context.contentResolver.openInputStream(f.uri)?.use { i -> File(dst, name).outputStream().use { i.copyTo(it) } }
            }
        }
        root.listFiles().filter { it.isDirectory && it.name in folders }.forEach { copy(it, File(dataDir, it.name!!)) }
    }.exceptionOrNull()?.let { "Import failed: ${it.message}" }
}
