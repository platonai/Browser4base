package ai.platon.pulsar.api.manage

import ai.platon.pulsar.api.AbstractBrowser
import ai.platon.pulsar.api.AbstractWebDriver
import ai.platon.pulsar.api.BrowserId
import ai.platon.pulsar.api.WebDriver
import ai.platon.pulsar.api.model.BrowserEvents
import ai.platon.pulsar.api.model.BrowserSettings
import ai.platon.pulsar.common.browser.BrowserProfileMode
import ai.platon.pulsar.common.config.ImmutableConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class BasicBrowserManagerTest {

    private val browserFactory = mock<BrowserFactory>()
    private val manager = BasicBrowserManager(browserFactory, ImmutableConfig())

    @BeforeEach
    fun setUp() {
        whenever(browserFactory.settings).thenReturn(BrowserSettings())
    }

    private fun registerPrototypeBrowser(): AbstractBrowser {
        val browser: AbstractBrowser = mock()
        whenever(browser.id).thenReturn(BrowserId.PROTOTYPE)
        whenever(browserFactory.launch(BrowserProfileMode.PROTOTYPE)).thenReturn(browser)
        return manager.launch(BrowserProfileMode.PROTOTYPE) as AbstractBrowser
    }

    @Test
    @DisplayName("launch by profile mode registers the browser")
    fun launchProfileModeRegistersBrowser() {
        val browser = registerPrototypeBrowser()

        assertSame(browser, manager.findBrowserOrNull(BrowserId.PROTOTYPE))
        assertTrue(manager.browsers.containsKey(BrowserId.PROTOTYPE))
    }

    @Test
    @DisplayName("launch variants register their browsers")
    fun launchVariantsRegisterBrowsers() {
        val systemDefault: AbstractBrowser = mock()
        whenever(systemDefault.id).thenReturn(BrowserId.SYSTEM_DEFAULT)
        whenever(browserFactory.launchSystemDefaultBrowser()).thenReturn(systemDefault)
        val randomTemp: AbstractBrowser = mock()
        whenever(randomTemp.id).thenReturn(BrowserId.RANDOM_TEMP)
        whenever(browserFactory.launchRandomTempBrowser()).thenReturn(randomTemp)

        assertSame(systemDefault, manager.launchSystemDefaultBrowser())
        assertSame(randomTemp, manager.launchRandomTempBrowser())
        assertEquals(2, manager.browsers.size)
    }

    @Test
    @DisplayName("findBrowserOrNull returns null for unknown ids")
    fun findBrowserOrNullReturnsNullForUnknownIds() {
        assertNull(manager.findBrowserOrNull(BrowserId.PROTOTYPE))
    }

    @Test
    @DisplayName("closeBrowser removes and closes the browser")
    fun closeBrowserRemovesAndCloses() {
        val browser = registerPrototypeBrowser()

        manager.closeBrowser(BrowserId.PROTOTYPE)

        assertNull(manager.findBrowserOrNull(BrowserId.PROTOTYPE))
        verify(browser).close()
    }

    @Test
    @DisplayName("close closes all registered browsers")
    fun closeClosesAllBrowsers() {
        val prototype = registerPrototypeBrowser()
        val default: AbstractBrowser = mock()
        whenever(default.id).thenReturn(BrowserId.DEFAULT)
        whenever(browserFactory.launch(BrowserProfileMode.DEFAULT)).thenReturn(default)
        manager.launch(BrowserProfileMode.DEFAULT)

        manager.close()
        manager.close()

        verify(prototype).close()
        verify(default).close()
        assertTrue(manager.browsers.isEmpty())
    }

    @Test
    @DisplayName("maintain emits lifecycle events for each browser")
    fun maintainEmitsLifecycleEvents() {
        val browser = registerPrototypeBrowser()

        manager.maintain()

        verify(browser).emit(BrowserEvents.willMaintain)
        verify(browser).emit(BrowserEvents.maintain)
        verify(browser).emit(BrowserEvents.didMaintain)
    }

    @Test
    @DisplayName("isActive reflects the browser state")
    fun isActiveReflectsBrowserState() {
        val browser = registerPrototypeBrowser()
        whenever(browser.isActive).thenReturn(true)
        assertTrue(manager.isActive(BrowserId.PROTOTYPE))

        whenever(browser.isActive).thenReturn(false)
        assertEquals(false, manager.isActive(BrowserId.PROTOTYPE))
    }

    @Test
    @DisplayName("findLeastValuableDriver picks the oldest idle driver")
    fun findLeastValuableDriverPicksOldestIdleDriver() {
        val browser = registerPrototypeBrowser()
        val idleOld: AbstractWebDriver = mock()
        val idleNew: AbstractWebDriver = mock()
        val working: AbstractWebDriver = mock()
        whenever(idleOld.isReady).thenReturn(false)
        whenever(idleOld.isWorking).thenReturn(false)
        whenever(idleOld.lastActiveTime).thenReturn(Instant.now().minusSeconds(120))
        whenever(idleNew.isReady).thenReturn(false)
        whenever(idleNew.isWorking).thenReturn(false)
        whenever(idleNew.lastActiveTime).thenReturn(Instant.now().minusSeconds(10))
        whenever(working.isReady).thenReturn(true)
        whenever(working.isWorking).thenReturn(true)
        whenever(browser.drivers).thenReturn(mapOf("idleOld" to idleOld, "idleNew" to idleNew, "working" to working))

        assertSame(idleOld, manager.findLeastValuableDriver())

        manager.closeLeastValuableDriver()
        verify(idleOld).close()
    }

    @Test
    @DisplayName("closeDriver closes the driver")
    fun closeDriverClosesDriver() {
        val driver: WebDriver = mock()

        manager.closeDriver(driver)

        verify(driver).close()
    }
}
