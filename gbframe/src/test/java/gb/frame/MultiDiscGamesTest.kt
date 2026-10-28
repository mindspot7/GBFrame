package gb.frame

import org.junit.Assert.assertEquals
import org.junit.Test

class MultiDiscGamesTest {
    @Test fun keepsOnlyFirstDiscForDiscDiskCdAndDvdNames() {
        val first = Game("RPG (USA) (Disc 1 of 2)", "first")
        val laterDiscs = listOf(
            Game("RPG (USA) (Disc 2 of 2)", "disc2"),
            Game("RPG (USA) (Disk 3)", "disk3"),
            Game("RPG (USA) (CD4)", "cd4"),
            Game("RPG (USA) (DVD 5)", "dvd5"),
        )

        assertEquals(listOf(first), keepFirstDiscOnly(listOf(first) + laterDiscs))
    }

    @Test fun leavesUnmarkedAndNonDiscNumberedTitlesAlone() {
        val games = listOf(
            Game("Final Fantasy 2 (USA)", "ff2"),
            Game("Sonic CD (USA)", "sonic-cd"),
            Game("Single-disc RPG", "single"),
        )

        assertEquals(games, keepFirstDiscOnly(games))
    }
}
