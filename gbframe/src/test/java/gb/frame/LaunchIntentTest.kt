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

    @Test fun resolvesShortFrontendTitleWhenItUniquelyMatchesLongDiscName() {
        val daytona = Game("Daytona USA - Championship Circuit Edition", "saf:/Daytona USA - Championship Circuit Edition.chd")

        assertEquals(daytona, LaunchIntent.resolve("Daytona USA", listOf(daytona)))
    }

    @Test fun doesNotGuessWhenShortFrontendTitleMatchesMultipleGames() {
        val daytonaSaturn = Game("Daytona USA - Championship Circuit Edition", "saf:/saturn/Daytona USA - Championship Circuit Edition.chd")
        val daytonaDreamcast = Game("Daytona USA", "saf:/dreamcast/Daytona USA.chd")

        assertNull(LaunchIntent.resolve("Daytona", listOf(daytonaSaturn, daytonaDreamcast)))
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
