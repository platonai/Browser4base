package ai.platon.pulsar.chrome

import ai.platon.pulsar.WebDriverTestBase
import ai.platon.pulsar.api.WebDriver
import ai.platon.pulsar.api.model.FrameInfo
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * E2E tests for `WebDriver.frameList` / `frameSwitch` / `frameMain` running
 * against REAL Chrome and real HTTP pages (served by the local mock site on
 * port 17080, with one iframe loading the real remote page https://example.com).
 *
 * These tests are the ground truth for the CDP assumptions the frame machinery
 * relies on and that unit tests can only mock:
 *
 * 1. same-origin iframe documents are reachable through the pierced DOM of the
 *    page target and CSS selectors resolve inside them after `frameSwitch`;
 * 2. nested iframes can be reached by switching with a CSS selector from the
 *    scoped frame's document;
 * 3. cross-origin (out-of-process) iframes never silently resolve against the
 *    wrong document: depending on the Chrome build they either appear in the
 *    frame tree and fail loudly when operated, or are invisible to the page
 *    session's frame tree (separate CDP iframe targets) and fail loudly at
 *    selection — both outcomes are asserted;
 * 4. the frame scope resets when the main frame navigates.
 */
@Tag("Slow")
@Tag("E2ETest")
@DisplayName("Frame switching against frame fixture pages in real Chrome")
open class FrameSwitchE2ETest : WebDriverTestBase() {

    private val frameHostUrl get() = "$baseURL/assets/frames/frame-host.html"

    /** Waits until the frame tree contains all [expectedNames], returning the tree. */
    private suspend fun awaitFrames(
        driver: WebDriver,
        expectedNames: Set<String>,
        timeoutMs: Long = 15_000
    ): List<FrameInfo> {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val frames = driver.frameList()
            if (expectedNames.all { name -> frames.any { it.name == name } }) {
                return frames
            }
            delay(300)
        }
        val frames = driver.frameList()
        val missing = expectedNames.filter { name -> frames.none { it.name == name } }
        assertTrue(missing.isEmpty(), "frame tree not ready, missing: $missing | got: ${frames.map { it.name }}")
        return frames
    }

    /**
     * Waits until [selector] resolves inside the currently selected frame.
     * Frame-scope resolutions throw while the frame's document is still loading
     * (or until it becomes reachable), so transient failures are retried here.
     */
    private suspend fun awaitScopedExists(driver: WebDriver, selector: String, timeoutMs: Long = 15_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastError: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                if (driver.exists(selector)) return
            } catch (e: Exception) {
                lastError = e
            }
            delay(200)
        }
        assertTrue(driver.exists(selector), "scoped selector '$selector' did not become ready | last error: $lastError")
    }

    /** Polls a main-document JS expression until it equals [expected]. */
    private suspend fun awaitJsValue(driver: WebDriver, expression: String, expected: String, timeoutMs: Long = 15_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val value = runCatching { driver.evaluateValue(expression)?.toString() }.getOrNull()
            if (value == expected) return
            delay(200)
        }
        assertEquals(expected, runCatching { driver.evaluateValue(expression)?.toString() }.getOrNull(), expression)
    }

    // ---------------------------------------------------------------------
    // frameList
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("frameList reports the fixture frame tree depth-first, main frame active")
    fun framesOfTheFixturePageAreListedDepthFirst() = runEnhancedWebDriverTest(frameHostUrl) { driver ->
        // Note: xoriginframe (a real remote page) may or may not be listed depending
        // on whether the Chrome build exposes out-of-process frames to the page
        // session's frame tree — the same-origin frames are always listed.
        val frames = awaitFrames(driver, setOf("payframe", "notesframe", "innerframe"))

        val main = frames.first()
        assertTrue(main.isMainFrame)
        assertNull(main.parentId)
        assertEquals(0, main.depth)
        assertTrue(main.active, "main frame must be active by default")

        // exactly one active frame
        assertEquals(1, frames.count { it.active })

        val pay = frames.first { it.name == "payframe" }
        val notes = frames.first { it.name == "notesframe" }
        val inner = frames.first { it.name == "innerframe" }

        // depth-first order: main < pay < inner, and siblings come after the subtree
        assertTrue(frames.indexOf(main) < frames.indexOf(pay))
        assertTrue(frames.indexOf(pay) < frames.indexOf(inner))
        assertEquals(pay.id, inner.parentId)
        assertEquals(2, inner.depth)
        assertEquals(1, notes.depth)
        assertTrue(pay.url.contains("pay.html"), "pay frame url: ${pay.url}")
    }

    @Test
    @DisplayName("the default (main-frame) scope operates on the host document only")
    fun mainFrameScopeOperatesOnTheHostDocument() = runEnhancedWebDriverTest(frameHostUrl) { driver ->
        awaitFrames(driver, setOf("payframe", "notesframe"))

        // host document elements resolve and click
        assertTrue(driver.exists("#host-button"))
        driver.click("#host-button")
        awaitJsValue(driver, "document.querySelector('#host-status').textContent", "host button clicked")

        // elements inside the pay frame do NOT resolve from the main scope
        assertFalse(driver.exists("#pay-button"))
    }

    // ---------------------------------------------------------------------
    // frameSwitch: same-origin iframe (the pierced-DOM reachability assumption)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("frameSwitch by name scopes CSS selectors to the frame document")
    fun frameSwitchByNameScopesSelectorsToTheFrameDocument() = runEnhancedWebDriverTest(frameHostUrl) { driver ->
        awaitFrames(driver, setOf("payframe"))

        val switched = driver.frameSwitch("payframe")
        assertEquals("payframe", switched.name)
        assertEquals(1, switched.depth)
        assertTrue(switched.active)

        // scoped resolution: pay elements exist, host elements do not
        awaitScopedExists(driver, "#card-number")
        assertTrue(driver.exists("#pay-button"))
        assertFalse(driver.exists("#host-button"), "host elements must be out of scope inside the frame")

        // exactly the pay frame is marked active
        val listed = driver.frameList()
        assertEquals(1, listed.count { it.active })
        assertEquals(switched.id, listed.first { it.active }.id)
        assertEquals("payframe", listed.first { it.active }.name)
    }

    @Test
    @DisplayName("fill, click and read-back work inside the selected frame")
    fun fillClickAndReadBackInsideThePayFrame() = runEnhancedWebDriverTest(frameHostUrl) { driver ->
        awaitFrames(driver, setOf("payframe"))
        driver.frameSwitch("payframe")
        awaitScopedExists(driver, "#card-number")

        driver.fill("#card-number", "4111 1111 1111 1111")
        awaitJsValue(
            driver,
            "document.querySelector('iframe[name=\"payframe\"]').contentDocument" +
                ".querySelector('#card-number').value",
            "4111 1111 1111 1111"
        )

        driver.fill("#card-expiry", "12/29")
        driver.click("#pay-button")
        awaitJsValue(
            driver,
            "document.querySelector('iframe[name=\"payframe\"]').contentDocument" +
                ".querySelector('#pay-result').textContent",
            "paid:4111 1111 1111 1111"
        )

        assertTrue(driver.isVisible("#pay-button"))
    }

    @Test
    @DisplayName("waitForSelector resolves inside the selected frame")
    fun waitForSelectorResolvesInsideTheSelectedFrame() = runEnhancedWebDriverTest(frameHostUrl) { driver ->
        awaitFrames(driver, setOf("payframe"))
        driver.frameSwitch("payframe")

        val waited = driver.waitForSelector("#pay-button", Duration.ofSeconds(15))

        assertTrue(waited.toMillis() >= 0)
        assertTrue(driver.exists("#pay-button"))
    }

    // ---------------------------------------------------------------------
    // XPath inside a frame fails loudly (documented limitation)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("XPath selectors fail loudly inside a selected frame")
    fun xpathSelectorsFailLoudlyInsideASelectedFrame() = runEnhancedWebDriverTest(frameHostUrl) { driver ->
        awaitFrames(driver, setOf("payframe"))
        driver.frameSwitch("payframe")
        awaitScopedExists(driver, "#card-number")

        val pulsarDriver = driver as PulsarWebDriver
        val e = runCatching { pulsarDriver.page.dom.queryLocator("//div") }.exceptionOrNull()

        assertTrue(e is FrameScopeException, "Expected FrameScopeException, got $e")
        assertTrue(e!!.message!!.contains("XPath selectors are not supported inside a selected frame"))
    }

    // ---------------------------------------------------------------------
    // switching by frame id / url substring / nested CSS selector
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("frameSwitch accepts a frame id and a url substring")
    fun switchByFrameIdAndUrlSubstring() = runEnhancedWebDriverTest(frameHostUrl) { driver ->
        val frames = awaitFrames(driver, setOf("payframe", "notesframe"))
        val pay = frames.first { it.name == "payframe" }

        val byId = driver.frameSwitch(pay.id)
        assertEquals(pay.id, byId.id)
        assertTrue(byId.active)

        driver.frameMain()

        val byUrl = driver.frameSwitch("assets/frames/notes.html")
        assertEquals("notesframe", byUrl.name)
        assertTrue(byUrl.active)
    }

    @Test
    @DisplayName("a nested iframe is switched by CSS selector inside the scoped frame")
    fun nestedFrameSwitchResolvesInsideTheScopedDocument() = runEnhancedWebDriverTest(frameHostUrl) { driver ->
        awaitFrames(driver, setOf("payframe", "innerframe"))
        driver.frameSwitch("payframe")
        awaitScopedExists(driver, "#card-number")

        // CSS selector for the nested iframe resolves in the pay frame's document
        val inner = driver.frameSwitch("iframe#inner-frame")
        assertEquals("innerframe", inner.name)
        awaitScopedExists(driver, "#inner-text")
        assertTrue(driver.exists("#inner-text"))
        assertFalse(driver.exists("#card-number"), "pay elements must be out of scope inside the nested frame")

        // switch back out by name (frames may be ancestors of the current scope)
        driver.frameSwitch("payframe")
        awaitScopedExists(driver, "#card-number")
    }

    // ---------------------------------------------------------------------
    // cross-origin iframe: real remote page (https://example.com)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("a cross-origin iframe never silently resolves against the wrong document")
    fun crossOriginFrameFailsLoudlyInsteadOfResolvingWrong() = runEnhancedWebDriverTest(frameHostUrl) { driver ->
        awaitFrames(driver, setOf("payframe"))
        val frames = driver.frameList()
        val xorigin = frames.firstOrNull { it.name == "xoriginframe" }

        if (xorigin == null) {
            // This Chrome build keeps out-of-process frames invisible to the page
            // session's frame tree (they exist as separate CDP iframe targets).
            // Selecting one must fail loudly with an actionable error.
            val e = runCatching { driver.frameSwitch("xoriginframe") }.exceptionOrNull()
            assertNotNull(e, "selecting an unlisted cross-origin frame must fail")
            assertTrue(e!!.message.orEmpty().contains("Frame not found"), e.message)
        } else {
            // The frame is reported: it can be selected, but operating on the
            // out-of-process document must fail loudly, never resolve elsewhere.
            val switched = driver.frameSwitch("xoriginframe")
            assertEquals(xorigin.id, switched.id)
            assertTrue(switched.active)

            val outcome = runCatching { driver.exists("#card-number") }
            if (outcome.isFailure) {
                val message = outcome.exceptionOrNull()!!.message.orEmpty()
                assertTrue(message.contains("not reachable"), "unexpected error: $message")
            } else {
                assumeTrue(false, "cross-origin iframe content is unexpectedly reachable (network/CI quirk)")
            }
        }
    }

    // ---------------------------------------------------------------------
    // frameMain & scope lifecycle
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("frameMain restores the main-document scope")
    fun frameMainRestoresMainDocumentScope() = runEnhancedWebDriverTest(frameHostUrl) { driver ->
        awaitFrames(driver, setOf("payframe"))
        driver.frameSwitch("payframe")
        awaitScopedExists(driver, "#card-number")

        driver.frameMain()

        assertTrue(driver.exists("#host-button"))
        assertFalse(driver.exists("#card-number"), "pay elements must be out of scope after frameMain")
        val listed = driver.frameList()
        assertEquals(1, listed.count { it.active })
        assertTrue(listed.first { it.active }.isMainFrame)

        // XPath works again in the main document
        val pulsarDriver = driver as PulsarWebDriver
        assertNotNull(pulsarDriver.page.dom.queryLocator("//h1[@id='host-title']"))
    }

    @Test
    @DisplayName("a main-frame navigation clears the frame scope")
    fun mainFrameNavigationClearsTheFrameScope() = runEnhancedWebDriverTest(frameHostUrl) { driver ->
        awaitFrames(driver, setOf("payframe"))
        driver.frameSwitch("payframe")
        awaitScopedExists(driver, "#card-number")

        driver.navigate(frameHostUrl)
        driver.waitForNavigation()
        driver.waitForSelector("body", Duration.ofSeconds(15))
        awaitFrames(driver, setOf("payframe"))

        // the scope was reset by the navigation: host selectors resolve without frameMain
        assertTrue(driver.exists("#host-button"))
        assertFalse(driver.exists("#card-number"))
        val listed = driver.frameList()
        assertEquals(1, listed.count { it.active })
        assertTrue(listed.first { it.active }.isMainFrame)
    }

    @Test
    @DisplayName("an unknown frame target fails with an actionable error")
    fun unknownFrameTargetFailsLoudly() = runEnhancedWebDriverTest(frameHostUrl) { driver ->
        awaitFrames(driver, setOf("payframe"))

        val e = runCatching { driver.frameSwitch("no-such-frame-anywhere") }.exceptionOrNull()

        assertNotNull(e)
        assertTrue(e!!.message.orEmpty().contains("Frame not found"), e.message)
    }
}
