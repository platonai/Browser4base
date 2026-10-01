package ai.platon.pulsar.chrome.protocol.transport

import ai.platon.pulsar.api.model.BrowserTab
import ai.platon.pulsar.api.model.ChromeVersion
import ai.platon.pulsar.api.model.DevToolsConfig
import ai.platon.pulsar.chrome.ChromeService
import ai.platon.pulsar.chrome.RemoteDevTools
import ai.platon.pulsar.chrome.Transport
import ai.platon.pulsar.chrome.util.ChromeIOException
import ai.platon.pulsar.common.getLogger
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Minimal request/response CDP client for a **browser-level** WebSocket.
 *
 * [KtorTransport] carries the frames; what discovery needs on top is id
 * correlation, which is all this class adds — no domain proxies, no event
 * listeners. It is deliberately small so it can run before a
 * [ChromeDevToolsImpl] exists (that is, before any page is known).
 */
internal class CdpWsRpc(
    private val url: String,
    private val defaultTimeoutMillis: Long = 10_000,
) : AutoCloseable {

    private val logger = getLogger(this)
    private val ids = AtomicLong()
    private val pending = ConcurrentHashMap<Long, CompletableFuture<JsonNode>>()
    private val closed = AtomicBoolean()

    private val transport: Transport = KtorTransport.create(URI.create(url)).also { transport ->
        transport.addMessageHandler(Consumer { text -> onMessage(text) })
    }

    val isOpen: Boolean get() = !closed.get() && transport.isOpen

    /**
     * Send one CDP command and wait for its reply.
     *
     * @throws ChromeIOException when the socket is closed, the reply does not
     *   arrive in time, or CDP returns an `error` object.
     */
    fun command(
        method: String,
        params: Map<String, Any?> = emptyMap(),
        timeoutMillis: Long = defaultTimeoutMillis,
    ): JsonNode {
        if (!isOpen) {
            throw ChromeIOException("Web socket is not open | $method | $url", null, false)
        }

        val id = ids.incrementAndGet()
        val future = CompletableFuture<JsonNode>()
        pending[id] = future
        val payload = MAPPER.writeValueAsString(mapOf("id" to id, "method" to method, "params" to params))

        try {
            runBlocking(Dispatchers.IO) { transport.send(payload) }
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            throw ChromeIOException("Timed out waiting for $method | $url", e, isOpen)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ChromeIOException("Interrupted waiting for $method | $url", e, isOpen)
        } catch (e: java.util.concurrent.ExecutionException) {
            val cause = e.cause ?: e
            throw ChromeIOException("$method failed: ${cause.message} | $url", cause, isOpen)
        } finally {
            pending.remove(id)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            pending.values.forEach { it.completeExceptionally(ChromeIOException("Web socket closed | $url")) }
            pending.clear()
            runCatching { transport.close() }
                .onFailure { logger.debug("Failed to close the browser web socket | {}", url) }
        }
    }

    override fun toString(): String = "CdpWsRpc($url, open=$isOpen)"

    private fun onMessage(text: String) {
        val node = runCatching { MAPPER.readTree(text) }.getOrNull() ?: return
        val id = node.get("id")?.asLong() ?: return
        val future = pending.remove(id) ?: return

        val error = node.get("error")
        if (error != null && !error.isNull) {
            val code = error.get("code")?.asLong() ?: 0
            val message = error.get("message")?.asText() ?: "unknown CDP error"
            future.completeExceptionally(ChromeIOException("CDP error $code: $message | $url"))
        } else {
            future.complete(node.get("result") ?: MAPPER.createObjectNode())
        }
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}

/**
 * A [ChromeService] for a browser that publishes a **browser-level CDP
 * WebSocket** but no DevTools HTTP discovery endpoints.
 *
 * `ChromeImpl` resolves everything through `/json/version` and `/json/list`,
 * which Chrome's built-in remote debugging
 * (`chrome://inspect/#remote-debugging`, recent Chrome versions) does not serve:
 * it writes `DevToolsActivePort` (port + `/devtools/browser/<uuid>`) and answers
 * every `/json*` request with HTTP 404, so no page target can be discovered over
 * HTTP. The same shape appears in other CDP providers that hand out a
 * `ws://…/devtools/browser/<uuid>` URL directly.
 *
 * This implementation never touches HTTP:
 *
 * - the browser connection is the given WebSocket URL,
 * - tabs come from `Target.getTargets` over that socket,
 * - each page is driven through `ws://<host>:<port>/devtools/page/<targetId>`,
 *   the standard per-page DevTools socket that Chrome serves in both modes,
 * - `version` comes from `Browser.getVersion`.
 *
 * @param host The host of the CDP endpoint, parsed from the WebSocket URL.
 * @param port The port of the CDP endpoint, parsed from the WebSocket URL.
 * @param browserWebSocketUrl The browser-level WebSocket URL.
 */
internal class WebSocketChromeImpl(
    override val host: String,
    override val port: Int,
    val browserWebSocketUrl: String,
) : ChromeService {

    private val logger = getLogger(this)
    private val closed = AtomicBoolean()
    private val remoteDevTools = ConcurrentHashMap<String, RemoteDevTools>()

    @Volatile
    private var rpc: CdpWsRpc? = null

    override val isActive: Boolean get() = !closed.get()

    override val version: ChromeVersion get() = browserVersion()

    override fun canConnect(): Boolean {
        if (closed.get()) {
            return false
        }
        // A live RPC connection is the only proof that counts: any listener can
        // accept TCP, but only a browser answers Browser.getVersion.
        val client = rpc
        return if (client != null && client.isOpen) {
            runCatching { client.command("Browser.getVersion", timeoutMillis = 2_000) }.isSuccess
        } else {
            runCatching { browserVersion() }.isSuccess
        }
    }

    /**
     * Page targets of the browser, as reported by `Target.getTargets`.
     *
     * Only `page` targets are returned — the DevTools HTTP API filters the same
     * way. Each tab carries a synthesized `webSocketDebuggerUrl` pointing at the
     * browser's page socket, which [createDevTools] uses.
     */
    override fun listTabs(): Array<BrowserTab> {
        return try {
            val result = browserCommand("Target.getTargets")
            val targetInfos = result.get("targetInfos") ?: return emptyArray()
            targetInfos.mapNotNull { browserTabOf(it) }.toTypedArray()
        } catch (e: ChromeIOException) {
            if (isActive) throw e else emptyArray()
        }
    }

    override fun createTab(): BrowserTab = createTab(ABOUT_BLANK_PAGE)

    override fun createTab(url: String): BrowserTab {
        val result = browserCommand("Target.createTarget", mapOf("url" to url))
        val targetId = result.get("targetId")?.asText()
            ?: throw ChromeIOException("Target.createTarget returned no targetId | $url")

        // The new target may not be listed yet; fall back to a tab built from
        // the request so callers always get a usable handle.
        return listTabs().firstOrNull { it.id == targetId } ?: BrowserTab().apply {
            id = targetId
            type = BrowserTab.PAGE_TYPE
            this.url = url
            webSocketDebuggerUrl = pageWebSocketUrl(host, port, targetId)
        }
    }

    override fun activateTab(tab: BrowserTab) {
        browserCommand("Target.activateTarget", mapOf("targetId" to tab.id))
    }

    override fun closeTab(tab: BrowserTab) {
        if (!isActive) {
            return
        }
        runCatching { browserCommand("Target.closeTarget", mapOf("targetId" to tab.id)) }
            .onFailure { logger.debug("Failed to close tab over the browser web socket | {}", it.message) }
        remoteDevTools.remove(tab.id)?.runCatching { close() }
    }

    override fun createDevTools(tab: BrowserTab, config: DevToolsConfig): RemoteDevTools {
        return remoteDevTools.computeIfAbsent(tab.id) { createDevTools0(tab, config) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        remoteDevTools.values.forEach { it.runCatching { close() } }
        remoteDevTools.clear()
        rpc?.runCatching { close() }
        rpc = null
    }

    override fun toString(): String = "WebSocketChromeImpl($browserWebSocketUrl)"

    /**
     * `Browser.getVersion` reshaped into the `/json/version` payload, so
     * [ChromeVersion.webSocketDebuggerUrl] keeps carrying the browser socket and
     * every consumer of the version keeps working unchanged.
     */
    private fun browserVersion(): ChromeVersion {
        val result = browserCommand("Browser.getVersion")
        val payload = mapOf(
            "Browser" to (result.get("product")?.asText() ?: ""),
            "Protocol-Version" to (result.get("protocolVersion")?.asText() ?: ""),
            "User-Agent" to (result.get("userAgent")?.asText() ?: ""),
            "V8-Version" to (result.get("jsVersion")?.asText() ?: ""),
            "webSocketDebuggerUrl" to browserWebSocketUrl,
        )
        return MAPPER.readerFor(ChromeVersion::class.java)
            .readValue(MAPPER.writeValueAsString(payload))
    }

    private fun browserTabOf(node: JsonNode): BrowserTab? = browserTabOf(node, host, port)

    private fun createDevTools0(tab: BrowserTab, config: DevToolsConfig): RemoteDevTools {
        val browserTransport = KtorTransport.create(URI.create(browserWebSocketUrl))
        val pageUrl = tab.webSocketDebuggerUrl?.takeIf { it.isNotBlank() }
            ?: pageWebSocketUrl(host, port, tab.id)
        val pageTransport = KtorTransport.create(URI.create(pageUrl))
        return ChromeDevToolsImpl(browserTransport, pageTransport, config)
    }

    private fun browserCommand(method: String, params: Map<String, Any?> = emptyMap()): JsonNode {
        if (closed.get()) {
            throw ChromeIOException("Chrome service is closed | $browserWebSocketUrl")
        }
        val client = synchronized(this) {
            rpc?.takeIf { it.isOpen } ?: CdpWsRpc(browserWebSocketUrl).also { rpc = it }
        }
        return client.command(method, params)
    }

    companion object {
        private val MAPPER = ObjectMapper()

        /** Every character an external key — and hence a directory name — may not contain. */
        private val UNSAFE_HOST_CHAR = Regex("[^A-Za-z0-9.-]")

        const val ABOUT_BLANK_PAGE = "about:blank"

        /** Default CDP port when a WebSocket URL carries none. */
        const val DEFAULT_CDP_PORT = 9222

        /** `ws://<host>:<port>/devtools/page/<targetId>` — the per-page socket. */
        fun pageWebSocketUrl(host: String, port: Int, targetId: String): String =
            "ws://$host:$port/devtools/page/$targetId"

        /** Host of a browser-level CDP WebSocket URL, defaulting to IPv4 loopback. */
        fun hostOf(browserWebSocketUrl: String): String =
            runCatching { URI.create(browserWebSocketUrl.trim()).host }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "127.0.0.1"

        /** Port of a browser-level CDP WebSocket URL, defaulting to [DEFAULT_CDP_PORT]. */
        fun portOf(browserWebSocketUrl: String): Int =
            runCatching { URI.create(browserWebSocketUrl.trim()).port }
                .getOrNull()
                ?.takeIf { it > 0 }
                ?: DEFAULT_CDP_PORT

        /** `host:port` of a browser-level CDP WebSocket URL. */
        fun hostPortOf(browserWebSocketUrl: String): String =
            "${hostOf(browserWebSocketUrl)}:${portOf(browserWebSocketUrl)}"

        /**
         * Path-safe external identity key for a browser socket, e.g.
         * `attach.ws.127.0.0.1.9222`.
         *
         * The key becomes a directory name under the external context dir
         * (`cx.ext.<key>`), so it must satisfy the external-key grammar of
         * `ProfilePaths`: ASCII letters, digits, `_`, `-` and `.` only. Hence dots
         * instead of the `host:port` form [hostPortOf] returns, and hence
         * [fileSafeHost] flattens an IPv6 literal (`[::1]` -> `--1`).
         */
        fun externalKeyOf(browserWebSocketUrl: String): String =
            "attach.ws.${fileSafeHost(hostOf(browserWebSocketUrl))}.${portOf(browserWebSocketUrl)}"

        /**
         * Flatten a URL host into an external-key-safe token by replacing every
         * character the key grammar rejects with `-`, so `[::1]` becomes `--1`.
         * Only the key is flattened: the host kept for the page socket URLs
         * (`ws://[::1]:9222/...`) must stay a legal URL host, which is what
         * [hostOf] returns.
         */
        private fun fileSafeHost(host: String): String =
            UNSAFE_HOST_CHAR.replace(host.removeSurrounding("[", "]"), "-")

        /**
         * Map one `Target.getTargets` entry to a [BrowserTab].
         *
         * Returns `null` for every non-page target (service workers, iframes,
         * browser UI) — the same filter the DevTools HTTP `/json/list` applies.
         * The `webSocketDebuggerUrl` is synthesized because `Target.getTargets`
         * does not report one.
         */
        fun browserTabOf(node: JsonNode, host: String, port: Int): BrowserTab? {
            val type = node.get("type")?.asText() ?: return null
            if (type != BrowserTab.PAGE_TYPE) {
                return null
            }
            val targetId = node.get("targetId")?.asText() ?: return null
            return BrowserTab().apply {
                id = targetId
                this.type = type
                title = node.get("title")?.asText()
                url = node.get("url")?.asText()
                webSocketDebuggerUrl = pageWebSocketUrl(host, port, targetId)
            }
        }
    }
}
