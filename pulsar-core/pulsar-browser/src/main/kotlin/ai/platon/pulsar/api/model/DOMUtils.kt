package ai.platon.pulsar.api.model

import ai.platon.pulsar.api.model.DefaultIncludeAttributes
import ai.platon.pulsar.api.model.MergedDOMTreeNode
import ai.platon.pulsar.api.model.NodeType

object DOMUtils {

    /**
     * Whether the node is hidden by CSS rendering rules (`display:none` or
     * `visibility:hidden`), judged from its own computed styles.
     *
     * Callers prune the node's whole subtree when this returns true, mirroring the
     * accessibility-tree contract: CSS-hidden content must not surface in snapshot
     * output at all. Because the computed `visibility` value already accounts for CSS
     * inheritance, an element that re-declares `visibility: visible` inside a hidden
     * ancestor is judged independently of that ancestor.
     *
     * Nodes without snapshot/style data (e.g. text nodes, or captures without
     * computed styles) are treated as visible, matching the pre-existing behavior.
     */
    fun isCssHidden(node: MergedDOMTreeNode): Boolean {
        val styles = node.snapshotNode?.computedStyles ?: return false
        return when {
            styles["display"]?.equals("none", ignoreCase = true) == true -> true
            styles["visibility"]?.equals("hidden", ignoreCase = true) == true -> true
            else -> false
        }
    }

    /**
     * Whether the subtree of [node] (including the node itself) contains any
     * CSS-hidden element. Used to decide whether an accessibility name computed
     * from contents can be trusted: Chromium embeds `visibility:hidden` text in
     * content-derived accessible names, so names of nodes that contain hidden
     * content must be recomputed from visible text only.
     */
    fun hasCssHiddenContent(node: MergedDOMTreeNode): Boolean {
        if (isCssHidden(node)) return true
        return node.children.any { hasCssHiddenContent(it) }
    }

    fun textContent(node: MergedDOMTreeNode, excludeCssHidden: Boolean = false): String {
        // CSS-hidden subtrees must never contribute text to snapshot names/output
        // (Browser4base issue #4): visibility:hidden tooltip text used to leak into
        // the aggregated names of visible ancestors.
        if (excludeCssHidden && node.nodeType != NodeType.TEXT_NODE && isCssHidden(node)) {
            return ""
        }

        val sb = StringBuilder()

        fun appendToken(s: String?) {
            val t = s?.trim()
            if (!t.isNullOrEmpty()) {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(t)
            }
        }

        when (node.nodeType) {
            NodeType.TEXT_NODE -> appendToken(node.nodeValue)
            else -> {
                // Prefer accessible name if present. In visibility-aware mode the
                // AX name is skipped: Chromium embeds `visibility:hidden` text in
                // content-derived accessible names, so they are not trustworthy for
                // aggregation, and the visible DOM text below already covers the
                // visible content.
                if (!excludeCssHidden) {
                    appendToken(node.axNode?.name)
                }
                // Include meaningful attributes
                if (node.attributes.isNotEmpty()) {
                    DefaultIncludeAttributes.ATTRIBUTES.forEach { key ->
                        node.attributes[key]?.let { appendToken(it) }
                    }
                }
            }
        }

        // Recurse into descendants
        node.children.forEach { appendToken(textContent(it, excludeCssHidden)) }

        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    fun slimHTML(node: MergedDOMTreeNode): String {
        val tagName = node.nodeName.lowercase()
        val sb = StringBuilder()

        sb.append("<").append(tagName)

        // Include meaningful attributes
        if (node.attributes.isNotEmpty()) {
            DefaultIncludeAttributes.ATTRIBUTES.forEach { key ->
                node.attributes[key]?.let {
                    sb.append(" ").append(key).append("=\"").append(it).append("\"")
                }
            }
        }

        sb.append(">")

        // Recurse into descendants
        node.children.forEach { sb.append(it.slimHTML()) }

        sb.append("</").append(tagName).append(">")

        return sb.toString()
    }
}
