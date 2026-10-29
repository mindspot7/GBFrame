package gb.frame

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameHackPolicyTest {
    @Test
    fun hacksAreEnabledByDefault() {
        assertTrue(GameHackPolicy.DEFAULT_ENABLED)
        assertTrue(GameHackPolicy.shouldApply(GameHackPolicy.DEFAULT_ENABLED, hardcoreActive = false))
    }

    @Test
    fun hardcoreAlwaysBlocksGameHacks() {
        assertFalse(GameHackPolicy.shouldApply(enabled = true, hardcoreActive = true))
        assertFalse(GameHackPolicy.shouldApply(enabled = false, hardcoreActive = false))
    }
}
