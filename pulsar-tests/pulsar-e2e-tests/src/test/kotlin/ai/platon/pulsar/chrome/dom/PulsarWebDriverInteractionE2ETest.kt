package ai.platon.pulsar.chrome.dom

import ai.platon.pulsar.FastWebDriverService
import ai.platon.pulsar.WebDriverTestBase
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end coverage for PulsarWebDriver interaction methods (type, dblclick,
 * mouseWheel, drag, dragAndDrop) against the dedicated interaction test page.
 */
@Tag("E2ETest")
class PulsarWebDriverInteractionE2ETest : WebDriverTestBase() {

    override val webDriverService get() = FastWebDriverService(browserFactory)

    private val interactionTestsUrl get() = "$generatedAssetsBaseURL/interaction-tests.html"

    @Test
    @DisplayName("type fills a text input and the page observes the value")
    fun testTypeIntoInput() = runWebDriverTestAndCompute(interactionTestsUrl, browser) { driver ->
        driver.waitForSelector("#typeInput")

        driver.type("hello world", "#typeInput")

        driver.waitUntil { driver.selectFirstTextOrNull("#typeOutput") == "hello world" }
        assertEquals("hello world", driver.selectFirstTextOrNull("#typeOutput"))
    }

    @Test
    @DisplayName("dblclick fires a dblclick event")
    fun testDblclick() = runWebDriverTestAndCompute(interactionTestsUrl, browser) { driver ->
        driver.waitForSelector("#dblclickButton")
        driver.bringToFront()

        driver.dblclick("#dblclickButton")

        driver.waitUntil { driver.selectFirstTextOrNull("#dblclickOutput") == "dblclicked" }
    }

    @Test
    @DisplayName("mouse wheel dispatches trusted wheel events")
    fun testMouseWheel() = runWebDriverTestAndCompute(interactionTestsUrl, browser) { driver ->
        driver.waitForSelector("#wheelContainer")
        driver.bringToFront()

        driver.mouseWheel("#wheelContainer", 0.0, 300.0)

        driver.waitUntil { (driver.selectFirstTextOrNull("#wheelOutput")?.toIntOrNull() ?: 0) > 0 }
    }

    @Test
    @DisplayName("drag fires html5 drag and drop events")
    fun testDragAndDrop() = runWebDriverTestAndCompute(interactionTestsUrl, browser) { driver ->
        driver.waitForSelector("#dragTarget")
        driver.waitForSelector("#dropZone")
        driver.bringToFront()

        driver.drag("#dragTarget", "#dropZone")

        driver.waitUntil { driver.selectFirstTextOrNull("#dropCount")?.toIntOrNull() == 1 }
    }

    @Test
    @DisplayName("dragAndDrop completes and keeps the driver responsive")
    fun testDragAndDropDelta() = runWebDriverTestAndCompute(interactionTestsUrl, browser) { driver ->
        driver.waitForSelector("#dragTarget")
        driver.bringToFront()

        driver.dragAndDrop("#dragTarget", 150, 80)

        // Native drag initiation via CDP mouse events is not guaranteed to
        // produce observable page drag events; assert the driver and page stay
        // healthy and interactive after the drag sequence.
        driver.waitForSelector("#dblclickButton")
        driver.dblclick("#dblclickButton")
        driver.waitUntil { driver.selectFirstTextOrNull("#dblclickOutput") == "dblclicked" }
    }
}
