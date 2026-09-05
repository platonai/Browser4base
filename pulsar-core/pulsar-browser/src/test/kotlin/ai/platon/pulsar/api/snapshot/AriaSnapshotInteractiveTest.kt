package ai.platon.pulsar.api.snapshot

import ai.platon.pulsar.api.model.MergedDOMTreeNode
import ai.platon.pulsar.api.model.NanoDOMTreeNode
import ai.platon.pulsar.api.model.NodeType
import ai.platon.pulsar.api.model.OptimizedDOMTreeNode
import ai.platon.pulsar.chrome.dom.model.AriaSnapshotOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Regression tests for Browser4base issue #3: the `interactive` filter must not
 * treat addressability (a backendNodeId/locator-based `ref`) as an interactivity
 * signal, and both renderers must share the same predicate.
 */
class AriaSnapshotInteractiveTest {

    private fun interactiveOptions() = AriaSnapshotOptions(interactive = true, boxes = false)
    private fun fullOptions() = AriaSnapshotOptions(interactive = false, compact = false, boxes = false)

    // ------------------------------------------------------------------ helpers

    private fun rolesOf(snapshot: String): List<String> {
        return snapshot.lineSequence()
            .map { it.trimStart() }
            .filter { it.startsWith("- ") }
            .mapNotNull { line ->
                val key = line.removePrefix("- ").trim()
                val role = key.substringBefore(' ').substringBefore(':').trim()
                role.takeIf { it.isNotEmpty() && !it.startsWith("/") }
            }
            .toList()
    }

    private val structuralRoles = setOf(
        "heading", "paragraph", "article", "listitem", "section", "region", "generic",
        "list", "banner", "contentinfo", "complementary", "group", "table", "main", "navigation"
    )

    private fun assertNoStructuralRoles(snapshot: String) {
        val roles = rolesOf(snapshot).filter { it != "text" }.toSet()
        val leaked = roles.intersect(structuralRoles)
        assertTrue(
            leaked.isEmpty(),
            "Interactive snapshot must not contain structural roles $leaked | snapshot:\n$snapshot"
        )
    }

    private fun ariaText(value: String): MergedDOMTreeNode = MergedDOMTreeNode(
        nodeName = "#text",
        nodeValue = value,
        nodeType = NodeType.TEXT_NODE
    )

    private fun ariaElement(
        tag: String,
        attrs: Map<String, String> = emptyMap(),
        backendNodeId: Int? = null,
        isInteractable: Boolean? = null,
        text: String? = null,
        children: List<OptimizedDOMTreeNode> = emptyList()
    ): OptimizedDOMTreeNode {
        val textNodes = text?.let { listOf(ariaText(it)) } ?: emptyList()
        val original = MergedDOMTreeNode(
            nodeId = backendNodeId ?: 0,
            backendNodeId = backendNodeId,
            nodeName = tag,
            nodeValue = "",
            attributes = attrs,
            isInteractable = isInteractable,
            children = textNodes
        )
        return OptimizedDOMTreeNode(originalNode = original, children = children)
    }

    private fun nanoText(value: String): NanoDOMTreeNode = NanoDOMTreeNode(
        nodeName = "#text",
        nodeValue = value
    )

    private fun nanoElement(
        tag: String,
        attrs: Map<String, Any> = emptyMap(),
        refId: Int = 0,
        interactive: Boolean? = null,
        text: String? = null,
        children: List<NanoDOMTreeNode> = emptyList()
    ): NanoDOMTreeNode {
        val textNodes = text?.let { listOf(nanoText(it)) } ?: emptyList()
        return NanoDOMTreeNode(
            locator = if (refId > 0) "0,$refId" else null,
            nodeName = tag,
            nodeValue = "",
            attributes = attrs,
            interactive = interactive,
            children = textNodes + children
        )
    }

    /**
     * A mixed page fragment: structural noise plus interactive controls, all carrying
     * backend node ids / locators (as real CDP trees do).
     */
    private fun ariaMixedTree(): OptimizedDOMTreeNode {
        return ariaElement(
            "div",
            backendNodeId = 1,
            children = listOf(
                ariaElement("h1", backendNodeId = 2, text = "Title"),
                ariaElement(
                    "p",
                    attrs = mapOf("role" to "paragraph", "aria-label" to "Body"),
                    backendNodeId = 3,
                    text = "Body"
                ),
                ariaElement("li", backendNodeId = 4, text = "Item"),
                ariaElement("article", backendNodeId = 5, text = "Story"),
                ariaElement(
                    "ul",
                    backendNodeId = 6,
                    children = listOf(ariaElement("li", backendNodeId = 7, text = "Entry"))
                ),
                ariaElement("button", backendNodeId = 8, text = "Go"),
                ariaElement(
                    "a",
                    attrs = mapOf("href" to "https://example.com", "aria-label" to "Docs"),
                    backendNodeId = 9,
                    text = "Docs"
                ),
                ariaElement(
                    "input",
                    attrs = mapOf("type" to "checkbox", "aria-label" to "Accept"),
                    backendNodeId = 10
                )
            )
        )
    }

