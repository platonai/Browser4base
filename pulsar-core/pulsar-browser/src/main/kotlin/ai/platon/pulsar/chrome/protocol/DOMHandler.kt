package ai.platon.pulsar.chrome.protocol

import ai.platon.pulsar.chrome.FrameManager
import ai.platon.pulsar.chrome.FrameScopeException
import ai.platon.pulsar.chrome.dom.CDPSnapshotService
import ai.platon.pulsar.chrome.util.CDPReturnError
import ai.platon.pulsar.chrome.util.ChromeDriverException
import ai.platon.pulsar.api.model.Locator
import ai.platon.pulsar.api.BrowserProtocol
import ai.platon.pulsar.api.model.NodeRef
import ai.platon.pulsar.api.snapshot.SnapshotService
import ai.platon.pulsar.api.model.ElementRefCriteria
import ai.platon.pulsar.common.Strings
import ai.platon.pulsar.common.brief
import ai.platon.pulsar.common.getLogger

data class LocatorAndCssSelector(
    val locator: Locator,
    val cssSelector: String?,
)

class DOMHandler(
    private val browserProtocol: BrowserProtocol,
    /**
     * Optional frame-scope manager. When the driver has switched into an
     * iframe ([FrameManager.activeFrameId] is set), plain CSS selectors are
     * resolved inside that frame's document instead of the main frame's.
     */
    private val frameManager: FrameManager? = null
) {
    private val logger = getLogger(this)

    private val isActive get() = browserProtocol.isOpen

    private val snapshot: SnapshotService by lazy { CDPSnapshotService(browserProtocol) }

    /**
     * Queries for an element using a selector.
     *
     * Supports locator formats:
     * - CSS selector: "div.class", "#id", etc.
     * - XPath selector: "//div[@class='class']", etc.
     * - Backend node ID: "backend:123", "e1233"
     * - Frame backend node ID: "fbn:FRAME_ID,123"
     *
     * @param locator The element locator, multiple formats are supported.
     * @return nodeId or null if not found
     */
    @Throws(ChromeDriverException::class)
    suspend fun queryLocator(locator: String): NodeRef? {
        return resolveLocator(locator)
    }

    /**
     * Queries for a list of elements using a selector.
     *
     * Supports four selector formats:
     * - CSS selector: "div.class", "#id", etc.
     * - XPath selector: "//div[@class='class']", etc.
     * - Backend node ID: "backend:123", "e1233"
     * - Frame backend node ID: "fbn:FRAME_ID,123"
     *
     * @param locator The element locator, multiple formats are supported.
     * @return nodeId or null if not found
     */
    @Throws(ChromeDriverException::class)
    suspend fun queryLocatorAll(locator: String): List<NodeRef>? {
        return resolveLocatorAll(locator)
    }

    fun normalizeSelector(locator: String, jsEscape: Boolean = false): String? {
        return if (jsEscape) {
            normalizeLocatorForJs(locator)?.cssSelector
        } else normalizeLocatorForNonJs(locator)?.cssSelector
    }

    fun normalizeLocator(locator: String, jsEscape: Boolean = false): LocatorAndCssSelector? {
        return if (jsEscape) {
            normalizeLocatorForJs(locator)
        } else normalizeLocatorForNonJs(locator)
    }

    /**
     * Normalize an element locator for use in non-JS contexts (e.g. CDP DOM APIs) by extracting a CSS selector if possible.
     * */
    private fun normalizeLocatorForNonJs(locator: String): LocatorAndCssSelector? {
        return findCssSelectorFromLocator(locator)
    }

    /**
     * Normalize an element locator for use in JavaScript contexts by extracting a CSS selector if possible and escaping it for safe embedding in JS code.
     * */
    private fun normalizeLocatorForJs(locator: String): LocatorAndCssSelector? {
        val ls = findCssSelectorFromLocator(locator) ?: return null

        // consider xpath
        val selector = ls.cssSelector ?: locator
        val safeSelector = Strings.escapeJsString(selector)
        return LocatorAndCssSelector(ls.locator, safeSelector)
    }

    private fun findCssSelectorFromLocator(locator: String): LocatorAndCssSelector? {
        val l = Locator.parse(locator) ?: return null

        if (l.type == Locator.Type.CSS_PATH) {
            return LocatorAndCssSelector(l, l.selector)
        }

        val backendNodeId = when (l.type) {
            Locator.Type.FRAME_BACKEND_NODE_ID -> l.selector.toIntOrNull()
            else -> null
        }

        if (backendNodeId != null) {
            val ref = ElementRefCriteria(backendNodeId = backendNodeId)
            val selector = snapshot.findElement(ref)?.cssSelector()
            return LocatorAndCssSelector(l, selector)
        }

        return LocatorAndCssSelector(l, null)
    }

    @Throws(ChromeDriverException::class)
    private suspend fun resolveLocatorAll(locator: String): List<NodeRef>? {
        return try {
            doResolveLocatorAll(locator)
        } catch (e: FrameScopeException) {
            // Frame-scope failures are deterministic user-facing errors; they
            // must surface instead of degrading to element-not-found.
            throw e
        } catch (e: CDPReturnError) {
            // code: -32000 message: "Could not find node with given id"
            // This exception is expected, will change this log to debug
            if (e.errorCode != -32000L) {
                // -32000L is expected, no log needed
                logger.warn("Exception resolveSelectorAll | {} {} | {}", e.errorCode, e.errorMessage, e.brief())
            } else {
                logger.debug("Element not found for selector '{}'", locator)
            }
            null
        } catch (e: Exception) {
            logger.warn("[Unexpected] exception ", e)
            null
        }
    }

    @Throws(ChromeDriverException::class)
    private suspend fun doResolveLocatorAll(locator: String): List<NodeRef>? {
        // Parse the selector into a Locator object. If parsing fails, return null.
        val (l, cssSelector) = normalizeLocator(locator, false) ?: return null

        require(Locator.Type.CSS_PATH.text.isEmpty())

        // Frame scope: plain CSS selectors resolve inside the selected frame's
        // document; XPath is not supported inside a frame (see doResolveLocator).
        val scopedFrameId = frameManager?.activeFrameId
        if (scopedFrameId != null) {
            return when {
                cssSelector != null -> resolveCSSSelectorAllInFrame(scopedFrameId, cssSelector)

                l.type == Locator.Type.XPATH -> throw FrameScopeException(
                    "XPath selectors are not supported inside a selected frame (frame " +
                        "'${frameManager?.activeFrame?.label}'). Switch back to the main frame " +
                        "(`frameMain()`) or use a CSS selector."
                )

                l.type == Locator.Type.BACKEND_NODE_ID || l.type == Locator.Type.FRAME_BACKEND_NODE_ID -> {
                    val nodeRef = resolveUnscopedLocator(l)
                    if (nodeRef != null) listOf(nodeRef) else null
                }

                else -> throw UnsupportedOperationException("Unsupported selector $locator")
            }
        }

        // Determine the type of the locator and resolve accordingly.
        return when {
            // For CSS_PATH type, use resolveCSSSelectorAll0 to resolve the selector.
            cssSelector != null -> resolveCSSSelectorAll(cssSelector)

            l.type == Locator.Type.XPATH -> resolveXPathAll(l.selector)

            // For BACKEND_NODE_ID and FRAME_BACKEND_NODE_ID types, use the single resolver and wrap in list.
            l.type == Locator.Type.BACKEND_NODE_ID || l.type == Locator.Type.FRAME_BACKEND_NODE_ID -> {
                // Optimized: handle BACKEND_NODE_ID directly to avoid re-parsing in resolveSelector0
                if (l.type == Locator.Type.BACKEND_NODE_ID) {
                    val backendNodeId = l.selector.toIntOrNull()
                    if (backendNodeId != null) {
                        val node = resolveByBackendNodeId(backendNodeId)
                        if (node != null) listOf(node) else null
                    } else {
                        null
                    }
                } else {
                    // Fallback to resolveSelector0 for complex types like FRAME_BACKEND_NODE_ID
                    val nodeRef = doResolveLocator(l.selector)
                    if (nodeRef != null) listOf(nodeRef) else null
                }
            }

            else -> throw UnsupportedOperationException("Unsupported selector $locator")
        }
    }

    @Throws(ChromeDriverException::class)
    private suspend fun resolveCSSSelectorAll(selector: String): List<NodeRef>? {
        if (!isActive) return null
        val rootId = browserProtocol.getDocument().nodeId

        val nodeIds = try {
            browserProtocol.querySelectorAll(rootId, selector)
        } catch (e: CDPReturnError) {
            if (e.errorCode != -32000L) {
                logger.warn(
                    "Exception from domAPI?.querySelectorAll | selector={}, errorCode={}, errorMessage={} | {}",
                    selector, e.errorCode, e.errorMessage, e.brief()
                )
            }
            null
        } catch (e: Exception) {
            logger.warn("Unexpected exception from domAPI?.querySelectorAll ", e)
            null
        }

        if (nodeIds.isNullOrEmpty()) {
            return null
        }

        // Optimized: map directly to NodeRefs without resolving each one via CDP if possible.
        // querySelectorAll returns nodeIds which are already valid.
        return nodeIds.map { nodeId -> NodeRef(nodeId, 0, null) }
    }

    @Throws(ChromeDriverException::class)
    private suspend fun resolveXPathAll(xpath: String): List<NodeRef>? {
        require(xpath.startsWith("//"))

        return try {
            if (!isActive) return null
            browserProtocol.getDocument().nodeId

            val searchResult = browserProtocol.performSearch(xpath, true) ?: return null
            val nodeIds = if (searchResult.resultCount > 0) {
                // Retrieve all matching nodes
                val results =
                    browserProtocol.getSearchResults(
                        searchResult.searchId,
                        fromIndex = 0,
                        toIndex = searchResult.resultCount
                    )
                // Clean up search results to avoid resource leak
                try {
                    browserProtocol.discardSearchResults(searchResult.searchId)
                } catch (_: Exception) {
                }
                results
            } else {
                null
            }

            if (nodeIds.isNullOrEmpty()) {
                return null
            }

            return nodeIds.map { nodeId -> NodeRef(nodeId, 0, null) }
        } catch (e: CDPReturnError) {
            if (e.errorCode != -32000L) {
                logger.warn(
                    "Exception from domAPI?.performSearch/getSearchResults" +
                            " | xpath={}, errorCode={}, errorMessage={} | {}",
                    xpath, e.errorCode, e.errorMessage, e.brief()
                )
            }
            null
        } catch (e: Exception) {
            logger.warn("Unexpected exception from domAPI?.performSearch/getSearchResults ", e)
            null
        }
    }

    @Throws(ChromeDriverException::class)
    private suspend fun resolveLocator(locator: String): NodeRef? {
        return try {
            doResolveLocator(locator)
        } catch (e: FrameScopeException) {
            // Frame-scope failures are deterministic user-facing errors; they
            // must surface instead of degrading to element-not-found.
            throw e
        } catch (e: CDPReturnError) {
            // code: -32000 message: "Could not find node with given id"
            // This exception is expected, will change this log to debug
            if (e.errorCode != -32000L) {
                // -32000L is expected, no log needed
                logger.warn("Exception resolveSelector | {} {} | {}", e.errorCode, e.errorMessage, e.brief())
            } else {
                logger.debug("Element not found for selector '{}'", locator)
            }
            null
        } catch (e: Exception) {
            logger.warn("[Unexpected] exception ", e)
            null
        }
    }

    /**
     * Resolves a selector to a `NodeRef` object, which contains information about the DOM node.
     * This method supports two types of selectors:
     * 1. Regular CSS selector: Resolves to a `NodeRef` using `querySelectorOrNull`.
     * 2. Backend node ID selector: Resolves to a `NodeRef` using `resolveByBackendNodeId`.
     *
     * @param locator A string representing the locator. It can be:
     * - A CSS selector (e.g., "div.class", "#id").
     * - A backend node ID in the format "backend:123".
     * - A frame-backendNode int the format "fbn:FRAME_ID,123"
     *
     * @return A `NodeRef` object if the selector resolves successfully, or `null` if not found.
     *
     * @throws ChromeDriverException If an error occurs during the resolution process.
     */
    @Throws(ChromeDriverException::class)
    private suspend fun doResolveLocator(locator: String): NodeRef? {
        val (l, cssSelector) = normalizeLocator(locator, false) ?: return null

        // Ensure that the default locator type, CSS_PATH, has no prefix
        require(Locator.Type.CSS_PATH.text.isEmpty())

        // When the driver has switched into a frame, plain CSS selectors and
        // XPath expressions resolve inside that frame's document (see
        // FrameManager). Node-based locators (backend ids, fbn) already point
        // at specific nodes and are resolved unscoped.
        val scopedFrameId = frameManager?.activeFrameId
        if (scopedFrameId != null) {
            val nodeRef = when {
                cssSelector != null -> resolveCSSSelectorInFrame(scopedFrameId, cssSelector)

                l.type == Locator.Type.XPATH -> throw FrameScopeException(
                    "XPath selectors are not supported inside a selected frame (frame " +
                        "'${frameManager?.activeFrame?.label}'). Switch back to the main frame " +
                        "(`frameMain()`) or use a CSS selector."
                )

                else -> resolveUnscopedLocator(l)
            }
            return nodeRef
        }

        // Determine the type of the locator and resolve accordingly.
        val nodeRef = when {
            // For CSS_PATH type, use querySelectorOrNull to resolve the selector.
            cssSelector != null -> resolveCSSSelector(cssSelector)

            l.type == Locator.Type.XPATH -> resolveXPath(l.selector)

            // For BACKEND_NODE_ID type, parse the backend node ID and resolve it.
            l.type == Locator.Type.BACKEND_NODE_ID -> {
                val backendNodeId = l.selector.toIntOrNull()
                if (backendNodeId == null) {
                    logger.warn("Invalid backend node ID format: '{}'", l.selector)
                    return null
                }
                resolveByBackendNodeId(backendNodeId)
            }

            // For FRAME_BACKEND_NODE_ID type, extract the backend node ID and resolve it.
            l.type == Locator.Type.FRAME_BACKEND_NODE_ID -> {
                val backendNodeId = l.selector.substringAfterLast(",").toIntOrNull() ?: return null
                resolveByBackendNodeId(backendNodeId)
            }

            else -> throw UnsupportedOperationException("Unsupported selector ${l.selector}")
        }

        // Return the resolved NodeRef or null if resolution failed.
        return nodeRef
    }

    /**
     * Resolve a node-based locator (backend id / fbn) — frame scope does not
     * apply because these locators identify concrete DOM nodes.
     */
    @Throws(ChromeDriverException::class)
    private suspend fun resolveUnscopedLocator(l: Locator): NodeRef? {
        return when (l.type) {
            Locator.Type.BACKEND_NODE_ID -> {
                val backendNodeId = l.selector.toIntOrNull()
                if (backendNodeId == null) {
                    logger.warn("Invalid backend node ID format: '{}'", l.selector)
                    return null
                }
                resolveByBackendNodeId(backendNodeId)
            }

            Locator.Type.FRAME_BACKEND_NODE_ID -> {
                val backendNodeId = l.selector.substringAfterLast(",").toIntOrNull() ?: return null
                resolveByBackendNodeId(backendNodeId)
            }

            else -> throw UnsupportedOperationException("Unsupported selector ${l.selector}")
        }
    }

    /**
     * Resolve a CSS selector inside the document of the currently selected
     * frame. Same-process frames (same-origin iframes) expose their document
     * through the pierced DOM tree; cross-origin frames fail loudly with an
     * actionable error (see [FrameManager]).
     */
    @Throws(ChromeDriverException::class)
    private suspend fun resolveCSSSelectorInFrame(frameId: String, cssSelector: String): NodeRef? {
        if (!isActive) return null
        val frameManager = frameManager ?: return null

        val nodeId = try {
            frameManager.queryInFrame(frameId, cssSelector)
        } catch (e: FrameScopeException) {
            throw e
        } catch (e: CDPReturnError) {
            if (e.errorCode != -32000L) {
                logger.warn(
                    "Exception from querySelector in frame | selector={}, frameId={}, errorCode={}, errorMessage={} | {}",
                    cssSelector, frameId, e.errorCode, e.errorMessage, e.brief()
                )
            }
            0
        } catch (e: Exception) {
            logger.warn("Unexpected exception from querySelector in frame | selector={}", cssSelector, e)
            0
        }

        if (nodeId == 0) {
            logger.debug("Scoped query missed | frameId={} | selector={}", frameId, cssSelector)
            return null
        }

        logger.debug("Scoped query hit | frameId={} | selector={} | nodeId={}", frameId, cssSelector, nodeId)
        return NodeRef(nodeId, 0, null)
    }

    /**
     * Resolve all CSS-selector matches inside the document of the currently
     * selected frame (see [resolveCSSSelectorInFrame]).
     */
    @Throws(ChromeDriverException::class)
    private suspend fun resolveCSSSelectorAllInFrame(frameId: String, cssSelector: String): List<NodeRef>? {
        if (!isActive) return null
        val frameManager = frameManager ?: return null

        val nodeIds = try {
            frameManager.queryAllInFrame(frameId, cssSelector)
        } catch (e: FrameScopeException) {
            throw e
        } catch (e: CDPReturnError) {
            if (e.errorCode != -32000L) {
                logger.warn(
                    "Exception from querySelectorAll in frame | selector={}, frameId={}, errorCode={}, errorMessage={} | {}",
                    cssSelector, frameId, e.errorCode, e.errorMessage, e.brief()
                )
            }
            emptyList()
        } catch (e: Exception) {
            logger.warn("Unexpected exception from querySelectorAll in frame | selector={}", cssSelector, e)
            emptyList()
        }

        if (nodeIds.isEmpty()) {
            return null
        }

        return nodeIds.map { nodeId -> NodeRef(nodeId, 0, null) }
    }

    @Throws(ChromeDriverException::class)
    private suspend fun resolveByBackendNodeId(backendNodeId: Int): NodeRef? = resolveNode(null, backendNodeId)

    /**
     * Resolves a backend node ID to a regular node ID.
     *
     * @param backendNodeId The backend node ID
     * @return nodeId or null if resolution fails
     */
    @Throws(ChromeDriverException::class)
    private suspend fun resolveNode(nodeId: Int?, backendNodeId: Int?): NodeRef? {
        // If we already have a nodeId, verify it's valid or just use it.
        // However, we need to return a NodeRef which might need backendNodeId if not provided.
        // Usually, resolveNode is called when we want to ensure we have a valid nodeId for a backendNodeId,
        // or we have a nodeId and want to get a stable reference.

        return try {
            // Protocol return error: "-32000/Either nodeId or backendNodeId must be specified."
            val remoteObject = if (nodeId != null && nodeId > 0) {
                // If nodeId is provided, we might not need to resolve it again unless we want to verify it exists
                // But the original code resolved it. Let's keep the behavior but check if it's necessary.
                // Resolving a nodeId returns a RemoteObject.
                browserProtocol.resolveNodeByNodeId(nodeId)
            } else if (backendNodeId != null && backendNodeId > 0) {
                browserProtocol.resolveNodeByBackendNodeId(backendNodeId)
            } else {
                return null
            }

            val tempObjectId = remoteObject.objectId
            if (tempObjectId == null) {
                logger.warn("Failed to resolve node: {}, {}", nodeId, backendNodeId)
                return null
            }

            // Use DOM.requestNode to get the nodeId from the runtime object.
            // This is crucial when we started with a backendNodeId.
            // When started with nodeId, it should return the same nodeId.
            val resolvedNodeId = browserProtocol.requestNode(tempObjectId) ?: 0

            // Release the remote object to avoid memory leaks
            try {
                browserProtocol.releaseObject(tempObjectId)
            } catch (_: Exception) {
            }

            if (resolvedNodeId == 0) {
                return null
            }

            // Do NOT cache objectId; return only ids that are stable across calls
            NodeRef(resolvedNodeId, backendNodeId ?: 0, null)
        } catch (e: Exception) {
            logger.warn("Exception resolving backend node ID {}: {}", backendNodeId, e.message)
            null
        }
    }

    @Throws(ChromeDriverException::class)
    private suspend fun resolveCSSSelector(cssSelector: String): NodeRef? {
        if (!isActive) return null
        val rootId = browserProtocol.getDocument().nodeId

        val nodeId = try {
            browserProtocol.querySelector(rootId, cssSelector)
        } catch (e: CDPReturnError) {
            // code: -32000 message: "Could not find node with given id"
            // This exception is expected, will change this log to debug
            if (e.errorCode != -32000L) {
                logger.warn(
                    "Exception from domAPI?.querySelector | selector={}, errorCode={}, errorMessage={} | {}",
                    cssSelector, e.errorCode, e.errorMessage, e.brief()
                )
            }
            null
        } catch (e: Exception) {
            logger.warn("Unexpected exception from domAPI?.querySelector ", e)
            null
        }

        if (nodeId == null || nodeId == 0) {
            return null
        }

        return NodeRef(nodeId, 0, null)
    }

    /**
     * Resolves an XPath expression to a NodeRef.
     *
     * Uses DOM.performSearch and DOM.getSearchResults to locate the node,
     * then describes and resolves it to obtain stable node identifiers.
     *
     * @param xpath The XPath expression to resolve.
     * @return A NodeRef containing the resolved node identifiers, or null if not found.
     * @throws ChromeDriverException If an unexpected CDP error occurs.
     */
    @Throws(ChromeDriverException::class)
    private suspend fun resolveXPath(xpath: String): NodeRef? {
        require(xpath.startsWith("//"))

        val nodeId = try {
            if (!isActive) return null
            browserProtocol.getDocument().nodeId

            val searchResult = browserProtocol.performSearch(xpath, true) ?: return null
            val nodeId = if (searchResult.resultCount > 0) {
                // Only retrieve the first matching node if results exist
                val results = browserProtocol.getSearchResults(searchResult.searchId, fromIndex = 0, toIndex = 1)
                // Clean up search results to avoid resource leak
                try {
                    browserProtocol.discardSearchResults(searchResult.searchId)
                } catch (_: Exception) {
                }
                results.firstOrNull()
            } else {
                null
            }
            nodeId
        } catch (e: CDPReturnError) {
            // code: -32000 message: "Could not find node with given id"
            // code: -32000 message: "Invalid search result range" (when toIndex > resultCount)
            // These exceptions are expected when element not found
            if (e.errorCode != -32000L) {
                logger.warn(
                    "Exception from resolveXPath" +
                            " | xpath={}, errorCode={}, errorMessage={} | {}",
                    xpath, e.errorCode, e.errorMessage, e.brief()
                )
            }
            null
        } catch (e: Exception) {
            logger.warn("Unexpected exception from domAPI?.performSearch/getSearchResults ", e)
            null
        }

        if (nodeId == null || nodeId == 0) {
            return null
        }

        return NodeRef(nodeId, 0, null)
    }
}
