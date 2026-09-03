package ai.platon.pulsar.chrome

import ai.platon.pulsar.api.BrowserProtocol
import ai.platon.pulsar.api.model.FrameInfo
import ai.platon.pulsar.api.model.Locator
import ai.platon.pulsar.api.model.WebDriverException
import ai.platon.pulsar.chrome.util.ChromeDriverException
import ai.platon.pulsar.chrome.util.CDPReturnError
import ai.platon.pulsar.common.getLogger
import ai.platon.cdt.kt.protocol.types.dom.Node as CdpNode
import ai.platon.cdt.kt.protocol.types.page.FrameTree
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.lang3.StringUtils
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Thrown when an element operation cannot run inside the selected frame:
 * the frame is cross-origin (its document is not reachable through this
 * session's DOM domain) or it no longer exists (removed or replaced by a
 * navigation).
 *
 * This is a deterministic user-facing failure — it must surface to the CLI/user
 * instead of being swallowed by element-not-found handling, so resolution code
 * lets [FrameScopeException] propagate where other failures degrade to null.
 */
open class FrameScopeException(message: String) : ChromeDriverException(message)

/**
 * Manages the frame scope of a tab driver: listing the page's frame tree and
 * switching the frame that CSS-selector-based element operations resolve against.
 *
 * This mirrors the `frame`/`frame main` UX of agent-browser. When no frame has
 * been selected ([activeFrame] is null) every operation behaves exactly as
 * before — selectors resolve against the main frame's document. After
 * [switch] selects an `<iframe>`, subsequent element operations whose locator
 * is a plain CSS selector resolve inside that frame's document, so flows like
 * "switch to the payment iframe, fill its card input, click its submit button"
 * work without hand-written `contentDocument` evaluation.
 *
 * ## Reachability
 *
 * A frame is *reachable* when its document can be queried through the DOM
 * domain of the tab's CDP session — that is, when the iframe runs in the same
 * renderer process as the page (same-origin iframes on stock Chrome).
 * Cross-origin iframes are out-of-process frames: depending on the Chrome
 * build they may not even be reported by `Page.getFrameTree` on the page
 * session (they exist as separate CDP iframe targets). When reported they can
 * be selected, but element operations inside them fail with a descriptive
 * error instead of silently resolving against the wrong document; when not
 * reported, selecting them fails with an actionable "Frame not found" error.
 *
 * ## Lifecycle
 *
 * The driver clears the active frame whenever the main frame navigates (the
 * whole frame tree is replaced); [reset] is the hook. Sub-frame navigations
 * keep the selection; if the selected frame is removed or navigated away the
 * next scoped operation fails with an actionable "frame is gone" error.
 *
 * @param browserProtocol The tab's CDP protocol facade.
 */
class FrameManager(private val browserProtocol: BrowserProtocol) {
    companion object {
        private val logger = getLogger(FrameManager::class)

        /** Node type constant for DOCUMENT nodes (org.w3c.dom.Node.DOCUMENT_NODE). */
        private const val DOCUMENT_NODE_TYPE = 9

        private val FRAME_TAGS = setOf("IFRAME", "FRAME")
    }

    /**
     * Serializes frame-scope mutations ([switch], [switchToMainFrame], [reset]) so
     * concurrent coroutines cannot interleave their read-modify-write sequences.
     */
    private val scopeMutex = Mutex()

    /**
     * Resolved document node ids, keyed by frame id.
     *
     * Resolving a frame's document requires `DOM.getDocument(pierce = true)` plus a
     * client-side tree walk — a full serialization of the page's DOM. The result is
     * cached per frame id; entries are evicted when the node id turns out to be stale
     * (CDP -32000 on query, i.e. the frame navigated) or when the main frame
     * navigates ([reset]).
     */
    private val frameDocumentNodeIds = ConcurrentHashMap<String, Int>()

    /** The frame that element operations are currently scoped to, or null for the main frame. */
    @Volatile
    var activeFrame: FrameInfo? = null
        private set

    /** The frame id of [activeFrame], or null when operations are scoped to the main frame. */
    val activeFrameId: String? get() = activeFrame?.id

    /** True when element operations are scoped to a frame other than the main frame. */
    val isScoped: Boolean get() = activeFrame != null

