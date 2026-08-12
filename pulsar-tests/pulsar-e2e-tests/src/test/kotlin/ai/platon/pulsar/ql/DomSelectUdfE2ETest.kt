package ai.platon.pulsar.ql

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end tests for the DOM select / inline select and IN_BOX UDFs,
 * all written as direct X-SQL against the local mock site.
 */
@Tag("E2ETest")
@Tag("Slow")
@DisplayName("DOM select and IN_BOX UDFs")
class DomSelectUdfE2ETest : XSqlTestBase() {

    private fun assertValue(sql: String, expected: String?) {
        assertEquals(listOf(listOf(expected)), queryRows(sql))
    }

    private val jobTitles = "Senior Frontend Engineer|Lead Frontend Developer|Senior React Engineer|Frontend Architect|Staff Frontend Engineer"

    @Test
    @DisplayName("DOM_SELECT_ALL, DOM_SELECT_FIRST and DOM_SELECT_NTH")
    fun testSelectElements() {
        assertValue(
            "SELECT ARRAY_LENGTH(DOM_SELECT_ALL(DOM_LOAD('$jobsUrl'), '.job-card-container'))",
            "5"
        )
        assertValue(
            "SELECT DOM_TEXT(DOM_SELECT_FIRST(DOM_LOAD('$jobsUrl'), '.job-card-list__title'))",
            "Senior Frontend Engineer"
        )
        assertValue(
            "SELECT DOM_TEXT(DOM_SELECT_NTH(DOM_LOAD('$jobsUrl'), '.job-card-list__title', 2))",
            "Lead Frontend Developer"
        )
    }

    @Test
    @DisplayName("DOM_ALL_TEXTS, DOM_FIRST_TEXT and DOM_NTH_TEXT")
    fun testTextExtraction() {
        val dom = "DOM_LOAD('$jobsUrl')"
        assertValue("SELECT STR_JOIN(DOM_ALL_TEXTS($dom, '.job-card-list__title'), '|')", jobTitles)
        assertValue("SELECT DOM_FIRST_TEXT($dom, '.job-card-list__title')", "Senior Frontend Engineer")
        assertValue("SELECT DOM_NTH_TEXT($dom, '.job-card-list__title', 2)", "Lead Frontend Developer")
    }

    @Test
    @DisplayName("DOM_ALL_OWN_TEXTS, DOM_FIRST_OWN_TEXT and DOM_NTH_OWN_TEXT")
    fun testOwnTextExtraction() {
        val dom = "DOM_LOAD('$jobsUrl')"
        assertValue("SELECT STR_JOIN(DOM_ALL_OWN_TEXTS($dom, '.job-card-list__title'), '|')", jobTitles)
        assertValue("SELECT DOM_FIRST_OWN_TEXT($dom, '.job-card-list__title')", "Senior Frontend Engineer")
        assertValue("SELECT DOM_NTH_OWN_TEXT($dom, '.job-card-list__title', 2)", "Lead Frontend Developer")
    }

    @Test
    @DisplayName("DOM_WHOLE_TEXTS, DOM_FIRST_WHOLE_TEXT and DOM_NTH_WHOLE_TEXT")
    fun testWholeTextExtraction() {
        val dom = "DOM_LOAD('$jobsUrl')"
        assertValue("SELECT STR_JOIN(DOM_WHOLE_TEXTS($dom, '.job-card-list__title'), '|')", jobTitles)
        assertValue("SELECT DOM_FIRST_WHOLE_TEXT($dom, '.job-card-list__title')", "Senior Frontend Engineer")
        assertValue("SELECT DOM_NTH_WHOLE_TEXT($dom, '.job-card-list__title', 2)", "Lead Frontend Developer")
    }

    @Test
    @DisplayName("DOM slim and minimal HTML extraction")
    fun testHtmlExtraction() {
        val dom = "DOM_LOAD('$jobsUrl')"
        assertValue("SELECT ARRAY_LENGTH(DOM_ALL_SLIM_HTMLS($dom, '.job-card-container'))", "5")
        assertTrue(queryValue("SELECT DOM_FIRST_SLIM_HTML($dom, '.job-card-container')")!!.contains("Senior Frontend Engineer"))
        assertTrue(queryValue("SELECT DOM_NTH_SLIM_HTML($dom, '.job-card-container', 2)")!!.contains("Lead Frontend Developer"))
        assertValue("SELECT ARRAY_LENGTH(DOM_ALL_MINIMAL_HTMLS($dom, '.job-card-container'))", "5")
        assertTrue(queryValue("SELECT DOM_FIRST_MINIMAL_HTML($dom, '.job-card-container')")!!.contains("Senior Frontend Engineer"))
        assertTrue(queryValue("SELECT DOM_NTH_MINIMAL_HTML($dom, '.job-card-container', 2)")!!.contains("Lead Frontend Developer"))
    }

