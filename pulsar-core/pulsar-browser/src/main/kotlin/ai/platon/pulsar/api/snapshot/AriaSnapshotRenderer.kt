package ai.platon.pulsar.api.snapshot

import ai.platon.pulsar.api.model.DOMRect
import ai.platon.pulsar.api.model.DOMUtils
import ai.platon.pulsar.api.model.MergedDOMTreeNode
import ai.platon.pulsar.api.model.NodeType
import ai.platon.pulsar.api.model.OptimizedDOMTreeNode
import ai.platon.pulsar.chrome.dom.model.AriaSnapshotOptions
import java.util.*

object AriaSnapshotRenderer {
    fun render(root: OptimizedDOMTreeNode, options: AriaSnapshotOptions = AriaSnapshotOptions()): String {
        return AriaSnapshotFormatting.render(toRenderChildren(root, options))
    }

    private fun toRenderChildren(
        node: OptimizedDOMTreeNode,
        options: AriaSnapshotOptions,
        depth: Int = 0
    ): List<AriaSnapshotFormatting.RenderChild> {
        // --depth: stop recursing at max depth
        if (options.maxDepth >= 0 && depth > options.maxDepth) {
            return emptyList()
        }

        val original = node.originalNode
        if (shouldIgnoreNode(original)) {
            return emptyList()
        }

        // CSS-hidden content (`display:none` / `visibility:hidden`) must not appear in
        // snapshot output — prune the whole subtree, mirroring how the nano renderer
        // prunes `invisible` nodes (Browser4base issue #4).
        if (DOMUtils.isCssHidden(original)) {
            return emptyList()
        }

        if (isTextNode(original)) {
            return AriaSnapshotFormatting.normalizeText(original.nodeValue)
                ?.let { listOf(AriaSnapshotFormatting.RenderChild.Text(it)) }
                ?: emptyList()
        }

        val accessibleName = accessibleName(node)
        val rawChildren = node.children
            .flatMap { child -> toRenderChildren(child, options, depth + 1) }

        val role = role(node) ?: return rawChildren

        // Deduplicate a sole text child identical to the accessible name only after
        // the role is known: presentational nodes (role=none) must promote their
        // text as content instead of swallowing it (issue #4).
        val children = AriaSnapshotFormatting.normalizeChildren(rawChildren, accessibleName)
        val props = renderProps(node, role, accessibleName, options)
        val ref = original.backendNodeId.takeIf { it != null && it > 0 }?.let { "e$it" }

        // --interactive: skip non-interactive nodes, promote their children.
        // Addressability (ref/backendNodeId) is deliberately not a qualifying signal,
        // see AriaSnapshotFiltering for the shared contract.
        if (options.interactive && !AriaSnapshotFiltering.isInteractiveNode(role, node.originalNode.isInteractable)) {
            return children
        }

        // --compact: skip generic/group/paragraph nodes that carry no semantic info.
        // When --interactive is active the filter already removed structural noise, so
        // compacting a kept (interactive) node would drop genuine click targets
        // (e.g. a nameless cursor:pointer div).
        if (!options.interactive && options.compact && shouldCompactNode(role, accessibleName, props, children)) {
            return children
        }

        if (children.isEmpty() && props.isEmpty() && ref == null && accessibleName.isNullOrEmpty()) {
            return emptyList()
        }

        if (shouldCollapseGenericNode(role, accessibleName, props, children)) {
            return children
        }

        val box = if (options.boxes) {
            node.originalNode.snapshotNode?.bounds?.let { b ->
                "${b.x.toInt()},${b.y.toInt()},${b.width.toInt()},${b.height.toInt()}"
            }
        } else null

        return listOf(
            AriaSnapshotFormatting.RenderChild.Node(
                AriaSnapshotFormatting.RenderNode(
                    role = role,
                    name = accessibleName,
                    checked = AriaSnapshotFormatting.triState(rawState(node, "checked", "aria-checked")),
                    disabled = AriaSnapshotFormatting.booleanAttribute(rawState(node, "disabled", "aria-disabled")),
                    expanded = AriaSnapshotFormatting.booleanAttribute(rawState(node, "expanded", "aria-expanded")),
                    level = level(node),
                    pressed = AriaSnapshotFormatting.triState(rawState(node, "pressed", "aria-pressed")),
                    selected = AriaSnapshotFormatting.booleanAttribute(rawState(node, "selected", "aria-selected")),
                    ref = ref,
                    cursorPointer = hasCursorPointer(node),
                    props = props,
                    children = children,
                    box = box
                )
            )
        )
    }

    private fun formatBox(bounds: DOMRect?): String? {
        if (bounds == null) return null
        val r = bounds.roundTo(1)
        return "${r.x},${r.y},${r.width},${r.height}"
    }

