package ai.platon.pulsar.api

import ai.platon.pulsar.api.model.BrowserSettings
import ai.platon.pulsar.api.model.NavigateEntry
import ai.platon.pulsar.api.snapshot.SnapshotService
import ai.platon.pulsar.common.CheckState
import ai.platon.pulsar.common.config.ImmutableConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for [AbstractWebDriver.waitUntil] — all three public overloads plus edge cases.
 *
 * Uses a minimal [TestWaitDriver] that extends [AbstractWebDriver] and provides
 * only the concrete overrides needed for [waitUntil] to function; all other
 * [WebDriver] methods throw [NotImplementedError].
 */
class WebDriverWaitUntilTest {

    private lateinit var testDriver: TestWaitDriver

    @BeforeEach
    fun setUp() {
        val config = ImmutableConfig()
        val browserSettings = BrowserSettings(config)
        val browser: AbstractBrowser = mock()
        whenever(browser.settings).thenReturn(browserSettings)

        testDriver = TestWaitDriver(browser)
    }

    // ---------------------------------------------------------------------------
    // waitUntil(Duration, predicate) — primary overload
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("waitUntil returns remaining duration when predicate is already true")
    fun testWaitUntilPredicateAlreadyTrue() = runBlocking {
        val remaining = testDriver.waitUntil(Duration.ofSeconds(5)) { true }
        assertTrue(remaining > Duration.ZERO, "Remaining time should be positive, got $remaining")
        assertTrue(remaining <= Duration.ofSeconds(5), "Remaining should not exceed timeout")
    }

    @Test
    @DisplayName("waitUntil polls until predicate becomes true")
    fun testWaitUntilPredicateBecomesTrue() = runBlocking {
        val counter = AtomicInteger(0)
        val remaining = testDriver.waitUntil(Duration.ofSeconds(5)) {
            counter.incrementAndGet() >= 3
        }
        assertTrue(counter.get() >= 3, "Predicate should be called at least 3 times, was ${counter.get()}")
        assertTrue(remaining > Duration.ZERO, "Remaining time should be positive")
    }

    @Test
    @DisplayName("waitUntil returns zero or negative remaining on timeout")
    fun testWaitUntilTimeout() = runBlocking {
        val remaining = testDriver.waitUntil(Duration.ofMillis(50)) { false }
        assertTrue(remaining <= Duration.ZERO,
            "Remaining time should be <= 0 on timeout, got $remaining")
    }

    @Test
    @DisplayName("waitUntil with very short timeout times out before first poll")
    fun testWaitUntilTimeoutShort() = runBlocking {
        val remaining = testDriver.waitUntil(Duration.ofMillis(1)) { false }
        assertTrue(remaining <= Duration.ZERO,
            "Remaining time should be <= 0 on short timeout, got $remaining")
    }

    // ---------------------------------------------------------------------------
    // waitUntil(Long, predicate) — millis overload
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("waitUntil with millis timeout returns positive remaining when predicate is true")
    fun testWaitUntilMillisPredicateTrue() = runBlocking {
        val remaining = testDriver.waitUntil(5000) { true }
        assertTrue(remaining > 0, "Remaining millis should be positive, got $remaining")
        assertTrue(remaining <= 5000, "Remaining should not exceed timeout")
    }

    @Test
    @DisplayName("waitUntil with millis timeout returns zero or negative on timeout")
    fun testWaitUntilMillisTimeout() = runBlocking {
        val remaining = testDriver.waitUntil(50) { false }
        assertTrue(remaining <= 0, "Remaining millis should be <= 0 on timeout, got $remaining")
    }

    @Test
    @DisplayName("waitUntil with millis timeout polls until predicate becomes true")
    fun testWaitUntilMillisPredicateBecomesTrue() = runBlocking {
        val counter = AtomicInteger(0)
        val remaining = testDriver.waitUntil(5000) {
            counter.incrementAndGet() >= 2
        }
        assertTrue(counter.get() >= 2, "Predicate should be called at least 2 times, was ${counter.get()}")
        assertTrue(remaining > 0, "Remaining millis should be positive, got $remaining")
    }

    // ---------------------------------------------------------------------------
    // waitUntil(predicate) — default-timeout overload
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("waitUntil without timeout uses default timeout and returns positive remaining")
    fun testWaitUntilDefaultTimeoutPredicateTrue() = runBlocking {
        val remaining = testDriver.waitUntil { true }
        assertTrue(remaining > Duration.ZERO, "Remaining should be positive with default timeout")
    }

