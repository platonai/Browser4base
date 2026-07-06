package ai.platon.pulsar.basic

import ai.platon.pulsar.common.PulsarParams
import ai.platon.pulsar.common.sleepSeconds
import ai.platon.pulsar.persist.AbstractWebPage
import ai.platon.pulsar.persist.model.PulsarWebPage
import ai.platon.pulsar.persist.metadata.Name
import ai.platon.pulsar.skeleton.common.message.PageLoadStatusFormatter
import ai.platon.pulsar.common.printlnPro
import ai.platon.pulsar.skeleton.common.persist.ext.options
import ai.platon.pulsar.test.TestUrls
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import java.time.Instant
import kotlin.test.*

/**
 * Created by Vincent on 16-7-20.
 * Copyright @ 2013-2016 Platon AI. All rights reserved
 */
class TestWebPage: TestBase() {
    private val url = TestUrls.PRODUCT_DETAIL_URL
    private val groupId = 43853791

    @BeforeEach
    suspend fun clearResources() {
        session.delete(url)
        assertTrue("Page should not exists | $url") { !session.exists(url) }
    }

    @org.junit.jupiter.api.Test
    suspend fun testFetchTime() {
        val args = "-i 5s"
        val normalizedArgs = "-expires PT5S"
        val option = session.options(args)
        var page = session.load(url, option)
        Assumptions.assumeTrue(page.protocolStatus.isSuccess,
            "Failed to fetch the page, abort the test | $url")

        val prevFetchTime1 = page.prevFetchTime

        val fetchTime1 = page.fetchTime
        assertTrue("Fetch time should be in 1 minutes, actual $fetchTime1") { fetchTime1 > Instant.now().minusSeconds(60) }

        assertTrue { page.protocolStatus.isSuccess }
        assertTrue { page.isContentUpdated }
        assertTrue(page is AbstractWebPage)
        assertEquals(option, page.variables[PulsarParams.VAR_LOAD_OPTIONS])
        assertTrue { page.args.contains(normalizedArgs) }
        // TODO: fix this issue: expected: <-expires PT5S> but was: <-expires PT5S -ignoreFailure -nJitRetry 3 -parse -test 1>
        // assertEquals(normalizedArgs, page.args)

        sleepSeconds(5)
        val expireAt = Instant.now()
        sleepSeconds(5)

        val options2 = session.options("$args -expireAt $expireAt")
        assertTrue { options2.isExpired(page.prevFetchTime) }

        page = session.load(url, options2)
        Assumptions.assumeTrue(page.protocolStatus.isSuccess,
            "Failed to fetch the page, abort the test | $url")

        assertTrue { page.protocolStatus.isSuccess }
        assertTrue("Page should be fetched since it expireAt $expireAt") { page.isFetched }
        assertTrue { page.isContentUpdated }
        assertEquals(options2, page.options)
        val prevFetchTime2 = page.prevFetchTime
        assertTrue("prevFetchTime2 should be in 1 minutes") { fetchTime1 > Instant.now().minusSeconds(60) }

//        val fetchTime2 = page.fetchTime

        printlnPro(PageLoadStatusFormatter(page, "", true, true, true, true))
        printlnPro("prevFetchTime: " + page.prevFetchTime)
        printlnPro("fetchTime: " + page.fetchTime)
        val responseTime = page.metadata[Name.RESPONSE_TIME]?:""
        printlnPro(responseTime)
        printlnPro(Instant.now())
        printlnPro("fetchCount: " + page.fetchCount)
        printlnPro("fetchInterval: " + page.fetchInterval)

        assertTrue { prevFetchTime1 < prevFetchTime2 }

        // Not required currently
        // assertEquals(prevFetchTime2, page.fetchTime)

        assertTrue { fetchTime1 < page.fetchTime }
        assertEquals(2, page.fetchCount)
    }
}

