package ai.platon.pulsar.chrome

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DOM wheel fallback keeps the contract of a real wheel tick")
class WheelFallbackJsTest {

    private val pageScript = PulsarWebDriver.wheelFallbackPageJs(12.5, 240.0, 0.0, 100.0)
    private val elementScript = PulsarWebDriver.WHEEL_FALLBACK_ELEMENT_JS

    @Test
    @DisplayName("page script hit-tests the element under the cursor and forwards its position")
    fun pageScriptHitTestsTheElementUnderTheCursor() {
        assertTrue(
            pageScript.contains("document.elementFromPoint(12.5, 240.0)"),
            "the tick must be hit-tested at the cursor, not dispatched on the document: $pageScript"
        )
        assertTrue(
            pageScript.contains("wheelFallback(target, 0.0, 100.0, 12.5, 240.0)"),
            "the tick must carry the deltas and the cursor position: $pageScript"
        )
    }

    @Test
    @DisplayName("element script replays on the resolved node instead of re-querying the selector")
    fun elementScriptUsesTheResolvedNode() {
        assertTrue(
            elementScript.contains("wheelFallback(this, deltaX, deltaY, x, y)"),
            "the element must be the callFunctionOn receiver: $elementScript"
        )
        assertFalse(
            elementScript.contains("querySelector"),
            "a selector would have to be escaped into the script and may resolve another element: $elementScript"
        )
    }

    @Test
    @DisplayName("both scripts scroll only when the wheel event was not canceled")
    fun bothScriptsScrollOnlyAfterTheEventSurvives() {
        listOf(pageScript, elementScript).forEach { script ->
            val cancellationGuard = script.indexOf("if (!target.dispatchEvent(evt))")
            val scroll = script.indexOf(".scrollBy(deltaX, deltaY)")

            assertTrue(cancellationGuard >= 0, "the dispatched event must be cancelable: $script")
            assertTrue(scroll > cancellationGuard, "the scroll must follow the cancellation guard: $script")
        }
    }

    @Test
    @DisplayName("both scripts scroll the nearest scrollable ancestor, not always the window")
    fun bothScriptsScrollTheNearestScrollableAncestor() {
        listOf(pageScript, elementScript).forEach { script ->
            assertTrue(
                script.contains("scrollableAncestor(target) || document.scrollingElement"),
                "a real tick scrolls the container under the cursor: $script"
            )
        }
    }
}
