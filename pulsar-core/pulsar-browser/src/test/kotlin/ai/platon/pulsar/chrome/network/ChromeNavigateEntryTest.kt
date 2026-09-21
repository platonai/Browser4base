package ai.platon.pulsar.chrome.network

import ai.platon.cdt.kt.protocol.events.network.RequestWillBeSent
import ai.platon.cdt.kt.protocol.events.page.FrameNavigated
import ai.platon.cdt.kt.protocol.types.network.Initiator
import ai.platon.cdt.kt.protocol.types.network.InitiatorType
import ai.platon.cdt.kt.protocol.types.network.Request
import ai.platon.cdt.kt.protocol.types.network.RequestReferrerPolicy
import ai.platon.cdt.kt.protocol.types.network.ResourcePriority
import ai.platon.cdt.kt.protocol.types.network.ResourceType
import ai.platon.cdt.kt.protocol.types.page.CrossOriginIsolatedContextType
import ai.platon.cdt.kt.protocol.types.page.Frame
import ai.platon.cdt.kt.protocol.types.page.NavigationType
import ai.platon.cdt.kt.protocol.types.page.SecureContextType
import ai.platon.pulsar.api.model.NavigateEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Regression tests for issue #9.
 *
 * `NavigateEntry.mainRequestId` used to be populated only when the `DOCUMENT` network request
 * happened to arrive *before* `Page.frameNavigated`, because the check was
 * `!mainFrameReceived && type == DOCUMENT`. Whenever the frame event won the race the record was
 * suppressed for good, so callers guarding a capture on `mainRequestId` rejected legitimate
 * navigations (notably redirects) and retired healthy drivers.
 */
class ChromeNavigateEntryTest {

    private fun request(
        requestId: String,
        url: String,
        type: ResourceType? = ResourceType.DOCUMENT,
        frameId: String? = null,
    ) = RequestWillBeSent(
        requestId = requestId,
        loaderId = "loader-1",
        documentURL = url,
        request = Request(
            url = url,
            method = "GET",
            headers = mapOf("accept" to "text/html"),
            initialPriority = ResourcePriority.HIGH,
            referrerPolicy = RequestReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN,
        ),
        timestamp = 1.0,
        wallTime = 1.0,
        initiator = Initiator(type = InitiatorType.OTHER),
        redirectResponse = null,
        type = type,
        frameId = frameId,
        hasUserGesture = false,
    )

    private fun frame(id: String, parentId: String? = null, url: String = "https://example.com/") = Frame(
        id = id,
        parentId = parentId,
        loaderId = "loader-1",
        url = url,
        domainAndRegistry = "example.com",
        securityOrigin = "https://example.com",
        mimeType = "text/html",
        secureContextType = SecureContextType.SECURE,
        crossOriginIsolatedContextType = CrossOriginIsolatedContextType.NOT_ISOLATED,
        gatedAPIFeatures = emptyList(),
    )

    private fun entry(url: String = "https://example.com/") = NavigateEntry(url)

    @Test
    @DisplayName("main request is recorded when the document request precedes the frame event")
    fun testMainRequestRecordedWhenRequestComesFirst() {
        val navigateEntry = entry()
        val chromeEntry = ChromeNavigateEntry(navigateEntry)

        chromeEntry.updateStateBeforeRequestSent(request("R1", "https://example.com/", frameId = "F1"))
        chromeEntry.updateStateAfterFrameNavigated(FrameNavigated(frame("F1"), NavigationType.NAVIGATION))

        assertEquals("R1", navigateEntry.mainRequestId)
        assertEquals("https://example.com/", navigateEntry.mainDocumentUrl)
        assertTrue(navigateEntry.mainRequestReceived)
    }

    @Test
    @DisplayName("main request is still recorded when the frame event precedes the document request (issue #9)")
    fun testMainRequestRecordedWhenFrameComesFirst() {
        val navigateEntry = entry()
        val chromeEntry = ChromeNavigateEntry(navigateEntry)

        // The frame event wins the race — this is what used to suppress the record permanently.
        chromeEntry.updateStateAfterFrameNavigated(FrameNavigated(frame("F1"), NavigationType.NAVIGATION))
        assertTrue(navigateEntry.mainFrameReceived)

        chromeEntry.updateStateBeforeRequestSent(request("R1", "https://example.com/", frameId = "F1"))

        assertEquals("R1", navigateEntry.mainRequestId, "the main-document request must not be dropped")
        assertTrue(navigateEntry.mainRequestReceived)
    }

