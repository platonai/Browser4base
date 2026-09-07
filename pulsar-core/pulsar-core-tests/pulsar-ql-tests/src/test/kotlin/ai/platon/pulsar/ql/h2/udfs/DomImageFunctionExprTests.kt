package ai.platon.pulsar.ql.h2.udfs

import ai.platon.pulsar.dom.Documents
import ai.platon.pulsar.ql.common.types.ValueDom
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Regression tests for https://github.com/platonai/Browser4base/issues/5
 *
 * `DOM_FIRST_IMG` / `DOM_NTH_IMG` / `DOM_ALL_IMGS` with a PowerCSS `:expr(...)`
 * selector used to silently match nothing: the selector argument was rewritten by
 * [appendSelectorIfMissing] into e.g. `img:expr(width > 200) img` (spaces inside
 * the expression were mistaken for selector separators), which never matches.
 */
@DisplayName("DOM image helpers with PowerCSS :expr() selectors")
class DomImageFunctionExprTests {

    private val html = """
        <html vi="0,0,1200,1000"><body vi="0,0,1200,1000">
          <div id="gallery" vi="0,0,1200,500">
            <img id="wide1" src="/img/wide1.jpg" alt="wide 1" vi="10,10,300,200">
            <img id="narrow" src="/img/narrow.jpg" alt="narrow" vi="10,220,100,200">
            <img id="wide2" src="/img/wide2.jpg" alt="wide 2" vi="10,430,500,200">
          </div>
        </body></html>
    """.trimIndent()

    private val dom = ValueDom.get(Documents.parse(html, "https://example.com"))

    @Test
    @DisplayName("DOM_FIRST_IMG evaluates :expr(width > 200) and returns the first wide image src")
    fun testFirstImgWithExprSelector() {
        assertEquals(
            "https://example.com/img/wide1.jpg",
            DomSelectFunctions.firstImg(dom, "img:expr(width > 200)")
        )
    }

    @Test
    @DisplayName("DOM_NTH_IMG evaluates :expr(width > 200) and returns the nth wide image src")
    fun testNthImgWithExprSelector() {
        assertEquals(
            "https://example.com/img/wide2.jpg",
            DomSelectFunctions.nthImg(dom, "img:expr(width > 200)", 2)
        )
    }

    @Test
    @DisplayName("DOM_ALL_IMGS evaluates :expr(width > 200) and returns every wide image src")
    fun testAllImgsWithExprSelector() {
        val all = DomSelectFunctions.allImgs(dom, "img:expr(width > 200)")
        assertEquals(2, all.list.size)
        assertEquals(
            listOf("https://example.com/img/wide1.jpg", "https://example.com/img/wide2.jpg"),
            all.list.map { it.string }
        )
    }
}