    /**
     * Whether the frame [frameId] is reachable through this session's DOM
     * domain (same-process frame) or is a cross-process (cross-origin) frame.
     *
     * @return true when reachable, false when the frame is out-of-process or
     *   could not be verified.
     */
    suspend fun isFrameReachable(frameId: String): Boolean {
        val mainId = try { browserProtocol.getFrameTree().frame.id } catch (e: Exception) { return false }
        if (frameId == mainId) return true
        // Resolve through the cached path so a successful probe warms the cache for
        // the element operations that follow the switch.
        return runCatching { findFrameDocumentNodeId(frameId) }.getOrNull() != null
    }

    /**
     * Lists the page's frame tree in depth-first order (the main frame first).
     * The frame that element operations are currently scoped to (the main frame
     * when no iframe has been selected) is marked with [FrameInfo.active].
     */
    suspend fun list(): List<FrameInfo> = scopeMutex.withLock { listLocked() }

    /**
     * Frame-tree listing without re-entering [scopeMutex]; callers must already
     * hold the lock.
     */
    private suspend fun listLocked(): List<FrameInfo> {
        val tree = browserProtocol.getFrameTree()
        val result = mutableListOf<FrameInfo>()
        flatten(tree, 0, result)
        var scopedId = activeFrameId
        if (scopedId != null && result.none { it.id == scopedId }) {
            // The selected frame is gone (removed or replaced by a navigation):
            // drop the stale scope so operations fall back to the main document.
            logger.debug("Active frame {} is no longer in the frame tree, frame scope cleared", scopedId)
            activeFrame = null
            scopedId = null
        }
        val activeId = scopedId ?: result.firstOrNull { it.isMainFrame }?.id
        return if (activeId == null) {
            result
        } else {
            result.map { if (it.id == activeId) it.copy(active = true) else it }
        }
    }

    /**
     * Lists only the frames nested under [frameId] (excluding [frameId] itself),
     * in depth-first order.
     */
    suspend fun listChildFrames(frameId: String): List<FrameInfo> {
        val all = list()
        val childIds = mutableSetOf<String>()
        all.filter { it.parentId == frameId }.forEach { child ->
            childIds += child.id
            collectDescendants(all, child.id, childIds)
        }
        return all.filter { it.id in childIds }
    }

    /**
     * Switches the frame scope so subsequent CSS-selector element operations
     * resolve inside the frame identified by [target].
     *
     * [target] is resolved in this order:
     * 1. an element ref (`e12`, `backend:123`, `fbn:<frame>,123`) pointing at
     *    an `<iframe>`/`<frame>` element (as printed by snapshots) — the owned
     *    frame id comes from the element's content document;
     * 2. a CSS selector matching an `<iframe>`/`<frame>` element inside the
     *    currently scoped document (supports nested switching);
     * 3. an exact CDP frame id (as printed by [list]);
     * 4. an exact frame name (`<iframe name="...">`);
     * 5. a URL substring match (case-insensitive).
     *
     * @param target The frame to switch to.
     * @return The resolved frame.
     * @throws WebDriverException When no frame matches [target] or the browser is unavailable.
     */
    @Throws(WebDriverException::class)
    suspend fun switch(target: String): FrameInfo {
        require(target.isNotBlank()) { "frame target must not be blank" }
        return scopeMutex.withLock { switchLocked(target) }
    }