    @Test
    @DisplayName("DOM integer and float extraction")
    fun testNumberExtraction() {
        val dom = "DOM_LOAD('$jobsUrl')"
        assertValue(
            "SELECT STR_JOIN(DOM_ALL_INTEGERS($dom, '.job-search-card__salary-info'), '|')",
            "150|140|160|170"
        )
        assertValue("SELECT DOM_FIRST_INTEGER($dom, '.job-search-card__salary-info')", "150")
        assertValue("SELECT DOM_NTH_INTEGER($dom, '.job-search-card__salary-info', 2)", "140")
        assertValue(
            "SELECT STR_JOIN(DOM_ALL_FLOATS($dom, '.job-search-card__salary-info'), '|')",
            "150.0|140.0|160.0|170.0"
        )
        assertValue("SELECT DOM_FIRST_FLOAT($dom, '.job-search-card__salary-info')", "150.0")
        assertValue("SELECT DOM_NTH_FLOAT($dom, '.job-search-card__salary-info', 2)", "140.0")
    }

    @Test
    @DisplayName("DOM attribute extraction")
    fun testAttributeExtraction() {
        val news = "DOM_LOAD('$newsUrl')"
        assertValue(
            "SELECT STR_JOIN(DOM_ALL_ATTRS($news, '.athing', 'id'), '|')",
            "37000001|37000002|37000003|37000004|37000005|37000006"
        )
        assertValue("SELECT DOM_FIRST_ATTR($news, '.athing', 'id')", "37000001")
        assertValue("SELECT DOM_NTH_ATTR($news, '.athing', 2, 'id')", "37000002")
    }

    @Test
    @DisplayName("DOM multi attribute extraction")
    fun testMultiAttributeExtraction() {
        val estate = "DOM_LOAD('$realEstateUrl')"
        assertValue(
            "SELECT ARRAY_LENGTH(DOM_ALL_MULTI_ATTRS($estate, 'article[data-test=\"property-card\"]', MAKE_ARRAY('data-listing-id', 'data-test')))",
            "5"
        )
        val first = queryValue(
            "SELECT DOM_FIRST_MULTI_ATTRS($estate, 'article[data-test=\"property-card\"]', MAKE_ARRAY('data-listing-id', 'data-test'))"
        )
        assertTrue(first != null && "sf-001" in first && "property-card" in first)
        val nth = queryValue(
            "SELECT DOM_NTH_MULTI_ATTRS($estate, 'article[data-test=\"property-card\"]', 2, MAKE_ARRAY('data-listing-id', 'data-test'))"
        )
        assertTrue(nth != null && "sf-002" in nth)
    }

    @Test
    @DisplayName("DOM image source extraction")
    fun testImageExtraction() {
        val seo = "DOM_LOAD('$seoUrl')"
        assertValue(
            "SELECT STR_JOIN(DOM_ALL_IMGS($seo, ':root'), '|')",
            "http://127.0.0.1:17080/images/onpage-seo.png|http://127.0.0.1:17080/images/technical-seo.png|http://127.0.0.1:17080/images/link-building-chart.png|http://127.0.0.1:17080/images/backlinks-quality.png"
        )
        assertValue("SELECT DOM_FIRST_IMG($seo, ':root')", "http://127.0.0.1:17080/images/onpage-seo.png")
        assertValue("SELECT DOM_NTH_IMG($seo, ':root', 2)", "http://127.0.0.1:17080/images/technical-seo.png")
    }

    @Test
    @DisplayName("DOM href extraction")
    fun testHrefExtraction() {
        val news = "DOM_LOAD('$newsUrl')"
        assertValue(
            "SELECT STR_JOIN(DOM_ALL_HREFS($news, '.titleline'), '|')",
            "https://example.com/ai-breakthrough|https://example.com/rust-2-release|https://example.com/webassembly-perf|https://example.com/kubernetes-security|https://example.com/postgres-17|https://example.com/llm-fine-tuning"
        )
        assertValue("SELECT DOM_FIRST_HREF($news, '.titleline')", "https://example.com/ai-breakthrough")
        assertValue("SELECT DOM_NTH_HREF($news, '.titleline', 2)", "https://example.com/rust-2-release")
    }

    @Test
    @DisplayName("DOM node labels")
    fun testNodeLabels() {
        val jobs = "DOM_LOAD('$jobsUrl')"
        assertValue("SELECT ARRAY_LENGTH(DOM_ALL_NODES_LABELS($jobs, '.job-card-container'))", "5")
        assertTrue(queryValue("SELECT DOM_FIRST_NODE_LABELS($jobs, '.job-card-container')") != null)
        assertTrue(queryValue("SELECT DOM_NTH_NODE_LABELS($jobs, '.job-card-container', 2)") != null)
    }

