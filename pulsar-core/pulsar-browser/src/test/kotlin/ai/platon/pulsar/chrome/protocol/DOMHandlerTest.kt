package ai.platon.pulsar.chrome.protocol

import ai.platon.cdt.kt.protocol.types.dom.Node
import ai.platon.cdt.kt.protocol.types.dom.PerformSearch
import ai.platon.cdt.kt.protocol.types.page.CrossOriginIsolatedContextType
import ai.platon.cdt.kt.protocol.types.page.Frame
import ai.platon.cdt.kt.protocol.types.page.FrameTree
import ai.platon.cdt.kt.protocol.types.page.SecureContextType
import ai.platon.cdt.kt.protocol.types.runtime.RemoteObject
import ai.platon.pulsar.api.BrowserProtocol
import ai.platon.pulsar.api.model.Locator
import ai.platon.pulsar.chrome.FrameManager
import ai.platon.pulsar.chrome.FrameScopeException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
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

    // -------------------------------------------------------------------------
    // Frame-scoped resolution (FrameManager active): CSS selectors resolve
    // inside the selected frame's document; XPath and node locators follow
    // the documented frame-scope semantics.
    // -------------------------------------------------------------------------

    private fun frame(
        id: String,
        parentId: String? = null,
        name: String? = null,
        url: String
    ) = Frame(
        id = id,
        parentId = parentId,
        loaderId = "loader-$id",
        name = name,
        url = url,
        domainAndRegistry = "fixture.test",
        securityOrigin = "http://fixture.test",
        mimeType = "text/html",
        secureContextType = SecureContextType.SECURE,
        crossOriginIsolatedContextType = CrossOriginIsolatedContextType.NOT_ISOLATED,
        gatedAPIFeatures = emptyList(),
    )

    private fun documentNode(nodeId: Int, frameId: String? = null, children: List<Node>? = null): Node =
        Node(
            nodeId = nodeId,
            backendNodeId = nodeId * 100,
            nodeType = 9,
            nodeName = "#document",
            localName = "",
            nodeValue = "",
            frameId = frameId,
            children = children,
        )

    private fun elementNode(
        nodeId: Int,
        nodeName: String,
        contentDocument: Node? = null,
        children: List<Node>? = null
    ): Node =
        Node(
            nodeId = nodeId,
            backendNodeId = nodeId * 100,
            nodeType = 1,
            nodeName = nodeName,
            localName = nodeName.lowercase(),
            nodeValue = "",
            contentDocument = contentDocument,
            children = children,
        )

    /**
     * A DOMHandler whose FrameManager is switched into the pay frame (c1)
     * via the frame-tree stub: main(m1) -> pay(c1, name=payframe).
     */
    private fun scopedDomHandler(): DOMHandler {
        val frameManager = FrameManager(browserProtocol)
        runBlocking {
            val tree = FrameTree(
                frame = frame("m1", url = "http://fixture.test/main"),
                childFrames = listOf(
                    FrameTree(frame = frame("c1", parentId = "m1", name = "payframe", url = "http://fixture.test/pay")),
                ),
            )
            whenever(browserProtocol.getFrameTree()).thenReturn(tree)
            frameManager.switch("payframe")
        }
        assertEquals("c1", frameManager.activeFrameId)
        return DOMHandler(browserProtocol, frameManager)
    }

    /** Pierced DOM: document(10) > body(11) > iframe#pay(20) whose content document (30) belongs to frame c1. */
    private fun payFrameDom(): Node {
        val payDoc = documentNode(30, frameId = "c1")
        val payElement = elementNode(20, "IFRAME", contentDocument = payDoc)
        return documentNode(10, children = listOf(elementNode(11, "BODY", children = listOf(payElement))))
    }

    @Test
    @DisplayName("queryLocator resolves a css selector inside the selected frame's document")
    fun queryLocatorResolvesInSelectedFrame() {
        runBlocking {
            whenever(browserProtocol.getDocument(any(), any())).thenReturn(payFrameDom())
            whenever(browserProtocol.querySelector(30, "#card-number")).thenReturn(42)
            val scoped = scopedDomHandler()

            val node = scoped.queryLocator("#card-number")!!

            assertEquals(42, node.nodeId)
            verify(browserProtocol).querySelector(30, "#card-number")
        }
    }

    @Test
    @DisplayName("queryLocator returns null when the selector misses inside the selected frame")
    fun queryLocatorMissesInSelectedFrame() = runBlocking {
        whenever(browserProtocol.getDocument(any(), any())).thenReturn(payFrameDom())
        whenever(browserProtocol.querySelector(30, "#main-button")).thenReturn(0)
        val scoped = scopedDomHandler()

        assertNull(scoped.queryLocator("#main-button"))
    }

    @Test
    @DisplayName("queryLocatorAll resolves all css matches inside the selected frame")
    fun queryLocatorAllResolvesInSelectedFrame() {
        runBlocking {
            whenever(browserProtocol.getDocument(any(), any())).thenReturn(payFrameDom())
            whenever(browserProtocol.querySelectorAll(30, ".card")).thenReturn(listOf(2, 3))
            val scoped = scopedDomHandler()

            val nodes = scoped.queryLocatorAll(".card")!!

            assertEquals(listOf(2, 3), nodes.map { it.nodeId })
            verify(browserProtocol).querySelectorAll(30, ".card")
        }
    }

    @Test
    @DisplayName("XPath selectors fail loudly inside a selected frame")
    fun xpathFailsLoudlyInSelectedFrame() = runBlocking {
        val scoped = scopedDomHandler()

        val e = runCatching { scoped.queryLocator("//div") }.exceptionOrNull()

        assertTrue(e is FrameScopeException, "Expected FrameScopeException, got $e")
        assertTrue(e!!.message!!.contains("XPath selectors are not supported inside a selected frame"))
    }

    @Test
    @DisplayName("backend node id locators ignore the frame scope and resolve unscoped")
    fun backendNodeIdIgnoresFrameScope() {
        runBlocking {
            val remoteObject: RemoteObject = mock()
            whenever(remoteObject.objectId).thenReturn("obj-123")
            whenever(browserProtocol.resolveNodeByBackendNodeId(123)).thenReturn(remoteObject)
            whenever(browserProtocol.requestNode("obj-123")).thenReturn(456)
            val scoped = scopedDomHandler()

            val node = scoped.queryLocator("backend:123")!!

            assertEquals(456, node.nodeId)
            verify(browserProtocol).resolveNodeByBackendNodeId(123)
        }
    }

    @Test
    @DisplayName("an unreachable selected frame fails loudly instead of degrading to not-found")
    fun unreachableFrameFailsLoudly() = runBlocking {
        // No DOM node carries frame c1: the frame is out-of-process (cross-origin).
        whenever(browserProtocol.getDocument(any(), any()))
            .thenReturn(documentNode(10, children = listOf(elementNode(11, "BODY"))))
        val scoped = scopedDomHandler()

        val e = assertThrows(FrameScopeException::class.java) {
            runBlocking { scoped.queryLocator("#card-number") }
        }

        assertTrue(e.message!!.contains("not reachable"))
    }
}
