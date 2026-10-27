package gb.frame

import org.junit.Assert.*
import org.junit.Test

class MonitoringSettingsTest {
    private class Settings : MonitoringSettings {
        override var options = PerfOptions()
        override var showFfButton = false
        override fun update(options: PerfOptions, showFfButton: Boolean) { this.options = options; this.showFfButton = showFfButton }
    }
    @Test fun allMonitoringChangesEveryMetricWithoutChangingFastForwardButton() {
        val settings = Settings(); settings.set("fastForwardButton", true); settings.set("all", true)
        assertTrue(settings.options.all); assertTrue(settings.showFfButton)
        settings.set("all", false); assertFalse(settings.options.any); assertTrue(settings.showFfButton)
    }
    @Test fun individualMetricUpdatesAggregateSelection() {
        val settings = Settings(); settings.set("all", true); settings.set("gpu", false)
        assertFalse(settings.options.all); assertTrue(settings.options.fps); assertFalse(settings.options.gpu)
        assertFalse(settings.items().first { it.first == "all" }.third)
    }
    @Test fun fastForwardButtonChangesNoMonitoringMetric() {
        val settings = Settings(); settings.set("fps", true); val before = settings.options
        settings.set("fastForwardButton", true)
        assertEquals(before, settings.options); assertTrue(settings.showFfButton)
    }
}
