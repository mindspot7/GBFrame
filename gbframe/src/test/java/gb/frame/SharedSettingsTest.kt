package gb.frame

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedSettingsTest {
    @Test fun pictureFilterUsesNearestAndBilinearWithBilinearDefault() {
        assertEquals(1, PictureFilter.DEFAULT)
        assertEquals(0, PictureFilter.sanitize(-1))
        assertEquals(1, PictureFilter.sanitize(2))
        assertEquals(listOf("Nearest", "Bilinear"), PictureFilter.labels)
    }

    @Test fun textureFilterMapsFlycastValuesAndDefaultsToBilinear() {
        assertEquals(2, TextureFilter.DEFAULT)
        assertEquals(TextureFilter.BILINEAR, TextureFilter.sanitize(0))
        assertEquals(TextureFilter.NEAREST, TextureFilter.sanitize(1))
        assertEquals(TextureFilter.BILINEAR, TextureFilter.sanitize(2))
        assertEquals(0, TextureFilter.index(TextureFilter.NEAREST))
        assertEquals(1, TextureFilter.index(TextureFilter.BILINEAR))
        assertEquals(TextureFilter.NEAREST, TextureFilter.valueAt(0))
        assertEquals(TextureFilter.BILINEAR, TextureFilter.valueAt(1))
    }

    @Test fun autoFrameSkipUsesSharedOffNormalMaxValues() {
        assertEquals(listOf(0, 1, 2), DREAMCAST_AUTO_FRAME_SKIP_OPTIONS.map { it.value })
        assertEquals(listOf("Off", "Normal", "Max"), DREAMCAST_AUTO_FRAME_SKIP_OPTIONS.map { it.label })
    }
}
