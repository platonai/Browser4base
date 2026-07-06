package ai.platon.pulsar.chrome.protocol.transport

/**
 * Abstraction over a WebSocket connection to the Browser4 Chrome Extension.
 *
 * This interface lives in pulsar-core so that [ExtensionChromeService] and
 * [ExtensionDevToolsService] do not depend on Spring WebSocket types.  The
 * concrete implementation in pulsar-rest wraps a
 * `org.springframework.web.socket.WebSocketSession`.
 *
 * Incoming message routing is handled externally via
 * [ExtensionChromeService.handleIncomingMessage] — this interface only
 * provides outgoing send capability.
 */
interface ExtensionMessageSender {
    /** True if the underlying connection is still open. */
    val isOpen: Boolean

    /** Send a text message to the extension. */
    fun sendMessage(text: String)

    /** Close the connection. */
    fun close()
}
