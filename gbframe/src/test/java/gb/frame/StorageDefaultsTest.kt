package gb.frame

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageDefaultsTest {
    @Test fun appDataFolderLivesUnderSharedEmulatorsDirectory() {
        assertEquals("EMULATORS/GBSat", StorageDefaults.dataFolder("GBSat"))
        assertEquals("EMULATORS/GBCast", StorageDefaults.dataFolder("GBCast"))
    }

    @Test fun eachConsoleHasItsOwnRecommendedRomDirectory() {
        assertEquals("ROMS/SATURN", StorageDefaults.SATURN_GAMES_DIRECTORY)
        assertEquals("ROMS/DREAMCAST", StorageDefaults.DREAMCAST_GAMES_DIRECTORY)
    }
}
