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
    Text("BIOS, saves, save states, cheats and covers. Choose a dedicated folder for this app. Files are read and written directly in the selected folder. Reopen the app after changing it.",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
    store.texturesRelativePath?.let { path ->
        Text("Textures: $path/<game ID>. Texture packs are read from this folder.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
    }
    if (store.folder != null) TextButton(enabled = !syncing && !store.busy && !store.restartRequired, onClick = {
        syncing = true
        scope.launch { try { withContext(Dispatchers.IO) { store.checkAccess() } } finally { syncing = false } }
    }) { Text(if (syncing) "Checking…" else "Check access") }
    store.error?.let { Text("Data folder: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
    if (store.busy || syncing) AlertDialog(
        onDismissRequest = {}, title = { Text("Data folder") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(); Text("Moving to the selected data folder…") } },
        confirmButton = {},
    )
    if (store.restartRequired) AlertDialog(
        onDismissRequest = {}, title = { Text("Reopen the app") },
        text = { Text("The data folder was saved. Close the app now, then open it again to use the selected folder directly. Existing destination files were preserved." + (store.selectionNotice?.let { "\n\n$it" } ?: "")) },
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
        text = { Text("$error\n\nReconnect the selected storage or choose another data folder in System. Data is not redirected to a second local copy.") },
        confirmButton = { TextButton(onClick = { dismissed = error }) { Text("OK") } },
    )
}
