package gb.frame

import java.io.File

/** Folder names every app uses inside its data folder. */
object DataLayout {
    const val BIOS = "bios"
    const val SAVES = "saves"
    const val SAVESTATES = "savestates"
    const val CHEATS = "cheats"
    const val COVERS = "covers"

    /** A game's save state for [slot] (`1`..`5`, `quick`, `auto`): `savestates/<Game>/<Game>_<slot>.state`. */
    fun stateFile(dataDir: File, game: String, slot: String): DataFile {
        val name = SaveData.gameFolderName(game)
        return DataFile(DataFile(DataFile(dataDir, SAVESTATES), name), "${name}_$slot.state")
    }
}
