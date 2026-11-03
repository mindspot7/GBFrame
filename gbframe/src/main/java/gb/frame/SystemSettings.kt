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
data class SystemCdReadSpeedSetting(
    val value: Int,
    val options: List<CdReadSpeedOption>,
    val onChange: (Int) -> Unit,
    val description: String? = null,
)
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

/** Shared System order: common kit sections first, core extras next, app-specific file imports last. */
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
    coreSettings: @Composable () -> Unit = {},
    appFileSettings: (@Composable () -> Unit)? = null,
    recommendedSettingsPerGame: SystemToggleSetting? = null,
    cdReadSpeed: SystemCdReadSpeedSetting? = null,
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
    SaveStateSettings(autoSave, autoLoad, onAutoSave, onAutoLoad)
    recommendedSettingsPerGame?.let { RecommendedSettingsPerGameGroup(it.value, it.onChange) }
    val startupRows = listOf(
        Triple("Fast boot", "Skips the console startup screen. Games start sooner; compatibility is usually unchanged.", startup.fastBoot),
        Triple("Skip loading screens", "Speeds through supported loading screens. It reduces waiting, not gameplay speed.", startup.skipLoadingScreens),
        Triple("Skip transitions", "Speeds through supported scene changes. It reduces waiting, not gameplay speed.", startup.skipTransitions),
        Triple("Skip save waits", "Speeds through supported save and load waits. It does not change the saved data.", startup.skipSaveWaits),
    ).filter { it.third != null }
    SettingsGroup("Loading Times Hacks", Icons.Default.FastForward) {
        startupRows.forEachIndexed { index, (title, description, option) ->
            if (index > 0) SettingsDivider()
            option!!
            ToggleRow(Icons.Default.FastForward, title, option.description ?: description, option.value, option.onChange)
        }
        if (startupExtras != null && startupRows.isNotEmpty()) SettingsDivider()
        startupExtras?.invoke()
        if (cdReadSpeed != null) {
            if (startupRows.isNotEmpty() || startupExtras != null) SettingsDivider()
            CdReadSpeedSettingsRow(
                value = cdReadSpeed.value,
                options = cdReadSpeed.options,
                sub = cdReadSpeed.description,
                onChange = cdReadSpeed.onChange,
            )
        }
    }
    coreSettings()
    appFileSettings?.invoke()
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
        onDismissRequest = { chooseMethod = false }, title = { KitText("Bios folder") },
        text = { KitText("Import BIOS files into the app's active data folder. The location shown in System is where the emulator reads them.") },
        confirmButton = { TextButton(onClick = { chooseMethod = false; setting.onChooseFolder() }) { KitText("Choose folder") } },
        dismissButton = { TextButton(onClick = { chooseMethod = false; setting.onChooseFiles?.invoke() }) { KitText("Choose files") } },
    )
}