    private suspend fun switchLocked(target: String): FrameInfo {
        val frames = listLocked()
        if (frames.isEmpty()) {
            throw WebDriverException("Cannot switch frame: the page frame tree is empty")
        }
        val scopeFrame = activeFrame

        // 1. Element ref (e.g. `e12` from a snapshot, `backend:123`, `fbn:...`):
        //    resolve the <iframe>/<frame> element through the DOM and activate
        //    the frame it owns. Mirrors agent-browser's ref-first resolution.
        refBackendNodeId(target)?.let { backendNodeId ->
            return activateFrameByRef(backendNodeId, target, frames)
        }

        // 2. CSS selector for an <iframe>/<frame> element inside the current scope.
        if (!looksLikeUrl(target)) {
            val childId = resolveChildFrameIdBySelector(target, frames)
            if (childId != null) {
                return activate(frames.first { it.id == childId })
            }
        }

        // 3. Exact frame id (as printed by [list]).
        frames.firstOrNull { it.id == target }?.let { return activate(it) }

        // 4. Exact frame name.
        frames.firstOrNull { it.name == target }?.let { return activate(it) }

        // 5. URL substring, case-insensitive.
        val targetLower = target.lowercase()
        frames.firstOrNull { it.url.lowercase().contains(targetLower) }?.let { return activate(it) }

        // Nothing matched. When the target looks like a CSS selector and the
        // current scope's document is not reachable (cross-origin iframe or a
        // stale frame), say so — the selector may simply be unresolvable there.
        if (scopeFrame != null && !scopeFrame.isMainFrame &&
            findFrameDocumentNodeId(scopeFrame.id) == null
        ) {
            throw WebDriverException(
                "Frame not found: $target inside frame '${scopeFrame.label}'. The current frame's " +
                    "document is not reachable (cross-origin iframe or a frame replaced by a " +
                    "navigation). Switch back to the main frame (`frameMain()`) and select the " +
                    "target frame again."
            )
        }

        throw WebDriverException(
            "Frame not found: $target. List the page frames with `frameList()` and switch by " +
                "an element ref (e.g. `e12` from a snapshot), CSS selector (#id, iframe[src=...]), " +
                "frame name, frame id, or url."
        )
    }

    /**
     * The backend node id carried by [target] when it is an element ref
     * (`e12`, `backend:123`, `fbn:<frame>,123`), or null when [target] is not
     * a ref-shaped locator. Only ref-shaped targets take the DOM.describeNode
     * path; everything else falls through to selector/name/url matching.
     */
    private fun refBackendNodeId(target: String): Int? {
        val loc = Locator.parse(target) ?: return null
        return when (loc.type) {
            Locator.Type.BACKEND_NODE_ID -> loc.selector.toIntOrNull()
            Locator.Type.FRAME_BACKEND_NODE_ID -> loc.selector.substringAfterLast(",").toIntOrNull()
            else -> null
        }
    }

    /**
     * Activates the frame owned by the `<iframe>`/`<frame>` element that the
     * ref [target] points at. The element is resolved through
     * `DOM.describeNode(backendNodeId)`; its content document (or the element
     * itself on Chrome versions that stamp owners) carries the owned frame id.
     *
     * @throws WebDriverException When the ref is stale, points at a non-frame
     *   element, or its frame is not part of the page frame tree.
     */
    @Throws(WebDriverException::class)
    private suspend fun activateFrameByRef(
        backendNodeId: Int,
        target: String,
        frames: List<FrameInfo>
    ): FrameInfo {
        val node = try {
            browserProtocol.describeNode(backendNodeId = backendNodeId, depth = 1)
        } catch (e: Exception) {
            logger.debug("describeNode failed for frame ref '{}': {}", target, e.message)
            throw WebDriverException(
                "Frame not found: $target. The element ref is stale (the page navigated since " +
                    "the snapshot). Take a fresh snapshot, or switch by CSS selector, frame " +
                    "name, frame id, or url."
            )
        }

        val nodeName = node.nodeName.uppercase()
        if (nodeName !in FRAME_TAGS) {
            throw WebDriverException(
                "Ref $target does not point to an <iframe>/<frame> element (found <$nodeName>). " +
                    "Switch by CSS selector (#id, iframe[src=...]), frame name, frame id, or url."
            )
        }

        val frameId = node.contentDocument?.frameId?.takeIf { it.isNotBlank() }
            ?: node.frameId?.takeIf { it.isNotBlank() }
            ?: throw WebDriverException(
                "Could not resolve the frame of ref $target: the iframe's content document is " +
                    "not reachable (cross-origin iframe) or the element is stale."
            )

        val frame = frames.firstOrNull { it.id == frameId }
            ?: throw WebDriverException(
                "Frame not found: $target. The iframe's frame is not in the page frame tree " +
                    "(cross-origin iframe or a stale ref from a previous navigation). List the " +
                    "page frames with `frameList()`."
            )
        return activate(frame)
    }

