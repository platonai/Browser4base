package ai.platon.pulsar.chrome.protocol.transport

import ai.platon.pulsar.api.model.DevToolsConfig
import ai.platon.pulsar.chrome.Transport
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.function.Consumer

/**
 * Tests for [ChromeDevToolsImpl.reconnect] — the CDP link recovery used when
 * the websockets die (e.g. machine sleep) while the browser stays alive.
 */
class ChromeDevToolsImplReconnectTest {

    /**
     * Deterministic fake transport: reconnect() flips [isOpen] back to true.
     */
    private open class FakeTransport(private var open: Boolean) : Transport {
        var reconnectCalls = 0
        var connectCalls = 0

        override val isOpen: Boolean get() = open

        override fun connect(uri: URI) {
            connectCalls++
            open = true
        }

        override suspend fun send(message: String) {}

        override fun addMessageHandler(consumer: Consumer<String>) {}

        override fun close() {}

        override suspend fun reconnect(): Boolean {
            reconnectCalls++
            open = true
            return true
        }
    }

    private fun newDevTools(browserTransport: Transport, pageTransport: Transport) =
        ChromeDevToolsImpl(browserTransport, pageTransport, DevToolsConfig())

    @Test
    fun `reconnect reconnects the dead page transport`() {
        runBlocking {
            val browserTransport = FakeTransport(true)
            val pageTransport = FakeTransport(false)

            val devTools = newDevTools(browserTransport, pageTransport)
            assertTrue(devTools.reconnect())
            assertTrue(pageTransport.isOpen)
            assertTrue(browserTransport.isOpen)
            assertTrue(pageTransport.reconnectCalls == 1)
            assertTrue(browserTransport.reconnectCalls == 0)
        }
    }

    @Test
    fun `reconnect reconnects both transports when both are dead`() {
        runBlocking {
            val browserTransport = FakeTransport(false)
            val pageTransport = FakeTransport(false)

            val devTools = newDevTools(browserTransport, pageTransport)
            assertTrue(devTools.reconnect())
            assertTrue(pageTransport.isOpen)
            assertTrue(browserTransport.isOpen)
            assertTrue(pageTransport.reconnectCalls == 1)
            assertTrue(browserTransport.reconnectCalls == 1)
        }
    }

    @Test
    fun `reconnect is a no-op when everything is already open`() {
        runBlocking {
            val browserTransport = FakeTransport(true)
            val pageTransport = FakeTransport(true)

            val devTools = newDevTools(browserTransport, pageTransport)
            assertTrue(devTools.reconnect())
            assertTrue(pageTransport.reconnectCalls == 0)
            assertTrue(browserTransport.reconnectCalls == 0)
        }
    }

    @Test
    fun `reconnect fails when the page transport cannot reconnect`() {
        runBlocking {
            val browserTransport = FakeTransport(true)
            val pageTransport = object : FakeTransport(false) {
                override suspend fun reconnect(): Boolean {
                    reconnectCalls++
                    return false // stays closed
                }
            }

            val devTools = newDevTools(browserTransport, pageTransport)
            assertFalse(devTools.reconnect())
            assertFalse(pageTransport.isOpen)
        }
    }
}
