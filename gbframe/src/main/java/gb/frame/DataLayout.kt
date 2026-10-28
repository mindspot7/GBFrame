package gb.frame

import java.io.File

/** Folder names every app uses inside its data folder. */
object DataLayout {
    const val BIOS = "bios"
    const val SAVES = "saves"
    const val SAVESTATES = "savestates"
    const val CHEATS = "cheats"
    const val COVERS = "covers"

    /** Folders renamed since older versions (old -> new): [AppDataFolder] renames them in the picked folder. */
    val RENAMED = mapOf("states" to SAVESTATES)

    /** A game's save state for [slot] (`1`..`5`, `quick`, `auto`): `savestates/<Game>/<Game>_<slot>.state`. */
    fun stateFile(dataDir: File, game: String, slot: String): DataFile {
        val name = SaveData.gameFolderName(game)
        return DataFile(DataFile(DataFile(dataDir, SAVESTATES), name), "${name}_$slot.state")
    }

    /**
     * Moves [from] to [to] (renamed when they share a parent and [to] doesn't exist, else merged file by file without
     * overwriting); [from] is removed when emptied. Works on the data folder ([DataFile]) and on plain files.
     */
    fun move(from: File, to: File) {
        val src = DataFile(from.path); val dst = DataFile(to.path)
        if (!src.exists()) return
        if (src.parent == dst.parent && !dst.exists() && src.renameTo(dst)) return
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles()?.forEach { move(it, DataFile(dst, it.name)) }
            if (src.listFiles()?.isEmpty() == true) src.delete()
        } else if (!dst.exists()) {
            src.copyTo(dst)
            src.delete()
        }
    }
}
