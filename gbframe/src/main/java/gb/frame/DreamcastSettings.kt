package gb.frame

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable

data class RendererChoice(val value: Int, val label: String)
data class DisplayModeChoice(
    val value: Int,
    val label: String,
    val supportsSuperWidescreen: Boolean = false,
)

private fun supportsSuperWidescreen(value: Int, options: List<DisplayModeChoice>) =
    options.any { it.value == value && it.supportsSuperWidescreen }

internal fun orderedDisplayModeChoices(options: List<DisplayModeChoice>): List<DisplayModeChoice> =
    options.withIndex()
        .sortedWith(compareBy({ displayModePriority(it.value.label) }, { it.index }))
        .map { it.value }

private fun displayModePriority(label: String): Int = when {
    label.contains("4:3", ignoreCase = true) -> 0
    label.contains("16:9", ignoreCase = true) -> 1
    label.equals("Fullscreen", ignoreCase = true) || label.equals("Full screen", ignoreCase = true) -> 2
    else -> 3
}

@Composable
fun DisplayModeSettingsRow(
    value: Int,
    options: List<DisplayModeChoice>,
    description: String,
    onChange: (Int) -> Unit,
) {
    require(options.isNotEmpty())
    val orderedOptions = orderedDisplayModeChoices(options)
    val selected = orderedOptions.indexOfFirst { it.value == value }.coerceAtLeast(0)
    ChoiceRow("Display mode", description, orderedOptions.map { it.label }, selected) {
        onChange(orderedOptions[it].value)
    }
}

/** The kit owns both the shared toggle row and its visibility for widescreen-capable display modes. */
@Composable
fun SuperWidescreenSettingsRow(
    displayModeValue: Int,
    displayModes: List<DisplayModeChoice>,
    value: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingsDivider()
    if (supportsSuperWidescreen(displayModeValue, displayModes)) {
        ToggleRow(
            Icons.Default.AspectRatio,
            "Extended screen",
            "Extends the view beyond 16:9 to the screen ratio. It can reveal more of the scene, but may cause edge or HUD glitches.",
            value,
            onChange,
        )
        SettingsDivider()
    }
}

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
fun DisplayModeQuickMenuItem(
    value: Int,
    options: List<DisplayModeChoice>,
    id: String = "display-mode",
    onChange: (Int) -> Unit,
) {
    require(options.isNotEmpty())
    val orderedOptions = orderedDisplayModeChoices(options)
    val selected = orderedOptions.indexOfFirst { it.value == value }.let { if (it < 0) 0 else it }
    QuickMenuItemRow(id = id, icon = Icons.Default.AspectRatio, label = "Display", value = orderedOptions[selected].label) {
        onChange(orderedOptions[(selected + 1) % orderedOptions.size].value)
    }
}

/** Shared in-game toggle; omitted when the selected display mode does not support Extended screen. */
@Composable
fun SuperWidescreenQuickMenuItem(
    displayModeValue: Int,
    displayModes: List<DisplayModeChoice>,
    value: Boolean,
    id: String = "super-widescreen",
    onChange: (Boolean) -> Unit,
) {
    if (supportsSuperWidescreen(displayModeValue, displayModes)) {
        QuickMenuItemRow(
            id = id,
            icon = Icons.Default.AspectRatio,
            label = "Extended screen",
            value = if (value) "On" else "Off",
            onClick = { onChange(!value) },
        )
    }
}

@Composable
fun RendererQuickMenuItem(
    value: Int,
    options: List<RendererChoice>,
    id: String = "renderer",
    onChange: (Int) -> Unit,
) {
    require(options.isNotEmpty())
    val selected = options.indexOfFirst { it.value == value }.let { if (it < 0) 0 else it }
    QuickMenuItemRow(id = id, icon = Icons.Default.Tv, label = "Renderer", value = options[selected].label) {
        onChange(options[(selected + 1) % options.size].value)
    }
}

/** Names of the enhanced-resolution choices, the same in every app: 1x is the console's own resolution, Best matches the screen. */
const val EnhancedResolutionOneX = "1x"
const val EnhancedResolutionBest = "Best"

/** Label of the Best choice with its detail, e.g. "Best (540p)" or "Best (3x)". */
fun enhancedResolutionBestLabel(detail: String) = "$EnhancedResolutionBest ($detail)"

/**
 * The enhanced-resolution menu, identical in every app: 1x to 4x, then Best, the size that fills this screen whatever
 * the GPU can do. [bestDetail] names that size in the app's own unit.
 */
fun enhancedResolutionLabels(bestDetail: String): List<String> =
    listOf(EnhancedResolutionOneX, "2x", "3x", "4x", enhancedResolutionBestLabel(bestDetail))

@Composable
fun EnhancedResolutionSettingsRow(value: Int, values: List<Int>, labels: List<String>, sub: String, onChange: (Int) -> Unit) {
    require(values.isNotEmpty() && values.size == labels.size)
    val selected = values.indexOf(value).coerceAtLeast(0)
    ChoiceRow("Resolution", sub, labels, selected) { onChange(values[it]) }
}

/** Shared quick-menu presentation for core-provided enhanced-resolution choices. */
@Composable
fun EnhancedResolutionQuickMenuItem(
    value: Int,
    values: List<Int>,
    labels: List<String>,
    id: String = "enhanced-resolution",
    title: String = "Resolution",
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
