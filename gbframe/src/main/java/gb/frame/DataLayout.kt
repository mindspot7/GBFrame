package gb.frame

import java.io.File

/** Portable directory and file names. Slot numbers are the numbers displayed to the user (1..5). */
object DataLayout {
    const val BIOS = "bios"
    const val SAVES = "saves"
    const val SAVESTATES = "savestates"
    val requiredFolders = listOf(BIOS, SAVES, SAVESTATES)

    fun stateName(game: String, slot: String): String {
        require(slot in listOf("auto", "quick") || slot.toIntOrNull() in 1..SaveData.MANUAL_SAVE_STATE_SLOTS)
        return "${SaveData.gameFolderName(game)}_$slot.state"
    }

    fun stateFile(root: File, game: String, slot: String) =
        DataFile(root, "$SAVESTATES/${SaveData.gameFolderName(game)}/${stateName(game, slot)}")

    /** Explicit migration only: never overwrite a destination, and verify copies before retiring a source. */
    fun move(source: DataFile, destination: DataFile): Boolean {
        if (source.path == destination.path || !source.exists()) return true
        if (source.isDirectory) {
            destination.mkdirs()
            var complete = true
            for (child in source.listFiles().orEmpty()) {
                if (!move(child, DataFile(destination, child.name))) complete = false
            }
            if (complete) source.delete()
            return complete
        }
        destination.parentFile!!.mkdirs()
        if (!destination.exists() && runCatching { source.renameTo(destination) }.getOrDefault(false)) return true
        if (!destination.exists()) {
            // Publish via a temporary file in the target folder; a failed copy cannot masquerade as a valid state.
            val temp = DataFile.createTempFile(".migrate-", ".tmp", destination.parentFile)
            try {
                source.inputStream().use { input -> temp.outputStream().use { input.copyTo(it) } }
                check(sameContents(source, temp)) { "Data changed during migration: ${source.name}" }
                check(!destination.exists() && temp.renameTo(destination)) { "Could not publish ${destination.name}" }
            } finally { temp.delete() }
        }
        if (!sameContents(source, destination)) return false // retain both conflicting files
        check(source.delete()) { "Could not retire ${source.name}" }
        return true
    }

    internal fun sameContents(a: DataFile, b: DataFile): Boolean = a.inputStream().use { x -> b.inputStream().use { y ->
        val left = ByteArray(65536); val right = ByteArray(65536)
        while (true) {
            val n = x.read(left)
            if (n < 0) return@use y.read() < 0
            var count = 0
            while (count < n) { val read = y.read(right, count, n - count); if (read < 0) return@use false; count += read }
            if (!(0 until n).all { left[it] == right[it] }) return@use false
        }
        @Suppress("UNREACHABLE_CODE") false
    } }
}
