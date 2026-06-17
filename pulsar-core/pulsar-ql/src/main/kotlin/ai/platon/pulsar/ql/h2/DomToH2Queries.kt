package ai.platon.pulsar.ql.h2

import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.common.math.vectors.get
import ai.platon.pulsar.common.math.vectors.isEmpty
import ai.platon.pulsar.common.sleepSeconds
import ai.platon.pulsar.common.urls.URLUtils
import ai.platon.pulsar.dom.FeaturedDocument
import ai.platon.pulsar.dom.features.FeatureRegistry.registeredFeatures
import ai.platon.pulsar.dom.features.NodeFeature.Companion.isFloating
import ai.platon.pulsar.dom.nodes.GeoAnchor
import ai.platon.pulsar.dom.select.appendSelectorIfMissing
import ai.platon.pulsar.dom.select.select
import ai.platon.pulsar.dom.select.selectFirstOrNull
import ai.platon.pulsar.persist.WebPage
import ai.platon.pulsar.persist.model.GoraWebPage
import ai.platon.pulsar.persist.model.WebPageFormatter
import ai.platon.pulsar.ql.common.ResultSets
import ai.platon.pulsar.ql.common.types.ValueDom
import ai.platon.pulsar.skeleton.common.options.LoadOptions
import ai.platon.pulsar.skeleton.common.urls.NormURL
import ai.platon.pulsar.skeleton.session.PulsarSession
import ai.platon.pulsar.skeleton.workflow.common.url.CompletableListenableHyperlink
import org.apache.commons.math3.linear.RealVector
import org.h2.api.ErrorCode
import org.h2.message.DbException
import org.h2.value.DataType
import org.h2.value.Value
import org.h2.value.ValueArray
import org.h2.value.ValueString
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.sql.ResultSet
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

object DomToH2Queries {
    private val logger = getLogger(this::class)

    /**
     * Load all Web pages.
     *
     * @param session the session
     * @param urls the URLs to load, can be a single string represented by a [ValueString]
     * or an array of strings represented by a [ValueArray]
     * @return a collection of [WebPage] instances
     */
    suspend fun loadAll(session: PulsarSession, urls: Value): Collection<WebPage> {
        var pages: Collection<WebPage> = listOf()

        when (urls) {
            is ValueString -> {
                val normURL = session.normalize(urls.string)
                pages = ArrayList()
                pages.add(session.load(normURL))
            }

            is ValueArray ->
                if (urls.list.isNotEmpty()) {
                    pages = session.loadAll(urls.list.mapTo(mutableSetOf()) { it.string })
                }

            else -> throw DbException.get(ErrorCode.METHOD_NOT_FOUND_1, "Unsupported type ${Value::class}")
        }

        return pages
    }

    /**
     * Load all Web pages, and translate Web pages to targets using the given transformer.
     *
     * @param session        the session
     * @param configuredUrls the configured URLs, can be a single string represented by a [ValueString],
     *                       or an array of strings represented by a [ValueArray]
     * @param restrictCss    the CSS query to restrict elements
     * @param offset         the offset
     * @param limit          the limit
     * @param transformer    the transformer used to translate a Web page element into a collection of [O]
     * @return a collection of [O], the transformed results
     */
    suspend fun <O> loadAll(
        session: PulsarSession,
        configuredUrls: Value, restrictCss: String, offset: Int, limit: Int,
        transformer: (Element, String, Int, Int) -> Collection<O>
    ): Collection<O> {
        return when (configuredUrls) {
            is ValueString -> {
                val doc = session.loadDocument(configuredUrls.string)
                transformer(doc.document, restrictCss, offset, limit)
            }

            is ValueArray -> coroutineScope {
                configuredUrls.list.map { configuredUrl ->
                    async {
                        val doc = session.loadDocument(configuredUrl.string)
                        transformer(doc.document, restrictCss, offset, limit)
                    }
                }.awaitAll().flatten()
            }

            else -> throw DbException.get(ErrorCode.FUNCTION_NOT_FOUND_1, "Unsupported type ${configuredUrls::class}")
        }
    }

