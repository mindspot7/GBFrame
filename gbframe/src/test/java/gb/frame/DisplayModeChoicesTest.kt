package gb.frame

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayModeChoicesTest {
    @Test
    fun choicesUseTheSharedDisplayOrderEvenWhenConsumerOrderDiffers() {
        val choices = listOf(
            DisplayModeChoice(0, "4:3"),
            DisplayModeChoice(1, "Fullscreen"),
            DisplayModeChoice(2, "16:9"),
        )

        assertEquals(
            listOf(0, 2, 1),
            orderedDisplayModeChoices(choices).map { it.value },
        )
    }
}
