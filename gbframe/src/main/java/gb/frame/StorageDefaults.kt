package gb.frame

import android.net.Uri
import android.provider.DocumentsContract

/** Shared public-storage layout for GB-family emulator apps. Access is always granted through the system SAF picker. */
object StorageDefaults {
    const val EMULATORS_DIRECTORY = "EMULATORS"
    const val SATURN_GAMES_DIRECTORY = "ROMS/SATURN"
    const val DREAMCAST_GAMES_DIRECTORY = "ROMS/DREAMCAST"

    fun dataFolder(appName: String): String = "$EMULATORS_DIRECTORY/$appName"

    /** Opens the Android shared-storage provider at a suggested folder; the user must confirm its SAF grant. */
    fun treePickerUri(relativePath: String): Uri {
        val segments = relativePath.replace('\\', '/').trim('/').split('/')
        require(segments.isNotEmpty() && segments.all { it.isNotBlank() && it != "." && it != ".." }) {
            "Invalid suggested storage path"
        }
        return DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:${segments.joinToString("/")}",
        )
    }
}
