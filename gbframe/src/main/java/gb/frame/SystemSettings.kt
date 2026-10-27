package gb.frame

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*

data class SystemFolderSetting(val path: String?, val description: String, val onChoose: () -> Unit)
data class SystemBiosFile(val name: String, val loaded: Boolean)
data class SystemToggleSetting(val value: Boolean, val onChange: (Boolean) -> Unit, val description: String? = null)
/** Fast boot is mandatory in every app (on by default); the skip functions only when the core supports them. */
data class SystemStartupOptions(
    val fastBoot: SystemToggleSetting,
    val skipLoadingScreens: SystemToggleSetting? = null,
    val skipTransitions: SystemToggleSetting? = null,
    val skipSaveWaits: SystemToggleSetting? = null,
)
data class SystemBiosFolderSetting(
    val path: String?,
    val description: String,
    val files: List<SystemBiosFile>,
    val onChooseFolder: () -> Unit,
    val onChooseFiles: (() -> Unit)? = null,
    val busy: Boolean = false,
) {
    val subtitle: String get() = buildList {
        add(path?.takeIf { it.isNotBlank() } ?: description)
        files.forEach { add("${it.name} — ${if (it.loaded) "Loaded" else "Missing"}") }
    }.joinToString("\n")
}

/** The System layout is shared; consumers supply capabilities, paths and real actions only. */
@Composable
fun SystemSettings(
    dataFolder: AppDataFolder,
    gamesFolder: SystemFolderSetting,
    biosFolder: SystemBiosFolderSetting,
    autoSave: Boolean,
    autoLoad: Boolean,
    onAutoSave: (Boolean) -> Unit,
    onAutoLoad: (Boolean) -> Unit,
    startup: SystemStartupOptions,
    startupExtras: (@Composable () -> Unit)? = null,
    hleBios: (@Composable () -> Unit)? = null,
    emulatorFiles: (@Composable () -> Unit)? = null,
    extraSettings: @Composable () -> Unit = {},
) {
    SettingsGroup("Data", Icons.Default.Folder) {
        DataFolderControls(dataFolder, compact = true)
        SettingsDivider()
        ActionRow(Icons.Default.Folder, "Games folder", gamesFolder.path?.takeIf { it.isNotBlank() } ?: gamesFolder.description,
            "Choose", gamesFolder.onChoose, warn = gamesFolder.path.isNullOrBlank(), subMaxLines = Int.MAX_VALUE)
        SettingsDivider()
        BiosFolderRow(biosFolder)
    }
    hleBios?.let { SettingsGroup("BIOS emulation", Icons.Default.Memory) { it() } }
    emulatorFiles?.invoke()
    SaveStateSettings(autoSave, autoLoad, onAutoSave, onAutoLoad)
    val startupRows = listOf(
        Triple("Fast boot", "Skip firmware startup screens when supported", startup.fastBoot),
        Triple("Skip loading screens", "Run at maximum speed during detected loading screens", startup.skipLoadingScreens),
        Triple("Skip transitions", "Run at maximum speed through detected scene transitions", startup.skipTransitions),
        Triple("Skip save waits", "Run at maximum speed while the game reads or writes saves", startup.skipSaveWaits),
    ).filter { it.third != null }
    SettingsGroup("Loading Times", Icons.Default.FastForward) {
        startupRows.forEachIndexed { index, (title, description, option) ->
            if (index > 0) SettingsDivider()
            option!!
            ToggleRow(Icons.Default.FastForward, title, option.description ?: description, option.value, option.onChange)
        }
        if (startupExtras != null) SettingsDivider()
        startupExtras?.invoke()
    }
    extraSettings()
}

@Composable
fun BiosFolderRow(setting: SystemBiosFolderSetting) {
    var chooseMethod by remember { mutableStateOf(false) }
    ActionRow(Icons.Default.Memory, "Bios folder", setting.subtitle,
        if (setting.busy) "Importing…" else "Choose", {
            if (!setting.busy) {
                if (setting.onChooseFiles != null) chooseMethod = true else setting.onChooseFolder()
            }
        }, subMaxLines = Int.MAX_VALUE)
    if (chooseMethod) AlertDialog(
        onDismissRequest = { chooseMethod = false }, title = { Text("Bios folder") },
        text = { Text("Import BIOS files into the app's active data folder. The location shown in System is where the emulator reads them.") },
        confirmButton = { TextButton(onClick = { chooseMethod = false; setting.onChooseFolder() }) { Text("Choose folder") } },
        dismissButton = { TextButton(onClick = { chooseMethod = false; setting.onChooseFiles?.invoke() }) { Text("Choose files") } },
    )
}