    suspend fun loadOutPages(
        session: PulsarSession,
        portalUrl: String, restrictCss: String,
        offset: Int = 1, limit: Int = Int.MAX_VALUE,
        normalize: Boolean = true, ignoreQuery: Boolean = false
    ): Collection<WebPage> {
        val transformer = if (ignoreQuery) this::getLinksIgnoreQuery else this::getLinks

        val normURL = session.normalize(portalUrl)
        val limit2 = min(limit, normURL.options.topLinks)

        val document = session.loadDocument(normURL)
        var links =
            transformer(document.document, restrictCss, offset, Int.MAX_VALUE).filter { !URLUtils.isInternal(it) }

        if (normalize) {
            links = links.mapNotNull { session.normalizeOrNull(it)?.urlString }
        }

        val itemOptions = normURL.options.createItemOptions()
        val distinctLinks = session.normalize(links.toSet().take(limit2), itemOptions)

        return loadAll(session, distinctLinks)
    }

    /**
     * Load all pages specified by [normUrls], wait until all pages are loaded or timeout.
     */
    private fun loadAll(
        session: PulsarSession,
        normUrls: Iterable<NormURL>
    ): List<WebPage> {
        val distinctUrls = normUrls.distinctBy { it.urlString }
        if (distinctUrls.isEmpty()) {
            return listOf()
        }

        val futures = session.loadAllAsync(distinctUrls)

        val timeoutSeconds = distinctUrls.maxOfOrNull { it.options.pageLoadTimeout.seconds }?.plus(30) ?: 120
        logger.info("Waiting for {} completable hyperlinks, timeout={}s | @{}", futures.size, timeoutSeconds, futures.hashCode())

        try {
            CompletableFuture.allOf(*futures.toTypedArray()).get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            logger.warn("Timeout after {}s waiting for {} completable hyperlinks, {} completed",
                timeoutSeconds, futures.size, futures.count { it.isDone })
        }

        val pages = futures.mapNotNull { it.get() }.filter { it.isNotInternal }

        logger.info("Finished {}/{} pages | @{}", pages.size, futures.size, futures.hashCode())

        return pages
    }

    /**
     * Load all pages specified by [normUrls], wait until all pages are loaded or timeout.
     */
    private fun loadAll2(
        session: PulsarSession,
        normUrls: Iterable<NormURL>,
        options: LoadOptions
    ): Collection<WebPage> {
        val globalCache = session.globalCache
        val queue = globalCache.urlPool.higher3Cache.reentrantQueue
        val timeoutSeconds = options.pageLoadTimeout.seconds + 1
        val links = normUrls
            .asSequence()
            .map { CompletableListenableHyperlink<WebPage>(it.urlString, args = it.args, href = it.hrefSpec) }
            .onEach { it.completeOnTimeout(GoraWebPage.NIL, timeoutSeconds, TimeUnit.SECONDS) }
            .toList()

        queue.addAll(links)
        logger.debug(
            "Waiting for {} completable hyperlinks, timeout={}s", links.size, timeoutSeconds
        )

        var i = timeoutSeconds.toInt()
        val pendingLinks = links.toMutableList()
        while (i-- > 0 && pendingLinks.isNotEmpty()) {
            val finishedLinks = pendingLinks.filter { it.isDone }
            if (finishedLinks.isNotEmpty()) {
                logger.debug("Has finished {} links", finishedLinks.size)
            }

            if (i % 30 == 0) {
                logger.debug("Still {} pending links", pendingLinks.size)
            }

            pendingLinks.removeIf { it.isDone }
            sleepSeconds(1)
        }

        return links.filter { it.isDone }.mapNotNull { it.get() }.filter { it.isNotInternal }
    }

    /**
     * Select elements matching [cssQuery] from [dom] and transform each to a string value.
     * Null transform results are silently filtered out.
     *
     * TODO: any type support, only array of strings are supported now
     */
    fun <O> select(dom: ValueDom, cssQuery: String, transform: (Element) -> O): ValueArray {
        val values = dom.element.select(cssQuery) { transform(it) }
            .filterNotNull()
            .map { ValueString.get(it.toString()) }
            .toTypedArray()
        return ValueArray.get(values)
    }

    fun <O> selectFirstOrNull(dom: ValueDom, cssQuery: String, transformer: (Element) -> O): O? {
        return dom.element.selectFirstOrNull(cssQuery, transformer)
    }

    fun <O> selectNthOrNull(dom: ValueDom, cssQuery: String, n: Int, transform: (Element) -> O): O? {
        return dom.element.select(cssQuery, n, 1).firstOrNull()?.let { transform(it) }
    }

    fun getTexts(ele: Element, restrictCss: String, offset: Int, limit: Int): Collection<String> {
        return ele.select(restrictCss, offset, limit) { it.text() }
    }

