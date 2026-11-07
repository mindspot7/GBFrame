package gb.frame

/**
 * BIOS import rules shared by the apps (CONVENTIONS "BIOS"): BIOS images are `.bin` files.
 *
 * - The single-file picker gets [pickerMimeTypes]: Android's picker filters by MIME type only, and `.bin` files are
 *   `application/octet-stream`, so other kinds of files (images, videos, documents...) stay hidden.
 * - A folder import lists only names accepted by [isBiosFileName]; a picked file whose name is not `.bin` is refused
 *   with [notBinMessage] before it is read.
 */
object BiosFiles {
    val pickerMimeTypes: Array<String> = arrayOf("application/octet-stream")

    fun isBiosFileName(name: String?): Boolean = name != null && name.endsWith(".bin", ignoreCase = true)

    const val notBinMessage = "Choose a BIOS .bin file"
}
