package ai.platon.pulsar.api.model

import ai.platon.pulsar.common.DateTimes
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Tracks the lifecycle state of a single browser navigation.
 *
 * A [NavigateEntry] is created when the user initiates navigation — by typing a URL, clicking a link,
 * or through programmatic action — and is progressively updated as the browser emits network and frame
 * events. It serves as the shared mutable context between the CDP event layer
 * ([ChromeNavigateEntry][ai.platon.pulsar.chrome.network.ChromeNavigateEntry]) and the high-level
 * driver API ([AbstractWebDriver][ai.platon.pulsar.api.AbstractWebDriver]).
 *
 * ## Identity
 *
 * Equality, hashing, and ordering are based solely on [userTypedUrl]: two entries are equal if they
 * share the same user-typed URL, regardless of differences in any other field. This allows collection
 * lookups by URL string. The deprecated [url] property is a computed alias for [userTypedUrl].
 *
 * ## Thread safety
 *
 * Mutable fields are updated from CDP event callbacks on the browser's event thread. Use [synchronized]
 * to guard compound reads and writes across threads.
 *
 * @author Vincent
 * @since 2018-01-01
 * Copyright @ 2013-2026 Platon AI. All rights reserved.
 */
data class NavigateEntry constructor(
    /**
     * The raw URL as entered by the user or extracted from a link's `href` attribute.
     *
     * This is the un-normalized form and may carry query parameters such as tracking ids or timestamps
     * (e.g. `"https://www.example.com?timestamp=11712067353"`). It serves as the source of truth for
     * locating the corresponding WebPage in the database.
     *
     * Use [pageUrl] for the canonical, normalized form.
     */
    val userTypedUrl: String,
    /**
     * The database id of the WebPage associated with this navigation.
     * A value of `0` means no WebPage has been created yet.
     */
    val pageId: Long = 0,
    /**
     * The canonical, normalized URL used to look up the WebPage in the database.
     * This is typically the [userTypedUrl] stripped of transient query parameters.
     *
     * An empty string means no WebPage is associated.
     */
    val pageUrl: String = "",
    /**
     * The HTTP `Referer` (or `Referrer`) header value from the request that led to this page,
     * as recorded by the originating WebPage.
     */
    var pageReferrer: String? = null,
    /**
     * Indicates whether the navigation has been stopped.
     */
    var stopped: Boolean = false,
    /**
     * Indicates whether the browser tab for this navigation has been closed.
     */
    var closed: Boolean = false,
    /**
     * The instant of the most recent recorded activity on this entry.
     *
     * Updated by [updateState].
     */
    var lastActiveTime: Instant = Instant.now(),
    /**
     * The instant when this entry was created.
     */
    val createTime: Instant = Instant.now(),
): Comparable<NavigateEntry> {
    private val lock = ReentrantLock()

    /**
     * A computed alias for [userTypedUrl], maintained for backward compatibility.
     *
     * Callers should migrate to [userTypedUrl] directly.
     */
    @Deprecated("Use userTypedUrl instead", ReplaceWith("userTypedUrl"))
    val url: String get() = userTypedUrl

    /**
     * The CDP request id of the main document request.
     *
     * The main request is the first `DOCUMENT`-type network request that belongs to the main
     * frame. It is recorded regardless of the order in which the network and frame events
     * happen to arrive, so a redirect committed before `Page.frameNavigated` no longer leaves
     * this blank. An empty string means the main request has not been received yet.
     */
    var mainRequestId = ""

    /**
     * The URL of the main document request, following the redirect chain.
     *
     * For a navigation answered with a redirect this ends up as the redirect *target* — the URL
     * the document was actually committed from — while [userTypedUrl] keeps the URL that was
     * requested. Callers can use it to accept a legitimate redirect instead of rejecting the
     * capture as an origin mismatch.
     */
    var mainDocumentUrl = ""

    /**
     * Whether a main-document request has been observed for this navigation.
     *
     * Prefer this over [mainFrameReceived] when deciding whether a document belongs to this
     * navigation: it answers "did this navigation ask the browser for a document?", which is
     * what a snapshot origin guard actually needs, and it does not depend on frame-event timing.
     */
    val mainRequestReceived get() = mainRequestId.isNotBlank()

    /**
     * The HTTP request headers of the main document request, as reported by the CDP network layer.
     */
    var mainRequestHeaders: Map<String, Any> = mapOf()

    /**
     * The cookies sent with the main document request.
     *
     * Each element is a flat map representing one cookie (e.g. `"name"`, `"value"`, `"domain"`).
     * Populated from `RequestWillBeSentExtraInfo.associatedCookies` when available.
     */
    var mainRequestCookies: List<Map<String, String>> = emptyList()

    /**
     * The HTTP response status code of the main document request.
     *
     * A value of `-1` means no response has been received yet.
     */
    var mainResponseStatus: Int = -1

    /**
     * The HTTP response reason phrase of the main document request (e.g. `"OK"`, `"Not Found"`).
     */
    var mainResponseStatusText: String = ""

    /**
     * The HTTP response headers of the main document request, as reported by the CDP network layer.
     */
    var mainResponseHeaders: Map<String, Any> = mapOf()

    /**
     * The CDP frame id of the top-level browsing context, set when the
     * [FrameNavigated][ai.platon.cdt.kt.protocol.events.page.FrameNavigated] event fires with
     * `frame.parentId == null`.
     *
     * Remains `null` until the main frame arrives.
     */
    var mainFrameId: String? = null

    /**
     * Indicates whether the main frame has been received from the browser.
     *
     * Derived from [mainFrameId] — `true` once the top-level frame is known.
     */
    val mainFrameReceived get() = mainFrameId != null

    /**
     * The instant when the document reached the ready state.
     *
     * Defaults to [DateTimes.doomsday] (a far-future sentinel) until the document signals readiness.
     */
    var documentReadyTime = DateTimes.doomsday

    /**
     * A timestamped log of page-level actions recorded against this entry.
     *
     * Keys are free-form action names; values are the instants when each action was recorded via
     * [updateState]. This map is intended for observability and debugging.
     */
    val actionTimes = mutableMapOf<String, Instant>()

    /**
     * The total number of network requests emitted by this page (all resource types).
     */
    val networkRequestCount = AtomicInteger()

    /**
     * The total number of network responses received for this page (all resource types).
     */
    val networkResponseCount = AtomicInteger()

    /**
     * Records an action against this entry and marks it as active at the current instant.
     *
     * If [action] is non-blank, it is recorded in [actionTimes] with the current timestamp.
     * [lastActiveTime] is always updated.
     */
    fun updateState(action: String) {
        val now = Instant.now()
        lastActiveTime = now
        if (action.isNotBlank()) {
            actionTimes[action] = now
        }
    }

    /**
     * Executes [action] under this entry's internal lock, guarding compound reads and writes
     * that must appear atomic across threads.
     */
    fun synchronized(action: () -> Unit) {
        lock.withLock(action)
    }

    /**
     * Stores the main document request id, its URL, and its headers.
     *
     * If a [pageReferrer] is set, both `"referer"` and `"referrer"` keys are injected into the
     * stored headers — some sites (e.g. Amazon) use the misspelled `"referer"` form.
     *
     * @param requestId the CDP id of the request that started this navigation
     * @param headers the request headers as reported by the CDP network layer
     * @param url the URL of that request; when blank, [mainDocumentUrl] is left untouched
     */
    fun updateMainRequest(requestId: String, headers: Map<String, Any>, url: String = "") {
        mainRequestId = requestId
        mainRequestHeaders = headers
        if (url.isNotBlank()) {
            mainDocumentUrl = url
        }

        // Both forms are set because some sites (e.g. Amazon) use "referer" (HTTP spec
        // misspelling) while others may use the correct "referrer" form.
        val referrer = pageReferrer
        if (referrer != null) {
            val mutableHeaders = headers.toMutableMap()
            mutableHeaders["referer"] = referrer
            mutableHeaders["referrer"] = referrer
            mainRequestHeaders = mutableHeaders
        }
    }

    /**
     * Records a redirect hop of the main document.
     *
     * [mainRequestId] keeps pointing at the request that started the navigation, while
     * [mainDocumentUrl] follows the chain to the URL the document finally commits from.
     *
     * @param url the URL of the redirect hop; blank values are ignored
     */
    fun updateMainDocumentUrl(url: String) {
        if (url.isNotBlank()) {
            mainDocumentUrl = url
        }
    }

    /**
     * Stores the cookies extracted from the main document request's
     * `RequestWillBeSentExtraInfo.associatedCookies`.
     */
    fun updateMainRequestCookies(cookies: List<Map<String, String>>) {
        mainRequestCookies = cookies
    }

    /**
     * Stores the main document response status, reason phrase, and headers as received from the
     * CDP [ResponseReceived][ai.platon.cdt.kt.protocol.events.network.ResponseReceived] event.
     */
    fun updateMainResponse(status: Int, statusText: String, headers: Map<String, Any>) {
        mainResponseStatus = status
        mainResponseStatusText = statusText
        mainResponseHeaders = headers
    }

    /**
     * Two [NavigateEntry] instances are equal if they share the same [userTypedUrl].
     *
     * Identity is intentionally scoped to the user-typed URL alone so that entries can be compared
     * and looked up in collections regardless of transient state such as [stopped], [closed], or
     * network counters.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        return when (other) {
            null -> false
            is NavigateEntry -> other.userTypedUrl == userTypedUrl
            else -> false
        }
    }

    override fun hashCode() = userTypedUrl.hashCode()

    override fun compareTo(other: NavigateEntry) = userTypedUrl.compareTo(other.userTypedUrl)
}