    /**
     * Switches the frame scope back to the main frame.
     */
    suspend fun switchToMainFrame() {
        scopeMutex.withLock {
            if (activeFrame != null) {
                logger.info("Frame scope switched back to the main frame | previous={}", activeFrame)
                activeFrame = null
            }
        }
    }

    /**
     * Clears the active frame scope and the resolved-document cache. Invoked by
     * the driver when the main frame navigates and the whole frame tree is replaced.
     */
    suspend fun reset() {
        scopeMutex.withLock {
            if (activeFrame != null) {
                logger.debug("Frame scope cleared | frame={}", activeFrame)
                activeFrame = null
            }
            if (frameDocumentNodeIds.isNotEmpty()) {
                logger.debug("Frame document node cache cleared | entries={}", frameDocumentNodeIds.size)
                frameDocumentNodeIds.clear()
            }
        }
    }

    /**
     * Resolves a CSS selector inside the document of [frameId] and returns the
     * matching node id, or 0 when nothing matches.
     *
     * @throws ChromeDriverException When the frame's document cannot be reached
     *   (cross-origin/out-of-process frame, or a stale frame id).
     */
    @Throws(ChromeDriverException::class)
    suspend fun queryInFrame(frameId: String, cssSelector: String): Int {
        val documentNodeId = requireFrameDocumentNodeId(frameId)
        return try {
            browserProtocol.querySelector(documentNodeId, cssSelector)
        } catch (e: CDPReturnError) {
            if (e.errorCode != -32000L) {
                throw e
            }
            // The cached document node id may be stale (the frame navigated since it
            // was resolved). Evict the entry and resolve the document once more before
            // giving up on the selector.
            frameDocumentNodeIds.remove(frameId)
            val freshDocumentNodeId = requireFrameDocumentNodeId(frameId)
            browserProtocol.querySelector(freshDocumentNodeId, cssSelector)
        }
    }

    /**
     * Resolves a CSS selector inside the document of [frameId] and returns all
     * matching node ids (empty when nothing matches).
     *
     * @throws ChromeDriverException When the frame's document cannot be reached.
     */
    @Throws(ChromeDriverException::class)
    suspend fun queryAllInFrame(frameId: String, cssSelector: String): List<Int> {
        val documentNodeId = requireFrameDocumentNodeId(frameId)
        return try {
            browserProtocol.querySelectorAll(documentNodeId, cssSelector)
        } catch (e: CDPReturnError) {
            if (e.errorCode != -32000L) {
                throw e
            }
            // See queryInFrame: the cached document node id may be stale after a
            // frame navigation; evict and resolve once more.
            frameDocumentNodeIds.remove(frameId)
            val freshDocumentNodeId = requireFrameDocumentNodeId(frameId)
            browserProtocol.querySelectorAll(freshDocumentNodeId, cssSelector)
        }
    }

    /**
     * The document node id of [frameId], or null when the frame's document is
     * not reachable through this session's DOM domain (cross-process frame,
     * removed frame, or a frame id from a previous navigation).
     *
     * The pierced-DOM walk is expensive (a full `DOM.getDocument(pierce=true)`
     * round trip), so resolved ids are cached per frame; see [frameDocumentNodeIds].
     */
    suspend fun findFrameDocumentNodeId(frameId: String): Int? {
        if (frameId.isBlank()) return null
        frameDocumentNodeIds[frameId]?.let { return it }
        val nodeId = findFrameDocumentNode(frameId)?.nodeId
        if (nodeId != null) {
            frameDocumentNodeIds[frameId] = nodeId
        }
        return nodeId
    }

    private suspend fun activate(frame: FrameInfo): FrameInfo {
        val reachable = frame.isMainFrame || isFrameReachable(frame.id)
        // Store the marked copy so activeFrame.active is consistent with what
        // frameSwitch returned and what list() reports.
        val active = frame.copy(active = true)
        activeFrame = active
        if (reachable || frame.isMainFrame) {
            logger.info(
                "Frame scope switched | frame={} | reachable={} | url={}",
                frame.label, reachable, frame.url
            )
        } else {
            logger.warn(
                "Frame scope switched to an unreachable frame (cross-origin or stale) | frame={} | " +
                    "operations inside it will fail | url={}",
                frame.label, frame.url
            )
        }
        return active
    }

