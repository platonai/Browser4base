package ai.platon.pulsar.chrome.network

import ai.platon.cdt.kt.protocol.types.fetch.HeaderEntry
import ai.platon.cdt.kt.protocol.types.network.ErrorReason
import ai.platon.cdt.kt.protocol.types.network.Request
import ai.platon.cdt.kt.protocol.types.network.RequestReferrerPolicy
import ai.platon.cdt.kt.protocol.types.network.ResourcePriority
import ai.platon.pulsar.api.BrowserProtocol
import ai.platon.pulsar.chrome.util.ChromeRPCException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CDPRequestTest {

    private val browserProtocol: BrowserProtocol = mock()

    private fun cdpRequest(interceptionId: String? = "intercept-1") = CDPRequest(
        browserProtocol = browserProtocol,
        requestId = "req-1",
        request = Request(
            url = "https://example.com/page",
            method = "GET",
            headers = mapOf("Accept" to "text/html"),
            initialPriority = ResourcePriority.HIGH,
            referrerPolicy = RequestReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN,
        ),
        interceptionId = interceptionId,
    )

    @Test
    @DisplayName("exposes the underlying request url")
    fun exposesRequestUrl() {
        assertEquals("https://example.com/page", cdpRequest().url)
    }

    @Test
    @DisplayName("continueRequest delegates to the protocol and marks handled")
    fun continueRequestDelegatesAndMarksHandled() = runBlocking {
        val request = cdpRequest()

        request.continueRequest(ContinueRequestOverrides())

        assertTrue(request.interceptionHandled)
        verify(browserProtocol).continueRequest(eq("intercept-1"), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    @DisplayName("continueRequest requires an interception id")
    fun continueRequestRequiresInterceptionId() {
        val request = cdpRequest(interceptionId = null)

        assertThrows(ChromeRPCException::class.java) {
            runBlocking { request.continueRequest(ContinueRequestOverrides()) }
        }
    }

    @Test
    @DisplayName("respond fulfills with default status and adds content-length")
    fun respondFulfillsWithDefaults() = runBlocking {
        val request = cdpRequest()

        request.respond(ResponseForRequest())

        assertTrue(request.interceptionHandled)
        verify(browserProtocol).fulfillRequest(
            eq("intercept-1"),
            eq(200),
            argThat<List<HeaderEntry>> { headers ->
                headers.any { it.name == "content-length" && it.value == "4" }
            },
            any(),
            eq("bnVsbA=="),
            eq("OK"),
        )
    }

    @Test
    @DisplayName("respond resets handled flag when the protocol call fails")
    fun respondResetsHandledFlagOnFailure() = runBlocking {
        whenever(browserProtocol.fulfillRequest(any(), any(), any(), any(), any(), any()))
            .thenThrow(RuntimeException("closed"))
        val request = cdpRequest()

        request.respond(ResponseForRequest())

        assertFalse(request.interceptionHandled)
    }

    @Test
    @DisplayName("respond requires an interception id")
    fun respondRequiresInterceptionId() {
        val request = cdpRequest(interceptionId = null)

        assertThrows(ChromeRPCException::class.java) {
            runBlocking { request.respond(ResponseForRequest()) }
        }
    }

    @Test
    @DisplayName("abort fails the request through the protocol")
    fun abortFailsRequest() = runBlocking {
        cdpRequest().abort(ErrorReason.ABORTED)

        verify(browserProtocol).failRequest(eq("intercept-1"), eq(ErrorReason.ABORTED))
    }

    @Test
    @DisplayName("abort requires an interception id")
    fun abortRequiresInterceptionId() {
        val request = cdpRequest(interceptionId = null)

        assertThrows(ChromeRPCException::class.java) {
            runBlocking { request.abort(ErrorReason.ABORTED) }
        }
    }
}
