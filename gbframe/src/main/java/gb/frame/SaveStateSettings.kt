package gb.frame

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable

/** Global automatic-state preferences belong under System, separate from numbered slots. */
@Composable
fun SaveStateSettings(autoSave: Boolean, autoLoad: Boolean, onAutoSave: (Boolean) -> Unit, onAutoLoad: (Boolean) -> Unit) {
    SettingsGroup("Save states", Icons.Default.Save) {
        ToggleRow(Icons.Default.Save, "Auto save", "Saves your place when you close the game or leave the app. Uses extra storage for save-state files.", autoSave, onAutoSave)
        SettingsDivider()
        ToggleRow(Icons.Default.Restore, "Auto load", "Continues from the most recent save state when you start this game. Turn it off to boot from the game’s normal start.", autoLoad, onAutoLoad)
    }
}
