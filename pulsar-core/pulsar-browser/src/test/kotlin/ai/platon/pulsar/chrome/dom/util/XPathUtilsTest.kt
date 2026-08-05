package ai.platon.pulsar.chrome.dom.util

import ai.platon.pulsar.api.model.MergedDOMTreeNode
import ai.platon.pulsar.api.model.NodeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class XPathUtilsTest {

    @Test
    @DisplayName("non-element nodes produce an empty xpath")
    fun nonElementNodesProduceEmptyXpath() {
        val text = node(1, "#text", nodeType = NodeType.TEXT_NODE)

        assertEquals("", XPathUtils.generateXPath(text))
    }

    @Test
    @DisplayName("builds path from ancestors and target with id predicate")
    fun buildsPathWithIdPredicate() {
        val html = node(10, "HTML")
        val body = node(11, "BODY")
        val div = node(12, "DIV", mapOf("id" to "main"))

        assertEquals("/html/body/div[@id='main']", XPathUtils.generateXPath(div, listOf(html, body)))
    }

    @Test
    @DisplayName("uses 1-based sibling index when siblings share a tag")
    fun usesOneBasedSiblingIndex() {
        val ul = node(20, "UL")
        val li1 = node(21, "LI")
        val li2 = node(22, "LI")
        val li3 = node(23, "LI")
        val siblings = mapOf(20 to listOf(li1, li2, li3))

        assertEquals("/ul/li", XPathUtils.generateXPath(li1, listOf(ul), siblings))
        assertEquals("/ul/li[3]", XPathUtils.generateXPath(li3, listOf(ul), siblings))
    }

    @Test
    @DisplayName("stops at iframe boundary")
    fun stopsAtIframeBoundary() {
        val html = node(30, "HTML")
        val body = node(31, "BODY")
        val iframe = node(32, "IFRAME")
        val div = node(33, "DIV")

        assertEquals("/html/body/iframe/div", XPathUtils.generateXPath(div, listOf(html, body, iframe)))
    }

    @Test
    @DisplayName("keeps shadow host in path and formats shadow slots with name")
    fun shadowDomHandling() {
        val host = node(40, "DIV", shadowRoots = listOf(node(41, "#SHADOW-ROOT")))
        val slot = node(42, "SLOT", mapOf("name" to "title"))
        val span = node(43, "SPAN")

        assertEquals("/div/slot[@name='title']/span", XPathUtils.generateXPath(span, listOf(host, slot)))
    }

    @Test
    @DisplayName("renders template ancestors as plain template segment")
    fun templateHandling() {
        val body = node(50, "BODY")
        val template = node(51, "TEMPLATE")
        val div = node(52, "DIV")

        assertEquals("/body/template/div", XPathUtils.generateXPath(div, listOf(body, template)))
    }

    @Test
    @DisplayName("custom elements use name attribute when available")
    fun customElementUsesNameAttribute() {
        val body = node(60, "BODY")
        val widget = node(61, "MY-WIDGET", mapOf("name" to "editor"))

        assertEquals("/body/my-widget[@name='editor']", XPathUtils.generateXPath(widget, listOf(body)))
    }

    @Test
    @DisplayName("generates simple xpath using ids only and skips non-elements")
    fun simpleXpathUsesIdsAndSkipsNonElements() {
        val html = node(70, "HTML")
        val text = node(71, "#text", nodeType = NodeType.TEXT_NODE)
        val div = node(72, "DIV", mapOf("id" to "main"))

        assertEquals("/html/div[@id='main']", XPathUtils.generateSimpleXPath(div, listOf(html, text)))
    }

    @Test
    @DisplayName("caches repeated xpath computations")
    fun cachesRepeatedXpathComputations() {
        val html = node(80, "HTML")
        val body = node(81, "BODY")
        val div = node(82, "DIV", mapOf("id" to "cached"))
        val expected = "/html/body/div[@id='cached']"

        assertEquals(expected, XPathUtils.generateXPath(div, listOf(html, body)))
        assertEquals(expected, XPathUtils.generateXPath(div, listOf(html, body)))
    }

    private fun node(
        nodeId: Int,
        nodeName: String,
        attributes: Map<String, String> = emptyMap(),
        nodeType: NodeType = NodeType.ELEMENT_NODE,
        shadowRoots: List<MergedDOMTreeNode> = emptyList(),
    ) = MergedDOMTreeNode(
        nodeId = nodeId,
        nodeName = nodeName,
        attributes = attributes,
        nodeType = nodeType,
        shadowRoots = shadowRoots,
    )
}
