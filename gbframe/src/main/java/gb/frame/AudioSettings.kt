package gb.frame

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        else -> "Plays short pieces of audio at normal pitch"
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
                Text("Volume", style = MaterialTheme.typography.titleSmall, color = Color.White, modifier = Modifier.weight(1f))
                Text("${settings.volume}%", color = palette.highlight, fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = settings.volume.toFloat(), valueRange = 0f..100f, steps = 19,
                onValueChange = { settings.volume = it.toInt() },
                onValueChangeFinished = { settings.save() },
                colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent),
            )
        }
    }
    SettingsGroup("Fast-forward", Icons.Default.FastForward) {
        ChoiceRow("Fast-forward audio", FastForwardAudio.description(settings.fastForwardAudio), FastForwardAudio.labels,
            settings.fastForwardAudio.coerceIn(0, 2)) { settings.fastForwardAudio = it; settings.save() }
    }
}

/** The standard rows of the quick menu's Audio page: volume down/up (10% steps) and fast-forward audio. */
@Composable
fun AudioQuickMenuRows(settings: AudioSettings) {
    QuickMenuItemRow(id = "volume_down", icon = Icons.AutoMirrored.Filled.VolumeDown, label = "Volume down",
        value = "${settings.volume}%") { settings.volume = (settings.volume - 10).coerceAtLeast(0); settings.save() }
    QuickMenuItemRow(id = "volume_up", icon = Icons.AutoMirrored.Filled.VolumeUp, label = "Volume up",
        value = "${settings.volume}%") { settings.volume = (settings.volume + 10).coerceAtMost(100); settings.save() }
    QuickMenuItemRow(id = "fast_forward_audio", icon = Icons.Default.FastForward, label = "Fast-forward audio",
        value = FastForwardAudio.labels[settings.fastForwardAudio.coerceIn(0, 2)]) {
        settings.fastForwardAudio = (settings.fastForwardAudio + 1) % 3; settings.save()
    }
}
