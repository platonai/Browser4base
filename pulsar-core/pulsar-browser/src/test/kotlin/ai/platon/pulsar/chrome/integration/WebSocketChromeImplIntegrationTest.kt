package ai.platon.pulsar.chrome.integration

import ai.platon.pulsar.api.BrowserProtocol
import ai.platon.pulsar.api.ChromeOptions
import ai.platon.pulsar.api.LauncherOptions
import ai.platon.pulsar.chrome.ChromeLauncher
import ai.platon.pulsar.chrome.protocol.transport.WebSocketChromeImpl
import ai.platon.pulsar.common.browser.BrowserFiles
import ai.platon.pulsar.common.browser.BrowserFiles.CDP_URL_FILE_NAME
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Drives a real browser from its **browser-level WebSocket URL alone**.
 *
 * This is the code path Chrome's built-in remote debugging
 * (`chrome://inspect/#remote-debugging`) needs: `DevToolsActivePort` publishes
 * exactly this URL shape while every `/json*` request answers 404, so the
 * HTTP-based `ChromeImpl` can never find a page target. Nothing below reads
 * `/json/version` or `/json/list` — the input is the CDP URL file the launcher
 * writes, which carries the same `ws://<host>:<port>/devtools/browser/<uuid>`
 * form.
 */
@Tag("Integration")
class WebSocketChromeImplIntegrationTest {

    @Test
    fun drivesARealBrowserFromItsBrowserWebSocketOnly() {
        val launchOptions = ChromeOptions().apply { headless = true }
        val userDataDir = BrowserFiles.computeRandomTmpContextDir()
        val cdpUrlPath = userDataDir.resolveSibling(CDP_URL_FILE_NAME)

        ChromeLauncher(userDataDir, options = LauncherOptions()).use { launcher ->
            launcher.launch(launchOptions)

            val browserWsUrl = Files.readString(cdpUrlPath).trim()
            assertTrue(browserWsUrl.startsWith("ws://"), "unexpected CDP url: $browserWsUrl")
            assertTrue(browserWsUrl.contains("/devtools/browser/"), "unexpected CDP url: $browserWsUrl")

            val httpFreeChrome = WebSocketChromeImpl(
                host = WebSocketChromeImpl.hostOf(browserWsUrl),
                port = WebSocketChromeImpl.portOf(browserWsUrl),
                browserWebSocketUrl = browserWsUrl,
            )

            httpFreeChrome.use { chrome ->
                assertTrue(chrome.canConnect(), "Browser.getVersion must answer over the browser socket")

                val version = chrome.version
                assertTrue(!version.browser.isNullOrBlank(), "no browser identity: ${version.browser}")
                assertEquals(browserWsUrl, version.webSocketDebuggerUrl)

                chrome.createTab("about:blank")
                val tabs = chrome.listTabs()
                assertTrue(tabs.isNotEmpty(), "the tab just created must be discovered over the socket")

                val tab = tabs.first { candidate -> candidate.isPageType() }
                assertTrue(tab.id.isNotBlank(), "a discovered page tab needs a target id")
                assertEquals(
                    "ws://${chrome.host}:${chrome.port}/devtools/page/${tab.id}",
                    tab.webSocketDebuggerUrl
                )
                assertFalse(
                    tab.webSocketDebuggerUrl!!.contains("/devtools/browser"),
                    "the page socket must not reuse the browser socket: ${tab.webSocketDebuggerUrl}"
                )

                val browserProtocol = BrowserProtocol.create(chrome.createDevTools(tab))
                assertTrue(runBlocking { browserProtocol.isBrowserAlive() }, "browser-level CDP must answer")
                assertTrue(runBlocking { browserProtocol.isTargetAlive() }, "Target.getTargets must answer")
                assertTrue(runBlocking { browserProtocol.isV8Alive() }, "the page socket must execute JavaScript")

                runBlocking { browserProtocol.navigate("https://example.com/") }
                val title = runBlocking {
                    browserProtocol.evaluate("document.title", returnByValue = true).result?.value
                }
                assertTrue(
                    title?.toString()?.contains("Example", ignoreCase = true) == true,
                    "navigation over the synthesized page socket did not take effect: $title"
                )

                // Tab lifecycle over the same browser socket. Both ends are
                // asynchronous — `Target.createTarget`/`Target.closeTarget`
                // return before `Target.getTargets` reflects the change — so
                // wait for the state instead of asserting on the instant after
                // the command.
                val extra = chrome.createTab("about:blank")
                chrome.activateTab(extra)
                assertTrue(
                    awaitTabListing(chrome, extra.id, present = true),
                    "the tab created over the browser socket must be listed"
                )
                chrome.closeTab(extra)
                assertTrue(
                    awaitTabListing(chrome, extra.id, present = false),
                    "closeTab must remove target ${extra.id}"
                )
            }
        }
    }

    /**
     * Waits until [tabId] is listed (`present = true`) or gone
     * (`present = false`) in `Target.getTargets`.
     *
     * CDP target creation and destruction are asynchronous: `closeTarget`
     * returns as soon as the browser accepts the command, and the target
     * disappears from `getTargets` a moment later. Asserting on the instant
     * after the call is a race a loaded CI runner loses — the HTTP-based
     * [ai.platon.pulsar.chrome.protocol.transport.ChromeImpl.closeTab] has the
     * same fire-and-forget contract.
     */
    private fun awaitTabListing(
        chrome: WebSocketChromeImpl,
        tabId: String,
        present: Boolean,
        timeoutMillis: Long = 15_000,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            if (chrome.listTabs().any { candidate -> candidate.id == tabId } == present) {
                return true
            }
            if (System.currentTimeMillis() >= deadline) {
                return false
            }
            Thread.sleep(200)
        }
    }
}