    private fun nanoMixedTree(): NanoDOMTreeNode {
        return nanoElement(
            "div",
            refId = 1,
            children = listOf(
                nanoElement("h1", refId = 2, text = "Title"),
                nanoElement("p", attrs = mapOf("role" to "paragraph"), refId = 3, text = "Body"),
                nanoElement("li", refId = 4, text = "Item"),
                nanoElement("article", refId = 5, text = "Story"),
                nanoElement(
                    "ul",
                    refId = 6,
                    children = listOf(nanoElement("li", refId = 7, text = "Entry"))
                ),
                nanoElement("button", refId = 8, text = "Go"),
                nanoElement(
                    "a",
                    attrs = mapOf("href" to "https://example.com"),
                    refId = 9,
                    text = "Docs"
                ),
                nanoElement(
                    "input",
                    attrs = mapOf("type" to "checkbox", "aria-label" to "Accept"),
                    refId = 10
                )
            )
        )
    }

    // ------------------------------------------------------- shared contract

    @Test
    @DisplayName("shared predicate: role widgets and interactable flags qualify, structural roles and refs do not")
    fun sharedPredicateQualifiesRoleWidgetsAndInteractableFlagsOnly() {
        assertTrue(AriaSnapshotFiltering.isInteractiveNode("button", null))
        assertTrue(AriaSnapshotFiltering.isInteractiveNode("textbox", null))
        assertTrue(AriaSnapshotFiltering.isInteractiveNode("generic", true))
        assertFalse(AriaSnapshotFiltering.isInteractiveNode("generic", null))
        assertFalse(AriaSnapshotFiltering.isInteractiveNode("heading", null))
        assertFalse(AriaSnapshotFiltering.isInteractiveNode("paragraph", null))
        assertFalse(AriaSnapshotFiltering.isInteractiveNode("listitem", null))
        assertFalse(AriaSnapshotFiltering.isInteractiveNode("article", null))
        assertFalse(AriaSnapshotFiltering.isInteractiveNode("region", null))
    }

    // ------------------------------------------------------------ full renderer

    @Test
    @DisplayName("full renderer: interactive mode filters structural nodes even when refs are present")
    fun fullRendererInteractiveModeFiltersStructuralNodesEvenWithRefs() {
        val snapshot = AriaSnapshotRenderer.render(ariaMixedTree(), interactiveOptions())

        assertTrue(snapshot.contains("button \"Go\" [ref=e8]"), "Button should be kept: $snapshot")
        assertTrue(snapshot.contains("link \"Docs\" [ref=e9]"), "Link should be kept: $snapshot")
        assertTrue(snapshot.contains("- /url: https://example.com"), "Link URL should be kept: $snapshot")
        assertTrue(snapshot.contains("checkbox \"Accept\" [ref=e10]"), "Checkbox should be kept: $snapshot")
        assertNoStructuralRoles(snapshot)
    }

    @Test
    @DisplayName("full renderer: non-interactive mode keeps structural nodes (filter is interactive-only)")
    fun fullRendererNonInteractiveModeKeepsStructuralNodes() {
        val snapshot = AriaSnapshotRenderer.render(ariaMixedTree(), fullOptions())

        assertTrue(snapshot.contains("heading \"Title\" [ref=e2]"), "Heading should be kept: $snapshot")
        assertTrue(snapshot.contains("paragraph \"Body\" [ref=e3]"), "Paragraph should be kept: $snapshot")
        assertTrue(snapshot.contains("listitem \"Item\" [ref=e4]"), "Listitem should be kept: $snapshot")
        assertTrue(snapshot.contains("article \"Story\" [ref=e5]"), "Article should be kept: $snapshot")
        assertTrue(snapshot.contains("button \"Go\" [ref=e8]"), "Button should be kept: $snapshot")
    }

    @Test
    @DisplayName("full renderer: a plain div with a backendNodeId must not survive interactive mode")
    fun fullRendererPlainDivWithBackendNodeIdDoesNotSurviveInteractiveMode() {
        val root = ariaElement("div", backendNodeId = 55, text = "Plain container")

        val interactive = AriaSnapshotRenderer.render(root, interactiveOptions())
        assertTrue(interactive.isBlank(), "Nothing should survive: $interactive")

        val full = AriaSnapshotRenderer.render(root, fullOptions())
        assertTrue(full.contains("generic \"Plain container\" [ref=e55]"), "Default mode keeps the div: $full")
    }

