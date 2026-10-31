package gb.frame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchIntentTest {
    @Test fun resolvesByFilenameBeforeDisplayedTitle() {
        val sameTitle = Game("Crazy Taxi", "/games/Crazy Taxi (Europe).gdi")
        val filenameMatch = Game("Other label", "/games/Crazy Taxi (USA).chd")

        assertEquals(filenameMatch, LaunchIntent.resolve("Crazy Taxi (USA).chd", listOf(sameTitle, filenameMatch)))
    }

    @Test fun resolvesFrontendGameTitleCaseInsensitively() {
        val crazyTaxi = Game("Crazy Taxi", "saf://games/Crazy%20Taxi.cdi")

        assertEquals(crazyTaxi, LaunchIntent.resolve("crazy taxi", listOf(crazyTaxi)))
    }

    @Test fun doesNotMatchUnknownGame() {
        assertNull(LaunchIntent.resolve("Missing Game", listOf(Game("Crazy Taxi", "/games/crazy.cdi"))))
    }
}
