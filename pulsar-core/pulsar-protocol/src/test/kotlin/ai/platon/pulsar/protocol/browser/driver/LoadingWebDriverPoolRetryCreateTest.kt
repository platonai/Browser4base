package ai.platon.pulsar.protocol.browser.driver

import ai.platon.pulsar.api.AbstractWebDriver
import ai.platon.pulsar.api.Browser
import ai.platon.pulsar.api.BrowserId
import ai.platon.pulsar.api.BrowserManager
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.config.VolatileConfig
import ai.platon.pulsar.protocol.browser.emulator.WebDriverPoolExhaustedException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration

/**
 * The pool must retry driver creation while a poll task is waiting for a driver.
 *
 * Regression test for a CI failure (TestLoadResources.testLoadResource): driver creation
 * can be skipped for transient reasons (e.g. a momentary critical system load right after
 * the pool's own browser launch). With a single creation attempt followed by a long blind
 * wait on the standby queue, such a transient skip stranded the task for the whole poll
 * timeout and finally threw [WebDriverPoolExhaustedException] even though the pool had
 * free driver slots.
 * */
@Tag("Unit")
@Tag("Fast")
@DisplayName("LoadingWebDriverPool retries driver creation while waiting")
class LoadingWebDriverPoolRetryCreateTest {

    private lateinit var browserId: BrowserId
    private lateinit var browserManager: BrowserManager
    private lateinit var browser: Browser
    private lateinit var driver: AbstractWebDriver
    private lateinit var pool: LoadingWebDriverPool

    @BeforeEach
    fun setUp() {
        browserId = BrowserId.createRandomTemp()
        browserManager = mock()
        browser = mock()
        driver = mock()

        whenever(browser.isActive).thenReturn(true)
        whenever(browser.newDriver()).thenReturn(driver)
        whenever(browserManager.launch(any(), anyOrNull())).thenReturn(browser)
        whenever(driver.isRecyclable).thenReturn(true)
    }

    @AfterEach
    fun tearDown() {
        pool.close()
    }

    @Test
    @DisplayName("poll retries driver creation when the first attempts are skipped")
    fun pollRetriesCreationWhenItWasSkipped() {
        // The first two creation attempts are skipped (transient condition), then
        // creation is allowed again. The poll must not give up after the first skip.
        pool = RetryCreatePool(browserId, browserManager, ImmutableConfig(), skipCreateCount = 2)

        val polledDriver = pool.poll(0, VolatileConfig.UNSAFE, Duration.ofSeconds(10))

        assertNotNull(polledDriver, "A driver should be created and polled once creation is allowed again")
        assertEquals(driver, polledDriver)
        assertEquals(1, pool.numCreated)
        verify(browserManager).launch(any(), anyOrNull())
    }

    @Test
    @DisplayName("poll throws WebDriverPoolExhaustedException when creation never succeeds")
    fun pollThrowsExhaustedWhenCreationNeverSucceeds() {
        pool = RetryCreatePool(browserId, browserManager, ImmutableConfig(), skipCreateCount = Int.MAX_VALUE)

        assertThrows(WebDriverPoolExhaustedException::class.java) {
            pool.poll(0, VolatileConfig.UNSAFE, Duration.ofSeconds(3))
        }
        assertEquals(0, pool.numCreated)
    }

    /**
     * A pool whose driver creation is skipped for the first [skipCreateCount] attempts,
     * simulating a transient condition such as a momentary critical system load.
     * */
    private class RetryCreatePool(
        browserId: BrowserId,
        browserManager: BrowserManager,
        conf: ImmutableConfig,
        private val skipCreateCount: Int
    ) : LoadingWebDriverPool(browserId, browserManager, conf) {
        private var skipRemaining = skipCreateCount

        override fun shouldCreateWebDriver(): Boolean {
            return if (skipRemaining-- > 0) false else super.shouldCreateWebDriver()
        }
    }
}
