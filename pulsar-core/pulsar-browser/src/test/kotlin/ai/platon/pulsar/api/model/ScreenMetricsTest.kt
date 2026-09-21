package ai.platon.pulsar.api.model

import ai.platon.pulsar.common.math.geometric.DimI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for issue #11 section 7: the device metrics override must report a screen size together
 * with the viewport, so the page never sees the impossible
 * `window.innerWidth > screen.width` relation.
 */
class ScreenMetricsTest {

    @Test
    @DisplayName("a viewport larger than the host screen clamps the screen up to the viewport")
    fun testViewportLargerThanScreenIsClamped() {
        val viewport = DimI(1920, 1080)
        val hostScreen = DimI(1680, 1050)

        val screen = ScreenMetrics.effectiveScreen(viewport, hostScreen)

        assertEquals(1920, screen.width, "screen.width must never be smaller than innerWidth")
        assertEquals(1080, screen.height)
    }

    @Test
    @DisplayName("a host screen larger than the viewport is reported as-is")
    fun testHostScreenLargerThanViewportIsKept() {
        val viewport = DimI(1920, 1080)
        val hostScreen = DimI(2560, 1440)

        val screen = ScreenMetrics.effectiveScreen(viewport, hostScreen)

        assertEquals(2560, screen.width)
        assertEquals(1440, screen.height)
    }

    @Test
    @DisplayName("an undetectable host screen falls back to the viewport size")
    fun testUndetectableScreenFallsBackToViewport() {
        val viewport = DimI(1920, 1080)

        val screen = ScreenMetrics.effectiveScreen(viewport, null)

        assertEquals(1920, screen.width)
        assertEquals(1080, screen.height)
    }

    @Test
    @DisplayName("mixed dimensions are clamped per axis")
    fun testMixedDimensionsAreClampedPerAxis() {
        val viewport = DimI(1920, 1080)
        val hostScreen = DimI(2560, 900)

        val screen = ScreenMetrics.effectiveScreen(viewport, hostScreen)

        assertEquals(2560, screen.width, "the wider host screen is kept")
        assertEquals(1080, screen.height, "the shorter host screen is clamped up to the viewport")
    }
}