    @Test
    @DisplayName("waitUntil without timeout polls until predicate true")
    fun testWaitUntilDefaultTimeoutPredicateBecomesTrue() = runBlocking {
        val counter = AtomicInteger(0)
        val remaining = testDriver.waitUntil {
            counter.incrementAndGet() >= 2
        }
        assertTrue(counter.get() >= 2, "Predicate should be called at least 2 times")
        assertTrue(remaining > Duration.ZERO, "Remaining should be positive")
    }

    // ---------------------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("waitUntil with zero timeout returns zero or negative remaining")
    fun testWaitUntilZeroTimeout() = runBlocking {
        val remaining = testDriver.waitUntil(Duration.ZERO) { false }
        assertTrue(remaining <= Duration.ZERO,
            "Remaining should be <= 0 for zero timeout, got $remaining")
    }

    @Test
    @DisplayName("waitUntil propagates exception from predicate")
    fun testWaitUntilPredicateThrows() = runBlocking {
        val callCount = AtomicInteger(0)
        assertThrows<RuntimeException> {
            testDriver.waitUntil(Duration.ofSeconds(2)) {
                callCount.incrementAndGet()
                throw RuntimeException("predicate failure")
            }
        }
        assertEquals(1, callCount.get(), "Predicate should be called exactly once before throwing")
    }

    @Test
    @DisplayName("waitUntil with zero millis timeout returns zero or negative")
    fun testWaitUntilZeroMillisTimeout() = runBlocking {
        val remaining = testDriver.waitUntil(0) { false }
        assertTrue(remaining <= 0, "Remaining millis should be <= 0 for zero timeout, got $remaining")
    }

    @Test
    @DisplayName("waitUntil alternating predicate: first call is true")
    fun testWaitUntilAlternatingPredicate() = runBlocking {
        var toggle = false
        val remaining = testDriver.waitUntil(Duration.ofSeconds(5)) {
            toggle = !toggle
            toggle // first evaluation: true
        }
        assertTrue(remaining > Duration.ZERO, "Should succeed when predicate is true on first call")
    }
}

/**
 * Minimal [AbstractWebDriver] subclass for testing [waitUntil] in isolation.
 *
 * Only provides the concrete members that [waitUntil] depends on:
 * - [delayPolicy] and [timeoutPolicy] (delegated to real [BrowserSettings])
 * - [snapshotService] (mocked — never called by waitUntil)
 *
 * All other [WebDriver] methods throw [NotImplementedError] and must not be
 * invoked by any waitUntil test.
 */
