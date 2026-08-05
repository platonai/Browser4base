package ai.platon.pulsar.api.dom

import ai.platon.pulsar.api.model.CleanedDOMTreeNode
import ai.platon.pulsar.api.model.CompactRect
import ai.platon.pulsar.api.model.SerializableDOMTreeNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class InteractiveNodeListBuilderTest {

    @Test
    @DisplayName("collects interactive nodes ordered by index with links")
    fun collectsInteractiveNodesOrderedByIndex() {
        val builder = builder(
            interactive(nodeId = 10, index = 1, nodeName = "BUTTON", attrs = mapOf("id" to "b1")),
            interactive(nodeId = 12, index = 2, nodeName = "INPUT", attrs = mapOf("type" to "text")),
            interactive(nodeId = 13, index = 3, nodeName = "A", attrs = mapOf("href" to "/x")),
        )

        val nodes = builder.build().nodes

        assertEquals(3, nodes.size)
        assertEquals(listOf(1, 2, 3), nodes.map { it.interactiveIndex })
        assertEquals(listOf("10", "12", "13"), nodes.map { it.backendNodeId })
        assertEquals(null, nodes[0].prevInteractiveIndex)
        assertEquals(2, nodes[0].nextInteractiveIndex)
        assertEquals(1, nodes[1].prevInteractiveIndex)
        assertEquals(3, nodes[1].nextInteractiveIndex)
        assertEquals(2, nodes[2].prevInteractiveIndex)
        assertEquals(null, nodes[2].nextInteractiveIndex)
    }

    @Test
    @DisplayName("textBefore joins non-interactive text between nodes")
    fun textBeforeJoinsNonInteractiveText() {
        val builder = builder(
            interactive(nodeId = 10, index = 1, nodeName = "BUTTON"),
            text(nodeId = 11, value = "hello"),
            interactive(nodeId = 12, index = 2, nodeName = "INPUT"),
            interactive(nodeId = 13, index = 3, nodeName = "A"),
        )

        val nodes = builder.build().nodes

        assertNull(nodes[0].textBefore)
        assertEquals("hello", nodes[1].textBefore)
        assertNull(nodes[2].textBefore)
    }

    @Test
    @DisplayName("slimHTML renders self-closing and text nodes with attributes")
    fun slimHtmlRendersAttributes() {
        val withId = cleaned(nodeId = 20, nodeName = "BUTTON", attrs = mapOf("id" to "b1"))
        val withText = cleaned(
            nodeId = 21,
            nodeName = "SPAN",
            nodeValue = "text",
            attrs = mapOf("id" to "a b"),
        )

        assertEquals("<BUTTON id=b1 />", InteractiveNodeListBuilder.slimHTML(SerializableDOMTreeNode(originalNode = withId)))
        assertEquals("<SPAN id='a b'>text</SPAN>", InteractiveNodeListBuilder.slimHTML(SerializableDOMTreeNode(originalNode = withText)))
    }

    @Test
    @DisplayName("non-interactive only trees produce an empty list")
    fun nonInteractiveOnlyTreesProduceEmptyList() {
        val builder = builder(text(nodeId = 30, value = "only text"))

        assertEquals(0, builder.build().nodes.size)
    }

    @Test
    @DisplayName("viewport filtering drops other viewports unless all requested")
    fun viewportFiltering() {
        val filtered = InteractiveNodeListBuilder(
            root = SerializableDOMTreeNode(
                children = listOf(
                    interactive(nodeId = 40, index = 1, viewportIndex = 0),
                    interactive(nodeId = 41, index = 2, viewportIndex = 2),
                ),
            ),
            includeAllViewports = false,
            currentViewportIndex = 0,
            lastViewportIndex = 10000,
        ).build()
        assertEquals(listOf(0), filtered.nodes.map { it.viewportIndex })

        val all = SerializableDOMTreeNode(
            children = listOf(
                interactive(nodeId = 40, index = 1, viewportIndex = 0),
                interactive(nodeId = 41, index = 2, viewportIndex = 2),
            ),
        ).buildInteractiveNodeList()
        assertEquals(listOf(0, 2), all.nodes.map { it.viewportIndex })
    }

    @Test
    @DisplayName("estimatedSize sums node payloads")
    fun estimatedSizeSumsNodePayloads() {
        val nodes = builder(
            interactive(nodeId = 50, index = 1, nodeName = "BUTTON", attrs = mapOf("id" to "b1")),
            interactive(nodeId = 51, index = 2, nodeName = "INPUT"),
        ).build().nodes

        val expected = nodes.sumOf {
            "[0,812]{1}(369,1659,87,13)".length + (it.textBefore?.length ?: 0) + (it.slimHTML?.length ?: 0)
        }
        assertEquals(expected, InteractiveNodeListBuilder.estimatedSize(nodes))
        assertTrue(expected > 0)
    }

    private fun builder(vararg nodes: SerializableDOMTreeNode) =
        InteractiveNodeListBuilder(SerializableDOMTreeNode(children = nodes.toList()))

    private fun interactive(
        nodeId: Int,
        index: Int,
        nodeName: String = "DIV",
        attrs: Map<String, Any>? = null,
        viewportIndex: Int? = 0,
    ) = SerializableDOMTreeNode(
        interactiveIndex = index,
        originalNode = cleaned(
            nodeId = nodeId,
            nodeName = nodeName,
            attrs = attrs,
            interactiveIndex = index,
            viewportIndex = viewportIndex,
            bounds = CompactRect(10.0, 20.0, 100.0, 50.0),
        ),
    )

    private fun text(nodeId: Int, value: String) = SerializableDOMTreeNode(
        originalNode = cleaned(
            nodeId = nodeId,
            nodeName = "#text",
            nodeValue = value,
            nodeType = 3,
            interactiveIndex = null,
        ),
    )

    private fun cleaned(
        nodeId: Int,
        nodeName: String,
        nodeValue: String? = null,
        attrs: Map<String, Any>? = null,
        nodeType: Int = 1,
        interactiveIndex: Int? = null,
        viewportIndex: Int? = null,
        bounds: CompactRect? = null,
    ) = CleanedDOMTreeNode(
        locator = "0,$nodeId",
        frameId = null,
        xpath = null,
        elementHash = null,
        nodeId = nodeId,
        backendNodeId = nodeId,
        nodeType = nodeType,
        nodeName = nodeName,
        nodeValue = nodeValue,
        attributes = attrs,
        sessionId = null,
        isScrollable = null,
        isVisible = null,
        isInteractable = null,
        interactiveIndex = interactiveIndex,
        clientRects = null,
        scrollRects = null,
        bounds = bounds,
        absoluteBounds = null,
        viewportIndex = viewportIndex,
        paintOrder = null,
        stackingContexts = null,
        contentDocument = null,
    )
}
