package gb.frame

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconCompatibilityTest {
    @Test
    fun usesAdaptiveIconOnlyOnRgDs() {
        assertTrue(LauncherIconCompatibility.shouldUseAdaptiveIcon("Anbernic", "RG DS"))
        assertTrue(LauncherIconCompatibility.shouldUseAdaptiveIcon(" anbernic ", " rg ds "))
        assertFalse(LauncherIconCompatibility.shouldUseAdaptiveIcon("Anbernic", "RG Rotate"))
        assertFalse(LauncherIconCompatibility.shouldUseAdaptiveIcon("Retroid", "RG DS"))
        assertFalse(LauncherIconCompatibility.shouldUseAdaptiveIcon("", ""))
    }
}
