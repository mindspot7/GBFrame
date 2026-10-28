package gb.frame

import android.content.SharedPreferences
import java.io.File

/** Common state layout and legacy migration. Game links live in app preferences, never game.txt sidecars. */
class SaveStateFiles(private val root: File, private val preferences: SharedPreferences) {
    private val key = "gbframe.stateGames"
    private var migrated = false
    fun games(): Set<String> = preferences.getStringSet(key, emptySet()).orEmpty().toSet()
    @Synchronized fun remember(game: String) { rememberAll(listOf(game)) }
    @Synchronized private fun rememberAll(games: Collection<String>) {
        val previous = games()
        val next = previous + games
        if (next != previous) check(preferences.edit().putStringSet(key, next).commit())
    }
    fun rememberExistingGames(catalog: List<Game>) {
        val folders = DataFile(root, DataLayout.SAVESTATES).listFiles().orEmpty().filter { it.isDirectory }.map { it.name }.toSet()
        rememberAll(catalog.filter { SaveData.gameFolderName(it.path) in folders }.map { it.path })
    }
    fun files(game: String): List<Pair<String, DataFile>> =
        ((1..SaveData.MANUAL_SAVE_STATE_SLOTS).map { it.toString() } + listOf("auto", "quick"))
            .map { it to file(game, it) }.filter { it.second.isFile }

    fun file(game: String, slot: String) = DataLayout.stateFile(root, game, slot)

    /** Called once at startup, before states are listed; preserves unknown/conflicting legacy files. */
    fun migrateLegacyNumberedStates() {
        if (migrated) return
        for (parent in listOf(DataFile(root, "states"), DataFile(root, DataLayout.SAVESTATES))) {
            for (dir in parent.listFiles().orEmpty().filter { it.isDirectory }) {
                val link = DataFile(dir, "game.txt")
                val game = link.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: games().firstOrNull { SaveData.gameFolderName(it) == dir.name } ?: continue
                remember(game) // commit the replacement link before deleting any old sidecar
                migrateLegacyGame(game, listOf(dir))
            }
        }
        migrated = true
    }

    fun migrateLegacyGame(game: String, directories: List<DataFile>) {
        val labels = (0 until SaveData.MANUAL_SAVE_STATE_SLOTS).associate { it.toString() to (it + 1).toString() } +
            mapOf("auto" to "auto", "quick" to "quick")
        for (dir in directories.distinctBy { it.path }) {
            var complete = true
            for ((old, slot) in labels) {
                if (!moveFamily(DataFile(dir, "$old.savestate"), file(game, slot))) complete = false
            }
            if (complete && dir.isDirectory) {
                val remaining = dir.listFiles().orEmpty().filter { it.name != "game.txt" }
                if (remaining.none { it.name.contains(".savestate") }) {
                    DataFile(dir, "game.txt").takeIf { it.exists() }?.delete()
                    if (dir.listFiles().orEmpty().isEmpty()) dir.delete()
                }
            }
        }
    }

    /** Move the state and its screenshot/achievement payload together; binary contents are unchanged. */
    fun moveFamily(old: DataFile, target: DataFile): Boolean {
        var complete = true
        // Keep a conflicting legacy family intact, rather than combining screenshots/RA from different states.
        if (old.isFile && target.isFile && !DataLayout.sameContents(old, target)) return false
        for (suffix in listOf(".png", ".ra", "")) {
            if (!DataLayout.move(DataFile(old.path + suffix), DataFile(target.path + suffix))) complete = false
        }
        return complete
    }
}