private open class TestWaitDriver(
    browser: AbstractBrowser
) : AbstractWebDriver("test-guid", browser) {
    override val snapshotService: SnapshotService = mock()

    // -------- Abstract members from WebDriver NOT implemented by AbstractWebDriver --------

    override val browserType get() = TODO()
    override val isOpen get() = TODO()
    override suspend fun healthy(): CheckState = TODO()
    override suspend fun navigate(entry: NavigateEntry) = TODO()
    override suspend fun reload() = TODO()
    override suspend fun currentUrl(): String = TODO()
    override suspend fun url(): String = TODO()
    override suspend fun documentURI(): String = TODO()
    override suspend fun baseURI(): String = TODO()
    override suspend fun referrer(): String = TODO()
    override suspend fun pageSource(): String? = TODO()
    override suspend fun title(): String = TODO()
    override suspend fun nanoDOMTree() = TODO()
    override suspend fun browserUseState(target: ai.platon.pulsar.api.model.PageTarget, snapshotOptions: ai.platon.pulsar.api.model.SnapshotOptions) = TODO()
    override suspend fun getCookies() = TODO()
    override suspend fun deleteCookies(name: String, url: String?, domain: String?, path: String?) = TODO()
    override suspend fun clearBrowserCookies() = TODO()
    override suspend fun addBlockedURLs(urlPatterns: List<String>) = TODO()
    override suspend fun saveStorageState(): String = TODO()
    override suspend fun loadStorageState(state: String): String = TODO()
    override suspend fun waitForPage(url: String, timeout: Duration) = TODO()
    override suspend fun waitForFunction(pageFunction: String, timeout: Duration) = TODO()
    override suspend fun waitForSelector(selector: String, timeout: Duration, action: suspend () -> Unit): Duration = TODO()
    override suspend fun exists(selector: String): Boolean = TODO()
    override suspend fun isVisible(selector: String): Boolean = TODO()
    override suspend fun isChecked(selector: String): Boolean = TODO()
    override suspend fun bringToFront() = TODO()
    override suspend fun hover(selector: String) = TODO()
    override suspend fun focus(selector: String) = TODO()
    override suspend fun type(text: String, selector: String?) = TODO()
    override suspend fun press(key: String, selector: String?) = TODO()
    override suspend fun fill(selector: String, text: String) = TODO()
    override suspend fun keyDown(key: String) = TODO()
    override suspend fun keyUp(key: String) = TODO()
    override suspend fun click(selector: String, count: Int) = TODO()
    override suspend fun click(selector: String, modifier: String) = TODO()
    override suspend fun dblclick(selector: String) = TODO()
    override suspend fun dblclick(selector: String, modifier: String) = TODO()
    override suspend fun resize(width: Int, height: Int) = TODO()
    override suspend fun dialogAccept(promptText: String?) = TODO()
    override suspend fun dialogDismiss() = TODO()
    override suspend fun clickTextMatches(selector: String, pattern: String, count: Int) = TODO()
    override suspend fun clickMatches(selector: String, attrName: String, pattern: String, count: Int) = TODO()
    override suspend fun selectOption(selector: String, values: List<String>) = TODO()
    override suspend fun check(selector: String) = TODO()
    override suspend fun uncheck(selector: String) = TODO()
    override suspend fun scrollTo(selector: String): Double = TODO()
    override suspend fun mouseWheelDown(count: Int, deltaX: Double, deltaY: Double, delayMillis: Long) = TODO()
    override suspend fun mouseWheelUp(count: Int, deltaX: Double, deltaY: Double, delayMillis: Long) = TODO()
    override suspend fun mouseWheel(deltaX: Double, deltaY: Double) = TODO()
    override suspend fun mouseWheel(selector: String, deltaX: Double, deltaY: Double) = TODO()
    override suspend fun mouseMove(x: Double, y: Double) = TODO()
    override suspend fun mouseDown(button: String, clickCount: Int) = TODO()
    override suspend fun mouseUp(button: String, clickCount: Int) = TODO()
    override suspend fun moveMouseTo(selector: String, deltaX: Int, deltaY: Int) = TODO()
    override suspend fun dragAndDrop(selector: String, deltaX: Int, deltaY: Int) = TODO()
    override suspend fun querySelectorAll(selector: String) = TODO()
    override suspend fun selectFirstTextOrNull(selector: String): String? = TODO()
    override suspend fun selectTextAll(selector: String) = TODO()
    override suspend fun selectFirstAttributeOrNull(selector: String, attrName: String): String? = TODO()
    override suspend fun selectAttributes(selector: String) = TODO()
    override suspend fun selectAttributeAll(selector: String, attrName: String, start: Int, limit: Int) = TODO()
    override suspend fun setAttribute(selector: String, attrName: String, attrValue: String) = TODO()
    override suspend fun setAttributeAll(selector: String, attrName: String, attrValue: String) = TODO()
    override suspend fun selectFirstPropertyValueOrNull(selector: String, propName: String): String? = TODO()
    override suspend fun selectPropertyValueAll(selector: String, propName: String, start: Int, limit: Int) = TODO()
    override suspend fun setProperty(selector: String, propName: String, propValue: String) = TODO()
    override suspend fun setPropertyAll(selector: String, propName: String, propValue: String) = TODO()
    override suspend fun evaluate(expression: String): Any? = TODO()
    override suspend fun evaluateValue(expression: String): Any? = TODO()
    override suspend fun evaluateValue(selector: String, functionDeclaration: String): Any? = TODO()
    override suspend fun outerHTML(selector: String): String? = TODO()
    override suspend fun evaluateDetail(expression: String) = TODO()
    override suspend fun evaluateValueDetail(expression: String) = TODO()
    override suspend fun evaluateValueDetail(expression: String, awaitPromise: Boolean) = TODO()
    override suspend fun evaluateValueDetail(selector: String, functionDeclaration: String) = TODO()
    override suspend fun executeCdpCommand(method: String, params: Map<String, Any?>?) = TODO()
    override suspend fun generateLocator(selector: String): String? = TODO()
    override suspend fun screenshot(fullPage: Boolean): String? = TODO()
    override suspend fun screenshot(selector: String): String? = TODO()
    override suspend fun screenshot(rect: ai.platon.pulsar.common.math.geometric.RectD): String? = TODO()
    override suspend fun pdf(): String? = TODO()
    override suspend fun ariaSnapshot(boxes: Boolean): String = TODO()
    override suspend fun clickablePoint(selector: String) = TODO()
    override suspend fun boundingBox(selector: String) = TODO()
    override suspend fun upload(selector: String, paths: List<String>) = TODO()
    override suspend fun pause() = TODO()
    override suspend fun stop() = TODO()
    override suspend fun goBack() = TODO()
    override suspend fun goForward() = TODO()
    override suspend fun frameList() = TODO()
    override suspend fun frameSwitch(frame: String) = TODO()
    override suspend fun frameMain() = TODO()
    override fun userTypedUrl(): String = TODO()
}
