package ai.platon.pulsar.chrome.protocol

import ai.platon.cdt.kt.protocol.types.dom.Node
import ai.platon.cdt.kt.protocol.types.dom.PerformSearch
import ai.platon.cdt.kt.protocol.types.runtime.RemoteObject
import ai.platon.pulsar.api.BrowserProtocol
import ai.platon.pulsar.api.model.Locator
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DOMHandlerTest {

    private val browserProtocol: BrowserProtocol = mock()
    private val domHandler = DOMHandler(browserProtocol)

    @BeforeEach
    fun setUp() {
        whenever(browserProtocol.isOpen).thenReturn(true)
    }

    @Test
    @DisplayName("normalizeSelector keeps css selectors")
    fun normalizeSelectorKeepsCssSelectors() {
        assertEquals("div.test", domHandler.normalizeSelector("div.test"))
        assertEquals("div.test", domHandler.normalizeSelector("div.test", jsEscape = true))
    }

    @Test
    @DisplayName("normalizeSelector returns null for non-css locators")
    fun normalizeSelectorReturnsNullForNonCssLocators() {
        assertNull(domHandler.normalizeSelector("//div"))
        assertNull(domHandler.normalizeSelector("backend:123"))
        assertNull(domHandler.normalizeSelector("fbn:1,123"))
        assertNull(domHandler.normalizeSelector("unknown:xyz"))
    }

    @Test
    @DisplayName("normalizeLocator parses the supported locator formats")
    fun normalizeLocatorParsesSupportedFormats() {
        val css = domHandler.normalizeLocator("div.test")!!
        assertEquals(Locator.Type.CSS_PATH, css.locator.type)
        assertEquals("div.test", css.cssSelector)

        val backend = domHandler.normalizeLocator("backend:123")!!
        assertEquals(Locator.Type.BACKEND_NODE_ID, backend.locator.type)
        assertEquals("123", backend.locator.selector)
        assertNull(backend.cssSelector)

        val playwright = domHandler.normalizeLocator("e15")!!
        assertEquals(Locator.Type.BACKEND_NODE_ID, playwright.locator.type)
        assertEquals("15", playwright.locator.selector)

        val fbn = domHandler.normalizeLocator("fbn:1,123")!!
        assertEquals(Locator.Type.FRAME_BACKEND_NODE_ID, fbn.locator.type)

        val xpath = domHandler.normalizeLocator("//div")!!
        assertEquals(Locator.Type.XPATH, xpath.locator.type)

        assertNull(domHandler.normalizeLocator("unknown:xyz"))
    }

    @Test
    @DisplayName("queryLocator resolves backend node id locators")
    fun queryLocatorResolvesBackendNodeId() = runBlocking {
        val remoteObject: RemoteObject = mock()
        whenever(remoteObject.objectId).thenReturn("obj-123")
        whenever(browserProtocol.resolveNodeByBackendNodeId(123)).thenReturn(remoteObject)
        whenever(browserProtocol.requestNode("obj-123")).thenReturn(456)

        val node = domHandler.queryLocator("backend:123")!!

        assertEquals(456, node.nodeId)
        assertEquals(123, node.backendNodeId)
        verify(browserProtocol).releaseObject("obj-123")
    }

    @Test
    @DisplayName("queryLocatorAll resolves css selectors")
    fun queryLocatorAllResolvesCssSelectors() = runBlocking {
        val document: Node = mock()
        whenever(document.nodeId).thenReturn(1)
        whenever(browserProtocol.getDocument()).thenReturn(document)
        whenever(browserProtocol.querySelectorAll(1, "div.test")).thenReturn(listOf(2, 3))

        val nodes = domHandler.queryLocatorAll("div.test")!!

        assertEquals(listOf(2, 3), nodes.map { it.nodeId })
    }

    @Test
    @DisplayName("queryLocatorAll resolves xpath selectors")
    fun queryLocatorAllResolvesXpath() = runBlocking {
        val document: Node = mock()
        whenever(document.nodeId).thenReturn(1)
        whenever(browserProtocol.getDocument()).thenReturn(document)
        val searchResult: PerformSearch = mock()
        whenever(searchResult.searchId).thenReturn("s1")
        whenever(searchResult.resultCount).thenReturn(1)
        whenever(browserProtocol.performSearch(any(), any())).thenReturn(searchResult)
        whenever(browserProtocol.getSearchResults("s1", 0, 1)).thenReturn(listOf(7))

        val nodes = domHandler.queryLocatorAll("//div")!!

        assertEquals(listOf(7), nodes.map { it.nodeId })
        verify(browserProtocol).discardSearchResults(eq("s1"))
    }
}
