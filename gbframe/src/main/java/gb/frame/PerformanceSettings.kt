package gb.frame

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable

/** A core-implemented CD speed choice. Apps supply the values their core supports. */
data class CdReadSpeedOption(val value: Int, val label: String)
data class AutoFrameSkipOption(val value: Int, val label: String)
val DREAMCAST_AUTO_FRAME_SKIP_OPTIONS = listOf(
    AutoFrameSkipOption(0, "Off"),
    AutoFrameSkipOption(1, "Normal"),
    AutoFrameSkipOption(2, "Max"),
)

@Composable
fun AutoFrameSkipSettingsRow(value: Int, onChange: (Int) -> Unit) {
    val selected = DREAMCAST_AUTO_FRAME_SKIP_OPTIONS.indexOfFirst { it.value == value }.coerceAtLeast(0)
    ChoiceRow(
        "Auto frame skip",
        "Normal skips when both the GPU and emulation CPU are behind. Max also skips when only the GPU is behind, so it drops more frames.",
        DREAMCAST_AUTO_FRAME_SKIP_OPTIONS.map { it.label },
        selected,
    ) { onChange(DREAMCAST_AUTO_FRAME_SKIP_OPTIONS[it].value) }
}

@Composable
fun AutoFrameSkipQuickMenuItem(value: Int, id: String = "auto-frame-skip", onChange: (Int) -> Unit) {
    val selected = DREAMCAST_AUTO_FRAME_SKIP_OPTIONS.indexOfFirst { it.value == value }.let { if (it < 0) 0 else it }
    QuickMenuItemRow(id = id, icon = Icons.Default.Speed, label = "Auto frame skip", value = DREAMCAST_AUTO_FRAME_SKIP_OPTIONS[selected].label) {
        onChange(DREAMCAST_AUTO_FRAME_SKIP_OPTIONS[(selected + 1) % DREAMCAST_AUTO_FRAME_SKIP_OPTIONS.size].value)
    }
}

@Composable
fun CpuRecompilerSettingsRow(value: Boolean, onChange: (Boolean) -> Unit) = ToggleRow(
    Icons.Default.Speed,
    "CPU recompiler (Dynarec)",
    "Faster CPU emulation on supported cores. Turn it off if a game has compatibility issues.",
    value,
    onChange,
)

@Composable
fun CpuRecompilerQuickMenuItem(value: Boolean, id: String = "cpu-recompiler", onChange: (Boolean) -> Unit) {
    QuickMenuItemRow(id = id, icon = Icons.Default.Speed, label = "CPU recompiler (Dynarec)", value = if (value) "On" else "Off") {
        onChange(!value)
    }
}

@Composable
fun FastCdLoadSettingsRow(value: Boolean, onChange: (Boolean) -> Unit, sub: String = "Shortens emulated CD transfer delays. Some games need original timing.") = ToggleRow(
    Icons.Default.Speed, "Fast CD load", sub, value, onChange,
)

@Composable
fun FastCdLoadQuickMenuItem(value: Boolean, id: String = "fast-cd-load", onChange: (Boolean) -> Unit) {
    QuickMenuItemRow(id = id, icon = Icons.Default.Speed, label = "Fast CD load", value = if (value) "On" else "Off") {
        onChange(!value)
    }
}

@Composable
fun CdReadSpeedSettingsRow(
    value: Int,
    options: List<CdReadSpeedOption>,
    sub: String? = null,
    title: String = "CD read speed",
    onChange: (Int) -> Unit,
) {
    require(options.isNotEmpty())
    val selected = options.indexOfFirst { it.value == value }.coerceAtLeast(0)
    ChoiceRow(title, sub, options.map { it.label }, selected) { onChange(options[it].value) }
}

@Composable
fun CdReadSpeedQuickMenuItem(
    value: Int,
    options: List<CdReadSpeedOption>,
    id: String = "cd-read-speed",
    title: String = "CD read speed",
    onChange: (Int) -> Unit,
) {
    require(options.isNotEmpty())
    val selected = options.indexOfFirst { it.value == value }.let { if (it < 0) 0 else it }
    QuickMenuItemRow(id = id, icon = Icons.Default.Speed, label = title, value = options[selected].label) {
        onChange(options[(selected + 1) % options.size].value)
    }
}

/** Standard opt-in for the measured per-game profiles supplied by an app. */
@Composable
fun RecommendedSettingsPerGameRow(value: Boolean, onChange: (Boolean) -> Unit) = ToggleRow(
    Icons.Default.Tune,
    "Recommended settings per game",
    "Applies measured settings for this game and device class. Your saved per-game choices take priority.",
    value,
    onChange,
)
