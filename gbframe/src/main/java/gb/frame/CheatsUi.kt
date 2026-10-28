package gb.frame

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** One cheat displayed by the GBFrame cheats screen. The app assigns a stable ID for the current game's list. */
data class CheatUiItem(
    val id: String,
    val name: String,
    val code: String,
    val enabled: Boolean,
    val sourceLabel: String? = null,
)

/** Optional per-game frame-rate patch control supplied by a console-specific cheat backend. */
data class CheatPatchControl(
    val label: String,
    val description: String,
    val enabled: Boolean,
)

/** Result of an app/core-specific cheat operation. */
data class CheatActionResult(val success: Boolean, val message: String? = null) {
    companion object {
        fun success(message: String? = null) = CheatActionResult(true, message)
        fun failure(message: String) = CheatActionResult(false, message)
    }
}

/**
 * App-provided adapter for the shared cheats UI. GBFrame owns presentation and interaction; the app owns
 * code parsing, persistence, database lookup and applying/removing cheats in its own core.
 *
 * The [cheats] getter should read observable Compose state so the list refreshes after edits.
 * A cheat code format is core-specific; never use another emulator's parser just to reuse this screen.
 */
interface CheatsBackend {
    val cheats: List<CheatUiItem>
    /** Optional core-specific frame-rate patch for the active game. Null when no patch is available. */
    val frameRatePatch: CheatPatchControl? get() = null
    /** Optional status such as the matched database entry or a message that no entry was found. */
    val databaseStatus: String? get() = null
    /** True only when the app can add cheats from a real database entry for the active game. */
    val canAddFromDatabase: Boolean get() = false
    /** Non-null while editing/applying cheats is disallowed (for example, RetroAchievements hardcore mode). */
    val blockedMessage: String? get() = null
    /** Hint shown in the code field, describing the formats this app actually supports. */
    val codeHint: String get() = "Enter a cheat code supported by this emulator."

    fun add(name: String, code: String): CheatActionResult
    fun setEnabled(id: String, enabled: Boolean)
    fun delete(id: String)
    /** Parse and import the supplied file contents using this core's supported format. */
    fun importFile(contents: String): CheatActionResult
    /**
     * Load the active game's database entries into the core. GBFrame calls this automatically when the cheats screen
     * opens; implementations must be idempotent because the user can also invoke the database action again.
     */
    fun addFromDatabase(): CheatActionResult =
        CheatActionResult.failure("Cheat database is not available for this game.")
    /** Enable or disable the optional patch reported by [frameRatePatch]. */
    fun setFrameRatePatchEnabled(enabled: Boolean): CheatActionResult =
        CheatActionResult.failure("Frame-rate patch is not available for this game.")
}

/**
 * Shared, full-window Cheats screen for every GB-family app.
 *
 * Supply a [CheatsBackend] adapter for the active core. This screen does not assume a particular cheat-code syntax,
 * cheat database, persistence format or native API.
 */
@Composable
fun CheatsScreen(
    backend: CheatsBackend,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<CheatActionResult?>(null) }
    val blocked = backend.blockedMessage != null

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            feedback = when {
                blocked -> CheatActionResult.failure(backend.blockedMessage ?: "Cheats are currently disabled.")
                text.isNullOrBlank() -> CheatActionResult.failure("The selected file could not be read or is empty.")
                else -> runCatching { backend.importFile(text) }
                    .getOrElse { CheatActionResult.failure(it.message ?: "Could not import cheat file.") }
            }
        }
    }

    val databaseKey = if (backend.canAddFromDatabase) backend.databaseStatus ?: "available" else null
    LaunchedEffect(backend, databaseKey, blocked) {
        if (databaseKey != null && !blocked) {
            val result = runCatching { backend.addFromDatabase() }
                .getOrElse { CheatActionResult.failure(it.message ?: "Could not load database cheats.") }
            if (!result.success) feedback = result
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .75f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 520.dp).heightIn(max = 560.dp).padding(16.dp),
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                    Text(
                        "Cheats",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (backend.canAddFromDatabase && !blocked) {
                        IconButton(onClick = {
                            feedback = runCatching { backend.addFromDatabase() }
                                .getOrElse { CheatActionResult.failure(it.message ?: "Could not add database cheats.") }
                        }) {
                            Icon(Icons.Default.LibraryAdd, "Add cheats from the database")
                        }
                    }
                    IconButton(enabled = !blocked, onClick = { importer.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.FileOpen, "Import cheat file")
                    }
                    IconButton(enabled = !blocked, onClick = { adding = !adding }) {
                        Icon(Icons.Default.Add, if (adding) "Cancel add" else "Add cheat")
                    }
                }

                if (adding) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        enabled = !blocked,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text(backend.codeHint) },
                        enabled = !blocked,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            enabled = !blocked,
                            onClick = {
                                val result = runCatching { backend.add(name, code) }
                                    .getOrElse { CheatActionResult.failure(it.message ?: "Could not add cheat.") }
                                feedback = result
                                if (result.success) {
                                    name = ""
                                    code = ""
                                    adding = false
                                }
                            },
                        ) { Text("Add") }
                    }
                }

                backend.databaseStatus?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                backend.blockedMessage?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                backend.frameRatePatch?.let { patch ->
                    ListItem(
                        headlineContent = { Text(patch.label) },
                        supportingContent = { Text(patch.description) },
                        leadingContent = {
                            Switch(
                                checked = patch.enabled,
                                enabled = !blocked,
                                onCheckedChange = { enabled ->
                                    feedback = runCatching { backend.setFrameRatePatchEnabled(enabled) }
                                        .getOrElse { CheatActionResult.failure(it.message ?: "Could not change frame-rate patch.") }
                                },
                            )
                        },
                    )
                }
                feedback?.let { result ->
                    result.message?.let { message ->
                        Text(
                            message,
                            color = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }

                if (backend.cheats.isEmpty() && !adding) {
                    Text(
                        "No cheats for this game yet. Add a code or import a supported cheat file.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                LazyColumn(Modifier.weight(1f)) {
                    items(backend.cheats, key = { it.id }) { cheat ->
                        ListItem(
                            headlineContent = { Text(cheat.name) },
                            supportingContent = {
                                Text(
                                    buildString {
                                        cheat.sourceLabel?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                                        append(cheat.code.lineSequence().firstOrNull().orEmpty().take(40))
                                    },
                                )
                            },
                            leadingContent = {
                                Switch(
                                    checked = cheat.enabled,
                                    enabled = !blocked,
                                    onCheckedChange = { enabled -> backend.setEnabled(cheat.id, enabled) },
                                )
                            },
                            trailingContent = {
                                IconButton(enabled = !blocked, onClick = { backend.delete(cheat.id) }) {
                                    Icon(Icons.Default.Delete, "Delete")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
