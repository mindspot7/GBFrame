package gb.frame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchControlsLayoutTest {
    @Test fun sharedOverlayTreatmentIsLimitedToSquareFourThreeAndSixteenNineDisplays() {
        assertTrue(TouchControlsLayoutPlanner.usesResponsiveLayout(720f, 720f))
        assertTrue(TouchControlsLayoutPlanner.usesResponsiveLayout(600f, 450f))
        assertTrue(TouchControlsLayoutPlanner.usesResponsiveLayout(800f, 450f))
        assertEquals(false, TouchControlsLayoutPlanner.usesResponsiveLayout(1080f, 2400f))
    }

    @Test fun portraitPhonePlacesNativeAspectVideoAtTopAndControlsBelow() {
        val plan = TouchControlsLayoutPlanner.plan(360f, 800f, 4f / 3f)

        assertEquals(TouchControlsPlacement.PORTRAIT_BOTTOM, plan.placement)
        assertEquals(0f, plan.video.left, 0.01f)
        assertEquals(0f, plan.video.top, 0.01f)
        assertEquals(360f, plan.video.width, 0.01f)
        assertEquals(270f, plan.video.height, 0.01f)
    }

    @Test fun landscapePhoneCentersVideoAndLeavesBothSidePanels() {
        val plan = TouchControlsLayoutPlanner.plan(800f, 450f, 4f / 3f)

        assertEquals(TouchControlsPlacement.LANDSCAPE_SIDES, plan.placement)
        assertTrue(plan.video.left > 0f)
        assertEquals(plan.video.left, 800f - plan.video.left - plan.video.width, 0.01f)
        assertEquals(800f * 0.62f, plan.video.width, 0.01f)
        assertEquals(plan.video.width / plan.video.height, 4f / 3f, 0.01f)
    }

    @Test fun fourThreeUsesFullDisplayAndSquareCentersTheWholeGameImage() {
        val fourThree = TouchControlsLayoutPlanner.plan(600f, 450f)
        assertEquals(TouchControlsPlacement.FULLSCREEN_OVERLAY, fourThree.placement)
        assertEquals(TouchControlsVideoBounds(0f, 0f, 600f, 450f), fourThree.video)

        val square = TouchControlsLayoutPlanner.plan(720f, 720f)
        assertEquals(TouchControlsPlacement.FULLSCREEN_OVERLAY, square.placement)
        assertEquals(TouchControlsVideoBounds(0f, 90f, 720f, 540f), square.video)
    }

    @Test fun disabledTouchControlsRestoreFullscreenVideo() {
        val plan = TouchControlsLayoutPlanner.plan(360f, 800f, controlsEnabled = false)

        assertEquals(TouchControlsPlacement.FULLSCREEN, plan.placement)
        assertEquals(TouchControlsVideoBounds(0f, 0f, 360f, 800f), plan.video)
    }
}
