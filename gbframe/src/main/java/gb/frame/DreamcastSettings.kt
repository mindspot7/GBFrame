package gb.frame

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HighQuality
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

/** Shared quick-menu presentation for core-provided enhanced-resolution choices. */
@Composable
fun EnhancedResolutionQuickMenuItem(
    value: Int,
    values: List<Int>,
    labels: List<String>,
    id: String = "enhanced-resolution",
    title: String = "Enhanced resolution",
    onChange: (Int) -> Unit,
) {
    require(values.isNotEmpty() && values.size == labels.size)
    val selected = values.indexOf(value).let { if (it < 0) 0 else it }
    QuickMenuItemRow(
        id = id,
        icon = Icons.Default.HighQuality,
        label = title,
        value = labels[selected],
    ) { onChange(values[(selected + 1) % values.size]) }
}

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
fun DreamcastSystemSettings(cable: Int, onCableChange: (Int) -> Unit, broadcast: Int, onBroadcastChange: (Int) -> Unit) {
    SettingsGroup("Dreamcast", Icons.Default.Tune) {
        DreamcastCableSettingsRow(cable, onCableChange)
        SettingsDivider()
        DreamcastBroadcastSettingsRow(broadcast, onBroadcastChange)
    }
}
