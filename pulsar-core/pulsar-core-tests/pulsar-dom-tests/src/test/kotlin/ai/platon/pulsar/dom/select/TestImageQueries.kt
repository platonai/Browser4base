package ai.platon.pulsar.dom.select

import ai.platon.pulsar.dom.Documents
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Regression tests for https://github.com/platonai/Browser4base/issues/5
 *
 * DOM_*_IMG style helpers auto-append the target tag (`img` / `a`) to the css query
 * when it is missing. The old implementation split the query on raw whitespace, so
 * spaces inside a pseudo-class argument such as `:expr(width > 200)` broke the
 * detection: the query was rewritten to `img:expr(width > 200) img`, which silently
 * matches nothing (an image element cannot contain another image element).
 */
@DisplayName("Image scanning selector path (appendSelectorIfMissing / selectImages)")
class TestImageQueries {

    private val html = """
        <html vi="0,0,1200,1000"><body vi="0,0,1200,1000">
          <div id="gallery" vi="0,0,1200,500">
            <img id="wide1" src="/img/wide1.jpg" alt="wide 1" vi="10,10,300,200">
            <img id="narrow" src="/img/narrow.jpg" alt="narrow" vi="10,220,100,200">
            <img id="wide2" src="/img/wide2.jpg" alt="wide 2" vi="10,430,500,200">
          </div>
        </body></html>
    """.trimIndent()

    private val doc = Documents.parse(html, "https://example.com")

    @Test
    @DisplayName("appendSelectorIfMissing must not append img when a :expr selector already targets img")
    fun testAppendSelectorIfMissingKeepsExprSelectorUntouched() {
        // issue #5: the spaces inside :expr(...) must not be treated as selector separators
        assertEquals("img:expr(width > 200)", appendSelectorIfMissing("img:expr(width > 200)", "img"))
        // same problem applies to :contains and attribute values that contain spaces
        assertEquals("a:contains(Some Text)", appendSelectorIfMissing("a:contains(Some Text)", "a"))
        assertEquals("img[src*=\"a b\"]", appendSelectorIfMissing("img[src*=\"a b\"]", "img"))
    }

    @Test
    @DisplayName("appendSelectorIfMissing still appends the target tag when the query targets containers")
    fun testAppendSelectorIfMissingStillAppendsWhenNeeded() {
        assertEquals(":root img", appendSelectorIfMissing(":root", "img"))
        assertEquals("div.gallery img", appendSelectorIfMissing("div.gallery", "img"))
        assertEquals("body a", appendSelectorIfMissing("body", "a"))
        // an expr on a container element still needs the img appended
        assertEquals("div:expr(width > 100) img", appendSelectorIfMissing("div:expr(width > 100)", "img"))
    }

    @Test
    @DisplayName("appendSelectorIfMissing keeps existing target tags untouched")
    fun testAppendSelectorIfMissingKeepsExistingTargets() {
        assertEquals("div.gallery img", appendSelectorIfMissing("div.gallery img", "img"))
        assertEquals("article > img.nav", appendSelectorIfMissing("article > img.nav", "img"))
        assertEquals("a[href]", appendSelectorIfMissing("a[href]", "a"))
    }

    @Test
    @DisplayName("PowerCSS :expr selector with spaces evaluates when querying the document")
    fun testSelectExprWithSpacesFindsWideImages() {
        val imgs = doc.select("img:expr(width > 200)")
        assertEquals(listOf("wide1", "wide2"), imgs.map { it.id() })
    }

    @Test
    @DisplayName("selectImages (img scanning helper) evaluates an :expr selector with spaces")
    fun testSelectImagesWithExprSelector() {
        // issue #5 repro: the query must not be rewritten into "img:expr(width > 200) img"
        val srcs = doc.selectImages("img:expr(width > 200)")
        assertEquals(
            listOf("https://example.com/img/wide1.jpg", "https://example.com/img/wide2.jpg"),
            srcs
        )
    }
}
