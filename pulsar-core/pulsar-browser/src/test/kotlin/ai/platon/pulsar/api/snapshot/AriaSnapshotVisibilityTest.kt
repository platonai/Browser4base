package ai.platon.pulsar.api.snapshot

import ai.platon.pulsar.api.model.AXNodeEx
import ai.platon.pulsar.api.model.MergedDOMTreeNode
import ai.platon.pulsar.api.model.NanoDOMTreeNode
import ai.platon.pulsar.api.model.NodeType
import ai.platon.pulsar.api.model.OptimizedDOMTreeNode
import ai.platon.pulsar.api.model.SnapshotNodeEx
import ai.platon.pulsar.chrome.dom.DOMStateBuilder
import ai.platon.pulsar.chrome.dom.model.AriaSnapshotOptions
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Regression tests for Browser4base issue #4: snapshot serialization must be
 * CSS-visibility-aware.
 *
 * - `visibility:hidden` descendants must never leak into snapshot output
 *   (element names, text items, grep matches).
 * - A `:hover`-revealed (visible) tooltip must be discoverable in the snapshot —
 *   as its own node/text, so "verify the tooltip appeared" works with snapshot
 *   commands.
 */
class AriaSnapshotVisibilityTest {

    private fun fullOptions() = AriaSnapshotOptions(interactive = false, compact = false, boxes = false)

    private fun ariaText(value: String): MergedDOMTreeNode = MergedDOMTreeNode(
        nodeName = "#text",
        nodeValue = value,
        nodeType = NodeType.TEXT_NODE
    )

    private fun ariaElement(
        tag: String,
        attrs: Map<String, String> = emptyMap(),
        backendNodeId: Int? = null,
        isVisible: Boolean? = null,
        computedStyles: Map<String, String>? = null,
        axName: String? = null,
        text: String? = null,
        children: List<MergedDOMTreeNode> = emptyList()
    ): MergedDOMTreeNode {
        val textNodes = text?.let { listOf(ariaText(it)) } ?: emptyList()
        return MergedDOMTreeNode(
            nodeId = backendNodeId ?: 0,
            backendNodeId = backendNodeId,
            nodeName = tag,
            nodeValue = "",
            attributes = attrs,
            isVisible = isVisible,
            snapshotNode = computedStyles?.let { SnapshotNodeEx(computedStyles = it) },
            axNode = axName?.let { AXNodeEx(axNodeId = "ax${backendNodeId ?: 0}", role = null, name = it) },
            children = textNodes + children
        )
    }

    /**
     * The tooltip fixture pattern from `interactive-5.html`: a term container
     * holds its own visible text plus a CSS-hidden tooltip span whose text must
     * never surface while hidden. The hidden/visible state mirrors the computed
     * styles Chrome reports for `visibility:hidden` vs `:hover`-revealed tooltips.
     */
    private fun tooltipPage(tooltipVisible: Boolean): OptimizedDOMTreeNode {
        val tooltip = ariaElement(
            "span",
            backendNodeId = 5,
            isVisible = tooltipVisible,
            computedStyles = mapOf(
                "display" to "inline",
                "visibility" to if (tooltipVisible) "visible" else "hidden"
            ),
            text = "A hierarchical representation of the page that assistive technologies use to navigate content."
        )
        val container = ariaElement(
            "span",
            backendNodeId = 4,
            isVisible = true,
            computedStyles = mapOf("display" to "inline", "visibility" to "visible"),
            text = "Accessibility Tree",
            children = listOf(tooltip)
        )
        val paragraph = ariaElement(
            "p",
            backendNodeId = 3,
            isVisible = true,
            computedStyles = mapOf("display" to "block", "visibility" to "visible"),
            children = listOf(container)
        )
        return OptimizedDOMTreeNode(
            originalNode = ariaElement(
                "div",
                backendNodeId = 2,
                isVisible = true,
                computedStyles = mapOf("display" to "block", "visibility" to "visible"),
                children = listOf(paragraph)
            ),
            children = listOf(
                OptimizedDOMTreeNode(
                    originalNode = paragraph,
                    children = listOf(
                        OptimizedDOMTreeNode(
                            originalNode = container,
                            children = listOf(OptimizedDOMTreeNode(originalNode = tooltip))
                        )
                    )
                )
            )
        )
    }

