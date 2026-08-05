package ai.platon.pulsar.chrome.protocol

import ai.platon.cdt.kt.protocol.types.dom.Node
import ai.platon.cdt.kt.protocol.types.page.Navigate
import ai.platon.cdt.kt.protocol.types.runtime.RemoteObject
import ai.platon.pulsar.api.BrowserProtocol
import ai.platon.pulsar.api.model.BrowserSettings
import ai.platon.pulsar.chrome.util.CDPReturnError
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PageHandlerTest {

    private val browserProtocol: BrowserProtocol = mock()
    private val pageHandler = PageHandler(browserProtocol, BrowserSettings())

    @BeforeEach
    fun setUp() {
        whenever(browserProtocol.isOpen).thenReturn(true)
    }

    @Test
    @DisplayName("navigate delegates to the protocol when active")
    fun navigateDelegatesWhenActive() = runBlocking {
        val navigate: Navigate = mock()
        whenever(browserProtocol.navigate("https://example.com")).thenReturn(navigate)

        assertSame(navigate, pageHandler.navigate("https://example.com"))
        verify(browserProtocol).navigate("https://example.com")
        Unit
    }

    @Test
    @DisplayName("navigate returns null when the protocol is closed")
    fun navigateReturnsNullWhenClosed() = runBlocking {
        whenever(browserProtocol.isOpen).thenReturn(false)

        assertNull(pageHandler.navigate("https://example.com"))
        verify(browserProtocol, never()).navigate(any())
        Unit
    }

    @Test
    @DisplayName("exists resolves a css selector through the protocol")
    fun existsResolvesCssSelector() = runBlocking {
        val document: Node = mock()
        whenever(document.nodeId).thenReturn(1)
        whenever(browserProtocol.getDocument()).thenReturn(document)
        whenever(browserProtocol.querySelector(1, "div.test")).thenReturn(42)

        assertTrue(pageHandler.exists("div.test"))
        verify(browserProtocol).querySelector(1, "div.test")
        Unit
    }

    @Test
    @DisplayName("exists returns false when the selector matches nothing")
    fun existsReturnsFalseWhenMissing() = runBlocking {
        val document: Node = mock()
        whenever(document.nodeId).thenReturn(1)
        whenever(browserProtocol.getDocument()).thenReturn(document)
        whenever(browserProtocol.querySelector(any(), any())).thenReturn(0)

        assertFalse(pageHandler.exists("div.test"))
    }

    @Test
    @DisplayName("exists returns false when cdp raises element-not-found")
    fun existsReturnsFalseOnCdpError() = runBlocking {
        val document: Node = mock()
        whenever(document.nodeId).thenReturn(1)
        whenever(browserProtocol.getDocument()).thenReturn(document)
        whenever(browserProtocol.querySelector(any(), any()))
            .thenThrow(CDPReturnError(errorCode = -32000, message = "Could not find node with given id"))

        assertFalse(pageHandler.exists("div.test"))
        Unit
    }

    @Test
    @DisplayName("exists resolves backend node id locators")
    fun existsResolvesBackendNodeId() = runBlocking {
        val remoteObject: RemoteObject = mock()
        whenever(remoteObject.objectId).thenReturn("obj-123")
        whenever(browserProtocol.resolveNodeByBackendNodeId(123)).thenReturn(remoteObject)
        whenever(browserProtocol.requestNode("obj-123")).thenReturn(456)

        assertTrue(pageHandler.exists("backend:123"))
        verify(browserProtocol).resolveNodeByBackendNodeId(123)
        Unit
    }

    @Test
    @DisplayName("exists supports playwright style e<id> locators")
    fun existsSupportsPlaywrightStyleLocators() = runBlocking {
        val remoteObject: RemoteObject = mock()
        whenever(remoteObject.objectId).thenReturn("obj-15")
        whenever(browserProtocol.resolveNodeByBackendNodeId(15)).thenReturn(remoteObject)
        whenever(browserProtocol.requestNode("obj-15")).thenReturn(20)

        assertTrue(pageHandler.exists("e15"))
        Unit
    }
}
