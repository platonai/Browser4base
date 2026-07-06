package ai.platon.pulsar.persist

import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.urls.URLUtils
import ai.platon.pulsar.persist.model.GoraWebPage
import ai.platon.pulsar.persist.model.WebPageRecord
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * A simple interface to query and store web pages.
 * */
class WebDb(
    val conf: ImmutableConfig,
    private val storage: WebDbStorage = LocalWebDbStorage(conf),
) : AutoCloseable {
    companion object {
        val dbGetCount = AtomicLong()
        val accumulateGetNanos = AtomicLong()
    }

    private val logger = LoggerFactory.getLogger(WebDb::class.java)
    private val tracer = logger.takeIf { it.isTraceEnabled }
    private val closed = AtomicBoolean()

    /**
     * Returns the WebPage corresponding to the given url.
     *
     * @param originalUrl the original url of the page, it comes from user input, webpage parsing, etc
     * @param field the fields required in the WebPage. Pass null to retrieve all fields
     * @return the WebPage corresponding to the key or null if it cannot be found
     */
    @Throws(WebDBException::class)
    fun getOrNull(originalUrl: String, field: String): WebPage? {
        return getOrNull(originalUrl, false, arrayOf(field))
    }

    /**
     * Returns the WebPage corresponding to the given url.
     *
     * @param originalUrl the original url of the page, it comes from user input, webpage parsing, etc
     * @param fields the fields required in the WebPage. Pass null to retrieve all fields
     * @return the WebPage corresponding to the key or null if it cannot be found
     */
    @Throws(WebDBException::class)
    fun getOrNull(originalUrl: String, norm: Boolean = false, fields: Array<String>? = null): WebPage? {
        // TODO: consider the design again whether we need normalize the url here
        val (url, key) = URLUtils.normalizedUrlAndKey(originalUrl, norm)

        val page = getOrNull0(originalUrl, norm, fields)

        if (page != null) {
            val p = GoraWebPage.box(url, page, conf.toVolatileConfig()).also { it.isLoaded = true }
            tracer?.trace("Got {} {} {} {}", p.fetchCount, p.prevFetchTime, p.fetchTime, key)
            return p
        }

        return null
    }

    @Throws(WebDBException::class)
    fun get(originalUrl: String, field: String) = getOrNull(originalUrl, field) ?: GoraWebPage.NIL

    @Throws(WebDBException::class)
    fun get(originalUrl: String, norm: Boolean = false, fields: Array<String>? = null): WebPage {
        return getOrNull(originalUrl, norm, fields) ?: GoraWebPage.NIL
    }

    @Throws(WebDBException::class)
    fun exists(originalUrl: String, norm: Boolean = false): Boolean {
        val (_, key) = URLUtils.normalizedUrlAndKey(originalUrl, norm)
        return storage.exists(key)
    }

    @Throws(WebDBException::class)
    fun getContent(originalUrl: String): ByteBuffer? {
        val (_, key) = URLUtils.normalizedUrlAndKey(originalUrl, false)
        return storage.readContent(key)
    }

    @Throws(WebDBException::class)
    fun getContentAsString(originalUrl: String): String? {
        val content = getContent(originalUrl) ?: return null
        val bytes = ByteArray(content.remaining())
        val pos = content.position()
        content.get(bytes)
        content.position(pos)
        return String(bytes, StandardCharsets.UTF_8)
    }

    @Throws(WebDBException::class)
    @JvmOverloads
    fun put(page: WebPage, replaceIfExists: Boolean = false) = putInternal(page, replaceIfExists)

    @Throws(WebDBException::class)
    private fun putInternal(page: WebPage, replaceIfExists: Boolean): Boolean {
        require(page is GoraWebPage) { "Only GoraWebPage is supported, got: ${page::class.java}" }
        val record = page.unbox()
        val (_, key) = URLUtils.normalizedUrlAndKey(page.url, false)
        return storage.write(record, key, replaceIfExists)
    }

    @Throws(WebDBException::class)
    fun putAll(pages: Iterable<WebPage>) = pages.forEach { put(it, false) }

    @JvmOverloads
    @Throws(WebDBException::class)
    fun delete(originalUrl: String, norm: Boolean = false): Boolean {
        val (_, key) = URLUtils.normalizedUrlAndKey(originalUrl, norm)
        return storage.delete(key)
    }

    @JvmOverloads
    @Throws(WebDBException::class)
    fun truncate(force: Boolean = false): Boolean {
        if (!force) {
            logger.warn("truncate() requires force=true to prevent accidental data loss")
            return false
        }
        return storage.truncate()
    }

    @Throws(WebDBException::class)
    fun flush() {
        storage.flush()
        tracer?.trace("Flush delegated to storage")
    }

    @Throws(WebDBException::class)
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            storage.close()
            tracer?.trace("WebDb closed")
        }
    }

    /**
     * Returns the WebPage corresponding to the given url.
     *
     * @param originalUrl the original url of the page, it comes from user input, webpage parsing, etc
     * @param fields the fields required in the WebPage. Pass null to retrieve all fields
     * @return the WebPage corresponding to the key or null if it cannot be found
     */
    @Throws(WebDBException::class)
    private fun getOrNull0(originalUrl: String, norm: Boolean = false, fields: Array<String>? = null): WebPageRecord? {
        val (_, key) = URLUtils.normalizedUrlAndKey(originalUrl, norm)

        tracer?.trace("Getting $key")

        val startTime = System.nanoTime()

        val page = storage.read(key)

        dbGetCount.incrementAndGet()
        accumulateGetNanos.addAndGet(System.nanoTime() - startTime)

        return page
    }
}