    @Test
    @DisplayName("full renderer: non-interactive container is dropped, interactive descendants are promoted")
    fun fullRendererPromotesInteractiveDescendantsOfSkippedContainers() {
        val root = ariaElement(
            "div",
            backendNodeId = 1,
            children = listOf(
                ariaElement(
                    "div",
                    backendNodeId = 2,
                    children = listOf(
                        ariaElement("button", backendNodeId = 3, text = "Go")
                    )
                )
            )
        )

        val snapshot = AriaSnapshotRenderer.render(root, interactiveOptions())

        assertTrue(snapshot.contains("button \"Go\" [ref=e3]"), "Button should be promoted: $snapshot")
        assertNoStructuralRoles(snapshot)
    }

    @Test
    @DisplayName("full renderer: interactable generic (cursor:pointer) survives interactive and compact mode")
    fun fullRendererKeepsInteractableGenericNode() {
        val root = ariaElement(
            "div",
            backendNodeId = 1,
            children = listOf(
                ariaElement("div", backendNodeId = 2, isInteractable = true, text = "Clickable area")
            )
        )

        // Default options: interactive = true, compact = true. Compact must not swallow
        // a node the interactive filter kept.
        val snapshot = AriaSnapshotRenderer.render(
            root,
            AriaSnapshotOptions(interactive = true, boxes = false)
        )

        assertTrue(
            snapshot.contains("generic \"Clickable area\" [ref=e2] [cursor=pointer]"),
            "Interactable generic should be kept with cursor marker: $snapshot"
        )
    }

    @Test
    @DisplayName("full renderer: input type matrix maps to interactive widget roles")
    fun fullRendererInputTypeMatrix() {
        val types = mapOf(
            "text" to "textbox",
            "checkbox" to "checkbox",
            "radio" to "radio",
            "search" to "searchbox",
            "range" to "slider",
            "number" to "spinbutton",
            "submit" to "button"
        )
        val root = ariaElement(
            "div",
            backendNodeId = 1,
            children = types.keys.mapIndexed { index, type ->
                ariaElement(
                    "input",
                    attrs = mapOf("type" to type, "aria-label" to "Field $type"),
                    backendNodeId = index + 2
                )
            }
        )

        val snapshot = AriaSnapshotRenderer.render(root, interactiveOptions())
        val roles = rolesOf(snapshot).toSet()

        types.values.forEach { expectedRole ->
            assertTrue(roles.contains(expectedRole), "Expected role $expectedRole in: $snapshot")
        }
        assertNoStructuralRoles(snapshot)
    }

    // ------------------------------------------------------------ nano renderer

    @Test
    @DisplayName("nano renderer: interactive mode filters structural nodes even when locator refs are present")
    fun nanoRendererInteractiveModeFiltersStructuralNodesEvenWithRefs() {
        val snapshot = NanoAriaSnapshotRenderer.render(nanoMixedTree(), interactiveOptions())

        assertTrue(snapshot.contains("button \"Go\" [ref=e8]"), "Button should be kept: $snapshot")
        assertTrue(snapshot.contains("link \"Docs\" [ref=e9]"), "Link should be kept: $snapshot")
        assertTrue(snapshot.contains("- /url: https://example.com"), "Link URL should be kept: $snapshot")
        assertTrue(snapshot.contains("checkbox \"Accept\" [ref=e10]"), "Checkbox should be kept: $snapshot")
        assertNoStructuralRoles(snapshot)
    }

    @Test
    @DisplayName("nano renderer: non-interactive mode keeps structural nodes (filter is interactive-only)")
    fun nanoRendererNonInteractiveModeKeepsStructuralNodes() {
        val snapshot = NanoAriaSnapshotRenderer.render(nanoMixedTree(), fullOptions())

        assertTrue(snapshot.contains("heading \"Title\" [ref=e2]"), "Heading should be kept: $snapshot")
        assertTrue(snapshot.contains("paragraph \"Body\" [ref=e3]"), "Paragraph should be kept: $snapshot")
        assertTrue(snapshot.contains("listitem \"Item\" [ref=e4]"), "Listitem should be kept: $snapshot")
        assertTrue(snapshot.contains("article \"Story\" [ref=e5]"), "Article should be kept: $snapshot")
        assertTrue(snapshot.contains("button \"Go\" [ref=e8]"), "Button should be kept: $snapshot")
    }

    @Test
    @DisplayName("nano renderer: a plain div with a locator ref must not survive interactive mode")
    fun nanoRendererPlainDivWithLocatorRefDoesNotSurviveInteractiveMode() {
        val root = nanoElement("div", refId = 55, text = "Plain container")

        val interactive = NanoAriaSnapshotRenderer.render(root, interactiveOptions())
        assertTrue(interactive.isBlank(), "Nothing should survive: $interactive")

        val full = NanoAriaSnapshotRenderer.render(root, fullOptions())
        assertTrue(full.contains("generic \"Plain container\" [ref=e55]"), "Default mode keeps the div: $full")
    }

