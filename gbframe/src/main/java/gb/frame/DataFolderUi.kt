package gb.frame

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Same data-folder picker/status in first-run setup and System, independently of the emulated console. */
@Composable
fun DataFolderControls(store: AppDataFolder) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch { store.choose(uri) }
    }
    ActionRow(Icons.Default.Folder, "Data folder", store.label, "Choose", {
        if (!store.busy && !syncing) picker.launch(store.folder?.let(android.net.Uri::parse))
    })
    Text("BIOS, saves, save states, cheats and covers. Choose a dedicated folder for this app. Existing files are restored after reopening; the local working copy is kept.",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
    store.texturesRelativePath?.let { path ->
        Text("Textures: $path/<game ID>. Texture packs are restored when the app reopens.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
    }
    if (store.folder != null) TextButton(enabled = !syncing && !store.busy && !store.restartRequired, onClick = {
        syncing = true
        scope.launch { try { withContext(Dispatchers.IO) { store.syncNow() } } finally { syncing = false } }
    }) { Text(if (syncing) "Synchronizing…" else "Sync now") }
    store.error?.let { Text("Data folder: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
    if (store.busy || syncing) AlertDialog(
        onDismissRequest = {}, title = { Text("Data folder") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(); Text("Copying and verifying data…") } },
        confirmButton = {},
    )
    if (store.restartRequired) AlertDialog(
        onDismissRequest = {}, title = { Text("Reopen the app") },
        text = { Text("The data folder was saved. Close the app now, then open it again to restore its files before the emulator starts. Your previous local data is preserved.") },
        confirmButton = { TextButton(onClick = {
            (context as? Activity)?.finishAffinity()
            android.os.Process.killProcess(android.os.Process.myPid())
        }) { Text("Close app") } },
    )
}

fun dataFolderWizardStep(store: AppDataFolder): WizardStep = WizardStep(
    Icons.Default.Folder, "Data folder",
    listOf(androidx.compose.ui.graphics.Color(0xFF3EC6FF), androidx.compose.ui.graphics.Color(0xFF1B6F99)),
    "Choose where to keep this app's data, including on an SD card. You can keep app storage and change this later in System.",
) { DataFolderControls(store) }

/** Surface inaccessible storage even when the user never opens System. */
@Composable
fun DataFolderWarning(store: AppDataFolder) {
    val error = store.error
    var dismissed by remember { mutableStateOf<String?>(null) }
    if (error == null) { LaunchedEffect(Unit) { dismissed = null }; return }
    if (dismissed != error) AlertDialog(
        onDismissRequest = { dismissed = error }, title = { Text("Data folder needs attention") },
        text = { Text("$error\n\nThe local working copy is preserved. Check Data folder in System before relying on the external copy.") },
        confirmButton = { TextButton(onClick = { dismissed = error }) { Text("OK") } },
    )
}