    private fun nanoElement(
        tag: String,
        refId: Int = 0,
        invisible: Boolean? = null,
        text: String? = null,
        children: List<NanoDOMTreeNode> = emptyList()
    ): NanoDOMTreeNode {
        val textNodes = text?.let { listOf(NanoDOMTreeNode(nodeName = "#text", nodeValue = text)) } ?: emptyList()
        return NanoDOMTreeNode(
            locator = if (refId > 0) "0,$refId" else null,
            nodeName = tag,
            nodeValue = "",
            invisible = invisible,
            children = textNodes + children
        )
    }

    // ------------------------------------------------------------ full renderer

    @Test
    @DisplayName("full renderer: visibility:hidden tooltip text must not leak into names or output")
    fun fullRendererHidesCssHiddenTooltipText() {
        val snapshot = AriaSnapshotRenderer.render(tooltipPage(tooltipVisible = false), fullOptions())

        assertFalse(
            snapshot.contains("hierarchical representation"),
            "Hidden tooltip text must not appear anywhere in the snapshot:\n$snapshot"
        )
        // The visible term keeps its own name; the hidden tooltip text is not merged in.
        assertTrue(
            snapshot.contains("Accessibility Tree"),
            "The visible term text should still be present:\n$snapshot"
        )
        assertFalse(
            snapshot.contains("hierarchical representation") && snapshot.contains("generic"),
            "The hidden tooltip node itself must be pruned:\n$snapshot"
        )
    }

    @Test
    @DisplayName("full renderer: hover-revealed tooltip text is discoverable in the snapshot")
    fun fullRendererIncludesHoverRevealedTooltip() {
        val snapshot = AriaSnapshotRenderer.render(tooltipPage(tooltipVisible = true), fullOptions())

        assertTrue(
            snapshot.contains("hierarchical representation"),
            "A :hover-revealed tooltip must be visible in the snapshot so verification works:\n$snapshot"
        )
    }

    @Test
    @DisplayName("full renderer: content-derived AX names contaminated with hidden text are recomputed from visible text")
    fun fullRendererStripsHiddenTextFromContentDerivedAxNames() {
        // Chromium computes content-derived accessible names from the full subtree,
        // embedding visibility:hidden text (the root cause of the reported leak).
        val hiddenTooltipText =
            "A hierarchical representation of the page that assistive technologies use to navigate content."
        val container = ariaElement(
            "span",
            backendNodeId = 4,
            isVisible = true,
            computedStyles = mapOf("display" to "inline", "visibility" to "visible"),
            text = "Accessibility Tree",
            axName = "Accessibility Tree $hiddenTooltipText",
            children = listOf(
                ariaElement(
                    "span",
                    backendNodeId = 5,
                    isVisible = false,
                    computedStyles = mapOf("display" to "inline", "visibility" to "hidden"),
                    text = hiddenTooltipText
                )
            )
        )
        val paragraph = ariaElement(
            "p",
            backendNodeId = 3,
            isVisible = true,
            computedStyles = mapOf("display" to "block", "visibility" to "visible"),
            axName = "Accessibility Tree $hiddenTooltipText — used by screen readers to understand page structure.",
            children = listOf(container)
        )
        val optimized = OptimizedDOMTreeNode(
            originalNode = paragraph,
            children = listOf(OptimizedDOMTreeNode(originalNode = container))
        )

        val snapshot = AriaSnapshotRenderer.render(optimized, fullOptions())

        assertFalse(
            snapshot.contains("hierarchical representation"),
            "Contaminated AX names must be recomputed from visible text only:\n$snapshot"
        )
        assertTrue(snapshot.contains("Accessibility Tree"), "Visible term text must remain:\n$snapshot")
    }

