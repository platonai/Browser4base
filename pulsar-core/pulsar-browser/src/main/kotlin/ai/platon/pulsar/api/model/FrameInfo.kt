package ai.platon.pulsar.api.model

/**
 * A frame in the current page's frame tree.
 *
 * The main (top-level) frame is the root of the tree and has a null [parentId].
 * Child frames are usually `<iframe>` (or legacy `<frameset>`/`<frame>`) elements
 * embedded in their parent document. A frame is identified by its CDP frame id
 * (see `Page.getFrameTree`); the id identifies the *frame node* (the Chrome
 * FrameTreeNode), so it survives document navigations inside the frame and is
 * only replaced when the frame node itself is destroyed — e.g. the `<iframe>` is
 * removed from its parent or an ancestor document navigates away. The DOM node
 * ids inside the frame, in contrast, are replaced on every document navigation.
 *
 * @property id The CDP frame id (stable while the frame node exists).
 * @property name The frame's `name` attribute, or empty when unnamed.
 * @property url The frame's current document URL (empty for frames that never committed a document).
 * @property parentId The id of the parent frame, or null for the main frame.
 * @property depth The frame depth in the tree (0 = main frame).
 * @property active True when this is the frame that element operations are currently
 *   scoped to (set by `WebDriver.frameList` / `frameSwitch`); exactly one frame — the
 *   main frame when no iframe has been selected — reports true.
 */
data class FrameInfo(
    val id: String,
    val name: String = "",
    val url: String = "",
    val parentId: String? = null,
    val depth: Int = 0,
    val active: Boolean = false,
) {
    /** True for the main (top-level) frame. */
    val isMainFrame: Boolean get() = parentId == null

    /**
     * A short human-readable label for this frame: the frame name when present,
     * otherwise the frame id (frame names are the most stable user-facing handle
     * across navigations of the embedding page).
     */
    val label: String get() = name.ifBlank { id }
}
