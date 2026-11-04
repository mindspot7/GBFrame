package gb.frame

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import java.net.URLDecoder

/** A frontend request keeps both its human-readable name and the URI/path that grants access to the actual file. */
data class GameLaunchRequest(val name: String, val source: String? = null)

/**
 * Starting a game straight from an intent, for frontends (ES-DE, Daijishou, Pegasus) and scripts.
 *
 * Match the request against the library first so CUE/GDI sets, per-game settings and saves use their SAF paths. If no
 * library entry matches, SafGames may open a supported single-file URI/path directly. Multi-file descriptors still need
 * the games-folder grant so the emulator can read their companion tracks.
 */
object LaunchIntent {
    /** Request from the most recent intent, until the app takes it ([done]). */
    val pending = mutableStateOf<GameLaunchRequest?>(null)

    /** Extras commonly used by frontends for a game's path, URI or title. */
    private val EXTRAS = listOf(
        "ROM", "rom", "romPath", "rom_path", "path", "PATH", "file", "filePath", "file_path",
        "filename", "game", "gamePath", "game_path", "uri", Intent.EXTRA_STREAM, Intent.EXTRA_TEXT,
    )

    fun handle(context: Context, intent: Intent?) {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return // reopened from recents
        val request = intent.data?.let { request(context, it) }
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri?.let { request(context, it) }
            ?: EXTRAS.firstNotNullOfOrNull { key ->
                val value = runCatching { intent.extras?.get(key) }.getOrNull()
                when (value) {
                    is Uri -> request(context, value)
                    is String -> request(context, value)
                    is ArrayList<*> -> value.firstNotNullOfOrNull { item ->
                        when (item) {
                            is Uri -> request(context, item)
                            is String -> request(context, item)
                            else -> null
                        }
                    }
                    is Array<*> -> value.firstNotNullOfOrNull { item ->
                        when (item) {
                            is Uri -> request(context, item)
                            is String -> request(context, item)
                            else -> null
                        }
                    }
                    else -> null
                }
            }
        if (request == null || request.name.isBlank()) return
        val sourceKind = request.source?.let { runCatching { Uri.parse(it).scheme }.getOrNull() ?: "path" } ?: "title"
        Log.i("GBFrame-Intent", "launch request: ${request.name}; source=$sourceKind")
        pending.value = request
    }

    private fun request(context: Context, uri: Uri): GameLaunchRequest? {
        val name = fileName(context, uri) ?: uri.lastPathSegment?.substringAfterLast(':') ?: return null
        return GameLaunchRequest(name, uri.toString())
    }

    private fun request(context: Context, value: String): GameLaunchRequest? {
        if (value.isBlank()) return null
        val normalized = value.trim().replace('\\', '/')
        if (normalized.startsWith("content://", true) || normalized.startsWith("file://", true)) {
            val uri = runCatching { Uri.parse(normalized) }.getOrNull() ?: return null
            return request(context, uri)
        }
        val name = normalized.substringAfterLast('/').substringAfterLast(':').takeIf { it.isNotBlank() } ?: return null
        // A bare frontend title is only a library key; paths and filenames are retained as fallback sources.
        val source = normalized.takeIf {
            it.startsWith("/") || it.startsWith("saf:/", true) || '/' in it || '\\' in value || '.' in name
        }
        return GameLaunchRequest(decodeSegment(name), source)
    }

    fun done() { pending.value = null }

    /** Match exact filenames and titles first; only a unique prefix can stand in for a shorter frontend title. */
    fun resolve(name: String, games: List<Game>): Game? {
        val request = normalize(decodeSegment(name))
        if (request.isBlank()) return null
        fun file(g: Game) = decodeSegment(g.path.substringAfterLast('/').substringAfterLast(':'))
        fun fileKey(g: Game) = normalize(file(g))
        fun titleKey(g: Game) = normalize(g.title)
        games.filter { fileKey(it) == request }.singleOrNull()?.let { return it }
        games.filter { titleKey(it) == request }.singleOrNull()?.let { return it }
        val prefixMatches = games.filter { game ->
            titleKey(game).startsWith(request) || fileKey(game).substringBeforeLast('.', fileKey(game)).startsWith(request)
        }
        return prefixMatches.singleOrNull()
    }

    /** Resolve through the library first; its indexed path carries sibling-track access and stable game identity. */
    fun resolveOrRefresh(name: String, cachedGames: List<Game>, refresh: () -> List<Game>): Game? =
        resolve(name, cachedGames) ?: resolve(name, refresh())

    /** Resolve a direct frontend source after the library lookup failed. */
    fun directGame(
        request: GameLaunchRequest,
        supportedExtensions: Set<String>,
        contentSingleFileExtensions: Set<String>,
    ): Game? {
        val source = request.source?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val name = decodeSegment(request.name.replace('\\', '/').substringAfterLast('/').substringAfterLast(':'))
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension !in supportedExtensions) return null
        val path = when {
            source.startsWith("content://", true) -> {
                // A single document grant cannot expose the sibling tracks referenced by a descriptor.
                if (extension !in contentSingleFileExtensions) return null
                source
            }
            source.startsWith("file://", true) -> runCatching { Uri.parse(source).path }.getOrNull() ?: return null
            source.startsWith("saf:/", true) || source.startsWith('/') -> source
            source.startsWith("http://", true) || source.startsWith("https://", true) -> return null
            else -> name // a filename extra can still be located under the selected SAF games folder
        }
        return Game(name.substringBeforeLast('.', name), path)
    }

    fun resolveOrRefresh(
        request: GameLaunchRequest,
        cachedGames: List<Game>,
        refresh: () -> List<Game>,
        supportedExtensions: Set<String>,
        contentSingleFileExtensions: Set<String>,
    ): Game? = resolveOrRefresh(request.name, cachedGames, refresh)
        ?: directGame(request, supportedExtensions, contentSingleFileExtensions)

    fun notFound(request: GameLaunchRequest): String = if (request.source != null) {
        "Could not open \"${request.name}\" from the frontend. Check its file permission; multi-track games need the selected games folder."
    } else {
        "\"${request.name}\" was not found in the library. Check the selected games folder or send a ROM filename, path, or URI."
    }

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
            // Providers without OpenableColumns may expose the name in their document id or URI path.
            val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: uri.lastPathSegment
            return id?.substringAfterLast('/')?.substringAfterLast(':')?.let(::decodeSegment)
        }
        return uri.lastPathSegment?.let(::decodeSegment) ?: uri.path?.substringAfterLast('/')?.let(::decodeSegment)
    }
}
