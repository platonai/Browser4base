package ai.platon.pulsar.api.snapshot

/**
 * Shared interactive-only filtering contract for ARIA snapshot rendering.
 *
 * Both [AriaSnapshotRenderer] and [NanoAriaSnapshotRenderer] must agree on what
 * "interactive" means for [ai.platon.pulsar.chrome.dom.model.AriaSnapshotOptions.interactive],
 * otherwise the same DOM produces different interactive snapshots depending on which
 * renderer runs (viewport-scoped snapshots use the nano renderer, whole-page snapshots
 * use the full renderer).
 *
 * A node qualifies when it is an interactive widget by [INTERACTIVE_ROLES] or carries an
 * interactability signal (`isInteractable` / `interactive` flag, computed from clickability,
 * cursor:pointer style, native control tags and AX roles during snapshot collection).
 *
 * Addressability must NOT qualify a node: backendNodeId-based refs are assigned to
 * virtually every DOM node, so a ref-based early return turned the interactive filter
 * into a no-op (Browser4base issue #3). Renderers skip non-qualifying nodes and promote
 * their children instead.
 */
object AriaSnapshotFiltering {
    /** Roles of interactive widgets kept in interactive-only snapshots. */
    val INTERACTIVE_ROLES = setOf(
        "button", "link", "textbox", "checkbox", "combobox", "searchbox",
        "spinbutton", "slider", "radio", "option", "listbox", "menuitem", "tab",
        "switch", "treeitem", "menuitemcheckbox", "menuitemradio"
    )

    /**
     * Decide whether a node qualifies for interactive-only snapshots.
     *
     * @param role The node's effective ARIA role (explicit or implicit).
     * @param interactable The snapshot-level interactability flag: [ai.platon.pulsar.api.model.MergedDOMTreeNode.isInteractable]
     *   in the full renderer, [ai.platon.pulsar.api.model.NanoDOMTreeNode.interactive] in the nano renderer.
     *   Both carry the same underlying value computed during snapshot collection.
     */
    fun isInteractiveNode(role: String, interactable: Boolean?): Boolean {
        return interactable == true || role in INTERACTIVE_ROLES
    }
}
