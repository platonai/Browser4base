package ai.platon.pulsar.browser

import ai.platon.pulsar.WebDriverTestBase
import ai.platon.pulsar.chrome.PulsarWebDriver
import ai.platon.pulsar.chrome.dom.model.AriaSnapshotOptions
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

/**
 * Regression tests for Browser4base issue #4 (CSS-visibility-aware snapshot
 * serialization), against the real fixture `interactive-5.html` where tooltips
 * are spans toggled by CSS `:hover` from `visibility:hidden` to `visibility:visible`.
 *
 * - While the tooltip is hidden its text must not appear in any snapshot output.
 * - After hovering, the revealed tooltip must be discoverable in snapshots.
 */
class AriaSnapshotVisibilityTests : WebDriverTestBase() {

    private val tooltipPage get() = "$generatedAssetsBaseURL/interactive-5.html"
    private val hiddenTooltipText1 = "hierarchical representation"
    private val hiddenTooltipText2 = "static capture"

    @Test
    @DisplayName("issue 4: hidden tooltip text does not leak into the full-page snapshot")
    fun hiddenTooltipTextDoesNotLeakIntoFullPageSnapshot() = runWebDriverTestAndCompute(tooltipPage, browser) { driver ->
        assertIs<PulsarWebDriver>(driver)
        driver.waitForSelector("#hoverSection")
        // Keep the pointer away from the tooltip terms so both tooltips stay visibility:hidden.
        driver.mouseMove(10.0, 10.0)
        driver.delay(500)

        val full = driver.ariaSnapshot()
        println("FULL SNAPSHOT (pre-hover) length=${full.length}")

        assertFalse(
            full.contains(hiddenTooltipText1) || full.contains(hiddenTooltipText2),
            "visibility:hidden tooltip text must not appear in the full-page snapshot:\n$full"
        )
    }

    @Test
    @DisplayName("issue 4: hover-revealed tooltip is discoverable in the full-page snapshot")
    fun hoverRevealedTooltipIsDiscoverableInFullPageSnapshot() =
        runWebDriverTestAndCompute(tooltipPage, browser) { driver ->
            assertIs<PulsarWebDriver>(driver)
            driver.waitForSelector("#hoverSection")
            driver.hover("[data-testid=tooltipA11y]")
            driver.delay(600)

            val visibility = driver.evaluate(
                """getComputedStyle(document.querySelector('[data-testid=tooltipA11y] .tooltip-text')).visibility""",
                ""
            )
            println("Tooltip computed visibility after hover: $visibility")
            assertTrue(
                visibility.toString().equals("visible", ignoreCase = true),
                "Precondition: hovering must reveal the tooltip, got visibility=$visibility"
            )

            val full = driver.ariaSnapshot()
            println("FULL SNAPSHOT (post-hover) length=${full.length}")

            assertTrue(
                full.contains(hiddenTooltipText1),
                "A hover-revealed tooltip must appear in the full-page snapshot:\n$full"
            )
        }

    @Test
    @DisplayName("issue 4: hover-revealed tooltip is discoverable in the viewport snapshot")
    fun hoverRevealedTooltipIsDiscoverableInViewportSnapshot() =
        runWebDriverTestAndCompute(tooltipPage, browser) { driver ->
            assertIs<PulsarWebDriver>(driver)
            driver.waitForSelector("#hoverSection")
            driver.hover("[data-testid=tooltipA11y]")
            driver.delay(600)

            val viewport = driver.ariaSnapshot(viewports = "0", boxes = false)
            println("VIEWPORT SNAPSHOT (post-hover) length=${viewport.length}")

            assertTrue(
                viewport.contains(hiddenTooltipText1),
                "A hover-revealed tooltip must be discoverable in the viewport snapshot:\n$viewport"
            )
        }

    @Test
    @DisplayName("issue 4: hidden tooltip stays out of the viewport snapshot while hidden")
    fun hiddenTooltipStaysOutOfViewportSnapshot() = runWebDriverTestAndCompute(tooltipPage, browser) { driver ->
        assertIs<PulsarWebDriver>(driver)
        driver.waitForSelector("#hoverSection")
        driver.mouseMove(10.0, 10.0)
        driver.delay(500)

        val viewport = driver.ariaSnapshot(viewports = "0", boxes = false)
        println("VIEWPORT SNAPSHOT (pre-hover) length=${viewport.length}")

        assertFalse(
            viewport.contains(hiddenTooltipText1) || viewport.contains(hiddenTooltipText2),
            "visibility:hidden tooltip text must not appear in the viewport snapshot:\n$viewport"
        )
    }

    @Test
    @DisplayName("issue 4: rendered snapshot with boxes carries a box for the revealed tooltip container")
    fun renderedSnapshotWithBoxesCarriesBoxForTooltip() =
        runWebDriverTestAndCompute(tooltipPage, browser) { driver ->
            assertIs<PulsarWebDriver>(driver)
            driver.waitForSelector("#hoverSection")
            driver.hover("[data-testid=tooltipSnapshot]")
            driver.delay(600)

            val snapshot = driver.ariaSnapshot(AriaSnapshotOptions(boxes = true))
            println("BOXED SNAPSHOT (post-hover) length=${snapshot.length}")

            assertTrue(
                snapshot.contains(hiddenTooltipText2),
                "The revealed snapshot tooltip must appear in a boxed snapshot:\n$snapshot"
            )
        }
}
