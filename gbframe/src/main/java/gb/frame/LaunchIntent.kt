package gb.frame

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import java.net.URLDecoder

/**
 * Starting a game straight from an intent, for frontends (ES-DE, Daijishou, Pegasus) and scripts:
 *
 * ```
 * adb shell am start -n <appId>/<activity> -a android.intent.action.VIEW -d "content://…/Virtua%20Fighter%202.chd"
 * adb shell am start -n <appId>/<activity> --es ROM "/storage/emulated/0/ROMS/SATURN/Virtua Fighter 2.chd"
 * ```
 *
 * The request is matched to a library game by file name first, then by its displayed title. This lets frontends pass
 * either a ROM path or a title such as `Crazy Taxi`; the disc is still opened through the configured games folder, so
 * multi-file discs find their tracks and save states, play time and per-game options stay with the same game. The
 * Activity calls [handle] from `onCreate` (when
 * `savedInstanceState` is null) and `onNewIntent`; the app watches [pending] once it can start games, resolves it with
 * [resolve] and calls [done].
 */
object LaunchIntent {
    /** File name of the game requested by the last intent, until the app takes it ([done]). */
    val pending = mutableStateOf<String?>(null)

    /** Extras that frontends use for the game's path or URI. */
    private val EXTRAS = listOf("ROM", "rom", "path", "PATH", "file", "filePath", "game", "uri")

    fun handle(context: Context, intent: Intent?) {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return // reopened from recents
        val name = intent.data?.let { fileName(context, it) }
            ?: EXTRAS.firstNotNullOfOrNull { key -> intent.getStringExtra(key)?.takeIf { it.isNotBlank() } }?.let { value ->
                if (value.startsWith("content://") || value.startsWith("file://")) fileName(context, Uri.parse(value))
                else value.substringAfterLast('/')
            }
        if (name.isNullOrBlank()) return
        Log.i("GBFrame-Intent", "launch request: $name")
        pending.value = name
    }

    /** The matching library game (file name first, then displayed title; exact case before case-insensitive), or null. */
    fun resolve(name: String, games: List<Game>): Game? {
        val request = decodeSegment(name.trim())
        fun file(g: Game) = decodeSegment(g.path.substringAfterLast('/').substringAfterLast(':'))
        return games.firstOrNull { file(it) == request }
            ?: games.firstOrNull { file(it).equals(request, ignoreCase = true) }
            ?: games.firstOrNull { it.title == request }
            ?: games.firstOrNull { it.title.equals(request, ignoreCase = true) }
    }

    fun done() { pending.value = null }

    /** Message for a request that matches no library game. */
    fun notFound(name: String) = "\"$name\" is not in the games folder"

    private fun decodeSegment(value: String): String = runCatching {
        // URLDecoder treats '+' as a space; in a URI path it is a literal plus.
        URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8)
    }.getOrDefault(value)

    private fun fileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
            // No access to the provider: the document id or the path usually ends with the file name
            val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: uri.lastPathSegment
            return id?.substringAfterLast('/')?.substringAfterLast(':')
        }
        return uri.lastPathSegment ?: uri.path?.substringAfterLast('/')
    }
}
