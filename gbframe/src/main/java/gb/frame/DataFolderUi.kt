package gb.frame

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Same data-folder picker/status in first-run setup and System, independently of the emulated console. */
@Composable
fun DataFolderControls(
    store: AppDataFolder,
    compact: Boolean = false,
    onChooseFolder: (() -> Unit)? = null,
    wizard: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch { store.choose(uri) }
    }
    val chooseFolder = onChooseFolder ?: {
        val start = store.folder?.let(android.net.Uri::parse)
            ?: if (!wizard) store.recommendedLocation?.substringBeforeLast('/')?.let(StorageDefaults::treePickerUri) else null
        if (!store.busy && !syncing) picker.launch(start)
    }
    val unselectedLabel = if (!wizard) store.recommendedLocation?.let { "Select $it to use shared storage" }
        ?: "Saves, save states, BIOS, cheats and covers. Uses app storage until you choose a folder."
    else "Saves, save states, BIOS, cheats and covers. Uses app storage until you choose a folder."
    ActionRow(Icons.Default.Folder, "Data folder", if (store.folder == null) unselectedLabel else store.label, "Choose", {
        if (!store.busy && !syncing) chooseFolder()
    }, subMaxLines = Int.MAX_VALUE)
    if (!compact) KitText("BIOS, saves, save states, cheats and covers. Files are read and written directly in a ${store.appName} folder created inside the selected folder. Reopen the app after changing it.",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
    if (!compact) store.texturesRelativePath?.let { path ->
        KitText("Textures: $path/<game ID>. Texture packs are read from this folder.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
    }
    if (!compact && store.folder != null) TextButton(enabled = !syncing && !store.busy && !store.restartRequired, onClick = {
        syncing = true
        scope.launch { try { withContext(Dispatchers.IO) { store.checkAccess() } } finally { syncing = false } }
    }) { KitText(if (syncing) "Checking…" else "Check access") }
    store.error?.let { KitText("Data folder: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
    if (store.busy || syncing) AlertDialog(
        onDismissRequest = {}, title = { KitText("Data folder") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(); KitText("Moving to the selected data folder…") } },
        confirmButton = {},
    )
    if (store.restartRequired) AlertDialog(
        onDismissRequest = {}, title = { KitText("Reopen the app") },
        text = { KitText("The data folder was saved. Close the app now, then open it again to use the selected folder directly. Existing destination files were preserved." + (store.selectionNotice?.let { "\n\n$it" } ?: "")) },
        confirmButton = { TextButton(onClick = {
            (context as? Activity)?.finishAffinity()
            android.os.Process.killProcess(android.os.Process.myPid())
        }) { KitText("Close app") } },
    )
}

fun dataFolderWizardStep(store: AppDataFolder, onChooseFolder: () -> Unit): WizardStep = WizardStep(
    Icons.Default.Folder, "Data folder",
    listOf(androidx.compose.ui.graphics.Color(0xFF3EC6FF), androidx.compose.ui.graphics.Color(0xFF1B6F99)),
    "Choose where to keep this app's data, including on an SD card. You can keep app storage and change this later in System.",
) { DataFolderControls(store, onChooseFolder = onChooseFolder, wizard = true) }

/** Shared console-specific games-folder step; choosing a folder is optional and can also be done later in System. */
fun gamesFolderWizardStep(
    recommendedLocation: String,
    body: String,
    selectedLocation: () -> String?,
    onChooseFolder: () -> Unit,
): WizardStep = WizardStep(
    androidx.compose.material.icons.Icons.Default.VideoLibrary,
    "Games folder",
    listOf(androidx.compose.ui.graphics.Color(0xFF66E3A4), androidx.compose.ui.graphics.Color(0xFF1FA2A8)),
    body,
) {
    KitText("You can choose the recommended games folder now or later in System: $recommendedLocation.",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
    androidx.compose.material3.Button(onClick = onChooseFolder) {
        KitText(if (selectedLocation().isNullOrBlank()) "Choose games folder" else "Change games folder")
    }
    selectedLocation()?.let { WizardStatus(it) }
}

/**
 * The setup wizard's last step: detected device class, recommended preset and the options it applies. Nothing is
 * applied by opening it: an app starts with the default settings (OpenGL, 1x, no shader, linear filter, auto frame
 * skip off) and only switches to the recommended ones when the user taps "Apply settings" ([onApply]).
 */
fun performanceWizardStep(
    preset: String,
    options: List<String>,
    device: DeviceTier.Info = DeviceTier.detect(),
    onApply: () -> Unit = {},
): WizardStep = WizardStep(
    Icons.Default.Speed, "Performance",
    listOf(androidx.compose.ui.graphics.Color(0xFFFFB15A), androidx.compose.ui.graphics.Color(0xFF8A4B12)),
    "",
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KitText("YOUR PROFILE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                KitText(preset.uppercase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                KitText("${device.tier.name} device · ${device.gpu.ifBlank { "Graphics detected" }}", style = MaterialTheme.typography.bodyMedium)
                KitText(device.classificationReason, style = MaterialTheme.typography.bodySmall)
            }
        }
        KitText("Auto-tuned for the best performance this device can sustain.", style = MaterialTheme.typography.bodySmall)
        KitText("RECOMMENDED SETTINGS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        options.forEach { option ->
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f)) {
                KitText(option, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        var applied by remember { mutableStateOf(false) }
        Button(
            enabled = !applied,
            onClick = { onApply(); applied = true },
            modifier = Modifier.fillMaxWidth(),
        ) { KitText(if (applied) "Settings applied" else "Apply settings") }
        KitText(
            if (applied) "The recommended settings are in use. You can change them later in Settings."
            else "Without applying, the app starts with the default settings: OpenGL, 1x, no shader, linear filter and auto frame skip off.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Surface inaccessible storage even when the user never opens System. */
@Composable
fun DataFolderWarning(store: AppDataFolder) {
    val error = store.error
    var dismissed by remember { mutableStateOf<String?>(null) }
    if (error == null) { LaunchedEffect(Unit) { dismissed = null }; return }
    if (dismissed != error) AlertDialog(
        onDismissRequest = { dismissed = error }, title = { KitText("Data folder needs attention") },
        text = { KitText("$error\n\nReconnect the selected storage or choose another data folder in System. Data is not redirected to a second local copy.") },
        confirmButton = { TextButton(onClick = { dismissed = error }) { KitText("OK") } },
    )
}
