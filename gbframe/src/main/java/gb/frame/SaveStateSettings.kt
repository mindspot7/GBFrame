package gb.frame

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable

/** Global automatic-state preferences belong under System, separate from numbered slots. */
@Composable
fun SaveStateSettings(autoSave: Boolean, autoLoad: Boolean, onAutoSave: (Boolean) -> Unit, onAutoLoad: (Boolean) -> Unit) {
    SettingsGroup("Save states", Icons.Default.Save) {
        ToggleRow(Icons.Default.Save, "Auto save", "Save the game's state when you close it or leave the app", autoSave, onAutoSave)
        SettingsDivider()
        ToggleRow(Icons.Default.Restore, "Auto load", "Continue from the game's latest save state (auto, quick or slot) when you start it", autoLoad, onAutoLoad)
    }
}
