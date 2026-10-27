package gb.frame

import org.junit.Assert.*
import org.junit.Test

class SystemSettingsTest {
    @Test fun biosSubtitleShowsEffectivePathAndEveryFileStatus() {
        val bios = SystemBiosFolderSetting("Documents/Emulator/bios", "Choose BIOS files", listOf(
            SystemBiosFile("boot.bin", true), SystemBiosFile("flash.bin", false),
        ), {})
        assertEquals("Documents/Emulator/bios\nboot.bin — Loaded\nflash.bin — Missing", bios.subtitle)
    }
    @Test fun unselectedBiosFolderExplainsWhatToChooseAndShowsMissingFiles() {
        val bios = SystemBiosFolderSetting(null, "Choose your firmware folder", listOf(SystemBiosFile("ipl.bin", false)), {})
        assertEquals("Choose your firmware folder\nipl.bin — Missing", bios.subtitle)
    }
}
