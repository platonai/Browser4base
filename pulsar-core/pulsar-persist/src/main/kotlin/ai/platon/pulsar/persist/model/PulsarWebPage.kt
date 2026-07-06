package ai.platon.pulsar.persist.model

import ai.platon.pulsar.common.DateTimes
import ai.platon.pulsar.common.DateTimes.doomsday
import ai.platon.pulsar.common.HtmlIntegrity
import ai.platon.pulsar.common.PulsarParams
import ai.platon.pulsar.common.Strings
import ai.platon.pulsar.common.browser.BrowserType
import ai.platon.pulsar.common.config.AppConstants
import ai.platon.pulsar.common.config.VolatileConfig
import ai.platon.pulsar.common.serialize.json.pulsarObjectMapper
import ai.platon.pulsar.common.urls.URLUtils
import ai.platon.pulsar.common.urls.URLUtils.unreverseUrl
import ai.platon.pulsar.persist.*
import ai.platon.pulsar.persist.metadata.FetchMode
import ai.platon.pulsar.persist.metadata.Name
import ai.platon.pulsar.persist.metadata.OpenPageCategory
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * The core web page structure
 */
class PulsarWebPage(
    /**
     * The url is the permanent internal address, while the location is the last working address.
     */
    url: String,
    /**
     * A webpage scope configuration, any modifications made to it will exclusively impact this particular webpage.
     */
    conf: VolatileConfig,
    /**
     * The underlying persistent object.
     */
    var page: WebPageRecord
) : AbstractWebPage(url, conf) {
    companion object {
        // NIL is a predefined internal page with a NIL_PAGE_URL, ID 0, and empty title and content.
        val NIL = newInternalPage(AppConstants.NIL_PAGE_URL, 0, "", "")

        /**
         * Creates a new PulsarWebPage with the given URL and configuration.
         *
         * @param url The URL of the web page.
         * @param conf The volatile configuration for the web page.
         * @return A new PulsarWebPage instance.
         */
        fun newWebPage(url: String, conf: VolatileConfig): PulsarWebPage {
            return newWebPage(url, conf, null)
        }

        /**
         * Creates a new PulsarWebPage with the given URL, configuration, and optional href.
         *
         * @param url The URL of the web page.
         * @param conf The volatile configuration for the web page.
         * @param href The optional href associated with the web page.
         * @return A new PulsarWebPage instance.
         */
        fun newWebPage(url: String, conf: VolatileConfig, href: String?): PulsarWebPage {
            return newWebPageInternal(url, conf, href)
        }

        /**
         * Creates a new internal PulsarWebPage with the given URL, ID, title, and content.
         *
         * @param url The URL of the internal page.
         * @param id The ID of the internal page. If negative, the ID will not be set.
         * @param title The title of the internal page.
         * @param content The content of the internal page.
         * @return A new PulsarWebPage instance representing an internal page.
         */
        fun newInternalPage(url: String, id: Long, title: String, content: String): PulsarWebPage {
            val unsafe = VolatileConfig.UNSAFE
            val page = newWebPage(url, unsafe)
            if (id >= 0) {
                page.id = id
            }

            // Initialize page properties with default values.
            page.location = url
            page.modifiedTime = Instant.EPOCH
            page.prevFetchTime = Instant.EPOCH
            page.fetchTime = doomsday
            page.fetchInterval = ChronoUnit.CENTURIES.duration

            page.distance = AppConstants.DISTANCE_INFINITE // or -1?

            page.pageTitle = title
            page.setStringContent(content)

            return page
        }

        /**
         * Wraps a WebPageRecord instance into a PulsarWebPage with the given URL and configuration.
         *
         * @param url The URL of the web page.
         * @param page The underlying WebPageRecord instance.
         * @param conf The volatile configuration for the web page.
         * @return A new PulsarWebPage instance wrapping the provided WebPageRecord.
         */
        fun box(url: String, page: WebPageRecord, conf: VolatileConfig): PulsarWebPage {
            return PulsarWebPage(url, conf, page)
        }

        /**
         * Internal method to create a new PulsarWebPage with the given URL, configuration, and optional href.
         *
         * @param url The URL of the web page.
         * @param conf The volatile configuration for the web page.
         * @param href The optional href associated with the web page.
         * @return A new PulsarWebPage instance.
         */
        private fun newWebPageInternal(url: String, conf: VolatileConfig, href: String?): PulsarWebPage {
            val page = PulsarWebPage(url, WebPageRecord(url), false, conf)

            // Initialize page properties with default values.
            page.location = url
            page.conf = conf
            page.href = href
            page.createTime = Instant.now()
            page.modifiedTime = Instant.now()
            page.fetchCount = 0

            return page
        }

        /**
         * Returns the URL based on whether it should be reversed or not.
         *
         * @param urlOrKey The original URL or key.
         * @param urlReversed A flag indicating if the URL should be reversed.
         * @return The processed URL.
         */
        private fun getUrl(urlOrKey: String, urlReversed: Boolean): String {
            return if (urlReversed) unreverseUrl(urlOrKey) else urlOrKey
        }
    }

    private val CONTENT_MONITOR = Any()

    //    private final Deque<String> lazyLoadedFields = new ConcurrentLinkedDeque<>();
    private constructor(
        urlOrKey: String, page: WebPageRecord, urlReversed: Boolean, conf: VolatileConfig
    ) : this(getUrl(urlOrKey, urlReversed), conf, page)

    override var href: String?
        /**
         * Get The hypertext reference of this page.
         * It defines the address of the document, which this time is linked from
         *
         *
         * TODO: use a separate field for href
         *
         * @return The hypertext reference
         */
        get() = metadata[Name.HREF]
        /**
         * Set The hypertext reference of this page.
         * It defines the address of the document, which this time is linked from
         *
         * @param href The hypertext reference
         */
        set(href) {
            metadata[Name.HREF] = href
        }

    override val isNil: Boolean
        get() = this === NIL

    override val isNotNil: Boolean
        get() = !isNil

    override val isInternal: Boolean
        get() = URLUtils.isInternal(url)

    override val isNotInternal: Boolean
        get() = !isInternal

    fun unbox(): WebPageRecord {
        return page
    }

    fun unsafeSetRecord(page: WebPageRecord) {
        this.page = page
    }

    fun unsafeCloneRecord(page: WebPage) {
        require(page is PulsarWebPage)
        this.page = page.page.copy(baseUrl = baseURI)
    }

    override val metadata: Metadata
        get() = Metadata(page.metadata)

    override var args: String
        /**
         * The load arguments is variant task by task, so the local version is the first choice,
         * while the persisted version is used for historical check only
         *
         * Underlying gora field should not use name 'args' which is already used,
         * see GProtocolStatus.args and GParseStatus.args
         */
        get() {
            // Underlying gora field should not use name 'args' which is already used.
            return page.params ?: ""
        }
        /**
         * Set the arguments and clear the LoadOptions object.
         */
        set(args) {
            variables.remove(PulsarParams.VAR_LOAD_OPTIONS)
            page.params = args
        }

    override var zoneId: ZoneId
        get() = if (page.zoneId == null) DateTimes.zoneId else ZoneId.of(page.zoneId.toString())
        set(zoneId) {
            page.zoneId = zoneId.id
        }

    override var distance: Int
        /**
         * Get the distance of the page from the seed in the graph.
         */
        get() {
            val distance = page.distance
            return if (distance < 0) AppConstants.DISTANCE_INFINITE else distance
        }
        /**
         * Set the distance of the page from the seed in the graph.
         */
        set(newDistance) {
            page.distance = newDistance
        }

    override var lastBrowser: BrowserType
        /**
         * Get the browser used to fetch the page last time.
         */
        get() {
            val browser = if (page.browser != null) page.browser.toString() else ""
            return BrowserType.fromString(browser)
        }
        /**
         * Set the browser used to fetch the page.
         */
        set(browser) {
            page.browser = browser.name
        }

    /**
     * @inheritDoc
     * TODO: use a separate field
     */
    override var maxRetries: Int
        get() = metadata.getInt(Name.FETCH_MAX_RETRY, 3)
        set(maxRetries) {
            metadata[Name.FETCH_MAX_RETRY] = maxRetries
        }

    /**
     * @inheritDoc
     * TODO: use a separate field
     */
    override var fetchMode: FetchMode
        /**
         * Get the fetch mode, only BROWSER mode is supported currently.
         * Fetch mode is used to determine the protocol before fetch, so it shall be set before fetch.
         */
        get() = FetchMode.fromString(metadata[Name.FETCH_MODE])
        /**
         * Get the fetch mode, only BROWSER mode is supported currently.
         * Fetch mode is used to determine the protocol before fetch, so it shall be set before fetch
         */
        set(mode) {
            metadata[Name.FETCH_MODE] = mode.name
        }

    override var isResource: Boolean
        /**
         * Checks whether the page is a single resource which can be fetched by a single request.
         */
        get() = page.resource != null
        /**
         * Indicates the page to be a single resource that can be fetched by a single request.
         */
        set(resource) {
            if (resource) {
                page.resource = 1
            }
        }

    override var htmlIntegrity: HtmlIntegrity
        get() {
            val integrity =
                if (page.htmlIntegrity != null) page.htmlIntegrity.toString() else ""
            return HtmlIntegrity.fromString(integrity)
        }
        set(integrity) {
            page.htmlIntegrity = integrity.name
        }

    override var createTime: Instant
        get() = Instant.ofEpochMilli(page.createTime)
        set(createTime) {
            page.createTime = createTime.toEpochMilli()
        }

    override var fetchCount: Int
        get() = page.fetchCount
        set(count) {
            page.fetchCount = count
        }

    override var baseURI: String
        get() = page.baseUrl ?: ""
        set(value) {
            page.baseUrl = value
        }

    override var location: String
        get() = metadata[Name.LOCATION] ?: ""
        set(location) {
            metadata[Name.LOCATION] = location
        }

    override var fetchTime: Instant
        get() = Instant.ofEpochMilli(page.fetchTime)
        set(fetchTime) {
            page.fetchTime = fetchTime.toEpochMilli()
        }

    override var prevFetchTime: Instant
        get() = Instant.ofEpochMilli(page.prevFetchTime)
        set(prevFetchTime) {
            page.prevFetchTime = prevFetchTime.toEpochMilli()
        }

    override var fetchInterval: Duration
        get() = Duration.ofSeconds(fetchIntervalSeconds)
        set(fetchInterval) {
            page.fetchInterval = fetchInterval.seconds.toInt()
        }

    private val fetchIntervalSeconds: Long
        get() {
            var seconds = page.fetchInterval.toLong()
            if (seconds < 0) {
                seconds = ChronoUnit.CENTURIES.duration.seconds
            }
            return seconds
        }

    override var protocolStatus: ProtocolStatus
        get() = ProtocolStatus(page.protocolStatus)
        set(protocolStatus) {
            page.protocolStatus = protocolStatus.protocolStatus
        }

    override val headers: ProtocolHeaders
        get() {
            val headers = page.headers
            return ProtocolHeaders.box(headers)
        }

    override var fetchRetries: Int
        get() = page.fetchRetries
        set(fetchRetries) {
            page.fetchRetries = fetchRetries
        }

    override var modifiedTime: Instant
        get() = Instant.ofEpochMilli(page.modifiedTime)
        set(modifiedTime) {
            page.modifiedTime = modifiedTime.toEpochMilli()
        }

    override var prevModifiedTime: Instant
        get() = Instant.ofEpochMilli(page.prevModifiedTime)
        set(prevModifiedTime) {
            page.prevModifiedTime = prevModifiedTime.toEpochMilli()
        }

    override var pageCategory: OpenPageCategory
        get() = OpenPageCategory.parse(page.pageCategory)
        set(value) {
            page.pageCategory = value.format()
        }

    override var encoding: String?
        get() = page.encoding
        set(value) {
            page.encoding = value
        }

    override var content: ByteBuffer?
        get() = getTmpContentOrPersistContent()
        set(value) {
            setByteBufferContent1(value)
        }

    override val persistContent: ByteBuffer?
        get() = getPersistContent0()

    override fun setStringContent(value: String?) {
        if (value != null) {
            setByteArrayContent(value.toByteArray())
        } else {
            setByteBufferContent1((null as ByteBuffer?))
        }
    }

    override fun setByteArrayContent(value: ByteArray?) {
        setByteArrayContent1(value)
    }

    override fun setByteBufferContent(value: ByteBuffer?) {
        setByteBufferContent1(value)
    }

    /**
     * Clear persist content, so the content will not write to the disk.
     */
    override fun clearPersistContent() {
        synchronized(CONTENT_MONITOR) {
            tmpContent = page.content
            page.content = null
            persistedContentLength = 0
        }
    }

    /**
     * Get the length of content in bytes.
     *
     * @return The length of the content in bytes.
     */
    override val contentLength: Long
        get() = when {
            page.contentLength != null -> page.contentLength
            else -> 0
        }

    /**
     * The length of the original page content in bytes, the content has no pulsar metadata inserted.
     *
     * @return The length of the original page content in bytes, negative means not specified
     */
    override var originalContentLength: Long
        get() = metadata.getLong(Name.ORIGINAL_CONTENT_LENGTH, -1)
        set(value) {
            metadata[Name.ORIGINAL_CONTENT_LENGTH] = "" + value
        }

    /**
     * Compute the length of content in bytes.
     */
    private fun computeContentLength(bytes: Long) {
        val lastBytes = contentLength
        page.lastContentLength = lastBytes
        page.contentLength = bytes
        computeAveContentLength(bytes)
    }

    private fun computeAveContentLength(bytes: Long) {
        val count = fetchCount
        val lastAveBytes = page.aveContentLength
        val aveBytes = if (count > 0 && lastAveBytes == 0L) {
            // old version, average bytes is not calculated
            bytes
        } else {
            (lastAveBytes * count + bytes) / (count + 1)
        }

        page.aveContentLength = aveBytes
    }

    override var persistedContentLength: Long
        get() = when {
            page.persistedContentLength != null -> page.persistedContentLength
            else -> 0
        }
        set(value) {
            page.persistedContentLength = value
        }

    override val lastContentLength
        get() = when {
            page.lastContentLength != null -> page.lastContentLength
            else -> 0
        }

    override val aveContentLength
        get() = when {
            page.aveContentLength != null -> page.aveContentLength
            else -> 0
        }

    override var contentType
        get() = if (page.contentType == null) "" else page.contentType.toString()
        set(value) {
            page.contentType = value.trim().lowercase(Locale.getDefault())
        }

    override var signature: ByteBuffer? = null

    override val signatureAsString: String
        get() = getSignatureAsString0()

    override var prevSignature: ByteBuffer?
        get() = page.prevSignature
        set(value) {
            page.prevSignature = value
        }

    override val prevSignatureAsString: String
        get() = getPrevSignatureAsString0()

    override var proxy: String?
        get() = page.proxy
        set(value) {
            page.proxy = value
        }

    override var activeDOMStatus: ActiveDOMStatus?
        get() = page.activeDOMStatus
        set(value) {
            page.activeDOMStatus = value
        }

    override var activeDOMStatTrace: ActiveDOMStatTrace?
        get() = page.activeDOMStatTrace
        set(value) {
            page.activeDOMStatTrace = value
        }

    override var activeDOMMetadata: ActiveDOMMetadata?
        get() = metadata["ACTIVE_DOM_METADATA"]?.let { pulsarObjectMapper().readValue(it) }
        set(value) {
            if (value != null) {
                metadata["ACTIVE_DOM_METADATA"] = pulsarObjectMapper().writeValueAsString(value)
            } else {
                metadata.remove("ACTIVE_DOM_METADATA")
            }
        }

    override var pageTitle
        get() = page.pageTitle
        set(value) {
            page.pageTitle = value
        }

    override var contentTitle
        get() = page.contentTitle
        set(value) {
            page.contentTitle = value
        }

    override var pageText
        get() = page.pageText
        set(value) {
            page.pageText = value
        }

    override var contentText
        get() = page.contentText
        set(value) {
            page.contentText = value
        }

    override var parseStatus: ParseStatus
        get() = ParseStatus(page.parseStatus)
        set(value) {
            page.parseStatus = value.unbox()
        }

    override var vividLinks: MutableMap<String, String>
        get() = page.vividLinks
        set(value) {
            page.vividLinks = value
        }

    override var links: MutableList<String>
        get() = page.links
        set(value) {
            page.links = value
        }

    override var inlinks: MutableMap<String, String>
        get() = page.inlinks
        set(value) {
            page.inlinks = value
        }

    override var anchor: String?
        get() = page.anchor
        set(value) {
            page.anchor = value
        }

    override var referrer: String?
        get() = page.referrer
        set(value) {
            page.referrer = value
        }

    private fun getTmpContentOrPersistContent(): ByteBuffer? {
        if (tmpContent != null) {
            return tmpContent
        }

        return persistContent
    }

    private fun getPersistContent0(): ByteBuffer? {
        synchronized(CONTENT_MONITOR) {
            return page.content
        }
    }

    private fun setByteArrayContent1(value: ByteArray?) {
        if (value != null) {
            setByteBufferContent1(ByteBuffer.wrap(value))
        } else {
            setByteBufferContent1((null as ByteBuffer?))
        }
    }

    private fun setByteBufferContent1(value: ByteBuffer?) {
        synchronized(CONTENT_MONITOR) {
            if (value != null) {
                page.content = value
                isContentUpdated = true

                var length = value.array().size.toLong()
                // save the length of the persisted content,
                // so we can query the length without loading the big or even huge content field
                persistedContentLength = length

                length = originalContentLength
                computeContentLength(length)
            } else {
                clearPersistContent()
            }
        }
    }

    private fun getSignatureAsString0(): String {
        var sig = signature
        if (sig == null) {
            sig = ByteBuffer.wrap("".toByteArray())
        }
        return Strings.toHexString(sig)
    }

    private fun getPrevSignatureAsString0(): String {
        var sig: ByteBuffer? = prevSignature
        if (sig == null) {
            sig = ByteBuffer.wrap("".toByteArray())
        }
        return Strings.toHexString(sig)
    }
}