    private fun renderProps(
        node: OptimizedDOMTreeNode,
        role: String,
        accessibleName: String?,
        options: AriaSnapshotOptions
    ): LinkedHashMap<String, String> {
        val attributes = node.originalNode.attributes
        val axProperties = axProperties(node)
        val props = linkedMapOf<String, String>()

        if (role == "link") {
            val url = attributes["href"]?.takeIf { it.isNotBlank() }
                ?: axProperties["url"]?.takeIf { it.isNotBlank() }
            if (url != null) {
                props["url"] = url
            }
        }

        // --urls: always include url for links even when element would be collapsed
        if (options.urls && role == "link" && !props.containsKey("url")) {
            val url = attributes["href"]?.takeIf { it.isNotBlank() }
                ?: axProperties["url"]?.takeIf { it.isNotBlank() }
            if (url != null) {
                props["url"] = url
            }
        }

        if (role == "textbox") {
            val placeholder = attributes["placeholder"] ?: attributes["aria-placeholder"]
            if (!placeholder.isNullOrBlank() && placeholder != accessibleName) {
                props["placeholder"] = placeholder
            }
        }

        AriaSnapshotFormatting.normalizeText(node.originalNode.axNode?.description)
            ?.takeIf { it != accessibleName }
            ?.let { props["description"] = it }

        val consumedProperties = setOf("checked", "disabled", "expanded", "level", "pressed", "selected", "url")
        axProperties.forEach { (name, value) ->
            if (name !in consumedProperties && !shouldOmitSupplementalProp(name, value)) {
                props.putIfAbsent(name, value)
            }
        }

        return props
    }

    private fun shouldCompactNode(
        role: String,
        accessibleName: String?,
        props: Map<String, String>,
        children: List<AriaSnapshotFormatting.RenderChild>
    ): Boolean {
        if (role != "generic" && role != "group" && role != "paragraph" && role != "section") {
            return false
        }
        if (!accessibleName.isNullOrEmpty()) return false
        if (props.isNotEmpty()) return false
        return true
    }

    /**
     * Compute the accessible name for snapshot output. CSS visibility must never
     * leak into names (Browser4base issue #4): Chromium embeds
     * `visibility:hidden` descendant text in content-derived accessible names, so
     * when a subtree contains CSS-hidden content the AX name is only trusted for
     * widgets that are named from an *external* source (label elements,
     * aria-labelledby) and have no own visible content.
     */
    private fun accessibleName(node: OptimizedDOMTreeNode): String? {
        val original = node.originalNode
        val role = role(node)
        val attributes = original.attributes
        val normalize = AriaSnapshotFormatting::normalizeText

        // 1. Explicit naming attributes never embed the node's own hidden content.
        normalize(attributes["aria-label"])?.let { return it }
        normalize(attributes["title"])?.let { return it }
        if (role == "img") {
            normalize(attributes["alt"])?.let { return it }
        }

        val axName = normalize(original.axNode?.name)
        val axDescription = if (role.equals("generic", ignoreCase = true)) {
            normalize(original.axNode?.description)
        } else null

        // 2. aria-labelledby is resolved by the browser against other elements and
        // may deliberately reference hidden content — the AX name is authoritative.
        if (!attributes["aria-labelledby"].isNullOrBlank()) {
            return axName ?: axDescription
        }

        // 3. Names derived from the node's own subtree must never embed CSS-hidden
        // text.
        val visibleText = normalize(DOMUtils.textContent(original, excludeCssHidden = true))
        val hasCssHiddenContent = DOMUtils.hasCssHiddenContent(original)

        return when {
            !hasCssHiddenContent -> axName ?: axDescription ?: visibleText
            isWidgetRole(role) && visibleText == null -> axName ?: axDescription
            else -> visibleText ?: axDescription
        }
    }

    /**
     * Roles that are typically named from an external source (a <label> element,
     * aria-labelledby) rather than from their own contents, so their AX name can
     * be trusted even when their subtree contains CSS-hidden content.
     */
    private fun isWidgetRole(role: String?): Boolean {
        return role != null && role in AriaSnapshotFiltering.INTERACTIVE_ROLES
    }