    @Test
    @DisplayName("DOM_ALL_RE1 and DOM_ALL_RE2 with default :root scope")
    fun testAllRe1AndAllRe2DefaultRoot() {
        val jobs = "DOM_LOAD('$jobsUrl')"
        // DOM_ALL_RE1 with 2-arg overload (dom, regex) — defaults to :root scope
        val re1Results = queryValue("SELECT ARRAY_LENGTH(DOM_ALL_RE1($jobs, '\\\\d+k'))")
        assertTrue(re1Results != null && re1Results.toInt() >= 0,
            "DOM_ALL_RE1(dom, regex) should work with default :root scope")
        // DOM_ALL_RE2 with 2-arg overload (dom, regex) — defaults to :root scope
        val re2Results = queryValue("SELECT ARRAY_LENGTH(DOM_ALL_RE2($jobs, '(\\\\d+)k - \\\$?(\\\\d+)k'))")
        assertTrue(re2Results != null && re2Results.toInt() >= 0,
            "DOM_ALL_RE2(dom, regex) should work with default :root scope")
    }

    @Test
    @DisplayName("DOM regex extraction with CSS selectors")
    fun testRegexExtraction() {
        val jobs = "DOM_LOAD('$jobsUrl')"
        assertValue(
            """SELECT STR_JOIN(DOM_ALL_RE1($jobs, '.job-search-card__salary-info', '(\d+)k'), '|')""",
            "150|140|160|170"
        )
        assertValue(
            """SELECT DOM_FIRST_RE1($jobs, '.job-search-card__salary-info', '(\d+)k')""",
            "150"
        )
        assertValue(
            """SELECT DOM_FIRST_RE1($jobs, '.job-search-card__salary-info', '(\d+)k - \$(\d+)k', 2)""",
            "200"
        )
        assertValue(
            "SELECT ARRAY_LENGTH(DOM_ALL_RE2($jobs, '.job-search-card__salary-info', '(\\d+)k - \\$(\\d+)k'))",
            "4"
        )
        assertValue(
            """SELECT STR_JOIN(DOM_FIRST_RE2($jobs, '.job-search-card__salary-info', '(\d+)k - \$(\d+)k'), ':')""",
            "150:200"
        )
        assertValue(
            """SELECT STR_JOIN(DOM_FIRST_RE2($jobs, '.job-search-card__salary-info', '(\d+)k - \$(\d+)k', 1, 2), ':')""",
            "150:200"
        )
        assertValue(
            "SELECT ARRAY_LENGTH(DOM_ALL_RE2($jobs, '.job-search-card__salary-info', '(\\d+)k - \\$(\\d+)k', 1, 2))",
            "4"
        )
    }

    @Test
    @DisplayName("DOM_INLINE_SELECT and DOM_INLINE_SELECT_TEXT")
    fun testInlineSelect() {
        val jobs = "DOM_LOAD('$jobsUrl')"
        assertValue("SELECT ARRAY_LENGTH(DOM_INLINE_SELECT($jobs, '.job-card-container'))", "5")
        assertValue("SELECT ARRAY_LENGTH(DOM_INLINE_SELECT($jobs, '.job-card-container', 1, 2))", "2")
        assertValue("SELECT ARRAY_LENGTH(DOM_INLINE_SELECT_TEXT($jobs, '.job-card-list__title'))", "5")
        assertValue(
            "SELECT STR_JOIN(DOM_INLINE_SELECT_TEXT($jobs, '.job-card-list__title', 1, 2), '|')",
            "Senior Frontend Engineer|Lead Frontend Developer"
        )
    }

    @Test
    @DisplayName("IN_BOX functions")
    fun testInBox() {
        val jobs = "DOM_LOAD('$jobsUrl')"
        assertValue("SELECT ARRAY_LENGTH(IN_BOX_ALL($jobs, 'garbage'))", "0")
        assertValue("SELECT ARRAY_LENGTH(IN_BOX_ALL($jobs, 'garbage', 1, 2))", "0")
        assertValue("SELECT DOM_IS_NIL(IN_BOX_FIRST($jobs, 'garbage'))", "TRUE")
        assertValue("SELECT DOM_IS_NIL(IN_BOX_NTH($jobs, 'garbage', 1))", "TRUE")
        assertValue("SELECT IN_BOX_FIRST_TEXT($jobs, 'garbage')", "")
        assertValue("SELECT IN_BOX_NTH_TEXT($jobs, 'garbage', 1)", "")
        assertValue("SELECT IN_BOX_FIRST_IMG($jobs, 'garbage')", "")
        assertValue("SELECT IN_BOX_NTH_IMG($jobs, 'garbage', 1)", "")
        assertValue("SELECT IN_BOX_FIRST_HREF($jobs, 'garbage')", "")
        assertValue("SELECT IN_BOX_NTH_HREF($jobs, 'garbage', 1)", "")
        assertValue("SELECT IN_BOX_FIRST_RE1($jobs, 'garbage', '(\\d+)')", "")
        assertValue("SELECT IN_BOX_FIRST_RE1($jobs, 'garbage', '(\\d+)', 1)", "")
        assertValue("SELECT ARRAY_LENGTH(IN_BOX_FIRST_RE2($jobs, 'garbage', '(\\d+)-(\\d+)'))", "2")
        assertValue("SELECT ARRAY_LENGTH(IN_BOX_FIRST_RE2($jobs, 'garbage', '(\\d+)-(\\d+)', 1, 2))", "2")
    }
}