    /**
     * The document node id of the frame the CSS selector [target] matches
     * inside the currently scoped document, or null when the selector matches
     * no frame element (or the scope document is not reachable — callers then
     * fall through to frame-id/name/url matching and tailor the final error).
     */
    @Throws(WebDriverException::class)
    private suspend fun resolveChildFrameIdBySelector(target: String, frames: List<FrameInfo>): String? {
        val scopeId = activeFrame?.id ?: frames.firstOrNull { it.isMainFrame }?.id ?: return null
        val element = resolveFrameElementNode(target, scopeId, frames) ?: return null
        return resolveOwnedFrameId(element, scopeId, frames)
    }

    /**
     * Resolves [target] (CSS selector or XPath-ish expression) to the
     * `<iframe>`/`<frame>` element node inside the document of frame [scopeId],
     * or null when nothing matches.
     */
    private suspend fun resolveFrameElementNode(
        target: String,
        scopeId: String,
        frames: List<FrameInfo>
    ): CdpNode? {
        val documentNodeId = if (scopeId == frames.firstOrNull { it.isMainFrame }?.id) {
            // Main frame: plain document root.
            runCatching { browserProtocol.getDocument() }.getOrNull()?.nodeId ?: return null
        } else {
            findFrameDocumentNodeId(scopeId) ?: return null
        }

        val nodeId = try {
            browserProtocol.querySelector(documentNodeId, target)
        } catch (e: Exception) {
            // Invalid or unsupported selector — the caller falls through to
            // frame-id/name/url matching.
            logger.debug("querySelector failed for frame target '{}': {}", target, e.message)
            return null
        }
        if (nodeId <= 0) return null

        return try {
            browserProtocol.describeNode(nodeId = nodeId)
        } catch (e: Exception) {
            logger.debug("describeNode failed for frame target '{}': {}", target, e.message)
            null
        }
    }

    /**
     * Maps an `<iframe>`/`<frame>` element to the id of the frame it owns.
     *
     * Same-process frames expose their content document through the pierced
     * DOM, so [CdpNode.contentDocument] is authoritative. Cross-process frames
     * (cross-origin iframes) have no content document here; the element itself
     * carries the owned frame id ([CdpNode.frameId]) — when even that is
     * absent, the element's `name`/`id`/`src` attributes are matched against
     * the candidate child frames of [scopeId] in the frame tree.
     */
    private suspend fun resolveOwnedFrameId(element: CdpNode, scopeId: String, frames: List<FrameInfo>): String? {
        val nodeName = element.nodeName.uppercase()
        if (nodeName !in FRAME_TAGS) {
            return null
        }

        element.contentDocument?.frameId?.let { if (it.isNotBlank()) return it }
        element.frameId?.let { if (it.isNotBlank()) return it }

        // Last resort: attribute matching against the frame tree children.
        val attributes = element.attributes.orEmpty().chunked(2).associate { (k, v) -> k to v }
        val name = attributes["name"].orEmpty()
        val id = attributes["id"].orEmpty()
        val src = attributes["src"].orEmpty()

        val children = frames.filter { it.parentId == scopeId }
        val byName = children.filter { it.name.isNotBlank() && (it.name == name || it.name == id) }
        if (byName.size == 1) return byName[0].id

        val absoluteSrc = resolveAbsoluteSrc(src) ?: return null
        val byUrl = children.filter { it.url.isNotBlank() && it.url == absoluteSrc }
        if (byUrl.size == 1) return byUrl[0].id

        return null
    }

    private fun resolveAbsoluteSrc(src: String): String? {
        if (src.isBlank() || src.startsWith("about:") || src.startsWith("javascript:")) return null
        return try {
            val uri = URI.create(src)
            if (uri.isAbsolute) src else null
        } catch (e: Exception) {
            null
        }
    }

    private fun looksLikeUrl(target: String): Boolean =
        target.startsWith("http://", ignoreCase = true) ||
            target.startsWith("https://", ignoreCase = true) ||
            target.startsWith("about:", ignoreCase = true) ||
            target.startsWith("file:", ignoreCase = true)

