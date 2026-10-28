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

    /** Records the game a folder belongs to (call after saving a state). */
    fun remember(game: String) {
        val folder = SaveData.gameFolderName(game)
        if (prefs.getString(key(folder), null) != game) prefs.edit().putString(key(folder), game).apply()
    }

    /** Links existing state folders to the scanned games (folders copied from another device or data folder). */
    fun rememberExistingGames(games: List<Game>) = games.forEach { if (dir(it.path).isDirectory) remember(it.path) }

    /** Games with a state folder whose path is known. */
    fun games(): List<String> = root.listFiles()?.filter { it.isDirectory }?.mapNotNull { prefs.getString(key(it.name), null) }.orEmpty()

    /** Slot name -> state file of a game. */
    fun files(game: String): Map<String, DataFile> {
        val prefix = SaveData.gameFolderName(game) + "_"
        return dir(game).listFiles { f -> f.name.startsWith(prefix) && f.name.endsWith(".state") }
            ?.associateBy { it.name.removePrefix(prefix).removeSuffix(".state") }.orEmpty()
    }
}
