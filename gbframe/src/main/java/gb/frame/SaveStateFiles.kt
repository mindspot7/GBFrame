package gb.frame

import android.content.SharedPreferences
import java.io.File

/**
 * Save states in `<dataDir>/savestates/<Game>/<Game>_<slot>.state` ([SaveData.gameFolderName]); side files (thumbnail,
 * achievements progress) add their own extension to the state's name. The game path of each folder is kept in the
 * preferences, so no file besides the states is written.
 */
class SaveStateFiles(dataDir: File, private val prefs: SharedPreferences) {
    val root = DataFile(dataDir, DataLayout.SAVESTATES)

    private fun key(folder: String) = "stateGame:$folder"
    private fun dir(game: String) = DataFile(root, SaveData.gameFolderName(game))

    fun file(game: String, slot: String): DataFile = DataLayout.stateFile(root.parentFile!!, game, slot)

    /** Moves a state and its side files (`<state>.<ext>`) to [to]; files already at the destination are kept. */
    fun moveFamily(from: File, to: File) {
        val dir = DataFile(from.parent ?: return)
        dir.listFiles { _, n -> n == from.name || n.startsWith(from.name + ".") }?.forEach { f ->
            DataLayout.move(f, DataFile(to.parent, to.name + f.name.removePrefix(from.name)))
        }
    }

    /** Records the game a folder belongs to (call after saving a state). */
    fun remember(game: String) {
        val folder = SaveData.gameFolderName(game)
        if (prefs.getString(key(folder), null) != game) prefs.edit().putString(key(folder), game).apply()
    }

    /** Links existing state folders to the scanned games (folders from before the paths were kept). */
    fun rememberExistingGames(games: List<Game>) = games.forEach { if (dir(it.path).isDirectory) remember(it.path) }

    /** Games with a state folder whose path is known. */
    fun games(): List<String> = root.listFiles()?.filter { it.isDirectory }?.mapNotNull { prefs.getString(key(it.name), null) }.orEmpty()

    /** Slot name -> state file of a game. */
    fun files(game: String): Map<String, DataFile> {
        val prefix = SaveData.gameFolderName(game) + "_"
        return dir(game).listFiles { f -> f.name.startsWith(prefix) && f.name.endsWith(".state") }
            ?.associateBy { it.name.removePrefix(prefix).removeSuffix(".state") }.orEmpty()
    }

    /** Moves the states of older folders of this game (other names or locations) into its folder. */
    fun migrateLegacyGame(game: String, oldDirs: List<File>) {
        val target = dir(game)
        oldDirs.filter { it.isDirectory && it.absoluteFile != target.absoluteFile }.forEach { DataLayout.move(it, target) }
        renameNumbered(target, game)
    }

    /**
     * Older versions named the files `<n>.savestate` (n from 0), `quick`/`auto`, with a `game.txt` holding the game path,
     * and folders could be named after the disc hash. Renames them to the current layout once per folder.
     */
    private var migrated = false
    fun migrateLegacyNumberedStates() {
        if (migrated) return
        migrated = true
        root.listFiles()?.filter { it.isDirectory }?.forEach { folder ->
            val txt = DataFile(folder, "game.txt")
            val game = runCatching { txt.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: prefs.getString(key(folder.name), null)
            if (game == null) return@forEach
            val target = dir(game)
            if (folder.absoluteFile != target.absoluteFile) DataLayout.move(folder, target)
            DataFile(target, "game.txt").delete()
            remember(game)
            renameNumbered(target, game)
        }
    }

    private fun renameNumbered(folder: File, game: String) {
        folder.listFiles()?.forEach { f ->
            if (f.name.endsWith(".state.ra") && f.length() == 0L) { f.delete(); return@forEach }
            val m = Regex("""^(\d+|quick|auto)\.savestate(.*)$""").find(f.name) ?: return@forEach
            val (old, side) = m.destructured
            if (side.isNotEmpty() && f.length() == 0L) { f.delete(); return@forEach } // empty side file (no progress)
            val slot = old.toIntOrNull()?.let { (it + 1).toString() } ?: old
            val dest = DataFile(folder, file(game, slot).name + side)
            if (!dest.exists()) f.renameTo(dest)
        }
    }
}
