package ai.platon.pulsar.chrome

import ai.platon.pulsar.api.BrowserProtocol
import ai.platon.pulsar.api.model.BrowserSettings
import ai.platon.pulsar.api.model.BrowserTab
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [PulsarWebDriver.reconnect] — the driver-level CDP link recovery
 * that preserves the same tab (and therefore the browser profile).
 */
class PulsarWebDriverReconnectTest {

    private fun newBrowser(): PulsarBrowser {
        val browser = mock<PulsarBrowser>()
        whenever(browser.settings).thenReturn(BrowserSettings())
        return browser
    }

    @Test
    fun `reconnect reconnects the protocol and reports healthy`() {
        runBlocking {
            val protocol = mock<BrowserProtocol>()
            val browser = newBrowser()
            whenever(protocol.isOpen).thenReturn(false, true)
            whenever(protocol.reconnect()).thenReturn(true)
            whenever(protocol.isTargetAlive()).thenReturn(true)

            val driver = PulsarWebDriver("guid-1", BrowserTab(), protocol, browser)
            assertTrue(driver.reconnect())
            verify(protocol).reconnect()
        }
    }

    @Test
    fun `reconnect is a no-op when the driver is already open`() {
        runBlocking {
            val protocol = mock<BrowserProtocol>()
            whenever(protocol.isOpen).thenReturn(true)

            val driver = PulsarWebDriver("guid-2", BrowserTab(), protocol, newBrowser())
            assertTrue(driver.reconnect())
            verify(protocol, never()).reconnect()
        }
    }

    @Test
    fun `reconnect fails when the protocol cannot reconnect`() {
        runBlocking {
            val protocol = mock<BrowserProtocol>()
            whenever(protocol.isOpen).thenReturn(false)
            whenever(protocol.reconnect()).thenReturn(false)

            val driver = PulsarWebDriver("guid-3", BrowserTab(), protocol, newBrowser())
            assertFalse(driver.reconnect())
        }
    }
}
