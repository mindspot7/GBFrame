package gb.frame

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.Composable

/** Core-neutral monitoring contract. Consumers only read, apply and persist their real core options. */
interface MonitoringSettings {
    val options: PerfOptions
    val showFfButton: Boolean
    fun update(options: PerfOptions, showFfButton: Boolean)

    fun set(key: String, enabled: Boolean) {
        val next = when (key) {
            "all" -> PerfOptions(enabled, enabled, enabled, enabled, enabled, enabled, enabled)
            "fps" -> options.copy(fps = enabled)
            "speed" -> options.copy(speed = enabled)
            "cpu" -> options.copy(cpu = enabled)
            "gpu" -> options.copy(gpu = enabled)
            "renderer" -> options.copy(renderer = enabled)
            "resolution" -> options.copy(resolution = enabled)
            "frameTime" -> options.copy(frameTime = enabled)
            "fastForwardButton" -> options
            else -> error("Unknown monitoring option: $key")
        }
        update(next, if (key == "fastForwardButton") enabled else showFfButton)
    }
    fun items(): List<Triple<String, String, Boolean>> = options.let { p -> listOf(
        Triple("all", "Show all monitoring", p.all),
        Triple("fps", "Show FPS", p.fps), Triple("speed", "Show speed", p.speed),
        Triple("renderer", "Show renderer", p.renderer), Triple("resolution", "Show resolution", p.resolution),
        Triple("cpu", "Show CPU", p.cpu), Triple("gpu", "Show GPU", p.gpu),
        Triple("frameTime", "Show frame time", p.frameTime),
        Triple("fastForwardButton", "Show fast-forward button", showFfButton),
    ) }
}

@Composable
fun MonitoringSettingsRows(settings: MonitoringSettings) {
    val help = mapOf(
        "all" to "Shows every available live performance reading. The overlay has a small rendering cost.",
        "fps" to "Shows frames drawn each second. The overlay has a small rendering cost.",
        "speed" to "Shows emulation speed compared with the console's normal speed.",
        "cpu" to "Shows CPU usage reported by this emulator.",
        "gpu" to "Shows GPU work for this emulator when available. Some devices cannot report it.",
        "renderer" to "Shows which graphics renderer is active.",
        "resolution" to "Shows the current internal rendering resolution.",
        "frameTime" to "Shows how long each frame takes; spikes can reveal slowdowns.",
        "fastForwardButton" to "Adds a fast-forward control over the game screen."
    )
    settings.items().forEachIndexed { index, (key, label, enabled) ->
        if (index > 0) SettingsDivider()
        ToggleRow(Icons.Default.Speed, label, help[key], enabled) { settings.set(key, it) }
    }
}

@Composable
fun QuickMenuScope.MonitoringQuickMenuRows(settings: MonitoringSettings) {
    for ((key, label, enabled) in settings.items()) {
        QuickMenuItemRow(id = "overlay-$key", icon = Icons.Default.Speed, label = label,
            value = if (enabled) "On" else "Off") { settings.set(key, !enabled) }
    }
}

/** Only one fast-forward entry point is displayed: on-screen button or quick-menu action. */
@Composable
fun QuickMenuScope.FastForwardQuickMenuAction(settings: MonitoringSettings, active: Boolean, toggle: () -> Unit) {
    if (!settings.showFfButton) QuickMenuItemRow(id = "fast-forward", icon = Icons.Default.Speed,
        label = "Fast-forward", value = if (active) "On" else "Off", onClick = toggle)
}
