package ai.platon.pulsar.persist

import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.config.MutableConfig
import ai.platon.pulsar.common.config.VolatileConfig
import ai.platon.pulsar.persist.model.PulsarWebPage
import ai.platon.pulsar.persist.model.HyperLinkRecord
import ai.platon.pulsar.persist.model.ParseStatusRecord
import ai.platon.pulsar.persist.model.ProtocolStatusRecord
import ai.platon.pulsar.persist.model.WebPageRecord
import kotlin.test.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread
import java.util.Comparator

class WebDbTest {

    private lateinit var tempDir: Path
    private lateinit var conf: ImmutableConfig
    private lateinit var webDb: WebDb

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("webdb-test-")
        val mutableConf = MutableConfig()
        mutableConf.set("storage.local.dir", tempDir.toAbsolutePath().toString())
        conf = mutableConf  // MutableConfig extends ImmutableConfig
        webDb = WebDb(conf)
    }

    @AfterTest
    fun tearDown() {
        webDb.close()
        // Clean up temp directory
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach { Files.deleteIfExists(it) }
    }

    @Test
    fun `test round-trip all fields`() {
        val url = "https://example.com/test/page"
        val page = createTestPage(url)

        // Populate all fields
        with(page.unbox()) {
            baseUrl = url
            createTime = 1000L
            distance = 3
            fetchCount = 5
            fetchPriority = 7
            fetchInterval = 3600
            zoneId = "UTC"
            params = "arg1=val1"
            batchId = "batch-001"
            resource = 1
            crawlStatus = 2
            browser = "CHROME"
            proxy = "proxy1"
            prevFetchTime = 900L
            prevCrawlTime1 = 800L
            fetchTime = 1000L
            fetchRetries = 2
            reprUrl = "https://example.com/rep"
            prevModifiedTime = 700L
            modifiedTime = 1000L
            protocolStatus = ProtocolStatusRecord(majorCode = 1, minorCode = 0, args = mutableMapOf("key" to "value"))
            encoding = "UTF-8"
            contentType = "text/html"
            contentLength = 1024L
            lastContentLength = 512L
            aveContentLength = 768L
            persistedContentLength = 1024L
            referrer = "https://referrer.com"
            htmlIntegrity = "SHA256"
            anchor = "Click here"
            anchorOrder = 1
            parseStatus = ParseStatusRecord(majorCode = 1, minorCode = 0)
            pageTitle = "Test Page"
            pageText = "This is the full page text"
            contentTitle = "Content Title"
            contentText = "Extracted content text"
            contentTextLen = 42
            pageCategory = "DETAIL"
            contentModifiedTime = 1000L
            prevContentModifiedTime = 900L
            contentPublishTime = 800L
            prevContentPublishTime = 700L
            refContentPublishTime = 600L
            prevRefContentPublishTime = 500L
            pageModelUpdateTime = 400L
            prevSignature = ByteBuffer.wrap("prevSig".toByteArray())
            signature = ByteBuffer.wrap("currSig".toByteArray())
            contentScore = 0.95f
            score = 0.85f
            sortScore = "99"
            pageCounters["views"] = 42
            headers["Server"] = "nginx"
            links.add("https://example.com/link1")
            deadLinks.add("https://example.com/dead")
            liveLinks["link1"] = HyperLinkRecord(url = "https://example.com/live1", anchor = "Live 1", order = 1)
            vividLinks["vivid1"] = "https://example.com/vivid1"
            inlinks["in1"] = "https://example.com/in1"
            markers["marker1"] = "value1"
            metadata["meta1"] = ByteBuffer.wrap("metavalue".toByteArray())
        }

        // Write
        val result = webDb.put(page, replaceIfExists = true)
        assertTrue(result, "Write should succeed")

        // Read back
        val loaded = webDb.getOrNull(url) ?: fail("Should retrieve the page")
        val record = (loaded as PulsarWebPage).unbox()

        // Verify all fields round-tripped correctly
        assertEquals(url, record.baseUrl)
        assertEquals(1000L, record.createTime)
        assertEquals(3, record.distance)
        assertEquals(5, record.fetchCount)
        assertEquals(7, record.fetchPriority)
        assertEquals(3600, record.fetchInterval)
        assertEquals("UTC", record.zoneId)
        assertEquals("arg1=val1", record.params)
        assertEquals("batch-001", record.batchId)
        assertEquals(1, record.resource)
        assertEquals(2, record.crawlStatus)
        assertEquals("CHROME", record.browser)
        assertEquals("proxy1", record.proxy)
        assertEquals(900L, record.prevFetchTime)
        assertEquals(800L, record.prevCrawlTime1)
        assertEquals(1000L, record.fetchTime)
        assertEquals(2, record.fetchRetries)
        assertEquals("https://example.com/rep", record.reprUrl)
        assertEquals(700L, record.prevModifiedTime)
        assertEquals(1000L, record.modifiedTime)
        assertEquals(1, record.protocolStatus?.majorCode)
        assertEquals("value", record.protocolStatus?.args?.get("key"))
        assertEquals("UTF-8", record.encoding)
        assertEquals("text/html", record.contentType)
        assertEquals(1024L, record.contentLength)
        assertEquals(512L, record.lastContentLength)
        assertEquals(768L, record.aveContentLength)
        assertEquals(1024L, record.persistedContentLength)
        assertEquals("https://referrer.com", record.referrer)
        assertEquals("SHA256", record.htmlIntegrity)
        assertEquals("Click here", record.anchor)
        assertEquals(1, record.anchorOrder)
        assertEquals(1, record.parseStatus?.majorCode)
        assertEquals("Test Page", record.pageTitle)
        assertEquals("This is the full page text", record.pageText)
        assertEquals("Content Title", record.contentTitle)
        assertEquals("Extracted content text", record.contentText)
        assertEquals(42, record.contentTextLen)
        assertEquals("DETAIL", record.pageCategory)
        assertEquals(1000L, record.contentModifiedTime)
        assertEquals(900L, record.prevContentModifiedTime)
        assertEquals(800L, record.contentPublishTime)
        assertEquals(700L, record.prevContentPublishTime)
        assertEquals(600L, record.refContentPublishTime)
        assertEquals(500L, record.prevRefContentPublishTime)
        assertEquals(400L, record.pageModelUpdateTime)
        assertEquals("prevSig", String(record.prevSignature!!.array()))
        assertEquals("currSig", String(record.signature!!.array()))
        assertEquals(0.95f, record.contentScore)
        assertEquals(0.85f, record.score)
        assertEquals("99", record.sortScore)
        assertEquals(42, record.pageCounters["views"])
        assertEquals("nginx", record.headers["Server"])
        assertEquals("https://example.com/link1", record.links[0])
        assertEquals("https://example.com/dead", record.deadLinks[0])
        assertEquals("https://example.com/live1", record.liveLinks["link1"]?.url)
        assertEquals("https://example.com/vivid1", record.vividLinks["vivid1"])
        assertEquals("https://example.com/in1", record.inlinks["in1"])
        assertEquals("value1", record.markers["marker1"])
        assertEquals("metavalue", String(record.metadata["meta1"]!!.array()))
    }

    @Test
    fun `test round-trip with content`() {
        val url = "https://example.com/content-test"
        val page = createTestPage(url)

        val htmlContent = "<html><body><h1>Hello World</h1></body></html>"
        page.setStringContent(htmlContent)

        val result = webDb.put(page, replaceIfExists = true)
        assertTrue(result, "Write should succeed")

        // Read back metadata
        val loaded = webDb.getOrNull(url) ?: fail("Should retrieve the page")
        val record = (loaded as PulsarWebPage).unbox()

        // Content should be null in metadata (stored separately)
        assertNull(record.content, "Content should be null in metadata")

        // Read content separately
        val content = webDb.getContent(url)
        assertNotNull(content, "Content should be retrievable")
        val contentStr = String(content.array(), content.arrayOffset() + content.position(), content.remaining())
        assertEquals(htmlContent, contentStr)

        // Read content as string
        val contentStr2 = webDb.getContentAsString(url)
        assertEquals(htmlContent, contentStr2)
    }

    @Test
    fun `test get non-existent page`() {
        val result = webDb.getOrNull("https://nonexistent.example.com")
        assertNull(result, "Should return null for non-existent page")

        val page = webDb.get("https://nonexistent.example.com")
        assertTrue(page.isNil, "Should return NIL page for non-existent URL")
    }

    @Test
    fun `test exists`() {
        val url = "https://example.com/exists-test"
        assertFalse(webDb.exists(url), "Should not exist before write")

        val page = createTestPage(url)
        webDb.put(page, replaceIfExists = true)

        assertTrue(webDb.exists(url), "Should exist after write")
    }

    @Test
    fun `test replace if exists false`() {
        val url = "https://example.com/replace-test"
        val page1 = createTestPage(url)
        page1.pageTitle = "First Version"
        val firstResult = webDb.put(page1, replaceIfExists = false)
        assertTrue(firstResult, "First write should succeed")

        val page2 = createTestPage(url)
        page2.pageTitle = "Second Version"
        val secondResult = webDb.put(page2, replaceIfExists = false)
        assertFalse(secondResult, "Second write with replaceIfExists=false should fail")

        // Verify the first version is still there
        val loaded = webDb.getOrNull(url)
        assertEquals("First Version", loaded?.pageTitle, "Should still be first version")
    }

    @Test
    fun `test delete`() {
        val url = "https://example.com/delete-test"
        val page = createTestPage(url)
        webDb.put(page, replaceIfExists = true)

        assertTrue(webDb.exists(url), "Should exist before delete")

        val deleted = webDb.delete(url)
        assertTrue(deleted, "Delete should return true for existing page")

        assertFalse(webDb.exists(url), "Should not exist after delete")
        assertNull(webDb.getOrNull(url), "getOrNull should return null after delete")
    }

    @Test
    fun `test delete non-existent`() {
        val deleted = webDb.delete("https://nonexistent.example.com")
        assertFalse(deleted, "Delete should return false for non-existent page")
    }

    @Test
    fun `test truncate`() {
        // Write multiple pages
        val urls = listOf(
            "https://example.com/truncate-1",
            "https://example.com/truncate-2",
            "https://example.com/truncate-3"
        )
        urls.forEach { url ->
            webDb.put(createTestPage(url), replaceIfExists = true)
        }

        // Verify all exist
        urls.forEach { url ->
            assertTrue(webDb.exists(url), "Should exist before truncate: $url")
        }

        // Truncate without force should not work
        val noForceResult = webDb.truncate(force = false)
        assertFalse(noForceResult, "Truncate without force should return false")

        // Truncate with force
        val forceResult = webDb.truncate(force = true)
        assertTrue(forceResult, "Truncate with force should succeed")

        // Verify all gone
        urls.forEach { url ->
            assertFalse(webDb.exists(url), "Should not exist after truncate: $url")
        }
    }

    @Test
    fun `test update existing page`() {
        val url = "https://example.com/update-test"
        val page = createTestPage(url)
        page.pageTitle = "Original"
        webDb.put(page, replaceIfExists = true)

        // Update
        val page2 = createTestPage(url)
        page2.pageTitle = "Updated"
        page2.fetchCount = 10
        webDb.put(page2, replaceIfExists = true)

        val loaded = webDb.getOrNull(url)
        assertEquals("Updated", loaded?.pageTitle)
        assertEquals(10, loaded?.fetchCount)
    }

    @Test
    fun `test content null page`() {
        val url = "https://example.com/no-content"
        val page = createTestPage(url)
        // Page without explicit content - content should be null
        webDb.put(page, replaceIfExists = true)

        val content = webDb.getContent(url)
        assertNull(content, "Content should be null for page without content")
        assertNull(webDb.getContentAsString(url), "Content string should be null for page without content")
    }

    @Test
    fun `test concurrent write different URLs`() {
        val numPages = 20
        val threads = (0 until numPages).map { index ->
            thread {
                val url = "https://example.com/concurrent-$index"
                val page = createTestPage(url)
                page.pageTitle = "Concurrent $index"
                webDb.put(page, replaceIfExists = true)
            }
        }
        threads.forEach { it.join() }

        // Verify all pages written correctly
        for (index in 0 until numPages) {
            val url = "https://example.com/concurrent-$index"
            assertTrue(webDb.exists(url), "Page $index should exist")
            val loaded = webDb.getOrNull(url)
            assertEquals("Concurrent $index", loaded?.pageTitle, "Page $index should have correct title")
        }
    }

    @Test
    fun `test concurrent read and write`() {
        val url = "https://example.com/concurrent-rw"
        val page = createTestPage(url)
        page.pageTitle = "Initial"
        webDb.put(page, replaceIfExists = true)

        val readerThreads = (0 until 5).map {
            thread {
                repeat(50) {
                    val p = webDb.getOrNull(url)
                    assertNotNull(p, "Page should exist during concurrent access")
                }
            }
        }

        val writerThreads = (0 until 5).map { index ->
            thread {
                repeat(10) {
                    val p = createTestPage(url)
                    p.pageTitle = "Updated-$index-$it"
                    webDb.put(p, replaceIfExists = true)
                }
            }
        }

        (readerThreads + writerThreads).forEach { it.join() }

        // Final read should succeed
        val final = webDb.getOrNull(url)
        assertNotNull(final, "Page should exist after concurrent access")
    }

    @Test
    fun `test url normalization get`() {
        val url = "https://example.com/normalized"
        val page = createTestPage(url)
        page.pageTitle = "Normalized"
        webDb.put(page, replaceIfExists = true)

        // Retrieval with norm=true should also work
        val loaded = webDb.getOrNull(url, norm = true)
        assertNotNull(loaded, "Should find page with norm=true")
        assertEquals("Normalized", loaded.pageTitle)
    }

    @Test
    fun `test db counters increment`() {
        val initialCount = WebDb.dbGetCount.get()

        val url = "https://example.com/counter-test"
        val page = createTestPage(url)
        webDb.put(page, replaceIfExists = true)

        webDb.getOrNull(url)
        webDb.getOrNull(url)

        assertEquals(initialCount + 2, WebDb.dbGetCount.get(), "dbGetCount should increment on reads")
    }

    // --- Helpers ---

    private fun createTestPage(url: String): PulsarWebPage {
        val volConf = VolatileConfig(conf)
        return PulsarWebPage.newWebPage(url, volConf)
    }
}
