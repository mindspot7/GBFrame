package gb.frame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun resolvesUrlEncodedFilenameFromCachedIndex() {
        val crazyTaxi = Game("Crazy Taxi", "saf:/Crazy Taxi.chd")

        assertEquals(crazyTaxi, LaunchIntent.resolve("Crazy%20Taxi.chd", listOf(crazyTaxi)))
    }

    @Test fun keepsLiteralPlusInFilename() {
        val game = Game("Crazy+Taxi", "saf:/Crazy+Taxi.chd")

        assertEquals(game, LaunchIntent.resolve("Crazy+Taxi.chd", listOf(game)))
    }

    @Test fun refreshesLibraryIndexWhenIntentTargetIsMissingFromCache() {
        val crazyTaxi = Game("Crazy Taxi", "saf:/Crazy Taxi.chd")
        var refreshed = false

        assertEquals(crazyTaxi, LaunchIntent.resolveOrRefresh("Crazy%20Taxi.chd", emptyList()) {
            refreshed = true
            listOf(crazyTaxi)
        })
        assertEquals(true, refreshed)
    }

    @Test fun doesNotRescanWhenCachedIndexAlreadyMatches() {
        val crazyTaxi = Game("Crazy Taxi", "saf:/Crazy Taxi.chd")
        var refreshed = false

        assertEquals(crazyTaxi, LaunchIntent.resolveOrRefresh("Crazy%20Taxi.chd", listOf(crazyTaxi)) {
            refreshed = true
            emptyList()
        })
        assertFalse(refreshed)
    }

    @Test fun doesNotMatchUnknownGame() {
        assertNull(LaunchIntent.resolve("Missing Game", listOf(Game("Crazy Taxi", "/games/crazy.cdi"))))
    }
}
