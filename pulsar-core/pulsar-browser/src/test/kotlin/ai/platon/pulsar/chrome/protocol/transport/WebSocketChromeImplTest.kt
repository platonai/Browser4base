package ai.platon.pulsar.chrome.protocol.transport

import ai.platon.pulsar.api.model.BrowserTab
import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the HTTP-free CDP discovery helpers used by
 * [WebSocketChromeImpl] — the path that serves browsers publishing a
 * browser-level WebSocket but no `/json*` endpoints (Chrome's built-in remote
 * debugging).
 */
class WebSocketChromeImplTest {

    private val mapper = ObjectMapper()

    @Test
    fun pageWebSocketUrlUsesTheStandardPageSocketPath() {
        assertEquals(
            "ws://127.0.0.1:9222/devtools/page/ABC123",
            WebSocketChromeImpl.pageWebSocketUrl("127.0.0.1", 9222, "ABC123")
        )
    }

    @Test
    fun hostAndPortComeFromTheBrowserWebSocketUrl() {
        val url = "ws://127.0.0.1:51343/devtools/browser/8b91cacf-d8aa-4fa3-8b45-687c7de7af8a"
        assertEquals("127.0.0.1", WebSocketChromeImpl.hostOf(url))
        assertEquals(51343, WebSocketChromeImpl.portOf(url))
        assertEquals("127.0.0.1:51343", WebSocketChromeImpl.hostPortOf(url))
    }

    @Test
    fun hostAndPortFallBackWhenTheUrlOmitsThem() {
        assertEquals("127.0.0.1", WebSocketChromeImpl.hostOf("ws:///devtools/browser/x"))
        assertEquals(WebSocketChromeImpl.DEFAULT_CDP_PORT, WebSocketChromeImpl.portOf("ws://localhost/devtools/browser/x"))
        // A malformed URL must not throw: attach reports the failure instead.
        assertEquals("127.0.0.1", WebSocketChromeImpl.hostOf("not a url"))
        assertEquals(WebSocketChromeImpl.DEFAULT_CDP_PORT, WebSocketChromeImpl.portOf("not a url"))
    }

    @Test
    fun browserTabOfMapsPageTargetsAndSynthesizesThePageSocket() {
        val node = mapper.readTree(
            """{"targetId":"A1B2","type":"page","title":"Example","url":"https://example.com/","attached":false}"""
        )

        val tab = WebSocketChromeImpl.browserTabOf(node, "127.0.0.1", 9222)

        requireNotNull(tab) { "a page target must map to a BrowserTab" }
        assertEquals("A1B2", tab.id)
        assertEquals("Example", tab.title)
        assertEquals("https://example.com/", tab.url)
        assertTrue(tab.isPageType())
        assertEquals("ws://127.0.0.1:9222/devtools/page/A1B2", tab.webSocketDebuggerUrl)
    }

    @Test
    fun browserTabOfIgnoresNonPageTargets() {
        for (type in listOf("service_worker", "iframe", "browser", "other", "shared_worker")) {
            val node = mapper.readTree("""{"targetId":"X","type":"$type","url":"chrome://x"}""")
            assertNull(
                WebSocketChromeImpl.browserTabOf(node, "127.0.0.1", 9222),
                "target type '$type' must not be attached as a page"
            )
        }
    }

    @Test
    fun browserTabOfRequiresATargetIdAndType() {
        assertNull(WebSocketChromeImpl.browserTabOf(mapper.readTree("""{"type":"page"}"""), "127.0.0.1", 9222))
        assertNull(WebSocketChromeImpl.browserTabOf(mapper.readTree("""{"targetId":"A"}"""), "127.0.0.1", 9222))
        assertNull(WebSocketChromeImpl.browserTabOf(mapper.readTree("""{}"""), "127.0.0.1", 9222))
    }

    @Test
    fun browserTabDefaultsMatchTheDevToolsHttpShape() {
        // `type` is what listTabs filters on; keep it aligned with the constant
        // the HTTP-based implementation relies on.
        assertEquals("page", BrowserTab.PAGE_TYPE)
        assertEquals("about:blank", WebSocketChromeImpl.ABOUT_BLANK_PAGE)
        assertFalse(WebSocketChromeImpl.pageWebSocketUrl("h", 1, "t").contains("/devtools/browser"))
    }
}