    private fun level(node: OptimizedDOMTreeNode): String? {
        return rawState(node, "level", "aria-level")?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun role(node: OptimizedDOMTreeNode): String? {
        val role = node.originalNode.axNode?.role?.trim()
            ?: node.originalNode.attributes["role"]?.trim()
        return when {
            role.equals("none", ignoreCase = true) || role.equals("presentation", ignoreCase = true) -> null
            !role.isNullOrEmpty() -> role
            else -> implicitRole(node) ?: if (isTextNode(node.originalNode)) null else "generic"
        }
    }

    private fun implicitRole(node: OptimizedDOMTreeNode): String? {
        val nodeName = node.originalNode.nodeName.trim().lowercase(Locale.ROOT)
        val attributes = node.originalNode.attributes
        return when (nodeName) {
            "a" -> attributes["href"]?.takeIf { it.isNotBlank() }?.let { "link" }
            "article" -> "article"
            "aside" -> "complementary"
            "button" -> "button"
            "footer" -> "contentinfo"
            "h1", "h2", "h3", "h4", "h5", "h6" -> "heading"
            "header" -> "banner"
            "iframe" -> "iframe"
            "img" -> "img"
            "li" -> "listitem"
            "main" -> "main"
            "nav" -> "navigation"
            "ol" -> "list"
            "option" -> "option"
            "select" -> if (
                attributes["multiple"]?.equals("true", ignoreCase = true) == true ||
                attributes["size"]?.toIntOrNull()?.let { it > 1 } == true
            ) {
                "listbox"
            } else {
                "combobox"
            }
            "summary" -> "button"
            "table" -> "table"
            "textarea" -> "textbox"
            "ul" -> "list"
            "input" -> when (attributes["type"]?.trim()?.lowercase(Locale.ROOT)) {
                null, "", "email", "password", "tel", "text", "url" -> "textbox"
                "button", "image", "reset", "submit" -> "button"
                "checkbox" -> "checkbox"
                "number" -> "spinbutton"
                "radio" -> "radio"
                "range" -> "slider"
                "search" -> "searchbox"
                else -> null
            }
            else -> null
        }
    }

    private fun rawState(node: OptimizedDOMTreeNode, propertyName: String, attributeName: String): String? {
        val attributes = node.originalNode.attributes
        return axProperties(node)[propertyName] ?: attributes[propertyName] ?: attributes[attributeName]
    }

    private fun axProperties(node: OptimizedDOMTreeNode): LinkedHashMap<String, String> {
        val properties = linkedMapOf<String, String>()
        node.originalNode.axNode?.properties.orEmpty().forEach { property ->
            val name = property.name.trim().lowercase(Locale.ROOT)
            val value = normalizePropertyValue(property.value) ?: return@forEach
            properties.putIfAbsent(name, value)
        }
        return properties
    }

    private fun normalizePropertyValue(value: Any?): String? {
        return when (value) {
            null -> null
            is Boolean -> value.toString().lowercase(Locale.ROOT)
            is String -> AriaSnapshotFormatting.normalizeText(value)
            else -> value.toString().trim().takeIf { it.isNotEmpty() }
        }
    }

    private fun hasCursorPointer(node: OptimizedDOMTreeNode): Boolean {
        val snapshotNode = node.originalNode.snapshotNode
        val styleCursorPointer = node.originalNode.attributes["style"]
            ?.contains(Regex("""cursor\s*:\s*pointer""", RegexOption.IGNORE_CASE)) == true
        return if (snapshotNode != null) {
            snapshotNode.cursorStyle?.equals("pointer", ignoreCase = true) == true ||
                    snapshotNode.isClickable == true ||
                    styleCursorPointer ||
                    node.originalNode.isInteractable == true ||
                    node.interactiveIndex != null
        } else {
            styleCursorPointer || node.originalNode.isInteractable == true || node.interactiveIndex != null
        }
    }

    private fun shouldCollapseGenericNode(
        role: String,
        accessibleName: String?,
        props: Map<String, String>,
        children: List<AriaSnapshotFormatting.RenderChild>
    ): Boolean {
        return role.equals("generic", ignoreCase = true) &&
                accessibleName.isNullOrEmpty() &&
                props.isEmpty() &&
                children.size == 1 &&
                children.first() is AriaSnapshotFormatting.RenderChild.Node
    }

    private fun shouldOmitSupplementalProp(name: String, value: String): Boolean {
        return when (name) {
            "focusable", "focused", "editable", "settable" -> true
            "invalid", "multiline", "readonly", "required" -> value.equals("false", ignoreCase = true)
            else -> false
        }
    }

    private fun shouldIgnoreNode(node: MergedDOMTreeNode): Boolean {
        val nodeName = node.nodeName.trim().lowercase(Locale.ROOT)
        return node.nodeType == NodeType.COMMENT_NODE ||
                nodeName == "#comment" ||
                nodeName == "comment" ||
                nodeName == "script" ||
                nodeName == "style"
    }

    private fun isTextNode(node: MergedDOMTreeNode): Boolean {
        val nodeName = node.nodeName.trim().lowercase(Locale.ROOT)
        return nodeName == "#text" || nodeName == "text"
    }
}
