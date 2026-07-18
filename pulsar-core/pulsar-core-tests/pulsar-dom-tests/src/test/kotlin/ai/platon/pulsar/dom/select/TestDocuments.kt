package ai.platon.pulsar.dom.select

import ai.platon.pulsar.dom.Documents
import ai.platon.pulsar.dom.nodes.node.ext.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestDocuments {

    @Test
    fun testParseWithoutFeatures() {
        val html = "<html><body><div>hello</div></body></html>"

        val featured = Documents.parse(html, "https://example.com")
        assertEquals(13, featured.features.dimension)

        val lean = Documents.parse(html, "https://example.com", calculateFeatures = false)
        assertEquals("https://example.com", lean.baseURI)
        assertEquals(0, lean.features.dimension, "features should not be calculated")
    }

    @Test
    fun testParseWithFeaturesCalculatesNodeFeatures() {
        val html = """<html><head></head><body><div id="d1" vi="10 20 100 200">hello</div></body></html>"""

        val document = Documents.parse(html, "https://example.com")
        val div = document.selectFirst("#d1")!!

        assertEquals(13, document.features.dimension)
        assertEquals(10, div.left)
        assertEquals(20, div.top)
        assertEquals(100, div.width)
        assertEquals(200, div.height)
        assertEquals(5, div.numChars)
        assertEquals(1, div.numTextNodes)
    }

    @Test
    fun testCalculateFeaturesToggleProducesSameDomStructure() {
        val html = "<html><body><div class='item'>a</div><div class='item'>b</div></body></html>"

        val withFeatures = Documents.parse(html, "https://example.com", calculateFeatures = true)
        val withoutFeatures = Documents.parse(html, "https://example.com", calculateFeatures = false)

        assertEquals(withFeatures.select(".item").size, withoutFeatures.select(".item").size)
        assertEquals(withFeatures.title, withoutFeatures.title)
        assertEquals(withFeatures.body.html(), withoutFeatures.body.html())
    }

    @Test
    fun testParseBodyFragmentRespectsFeatureToggle() {
        val fragment = "<p>paragraph</p><span>span</span>"

        val withFeatures = Documents.parseBodyFragment(fragment, "https://example.com", calculateFeatures = true)
        val withoutFeatures = Documents.parseBodyFragment(fragment, "https://example.com", calculateFeatures = false)

        assertEquals(13, withFeatures.features.dimension)
        assertEquals(0, withoutFeatures.features.dimension)
        assertEquals(withFeatures.body.html(), withoutFeatures.body.html())
    }

    @Test
    fun testLevel1FeatureCalculation() {
        val html = "<html><head></head><body><div id=\"parent\" vi=\"0 0 500 500\">" +
                "<a id=\"link1\" href=\"#\" vi=\"10 10 50 30\">link1</a>" +
                "<img id=\"img1\" src=\"x.jpg\" vi=\"70 10 60 40\"/>" +
                "<span id=\"text1\" vi=\"140 10 50 20\" tv0=\"140 10 50 20\">hello</span>" +
                "<span id=\"text2\" vi=\"200 10 60 20\" tv0=\"200 10 60 20\">world</span>" +
                "</div></body></html>"

        val document = Documents.parse(html, "https://example.com")
        val htmlElement = document.selectFirst("html")!!
        val body = document.body
        val parent = document.selectFirst("#parent")!!
        val link1 = document.selectFirst("#link1")!!
        val img1 = document.selectFirst("#img1")!!
        val text1 = document.selectFirst("#text1")!!
        val text2 = document.selectFirst("#text2")!!

        // document aggregates the whole tree
        assertEquals(15, document.document.numChars)
        assertEquals(3, document.document.numTextNodes)
        assertEquals(1, document.document.numImages)
        assertEquals(1, document.document.numAnchors)
        assertEquals(1, document.document.numChildren)

        // html has head and body as children
        assertEquals(15, htmlElement.numChars)
        assertEquals(3, htmlElement.numTextNodes)
        assertEquals(2, htmlElement.numChildren)
        assertEquals(1, htmlElement.depth)

        // body aggregates everything below it
        assertEquals(15, body.numChars)
        assertEquals(3, body.numTextNodes)
        assertEquals(1, body.numImages)
        assertEquals(1, body.numAnchors)
        assertEquals(1, body.numChildren)
        assertEquals(900, body.width)
        assertEquals(520, body.height)
        assertEquals(2, body.depth)

        // parent div
        assertEquals(0, parent.left)
        assertEquals(0, parent.top)
        assertEquals(500, parent.width)
        assertEquals(500, parent.height)
        assertEquals(15, parent.numChars)
        assertEquals(3, parent.numTextNodes)
        assertEquals(1, parent.numImages)
        assertEquals(1, parent.numAnchors)
        assertEquals(4, parent.numChildren)
        assertEquals(3, parent.depth)
        assertEquals(1.92, parent.textNodeDensity, 0.001)

        // link, image and text spans
        assertEquals(1, link1.numAnchors)
        assertEquals(5, link1.numChars)
        assertEquals(1, link1.numTextNodes)
        assertEquals(0, link1.numChildren)
        assertEquals(4, link1.numSiblings)
        assertEquals(4, link1.depth)

        assertEquals(1, img1.numImages)
        assertEquals(0, img1.numChars)
        assertEquals(0, img1.numChildren)
        assertEquals(4, img1.numSiblings)
        assertEquals(4, img1.depth)

        assertEquals(5, text1.numChars)
        assertEquals(1, text1.numTextNodes)
        assertEquals(0, text1.numChildren)
        assertEquals(4, text1.numSiblings)
        assertEquals(4, text1.depth)

        assertEquals(5, text2.numChars)
        assertEquals(1, text2.numTextNodes)
        assertEquals(0, text2.numChildren)
        assertEquals(4, text2.numSiblings)
        assertEquals(4, text2.depth)

        // text nodes carry their own character count and depth/sequence
        val helloText = text1.textNodes().first { it.text() == "hello" }
        assertEquals(5, helloText.numChars)
        assertEquals(5, helloText.depth)

        // induced text-node density is computed for all nodes
        assertTrue(body.textNodeDensity > 0)
        assertTrue(htmlElement.textNodeDensity > 0)
    }

    @Test
    @Tag("ManualOnly")
    @Ignore("ManualOnly")
    fun testDocumentLoadPerformance() {
        val htmlDir = Paths.get("D:\\Backup\\Data\\amazon-com")

        val allHtmlPaths = Files.list(htmlDir).use { path ->
            path.filter { it.toString().endsWith("html") || it.toString().endsWith("htm") }.sorted().toList()
        }

        allHtmlPaths.forEach {
            println(it)
        }

        val durations = mutableListOf<Long>()
        allHtmlPaths.take(200).forEachIndexed { index, path ->
            val startTime = Instant.now()
            val document = Documents.parse(path, "UTF-8", path.toString())
            val normalizedURI = document.normalizedURI ?: ""
            // println(normalizedURI)
            if (!normalizedURI.contains("/dp/")) {
                println("Not a product page: ${path.toUri()}")
                // Files.deleteIfExists(path)
            }
            val endTime = Instant.now()
            val duration: Long = endTime.toEpochMilli() - startTime.toEpochMilli()
            durations.add(duration)
            println("$index Parsed ${path.fileName} in $duration ms | $normalizedURI")
            println(durations.joinToString(", ", "durations (ms): [", "]"))

            if (durations.takeLast(5).average() > 5000) {
                return@forEachIndexed
            }
        }
    }
}