    @Test
    @DisplayName("a redirect chain keeps the original request id and follows the document url")
    fun testRedirectChainKeepsRequestIdAndFollowsUrl() {
        val navigateEntry = entry("https://example.com/start?psc=1")
        val chromeEntry = ChromeNavigateEntry(navigateEntry)

        chromeEntry.updateStateBeforeRequestSent(request("R1", "https://example.com/start?psc=1", frameId = "F1"))
        // The 302 hop: same frame, new url.
        chromeEntry.updateStateBeforeRequestSent(request("R2", "https://example.com/start?th=1", frameId = "F1"))

        assertEquals("R1", navigateEntry.mainRequestId, "the request that started the navigation is kept")
        assertEquals(
            "https://example.com/start?th=1",
            navigateEntry.mainDocumentUrl,
            "mainDocumentUrl must end at the url the document commits from"
        )
        assertEquals("https://example.com/start?psc=1", navigateEntry.userTypedUrl, "the typed url is untouched")
    }

    @Test
    @DisplayName("a subframe document request is not mistaken for the main document")
    fun testSubframeDocumentRequestIsIgnored() {
        val navigateEntry = entry()
        val chromeEntry = ChromeNavigateEntry(navigateEntry)

        chromeEntry.updateStateAfterFrameNavigated(FrameNavigated(frame("F1"), NavigationType.NAVIGATION))
        chromeEntry.updateStateAfterFrameNavigated(FrameNavigated(frame("F2", parentId = "F1"), NavigationType.NAVIGATION))

        // An iframe navigation is a DOCUMENT request too, but it belongs to F2.
        chromeEntry.updateStateBeforeRequestSent(request("R-sub", "https://ads.example.com/", frameId = "F2"))

        assertFalse(navigateEntry.mainRequestReceived, "an iframe document request must not become the main request")
        assertEquals("", navigateEntry.mainRequestId)
    }

    @Test
    @DisplayName("sub-resource requests never become the main document request")
    fun testSubResourceRequestsAreIgnored() {
        val navigateEntry = entry()
        val chromeEntry = ChromeNavigateEntry(navigateEntry)

        for (type in listOf(ResourceType.IMAGE, ResourceType.SCRIPT, ResourceType.STYLESHEET, ResourceType.XHR)) {
            chromeEntry.updateStateBeforeRequestSent(request("R-$type", "https://example.com/a.png", type = type))
        }

        assertFalse(navigateEntry.mainRequestReceived)
        assertEquals("", navigateEntry.mainRequestId)
    }

    @Test
    @DisplayName("mainRequestReceived tracks mainRequestId")
    fun testMainRequestReceivedTracksRequestId() {
        val navigateEntry = entry()

        assertFalse(navigateEntry.mainRequestReceived)

        navigateEntry.updateMainRequest("R1", emptyMap(), "https://example.com/")

        assertTrue(navigateEntry.mainRequestReceived)
    }

    @Test
    @DisplayName("updateMainRequest without a url leaves the recorded document url untouched")
    fun testUpdateMainRequestWithoutUrlKeepsDocumentUrl() {
        val navigateEntry = entry()

        navigateEntry.updateMainRequest("R1", emptyMap(), "https://example.com/")
        navigateEntry.updateMainRequest("R2", emptyMap())

        assertEquals("R2", navigateEntry.mainRequestId)
        assertEquals("https://example.com/", navigateEntry.mainDocumentUrl)
    }

    @Test
    @DisplayName("updateMainDocumentUrl ignores blank urls")
    fun testUpdateMainDocumentUrlIgnoresBlank() {
        val navigateEntry = entry()

        navigateEntry.updateMainDocumentUrl("https://example.com/landed")
        navigateEntry.updateMainDocumentUrl("")

        assertEquals("https://example.com/landed", navigateEntry.mainDocumentUrl)
    }
}