    /**
     * The document node of [frameId] in the pierced DOM tree, or null when the
     * frame's document is not reachable.
     *
     * The lookup walks the tree returned by `DOM.getDocument(pierce=true)` and
     * recognizes the frame in three ways (Chrome versions stamp different
     * nodes with `frameId`):
     * 1. a DOCUMENT node whose `frameId` equals the target;
     * 2. an `<iframe>`/`<frame>` owner element whose `contentDocument.frameId`
     *    (or the element's own `frameId`) equals the target — its content
     *    document is returned;
     * 3. any node stamped with the target frame id — its owning document
     *    (tracked while walking) is returned.
     */
    private suspend fun findFrameDocumentNode(frameId: String): CdpNode? {
        if (frameId.isBlank()) return null
        val root = try {
            browserProtocol.getDocument(depth = -1, pierce = true)
        } catch (e: Exception) {
            logger.debug("DOM.getDocument(pierce) failed: {}", e.message)
            null
        } ?: return null

        var found: CdpNode? = null
        var matchedBy: String? = null

        fun walk(node: CdpNode, ownerDocument: CdpNode) {
            if (found != null) return

            val isFrameOwner = node.nodeName.uppercase() in FRAME_TAGS
            val contentDocument = node.contentDocument
            if (isFrameOwner && contentDocument != null) {
                val ownedFrameId = contentDocument.frameId ?: node.frameId
                if (ownedFrameId == frameId) {
                    found = contentDocument
                    matchedBy = "frame-owner"
                    return
                }
                walk(contentDocument, contentDocument)
                if (found != null) return
            } else if (isFrameOwner && node.frameId == frameId) {
                // A frame-owner element without a reachable content document
                // (cross-origin/out-of-process iframe) is stamped with the id
                // of the frame it owns — but that frame's document is NOT in
                // this DOM tree. Do NOT fall through to the generic
                // frameId-stamp match below, which would wrongly report the
                // parent document as the frame's document.
                return
            }

            if (node.frameId == frameId) {
                // A node stamped with the target frame id. For DOCUMENT nodes the
                // node itself is the frame's document; any other node belongs to
                // the frame's document tracked during the walk.
                found = if (node.nodeType == DOCUMENT_NODE_TYPE) node else ownerDocument
                matchedBy = if (node.nodeType == DOCUMENT_NODE_TYPE) "document-node" else "node-frame-id"
                return
            }

            node.children?.forEach { child ->
                walk(child, ownerDocument)
                if (found != null) return
            }
            node.shadowRoots?.forEach { shadow ->
                walk(shadow, ownerDocument)
                if (found != null) return
            }
        }

        walk(root, root)
        if (found != null) {
            logger.debug(
                "Frame document resolved | frameId={} | docNodeId={} | matchedBy={}",
                frameId, found.nodeId, matchedBy
            )
        } else {
            logger.debug("Frame document NOT resolved | frameId={}", frameId)
        }
        return found
    }

    /**
     * The document node id of [frameId], failing loudly when it cannot be
     * reached.
     */
    @Throws(FrameScopeException::class)
    private suspend fun requireFrameDocumentNodeId(frameId: String): Int {
        val docNodeId = findFrameDocumentNodeId(frameId)
            ?: throw FrameScopeException(
                "The selected frame '${activeFrame?.label ?: frameId}' is not reachable: " +
                    "cross-origin iframes and frames removed by a navigation cannot be operated " +
                    "on. Switch back to the main frame (`frameMain()`) and select the frame again."
            )
        return docNodeId
    }

    private fun flatten(tree: FrameTree, depth: Int, result: MutableList<FrameInfo>) {
        val frame = tree.frame
        result += FrameInfo(
            id = frame.id,
            name = frame.name ?: "",
            url = StringUtils.abbreviate(frame.url, 512),
            parentId = frame.parentId,
            depth = depth
        )
        tree.childFrames?.forEach { flatten(it, depth + 1, result) }
    }

    private fun collectDescendants(frames: List<FrameInfo>, parentId: String, out: MutableSet<String>) {
        frames.filter { it.parentId == parentId }.forEach { child ->
            out += child.id
            collectDescendants(frames, child.id, out)
        }
    }
}
