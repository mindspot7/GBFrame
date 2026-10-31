package gb.frame

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable

data class RendererChoice(val value: Int, val label: String)

@Composable
fun RendererSettingsRow(
    value: Int,
    options: List<RendererChoice>,
    description: String,
    title: String = "Renderer",
    onChange: (Int) -> Unit,
) {
    require(options.isNotEmpty())
    val selected = options.indexOfFirst { it.value == value }.coerceAtLeast(0)
    ChoiceRow(title, description, options.map { it.label }, selected) { onChange(options[it].value) }
}

@Composable
fun EnhancedResolutionSettingsRow(value: Int, values: List<Int>, labels: List<String>, sub: String, onChange: (Int) -> Unit) {
    require(values.isNotEmpty() && values.size == labels.size)
    val selected = values.indexOf(value).coerceAtLeast(0)
    ChoiceRow("Enhanced resolution", sub, labels, selected) { onChange(values[it]) }
}

@Composable
fun DreamcastRegionSettingsRow(value: Int, onChange: (Int) -> Unit) = ChoiceRow(
    "Region", "Console region for BIOS and disc compatibility. Default follows the disc; applies on the next boot.",
    listOf("Japan", "USA", "Europe", "Default"), value.coerceIn(0, 3),
) { onChange(it) }

@Composable
fun DreamcastCableSettingsRow(value: Int, onChange: (Int) -> Unit) {
    val values = listOf(0, 2, 3)
    val labels = listOf("VGA", "RGB", "TV")
    ChoiceRow("Cable", "Video output type. Some games do not support VGA or RGB.", labels, values.indexOf(value).coerceAtLeast(0)) {
        onChange(values[it])
    }
}

@Composable
fun DreamcastBroadcastSettingsRow(value: Int, onChange: (Int) -> Unit) = ChoiceRow(
    "Broadcast", "Video standard stored in the Dreamcast system settings. Default keeps the existing system value.",
    listOf("NTSC", "PAL", "PAL-M", "PAL-N", "Default"), value.coerceIn(0, 4),
) { onChange(it) }

@Composable
fun DreamcastRamModSettingsRow(value: Boolean, onChange: (Boolean) -> Unit) = ToggleRow(
    Icons.Default.Memory,
    "32 MB RAM",
    "Doubles console RAM from 16 to 32 MB for homebrew or games that need it. It does not improve speed and may affect compatibility; arcade boards ignore it.",
    value,
    onChange,
)
