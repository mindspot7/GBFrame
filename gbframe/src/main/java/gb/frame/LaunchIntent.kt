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

    /** Extras commonly used by frontends for a game's path or URI. */
    private val EXTRAS = listOf("ROM", "rom", "path", "PATH", "file", "filePath", "game", "uri", Intent.EXTRA_STREAM)

    fun handle(context: Context, intent: Intent?) {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return // reopened from recents
        val name = intent.data?.let { fileName(context, it) }
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri?.let { fileName(context, it) }
            ?: EXTRAS.firstNotNullOfOrNull { key ->
                val value = runCatching { intent.extras?.get(key) }.getOrNull()
                when (value) {
                    is Uri -> fileName(context, value)
                    is String -> value.takeIf { it.isNotBlank() }?.let { stringValue ->
                        if (stringValue.startsWith("content://") || stringValue.startsWith("file://")) {
                            runCatching { fileName(context, Uri.parse(stringValue)) }.getOrNull()
                        } else stringValue.replace('\\', '/').substringAfterLast('/')
                    }
                    is ArrayList<*> -> value.firstNotNullOfOrNull { item ->
                        when (item) {
                            is Uri -> fileName(context, item)
                            is String -> item.replace('\\', '/').substringAfterLast('/').takeIf(String::isNotBlank)
                            else -> null
                        }
                    }
                    else -> null
                }
            }
        if (name.isNullOrBlank()) return
        Log.i("GBFrame-Intent", "launch request: $name")
        pending.value = name
    }

    /**
     * Match exact filenames and titles first. Some frontends send a shorter database title than the image filename;
     * accept a title/filename prefix only when it uniquely identifies a library entry.
     */
    fun resolve(name: String, games: List<Game>): Game? {
        val request = normalize(decodeSegment(name))
        if (request.isBlank()) return null
        fun file(g: Game) = decodeSegment(g.path.substringAfterLast('/').substringAfterLast(':'))
        fun fileKey(g: Game) = normalize(file(g))
        fun titleKey(g: Game) = normalize(g.title)
        games.firstOrNull { fileKey(it) == request }?.let { return it }
        games.firstOrNull { titleKey(it) == request }?.let { return it }
        val prefixMatches = games.filter { game ->
            titleKey(game).startsWith(request) || fileKey(game).substringBeforeLast('.', fileKey(game)).startsWith(request)
        }
        return prefixMatches.singleOrNull()
    }

    /** Resolve from the saved library index first, refreshing it only when the request is missing. */
    fun resolveOrRefresh(name: String, cachedGames: List<Game>, refresh: () -> List<Game>): Game? =
        resolve(name, cachedGames) ?: resolve(name, refresh())

    fun done() { pending.value = null }

    /** Message for a request that matches no library game. */
    fun notFound(name: String) = "\"$name\" is not in the games folder"

    private fun decodeSegment(value: String): String = runCatching {
        // URLDecoder treats '+' as a space; in a URI path it is a literal plus.
        // Use the String charset-name overload: Charset overload is missing on Android API 31 and
        // throws NoSuchMethodError there, which would otherwise leave encoded filenames unmatched.
        URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    }.getOrDefault(value)

    private fun normalize(value: String): String = value.trim().replace(Regex("\\s+"), " ").lowercase()

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
