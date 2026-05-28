package ai.platon.pulsar.persist

import ai.platon.pulsar.common.DateTimes.constructTimeHistory
import ai.platon.pulsar.common.config.AppConstants
import ai.platon.pulsar.common.config.VolatileConfig
import ai.platon.pulsar.persist.model.GoraWebPage
import ai.platon.pulsar.persist.metadata.Name
import ai.platon.pulsar.persist.model.ActiveDOMStat
import ai.platon.pulsar.persist.model.ActiveDOMStatTrace
import ai.platon.pulsar.persist.model.ActiveDOMStatus
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

class WebPageExt(private val page: WebPage) {

    companion object {

        fun newTestWebPage(url: String): WebPage {
            val page = GoraWebPage.newWebPage(url, VolatileConfig(), null)

            page.activeDOMStatus = ActiveDOMStatus(1, 1, "1", "1", "1")
            page.activeDOMStatTrace = ActiveDOMStatTrace()

            return page
        }
    }

    /**
     * Set fetch interval in seconds
     */
    fun setFetchInterval(seconds: Long) {
        page.fetchInterval = Duration.ofSeconds(seconds)
    }

    /**
     * Set fetch interval in seconds
     */
    fun setFetchInterval(seconds: Float) {
        setFetchInterval(seconds.roundToInt().toLong())
    }

    fun updateFetchTime(prevFetchTime: Instant, fetchTime: Instant) {
        page.prevFetchTime = prevFetchTime
        // the next time supposed to fetch
        page.fetchTime = fetchTime

        updateFetchTimeHistory(fetchTime)
    }

    fun sniffTitle(): String {
        var title = page.contentTitle
        if (title.isNullOrEmpty()) {
            title = page.anchor.toString()
        }
        if (title.isEmpty()) {
            title = page.pageTitle
        }
        if (title.isNullOrEmpty()) {
            title = page.location
        }
        if (title.isEmpty()) {
            title = page.url
        }
        return title
    }

    fun updateContent(pageDatum: PageDatum, contentTypeHint: String? = null) {
        var contentType = contentTypeHint

        page.originalContentLength = pageDatum.originalContentLength.toLong()
        page.setByteArrayContent(pageDatum.content)
        // clear content immediately to release resource as soon as possible
        pageDatum.content = null

        if (contentType != null) {
            pageDatum.contentType = contentType
        } else {
            contentType = pageDatum.contentType
        }

        if (contentType != null) {
            page.contentType = contentType
        } else {

        }
    }

    /**
     * *****************************************************************************
     * Parsing
     * ******************************************************************************
     */
    fun updateFetchTimeHistory(fetchTime: Instant) {
        var fetchTimeHistory = page.metadata[Name.FETCH_TIME_HISTORY]
        fetchTimeHistory = constructTimeHistory(fetchTimeHistory, fetchTime, 10)
        page.metadata[Name.FETCH_TIME_HISTORY] = fetchTimeHistory
    }

    fun sniffModifiedTime(): Instant {
        var modifiedTime = page.modifiedTime
        val headerModifiedTime = page.headers.lastModified
        if (isValidContentModifyTime(headerModifiedTime) && headerModifiedTime.isAfter(modifiedTime)) {
            modifiedTime = headerModifiedTime
        }
        // A fix
        if (modifiedTime.isAfter(Instant.now().plus(1, ChronoUnit.DAYS))) {
            // LOG.warn("Invalid modified time " + DateTimeUtil.isoInstantFormat(modifiedTime) + ", url : " + page.url());
            modifiedTime = Instant.now()
        }
        return modifiedTime
    }

    fun isValidContentModifyTime(publishTime: Instant): Boolean {
        return publishTime.isAfter(AppConstants.MIN_ARTICLE_PUBLISH_TIME)
    }
}