    @Test
    @DisplayName("nano renderer: non-interactive container is dropped, interactive descendants are promoted")
    fun nanoRendererPromotesInteractiveDescendantsOfSkippedContainers() {
        val root = nanoElement(
            "div",
            refId = 1,
            children = listOf(
                nanoElement(
                    "div",
                    refId = 2,
                    children = listOf(
                        nanoElement("button", refId = 3, text = "Go")
                    )
                )
            )
        )

        val snapshot = NanoAriaSnapshotRenderer.render(root, interactiveOptions())

        assertTrue(snapshot.contains("button \"Go\" [ref=e3]"), "Button should be promoted: $snapshot")
        assertNoStructuralRoles(snapshot)
    }

    @Test
    @DisplayName("nano renderer: interactable generic (cursor:pointer) survives interactive and compact mode")
    fun nanoRendererKeepsInteractableGenericNode() {
        val root = nanoElement(
            "div",
            refId = 1,
            children = listOf(
                nanoElement("div", refId = 2, interactive = true, text = "Clickable area")
            )
        )

        val snapshot = NanoAriaSnapshotRenderer.render(
            root,
            AriaSnapshotOptions(interactive = true, boxes = false)
        )

        assertTrue(
            snapshot.contains("generic \"Clickable area\" [ref=e2] [cursor=pointer]"),
            "Interactable generic should be kept with cursor marker: $snapshot"
        )
    }

    @Test
    @DisplayName("nano renderer: input type matrix maps to interactive widget roles")
    fun nanoRendererInputTypeMatrix() {
        val types = mapOf(
            "text" to "textbox",
            "checkbox" to "checkbox",
            "radio" to "radio",
            "search" to "searchbox",
            "range" to "slider",
            "number" to "spinbutton",
            "submit" to "button"
        )
        val root = nanoElement(
            "div",
            refId = 1,
            children = types.keys.mapIndexed { index, type ->
                nanoElement(
                    "input",
                    attrs = mapOf("type" to type, "aria-label" to "Field $type"),
                    refId = index + 2
                )
            }
        )

        val snapshot = NanoAriaSnapshotRenderer.render(root, interactiveOptions())
        val roles = rolesOf(snapshot).toSet()

        types.values.forEach { expectedRole ->
            assertTrue(roles.contains(expectedRole), "Expected role $expectedRole in: $snapshot")
        }
        assertNoStructuralRoles(snapshot)
    }

    // ------------------------------------------------------ cross-renderer parity

    @Test
    @DisplayName("both renderers produce identical interactive-mode output for equivalent trees")
    fun renderersAgreeOnInteractiveModeForEquivalentTrees() {
        val options = AriaSnapshotOptions(interactive = true, boxes = false)

        val ariaRoot = ariaElement(
            "div",
            backendNodeId = 1,
            children = listOf(
                ariaElement("h1", backendNodeId = 2, text = "Title"),
                ariaElement(
                    "a",
                    attrs = mapOf("href" to "https://example.com", "aria-label" to "Docs"),
                    backendNodeId = 3,
                    text = "Docs"
                ),
                ariaElement("button", backendNodeId = 4, text = "Go"),
                ariaElement(
                    "input",
                    attrs = mapOf("type" to "checkbox", "aria-label" to "Accept"),
                    backendNodeId = 5
                ),
                ariaElement("div", backendNodeId = 6, isInteractable = true, text = "Clickable area")
            )
        )
        val nanoRoot = nanoElement(
            "div",
            refId = 1,
            children = listOf(
                nanoElement("h1", refId = 2, text = "Title"),
                nanoElement(
                    "a",
                    attrs = mapOf("href" to "https://example.com", "aria-label" to "Docs"),
                    refId = 3,
                    text = "Docs"
                ),
                nanoElement("button", refId = 4, text = "Go"),
                nanoElement(
                    "input",
                    attrs = mapOf("type" to "checkbox", "aria-label" to "Accept"),
                    refId = 5
                ),
                nanoElement("div", refId = 6, interactive = true, text = "Clickable area")
            )
        )

        val full = AriaSnapshotRenderer.render(ariaRoot, options)
        val nano = NanoAriaSnapshotRenderer.render(nanoRoot, options)

        assertEquals(full, nano, "Both renderers must agree on interactive-mode output")
        assertFalse(full.contains("heading"), "Heading must be filtered: $full")
        assertTrue(
            full.contains("generic \"Clickable area\" [ref=e6] [cursor=pointer]"),
            "Interactable generic must be kept: $full"
        )
    }
}