    @Test
    @DisplayName("full renderer: external widget names (label/aria) survive hidden subtree content")
    fun fullRendererKeepsExternalWidgetNamesWithHiddenSubtreeContent() {
        // A widget whose name comes from an external source (<label>, aria-labelledby)
        // and has no own visible content must keep its AX name even when the subtree
        // contains CSS-hidden content.
        val button = ariaElement(
            "button",
            backendNodeId = 7,
            isVisible = true,
            computedStyles = mapOf("display" to "inline-block", "visibility" to "visible"),
            axName = "Save file",
            children = listOf(
                ariaElement(
                    "span",
                    backendNodeId = 8,
                    isVisible = false,
                    computedStyles = mapOf("display" to "none", "visibility" to "hidden"),
                    text = "Saves to disk"
                )
            )
        )
        val optimized = OptimizedDOMTreeNode(
            originalNode = ariaElement("div", backendNodeId = 1, isVisible = true),
            children = listOf(OptimizedDOMTreeNode(originalNode = button))
        )

        val snapshot = AriaSnapshotRenderer.render(optimized, fullOptions())

        assertTrue(
            snapshot.contains("button \"Save file\""),
            "Externally named widget must keep its AX name:\n$snapshot"
        )
        assertFalse(snapshot.contains("Saves to disk"), "Hidden content must stay hidden:\n$snapshot")
    }

    // ------------------------------------------------------------ nano renderer

    @Test
    @DisplayName("nano renderer: visibility:hidden tooltip subtree is pruned")
    fun nanoRendererPrunesCssHiddenTooltip() {
        val root = nanoElement(
            "div",
            refId = 1,
            children = listOf(
                nanoElement(
                    "span",
                    refId = 4,
                    text = "Accessibility Tree",
                    children = listOf(
                        nanoElement(
                            "span",
                            refId = 5,
                            invisible = true,
                            text = "A hierarchical representation of the page that assistive technologies use to navigate content."
                        )
                    )
                )
            )
        )

        val snapshot = NanoAriaSnapshotRenderer.render(root, fullOptions())

        assertFalse(
            snapshot.contains("hierarchical representation"),
            "Hidden tooltip text must not appear in the viewport snapshot:\n$snapshot"
        )
        assertTrue(snapshot.contains("Accessibility Tree"), "Visible term text must remain:\n$snapshot")
    }

    @Test
    @DisplayName("nano renderer: hover-revealed tooltip shows up as a node with its text")
    fun nanoRendererIncludesHoverRevealedTooltip() {
        val root = nanoElement(
            "div",
            refId = 1,
            children = listOf(
                nanoElement(
                    "span",
                    refId = 4,
                    text = "Accessibility Tree",
                    children = listOf(
                        nanoElement(
                            "span",
                            refId = 5,
                            invisible = false,
                            text = "A hierarchical representation of the page that assistive technologies use to navigate content."
                        )
                    )
                )
            )
        )

        val snapshot = NanoAriaSnapshotRenderer.render(root, fullOptions())

        assertTrue(
            snapshot.contains("hierarchical representation"),
            "A hover-revealed tooltip must be discoverable in the viewport snapshot:\n$snapshot"
        )
        assertTrue(
            snapshot.contains("[ref=e5]"),
            "The revealed tooltip should carry its own ref so it is addressable:\n$snapshot"
        )
    }

    // ------------------------------------------- serialization (viewport) path

    /**
     * End-to-end serialization check: a `visibility:hidden` node captured with
     * `isVisible = false` must keep that state in the nano tree used by viewport
     * snapshots, otherwise hidden content leaks into viewport output.
     */
    @Test
    @DisplayName("serialization: isVisible=false survives DOMStateBuilder so the nano tree can prune it")
    fun serializationPreservesHiddenStateForNanoTree() {
        val optimized = tooltipPage(tooltipVisible = false)
        val domState = DOMStateBuilder.build(optimized)

        val nanoTree = domState.serializableTree.toNanoTreeUnfiltered()
        val tooltipNode = findNanoNode(nanoTree) { n -> n.locator?.endsWith(",5") == true }

        assertTrue(tooltipNode != null, "Hidden tooltip should still be represented in the nano tree structure")
        assertTrue(
            tooltipNode?.invisible == true,
            "isVisible=false must be preserved as invisible=true on the nano node " +
                "(got ${tooltipNode?.invisible}), otherwise viewport snapshots leak hidden text"
        )
    }

    private fun findNanoNode(
        root: NanoDOMTreeNode,
        predicate: (NanoDOMTreeNode) -> Boolean
    ): NanoDOMTreeNode? {
        if (predicate(root)) return root
        root.children.orEmpty().forEach { child ->
            findNanoNode(child, predicate)?.let { return it }
        }
        return null
    }
}