    fun getLinks(ele: Element, restrictCss: String, offset: Int, limit: Int): Collection<String> {
        val cssQuery = appendSelectorIfMissing(restrictCss, "a")
        return ele.select(cssQuery, offset, limit) { it.absUrl("href") }
    }

    fun getLinksIgnoreQuery(ele: Element, restrictCss: String, offset: Int, limit: Int): Collection<String> {
        val cssQuery = appendSelectorIfMissing(restrictCss, "a")
        return ele.select(cssQuery, offset, limit) {
            it.absUrl("href").takeIf { URLUtils.isStandard(it) }?.substringBefore("?")
        }.filterNotNull()
    }

    fun getFeatures(ele: Element, restrictCss: String, offset: Int, limit: Int): Collection<RealVector> {
        return ele.select(restrictCss, offset, limit) { it.extension.features }
    }

    fun toValueArray(elements: Elements): ValueArray {
        val values = arrayOfNulls<ValueDom>(elements.size)
        for (i in elements.indices) {
            values[i] = ValueDom.getOrNil(elements[i])
        }
        return ValueArray.get(values)
    }

    /**
     * Get a result set containing a single column with the given [colName].
     *
     * When [colName] is "DOM" (case-insensitive), the elements in [collection] must be of type [ValueDom],
     * otherwise they are converted to [ValueString] via [Any.toString].
     */
    fun <E> toResultSet(colName: String, collection: Iterable<E>): ResultSet {
        val rs = ResultSets.newSimpleResultSet()
        val colType = if (colName.equals("DOM", ignoreCase = true)) ValueDom.type else Value.STRING
        val sqlType = DataType.convertTypeToSQLType(colType)
        rs.addColumn(colName, sqlType, 0, 0)

        if (colType == ValueDom.type) {
            collection.forEach { rs.addRow(it) }
        } else {
            collection.forEach { e -> rs.addRow(ValueString.get(e?.toString() ?: "")) }
        }

        return rs
    }

    /**
     * Get a result set with two columns: DOM (the element) and DOC (the document).
     */
    fun toDOMResultSet(document: FeaturedDocument, elements: Collection<ValueDom>): ResultSet {
        val rs = ResultSets.newSimpleResultSet()
        val colType = ValueDom.type
        val sqlType = DataType.convertTypeToSQLType(colType)
        rs.addColumn("DOM", sqlType, 0, 0)
        rs.addColumn("DOC", sqlType, 0, 0)

        val docDOM = ValueDom.get(document)
        elements.forEach { rs.addRow(it, docDOM) }

        return rs
    }

    /**
     * Get result set for each field in a collection of [GeoAnchor].
     */
    fun toResultSet(anchors: Collection<GeoAnchor>): ResultSet {
        val rs = ResultSets.newSimpleResultSet()
        rs.addColumns("URL", "TEXT", "PATH", "LEFT", "TOP", "WIDTH", "HEIGHT")

        anchors.forEach {
            rs.addRow(it.url, it.text, it.path, it.left, it.top, it.width, it.height)
        }

        return rs
    }

    /**
     * Get result set with KEY/VALUE columns for each field in a [WebPage].
     */
    fun toResultSet(page: WebPage): ResultSet {
        val rs = ResultSets.newSimpleResultSet()
        rs.addColumns("KEY", "VALUE")

        val fields = WebPageFormatter(page).toMap()
        for (entry in fields.entries) {
            val value = entry.value?.toString() ?: ""
            rs.addRow(entry.key, value)
        }

        return rs
    }

    /**
     * Get a row of data containing the DOM itself and all its feature values.
     * Every float feature has 2 fraction digits.
     */
    fun getFeatureRow(ele: Element): Array<Any?> {
        val columnCount = 1 + registeredFeatures.size
        val values = arrayOfNulls<Any>(columnCount)
        values[0] = ValueDom.get(ele)
        val features = if (!ele.extension.features.isEmpty) ele.extension.features else return values

        // TODO: configurable
        val base = 10f
        val fractionDigits = 2
        val factor = base.pow(fractionDigits)
        for (j in 1..registeredFeatures.size) {
            val key = j - 1
            val v = features[key]

            if (isFloating(key)) {
                values[j] = 1.0 * (factor * v).roundToInt() / factor
            } else {
                values[j] = v.toInt()
            }
        }

        return values
    }
}
