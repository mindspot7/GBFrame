package gb.frame

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Audio options every app has, in Settings → Audio and the quick menu → Audio. The app keeps and applies the values
 * (its preferences, its core's config) and adds its own options after these:
 *
 * ```
 * AudioSettingsGroups(myAudio)            // Settings → Audio: the standard groups first
 * SettingsGroup("Sound chip", …) { … }    // then the app's own
 *
 * QuickMenuPage("audio", "Audio") { AudioQuickMenuRows(myAudio); /* app rows */ }
 * ```
 */
interface AudioSettings {
    /** Output volume, 0..100. Setting it applies it to the core right away. */
    var volume: Int
    /** Audio while fast-forwarding: [FastForwardAudio.DEFAULT], [FastForwardAudio.SPEED_UP] or [FastForwardAudio.MUTE]. */
    var fastForwardAudio: Int
    /** Persists the values (after a slider drag ends, or a quick-menu change). */
    fun save() {}
}

object FastForwardAudio {
    const val DEFAULT = 0
    const val SPEED_UP = 1
    const val MUTE = 2
    val labels = listOf("Default", "Speed up", "Mute")

    fun description(mode: Int) = when (mode) {
        SPEED_UP -> "Plays all the audio faster (higher pitch), following the game speed"
        MUTE -> "No sound while fast-forwarding"
        else -> "Keeps the original pitch during fast-forward; sound may be choppy while the game runs faster"
    }
}

private const val VOLUME_STEP_PERCENT = 10

private fun AudioSettings.adjustVolume(delta: Int) {
    val next = (volume + delta).coerceIn(0, 100)
    if (next != volume) {
        volume = next
        save()
    }
}

/** The standard groups of Settings → Audio: Output (volume) and Fast-forward. */
@Composable
fun AudioSettingsGroups(settings: AudioSettings) {
    SettingsGroup("Output", Icons.AutoMirrored.Filled.VolumeUp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowIcon(if (settings.volume == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp)
                Spacer(Modifier.width(14.dp))
                KitText("Volume", style = MaterialTheme.typography.titleSmall, color = Color.White, modifier = Modifier.weight(1f))
                KitText("${settings.volume}%", color = palette.highlight, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { settings.adjustVolume(-VOLUME_STEP_PERCENT) },
                    enabled = settings.volume > 0,
                ) {
                    Icon(Icons.Default.Remove, "Decrease volume by 10%", tint = palette.accent)
                }
                Slider(
                    value = settings.volume.coerceIn(0, 100).toFloat(), valueRange = 0f..100f, steps = 19,
                    onValueChange = { settings.volume = it.toInt() },
                    onValueChangeFinished = { settings.save() },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent),
                )
                IconButton(
                    onClick = { settings.adjustVolume(VOLUME_STEP_PERCENT) },
                    enabled = settings.volume < 100,
                ) {
                    Icon(Icons.Default.Add, "Increase volume by 10%", tint = palette.accent)
                }
            }
        }
    }
    SettingsGroup("Fast-forward", Icons.Default.FastForward) {
        ChoiceRow("Fast-forward audio", FastForwardAudio.description(settings.fastForwardAudio), FastForwardAudio.labels,
            settings.fastForwardAudio.coerceIn(0, 2)) { settings.fastForwardAudio = it; settings.save() }
    }
}

/** The standard controls of the quick menu's Audio page: output volume and fast-forward audio. */
@Composable
fun AudioQuickMenuRows(settings: AudioSettings) {
    QuickMenuAdjustableItemContainer(
        id = "volume",
        onLeft = { settings.adjustVolume(-VOLUME_STEP_PERCENT) },
        onRight = { settings.adjustVolume(VOLUME_STEP_PERCENT) },
        onClick = {},
    ) {
        Icon(
            if (settings.volume == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            null,
            tint = palette.accent,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KitText("Volume", color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                KitText(
                    "${settings.volume}%",
                    color = palette.highlight,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { settings.adjustVolume(-VOLUME_STEP_PERCENT) },
                    enabled = settings.volume > 0,
                    modifier = Modifier.size(36.dp).focusProperties { canFocus = false },
                ) {
                    Icon(Icons.Default.Remove, "Decrease volume by 10%", tint = palette.accent)
                }
                Slider(
                    value = settings.volume.coerceIn(0, 100).toFloat(),
                    valueRange = 0f..100f,
                    steps = 19,
                    onValueChange = { settings.volume = it.toInt() },
                    onValueChangeFinished = { settings.save() },
                    modifier = Modifier.weight(1f).focusProperties { canFocus = false },
                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent),
                )
                IconButton(
                    onClick = { settings.adjustVolume(VOLUME_STEP_PERCENT) },
                    enabled = settings.volume < 100,
                    modifier = Modifier.size(36.dp).focusProperties { canFocus = false },
                ) {
                    Icon(Icons.Default.Add, "Increase volume by 10%", tint = palette.accent)
                }
            }
        }
    }
    QuickMenuItemRow(id = "fast_forward_audio", icon = Icons.Default.FastForward, label = "Fast-forward audio",
        value = FastForwardAudio.labels[settings.fastForwardAudio.coerceIn(0, 2)]) {
        settings.fastForwardAudio = (settings.fastForwardAudio + 1) % 3; settings.save()
    }
}
