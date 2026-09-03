package ai.platon.pulsar.chrome

import ai.platon.cdt.kt.protocol.types.dom.Node
import ai.platon.cdt.kt.protocol.types.page.CrossOriginIsolatedContextType
import ai.platon.cdt.kt.protocol.types.page.Frame
import ai.platon.cdt.kt.protocol.types.page.FrameTree
import ai.platon.cdt.kt.protocol.types.page.SecureContextType
import ai.platon.pulsar.api.BrowserProtocol
import ai.platon.pulsar.api.model.FrameInfo
import ai.platon.pulsar.api.model.WebDriverException
import ai.platon.pulsar.chrome.util.CDPReturnError
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [FrameManager] tree flattening, frame switching resolution,
 * and scope lifecycle — all against a mocked [BrowserProtocol].
 */
@DisplayName("FrameManager frame switching")
class FrameManagerTest {

    private val browserProtocol: BrowserProtocol = mock()

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

    /** main -> [pay (name=payframe)], [pay] -> [inner], [other (name=otherframe)] */
    private fun sampleTree(): FrameTree {
        val main = frame("m1", url = "http://fixture.test/main")
        val pay = frame("c1", parentId = "m1", name = "payframe", url = "http://fixture.test/pay")
        val inner = frame("d1", parentId = "c1", url = "http://fixture.test/inner")
        val other = frame("c2", parentId = "m1", name = "otherframe", url = "http://fixture.test/other")
        return FrameTree(
            frame = main,
            childFrames = listOf(
                FrameTree(frame = pay, childFrames = listOf(FrameTree(frame = inner))),
                FrameTree(frame = other),
            ),
        )
    }

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
        frameId: String? = null,
        children: List<Node>? = null
    ): Node = Node(
        nodeId = nodeId,
        backendNodeId = nodeId * 100,
        nodeType = 1,
        nodeName = nodeName,
        localName = nodeName.lowercase(),
        nodeValue = "",
        frameId = frameId,
        contentDocument = contentDocument,
        children = children,
    )

    /** Pierced DOM: document(10) > body(11) > iframe#pay(20) whose content document (30) belongs to frame c1. */
    private fun payFrameDom(): Node {
        val payDoc = documentNode(30, frameId = "c1")
        val payElement = elementNode(20, "IFRAME", contentDocument = payDoc)
        return documentNode(10, children = listOf(elementNode(11, "BODY", children = listOf(payElement))))
    }

    private suspend fun stubTree() {
        val tree = sampleTree()
        whenever(browserProtocol.getFrameTree()).thenReturn(tree)
    }

    // -------------------------------------------------------------------------
    // list()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("list returns the frame tree depth-first with the main frame first")
    fun listReturnsDepthFirstTree() = runBlocking {
        stubTree()
        val manager = FrameManager(browserProtocol)

        val frames = manager.list()

        assertEquals(listOf("m1", "c1", "d1", "c2"), frames.map { it.id })
        assertEquals(listOf(0, 1, 2, 1), frames.map { it.depth })
        assertEquals("payframe", frames[1].name)
        assertTrue(frames[0].isMainFrame)
        assertFalse(frames[1].isMainFrame)
    }

    @Test
    @DisplayName("list marks the main frame active when no frame is selected")
    fun listMarksMainActiveByDefault() = runBlocking {
        stubTree()
        val manager = FrameManager(browserProtocol)

        val frames = manager.list()

        assertTrue(frames.first { it.id == "m1" }.active)
        assertTrue(frames.filter { it.id != "m1" }.none { it.active })
        assertNull(manager.activeFrameId)
    }

    // -------------------------------------------------------------------------
    // switch() by id / name / url
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("switch by exact frame id activates that frame")
    fun switchById() = runBlocking {
        stubTree()
        val manager = FrameManager(browserProtocol)

        val switched = manager.switch("d1")

        assertEquals("d1", switched.id)
        assertTrue(switched.active)
        assertEquals("d1", manager.activeFrameId)
    }

    @Test
    @DisplayName("switch by frame name activates the named frame")
    fun switchByName() = runBlocking {
        stubTree()
        val manager = FrameManager(browserProtocol)

        val switched = manager.switch("payframe")

        assertEquals("c1", switched.id)
        assertEquals("c1", manager.activeFrameId)
    }

    @Test
    @DisplayName("switch by url substring activates the matching frame")
    fun switchByUrlSubstring() = runBlocking {
        stubTree()
        val manager = FrameManager(browserProtocol)

        assertEquals("c2", manager.switch("fixture.test/other").id)
        // case-insensitive
        assertEquals("c2", manager.switch("FIXTURE.TEST/OTHER").id)
    }

    @Test
    @DisplayName("switch with an unknown target fails with an actionable error")
    fun switchUnknownFails() = runBlocking {
        stubTree()
        val manager = FrameManager(browserProtocol)

        val e = runCatching { manager.switch("no-such-frame") }.exceptionOrNull()
        assertTrue(e is WebDriverException, "Expected WebDriverException, got $e")
        assertTrue(e!!.message!!.contains("Frame not found: no-such-frame"))
        assertNull(manager.activeFrameId)
    }

    @Test
    @DisplayName("switch to a css selector resolves the iframe element's frame")
    fun switchByCssSelector() = runBlocking {
        stubTree()
        val root = payFrameDom()

        whenever(browserProtocol.getDocument()).thenReturn(root)
        whenever(browserProtocol.getDocument(any(), any())).thenReturn(root)
        whenever(browserProtocol.querySelector(10, "#pay")).thenReturn(20)
        whenever(browserProtocol.describeNode(20, null, null, null, null)).thenReturn(
            elementNode(20, "IFRAME", contentDocument = documentNode(30, frameId = "c1"))
        )
        val manager = FrameManager(browserProtocol)

        val switched = manager.switch("#pay")

        assertEquals("c1", switched.id)
        assertEquals("c1", manager.activeFrameId)
    }

    // -------------------------------------------------------------------------
    // scope lifecycle
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("frameMain clears the active frame and list marks the main frame active again")
    fun frameMainClearsScope() = runBlocking {
        stubTree()
        val manager = FrameManager(browserProtocol)
        manager.switch("payframe")
        assertEquals("c1", manager.activeFrameId)

        manager.switchToMainFrame()

        assertNull(manager.activeFrameId)
        assertFalse(manager.isScoped)
        assertTrue(manager.list().first { it.id == "m1" }.active)
    }

    @Test
    @DisplayName("reset clears the active frame (main-frame navigation hook)")
    fun resetClearsScope() = runBlocking {
        stubTree()
        val manager = FrameManager(browserProtocol)
        manager.switch("payframe")

        manager.reset()

        assertNull(manager.activeFrameId)
    }

    @Test
    @DisplayName("switch returns the frame info marked active")
    fun switchedFrameIsMarkedActive() = runBlocking {
        stubTree()
        val manager = FrameManager(browserProtocol)

        val info: FrameInfo = manager.switch("payframe")

        assertTrue(info.active)
        assertEquals("payframe", info.label)
    }

    // -------------------------------------------------------------------------
    // scoped queries (the DOMHandler integration surface)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("queryInFrame queries the frame's document node")
    fun queryInFrameUsesFrameDocument() = runBlocking {
        stubTree()
        whenever(browserProtocol.getDocument(any(), any())).thenReturn(payFrameDom())
        whenever(browserProtocol.querySelector(30, "#card-number")).thenReturn(42)
        val manager = FrameManager(browserProtocol)
        manager.switch("payframe")

        val nodeId = manager.queryInFrame("c1", "#card-number")

        assertEquals(42, nodeId)
    }

    @Test
    @DisplayName("queryInFrame on a cross-origin frame fails loudly")
    fun queryInFrameCrossOriginFailsLoudly() = runBlocking {
        stubTree()
        // No node in the pierced DOM carries frame c1: the frame is out-of-process.
        whenever(browserProtocol.getDocument(any(), any()))
            .thenReturn(documentNode(10, children = listOf(elementNode(11, "BODY"))))
        val manager = FrameManager(browserProtocol)
        manager.switch("payframe")

        val e = runCatching { manager.queryInFrame("c1", "#card-number") }.exceptionOrNull()
        assertTrue(e is FrameScopeException, "Expected FrameScopeException, got $e")
        assertTrue(e!!.message!!.contains("not reachable"))
    }

    // -------------------------------------------------------------------------
    // switch() by element ref (backend node of an <iframe> element)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("switch by backend ref activates the frame owned by the iframe element")
    fun switchByBackendRef() = runBlocking {
        stubTree()
        whenever(browserProtocol.describeNode(null, 2000, null, 1, null))
            .thenReturn(elementNode(20, "IFRAME", contentDocument = documentNode(30, frameId = "c1")))
        val manager = FrameManager(browserProtocol)

        val switched = manager.switch("backend:2000")

        assertEquals("c1", switched.id)
        assertTrue(switched.active)
        assertEquals("c1", manager.activeFrameId)
    }

    @Test
    @DisplayName("switch by playwright-style ref eN activates the owned frame")
    fun switchByPlaywrightStyleRef() = runBlocking {
        stubTree()
        whenever(browserProtocol.describeNode(null, 2000, null, 1, null))
            .thenReturn(elementNode(20, "IFRAME", contentDocument = documentNode(30, frameId = "c1")))
        val manager = FrameManager(browserProtocol)

        assertEquals("c1", manager.switch("e2000").id)
    }

    @Test
    @DisplayName("a ref pointing at a non-frame element fails loudly")
    fun refToNonFrameElementFailsLoudly() = runBlocking {
        stubTree()
        whenever(browserProtocol.describeNode(null, 2000, null, 1, null))
            .thenReturn(elementNode(20, "BUTTON"))
        val manager = FrameManager(browserProtocol)

        val e = runCatching { manager.switch("backend:2000") }.exceptionOrNull()
        assertTrue(e is WebDriverException, "Expected WebDriverException, got $e")
        assertTrue(e!!.message!!.contains("does not point to an <iframe>"))
        assertNull(manager.activeFrameId)
    }

    @Test
    @DisplayName("a stale ref fails loudly with guidance instead of matching other frames")
    fun staleRefFailsLoudly() = runBlocking {
        stubTree()
        whenever(browserProtocol.describeNode(null, 2000, null, 1, null))
            .thenThrow(
                CDPReturnError(
                    errorCode = -32000,
                    errorMessage = "Could not find node with given id",
                    message = "Could not find node with given id"
                )
            )
        val manager = FrameManager(browserProtocol)

        val e = runCatching { manager.switch("backend:2000") }.exceptionOrNull()
        assertTrue(e is WebDriverException, "Expected WebDriverException, got $e")
        assertTrue(e!!.message!!.contains("stale"))
        assertNull(manager.activeFrameId)
    }

    // -------------------------------------------------------------------------
    // frame-document cache (perf: no repeated full pierced-DOM walks)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("scoped queries reuse the resolved frame document instead of re-walking the DOM")
    fun scopedQueriesReuseResolvedFrameDocument() {
        runBlocking {
            stubTree()
            whenever(browserProtocol.getDocument(any(), any())).thenReturn(payFrameDom())
            whenever(browserProtocol.querySelector(30, "#card-number")).thenReturn(42)
            val manager = FrameManager(browserProtocol)
            manager.switch("payframe")

            assertEquals(42, manager.queryInFrame("c1", "#card-number"))
            assertEquals(42, manager.queryInFrame("c1", "#card-number"))
            assertEquals(42, manager.queryInFrame("c1", "#card-number"))

            // Exactly one pierced-DOM walk: the switch-time reachability probe. The
            // three queries must all hit the cache.
            verify(browserProtocol, times(1)).getDocument(any(), any())
        }
    }

    @Test
    @DisplayName("reset evicts the cached document so the next scoped query re-resolves")
    fun resetEvictsDocumentNodeCache() {
        runBlocking {
            stubTree()
            whenever(browserProtocol.getDocument(any(), any())).thenReturn(payFrameDom())
            whenever(browserProtocol.querySelector(30, "#card-number")).thenReturn(42)
            val manager = FrameManager(browserProtocol)
            manager.switch("payframe")
            assertEquals(42, manager.queryInFrame("c1", "#card-number"))

            manager.reset()
            assertEquals(42, manager.queryInFrame("c1", "#card-number"))

            // Switch-time probe + one re-walk after the reset cleared the cache.
            verify(browserProtocol, times(2)).getDocument(any(), any())
        }
    }

    @Test
    @DisplayName("a stale cached document node id is evicted and re-resolved once on CDP -32000")
    fun staleDocumentNodeIdIsEvictedAndReResolved() {
        runBlocking {
            stubTree()
            // First pierced tree stamps frame c1's document as node 30; after the frame
            // "navigates", the same frame id resolves to document node 31.
            whenever(browserProtocol.getDocument(any(), any()))
                .thenReturn(payFrameDom(), documentNode(31, frameId = "c1"))
            whenever(browserProtocol.querySelector(30, "#card-number")).thenThrow(
                CDPReturnError(
                    errorCode = -32000,
                    errorMessage = "Could not find node with given id",
                    message = "Could not find node with given id"
                )
            )
            whenever(browserProtocol.querySelector(31, "#card-number")).thenReturn(7)
            val manager = FrameManager(browserProtocol)
            manager.switch("payframe")

            assertEquals(7, manager.queryInFrame("c1", "#card-number"))

            verify(browserProtocol).querySelector(30, "#card-number")
            verify(browserProtocol).querySelector(31, "#card-number")
        }
    }

    // -------------------------------------------------------------------------
    // nested switching & stale scope lifecycle
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("nested frame switching by css selector resolves inside the scoped frame")
    fun nestedSwitchByCssSelector() = runBlocking {
        stubTree()
        // Pierced DOM: main(10) > body(11) > iframe#pay(20, cd doc 30 = c1) > iframe#inner(40, cd doc 50 = d1)
        val nested = documentNode(
            10,
            children = listOf(
                elementNode(
                    11, "BODY",
                    children = listOf(
                        elementNode(
                            20, "IFRAME",
                            contentDocument = documentNode(
                                30, frameId = "c1",
                                children = listOf(
                                    elementNode(
                                        31, "BODY",
                                        children = listOf(
                                            elementNode(
                                                40, "IFRAME",
                                                contentDocument = documentNode(50, frameId = "d1")
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
        whenever(browserProtocol.getDocument(any(), any())).thenReturn(nested)
        whenever(browserProtocol.querySelector(30, "iframe#inner")).thenReturn(40)
        whenever(browserProtocol.describeNode(40, null, null, null, null))
            .thenReturn(elementNode(40, "IFRAME", contentDocument = documentNode(50, frameId = "d1")))
        val manager = FrameManager(browserProtocol)
        manager.switch("payframe")

        val switched = manager.switch("iframe#inner")

        assertEquals("d1", switched.id)
        assertEquals("d1", manager.activeFrameId)
        assertTrue(switched.active)
    }

    @Test
    @DisplayName("nested frame switching self-heals when the cached document node id went stale")
    fun nestedSwitchSelfHealsStaleCachedDocumentNode() {
        runBlocking {
            stubTree()
            // First tree stamps c1's document as node 30; after the tree was
            // renumbered, the same frame resolves to document node 31 (which now
            // hosts the iframe#inner element, node 40, owning frame d1).
            whenever(browserProtocol.getDocument(any(), any()))
                .thenReturn(
                    payFrameDom(),
                    documentNode(
                        31, frameId = "c1",
                        children = listOf(
                            elementNode(
                                32, "BODY",
                                children = listOf(
                                    elementNode(
                                        40, "IFRAME",
                                        contentDocument = documentNode(50, frameId = "d1")
                                    )
                                )
                            )
                        )
                    )
                )
            whenever(browserProtocol.querySelector(30, "iframe#inner")).thenThrow(
                CDPReturnError(
                    errorCode = -32000,
                    errorMessage = "Could not find node with given id",
                    message = "Could not find node with given id"
                )
            )
            whenever(browserProtocol.querySelector(31, "iframe#inner")).thenReturn(40)
            whenever(browserProtocol.describeNode(40, null, null, null, null))
                .thenReturn(elementNode(40, "IFRAME", contentDocument = documentNode(50, frameId = "d1")))
            val manager = FrameManager(browserProtocol)
            manager.switch("payframe")

            val switched = manager.switch("iframe#inner")

            assertEquals("d1", switched.id)
            assertEquals("d1", manager.activeFrameId)
            verify(browserProtocol).querySelector(30, "iframe#inner")
            verify(browserProtocol).querySelector(31, "iframe#inner")
        }
    }

    @Test
    @DisplayName("list clears a stale scope whose frame is no longer in the frame tree")
    fun listClearsScopeWhenSelectedFrameDisappears() = runBlocking {
        stubTree()
        val manager = FrameManager(browserProtocol)
        manager.switch("payframe")
        assertEquals("c1", manager.activeFrameId)

        // The page changed: the pay frame (c1) is gone, only main + other remain.
        val newTree = FrameTree(
            frame = frame("m1", url = "http://fixture.test/main"),
            childFrames = listOf(
                FrameTree(frame = frame("c2", parentId = "m1", name = "otherframe", url = "http://fixture.test/other"))
            ),
        )
        whenever(browserProtocol.getFrameTree()).thenReturn(newTree)

        val frames = manager.list()

        assertNull(manager.activeFrameId)
        assertTrue(frames.first { it.id == "m1" }.active)
        assertTrue(frames.none { it.active && it.id != "m1" })
    }

    @Test
    @DisplayName("activeFrame stores the active-marked copy returned by switch")
    fun activeFrameStoresMarkedCopy() = runBlocking {
        stubTree()
        val manager = FrameManager(browserProtocol)

        manager.switch("payframe")

        assertEquals("c1", manager.activeFrameId)
        assertTrue(manager.activeFrame!!.active)
        assertEquals("payframe", manager.activeFrame!!.label)
    }
}
