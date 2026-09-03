package ai.platon.pulsar.chrome.network

import ai.platon.pulsar.api.AbstractWebDriver
import ai.platon.pulsar.api.model.IllegalWebDriverStateException
import ai.platon.pulsar.chrome.FrameScopeException
import ai.platon.pulsar.chrome.PulsarWebDriver
import ai.platon.pulsar.chrome.util.CDPReturnError
import ai.platon.pulsar.chrome.util.ChromeDriverException
import ai.platon.pulsar.chrome.util.ChromeRPCException
import ai.platon.pulsar.common.CheckState
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicInteger

class RobustRPCTest {

    private val driver: AbstractWebDriver = mock()

    @BeforeEach
    fun setUp() {
        whenever(driver.quickCheckHealthy(any())).thenReturn(CheckState())
        whenever(driver.readableState).thenReturn("READY")
        whenever(driver.id).thenReturn(1)
    }

    private fun newRpc() = RobustRPC(driver)

    @Test
    @DisplayName("invoke returns the block result on success")
    fun invokeReturnsBlockResult() = runBlocking {
        assertEquals("ok", newRpc().invoke("action") { "ok" })
    }

    @Test
    @DisplayName("invoke returns null when the driver is unhealthy")
    fun invokeReturnsNullWhenDriverUnhealthy() = runBlocking {
        whenever(driver.quickCheckHealthy(any())).thenReturn(CheckState(code = 1))

        assertNull(newRpc().invoke("action") { "ok" })
    }

    @Test
    @DisplayName("invoke retries transient cdp errors and succeeds")
    fun invokeRetriesTransientError() = runBlocking {
        whenever(driver.healthy()).thenReturn(CheckState())
        val calls = AtomicInteger()
        val rpc = newRpc()

        val result = rpc.invoke("action") {
            if (calls.incrementAndGet() == 1) {
                throw CDPReturnError(
                    errorCode = -32000,
                    errorMessage = "Cannot find context with specified id",
                    message = "Cannot find context with specified id",
                )
            }
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, calls.get())
    }

    @Test
    @DisplayName("invoke does not retry permanent cdp errors")
    fun invokeDoesNotRetryPermanentError() = runBlocking {
        whenever(driver.healthy()).thenReturn(CheckState())
        val calls = AtomicInteger()
        val rpc = newRpc()

        val thrown = assertThrows(CDPReturnError::class.java) {
            runBlocking {
                rpc.invoke("action") {
                    calls.incrementAndGet()
                    throw CDPReturnError(
                        errorMessage = "Cannot navigate to invalid URL: ftp://example.com",
                        message = "Cannot navigate to invalid URL: ftp://example.com",
                    )
                }
            }
        }

        assertTrue(thrown.message.orEmpty().contains("invalid URL"))
        assertEquals(1, calls.get())
    }

    @Test
    @DisplayName("invoke does not retry FrameScopeException (deterministic frame-scope failure)")
    fun invokeDoesNotRetryFrameScopeException() = runBlocking {
        whenever(driver.healthy()).thenReturn(CheckState())
        val calls = AtomicInteger()
        val rpc = newRpc()

        val thrown = assertThrows(FrameScopeException::class.java) {
            runBlocking {
                rpc.invoke("action") {
                    calls.incrementAndGet()
                    throw FrameScopeException("The selected frame 'pay' is not reachable")
                }
            }
        }

        assertTrue(thrown.message.orEmpty().contains("not reachable"))
        assertEquals(1, calls.get())
    }

    @Test
    @DisplayName("invokeOnPage tracks the last error and rethrows rpc exceptions")
    fun invokeOnPageTracksLastError() = runBlocking {
        val pulsarDriver: PulsarWebDriver = mock()
        whenever(pulsarDriver.quickCheckHealthy(any())).thenReturn(CheckState())
        whenever(pulsarDriver.healthy()).thenReturn(CheckState())
        whenever(pulsarDriver.readableState).thenReturn("READY")
        whenever(pulsarDriver.id).thenReturn(2)
        val rpc = RobustRPC(pulsarDriver)
        val boom = ChromeRPCException(1, "boom")

        val thrown = assertThrows(ChromeRPCException::class.java) {
            runBlocking { rpc.invokeOnPage("action", "https://example.com") { throw boom } }
        }

        assertSame(boom, thrown)
        assertSame(boom, rpc.lastError)
        assertEquals(2, rpc.rpcFailures.get())
    }

    @Test
    @DisplayName("invokeSilently swallows rpc exceptions")
    fun invokeSilentlySwallowsRpcException() = runBlocking {
        val pulsarDriver: PulsarWebDriver = mock()
        whenever(pulsarDriver.quickCheckHealthy(any())).thenReturn(CheckState())
        whenever(pulsarDriver.healthy()).thenReturn(CheckState())
        whenever(pulsarDriver.readableState).thenReturn("READY")
        whenever(pulsarDriver.id).thenReturn(3)
        val rpc = RobustRPC(pulsarDriver)
        val boom = ChromeRPCException(1, "boom")

        val result = rpc.invokeSilently("action") { throw boom }

        assertNull(result)
        assertSame(boom, rpc.lastError)
    }

    @Test
    @DisplayName("handleChromeRPCException throws when failures exceed the limit")
    fun handleChromeRPCExceptionThrowsAfterTooManyFailures() {
        val rpc = newRpc()
        rpc.rpcFailures.set(rpc.maxRPCFailures + 1)

        assertThrows(IllegalWebDriverStateException::class.java) {
            rpc.handleChromeRPCException(ChromeRPCException(1, "boom"))
        }
    }

    @Test
    @DisplayName("interceptChromeException rethrows unknown exception types")
    fun interceptChromeExceptionRethrowsUnknownTypes() {
        val rpc = newRpc()
        val boom = ChromeDriverException("boom")

        val thrown = assertThrows(ChromeDriverException::class.java) {
            runBlocking { rpc.interceptChromeException(boom) }
        }

        assertSame(boom, thrown)
    }

    @Test
    @DisplayName("predicateOnPage maps block result to boolean")
    fun predicateOnPageMapsResultToBoolean() = runBlocking {
        val rpc = newRpc()

        assertTrue(rpc.predicateOnPage("action") { "found" })
        assertEquals(false, rpc.predicateOnPage<String?>("action") { null })
        assertEquals(true, rpc.predicateOnPage("action") { true })
        assertEquals(false, rpc.predicateOnPage("action") { false })
    }
}
