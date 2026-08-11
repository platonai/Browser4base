package ai.platon.pulsar.ql

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end tests for the core DOM scalar UDFs, all written as direct X-SQL
 * against the local mock site with assertions on the final results.
 */
@Tag("E2ETest")
@DisplayName("Core DOM scalar UDFs")
class DomCoreUdfE2ETest : XSqlTestBase() {

    private fun assertValue(sql: String, expected: String?) {
        assertEquals(listOf(listOf(expected)), queryRows(sql))
    }

    @Test
    @DisplayName("DOM_LOAD and DOM_FETCH")
    fun testLoadAndFetch() {
        assertValue("SELECT DOM_IS_NOT_NIL(DOM_LOAD('$jobsUrl'))", "true")
        assertValue("SELECT DOM_IS_NOT_NIL(DOM_FETCH('$jobsUrl'))", "true")
    }

    @Test
    @DisplayName("DOM_IS_NIL and DOM_IS_NOT_NIL")
    fun testIsNil() {
        assertValue("SELECT DOM_IS_NIL(DOM_LOAD('$jobsUrl'))", "false")
        assertValue("SELECT DOM_IS_NOT_NIL(DOM_LOAD('$jobsUrl'))", "true")
    }

    @Test
    @DisplayName("DOM_ATTR and DOM_HAS_ATTR")
    fun testAttr() {
        assertValue("SELECT DOM_ATTR(DOM_LOAD('$seoUrl'), 'lang')", "en")
        assertValue("SELECT DOM_HAS_ATTR(DOM_LOAD('$seoUrl'), 'lang')", "true")
    }

    @Test
    @DisplayName("DOM_ID, DOM_CLASS_NAME, DOM_CLASS_NAMES, DOM_HAS_CLASS")
    fun testIdentity() {
        assertValue("SELECT DOM_ID(DOM_SELECT_FIRST(DOM_LOAD('$newsUrl'), '.athing'))", "37000001")
        assertValue(
            "SELECT DOM_CLASS_NAME(DOM_SELECT_FIRST(DOM_LOAD('$jobsUrl'), '.job-card-container'))",
            "job-card-container"
        )
        assertValue(
            "SELECT DOM_HAS_CLASS(DOM_SELECT_FIRST(DOM_LOAD('$jobsUrl'), '.job-card-container'), 'job-card-container')",
            "true"
        )
        val classNames = queryValue(
            "SELECT DOM_CLASS_NAMES(DOM_SELECT_FIRST(DOM_LOAD('$jobsUrl'), '.job-card-container'))"
        )
        assertTrue(classNames != null && "job-card-container" in classNames)
    }

    @Test
    @DisplayName("DOM_TAG_NAME and DOM_UNIQUE_NAME")
    fun testTagName() {
        assertValue(
            "SELECT DOM_TAG_NAME(DOM_SELECT_FIRST(DOM_LOAD('$jobsUrl'), '.job-card-list__title'))",
            "h2"
        )
        assertTrue(
            queryValue("SELECT DOM_UNIQUE_NAME(DOM_SELECT_FIRST(DOM_LOAD('$jobsUrl'), '.job-card-container'))")!!.isNotEmpty()
        )
    }

    @Test
    @DisplayName("DOM_CSS_SELECTOR and DOM_CSS_PATH")
    fun testCssSelector() {
        val selector = queryValue(
            "SELECT DOM_CSS_SELECTOR(DOM_SELECT_FIRST(DOM_LOAD('$jobsUrl'), '.job-card-list__title'))"
        )
        assertTrue(selector != null && "job-card" in selector)
        val path = queryValue(
            "SELECT DOM_CSS_PATH(DOM_SELECT_FIRST(DOM_LOAD('$jobsUrl'), '.job-card-list__title'))"
        )
        assertTrue(path != null && "job-card" in path)
    }

    @Test
    @DisplayName("DOM_SEQUENCE and DOM_DEPTH")
    fun testSequenceAndDepth() {
        assertTrue(queryValue("SELECT DOM_SEQUENCE(DOM_LOAD('$jobsUrl'))")!!.toDouble() >= 0)
        assertTrue(queryValue("SELECT DOM_DEPTH(DOM_LOAD('$jobsUrl'))")!!.toDouble() >= 0)
    }

    @Test
    @DisplayName("DOM_URI, DOM_BASE_URI and DOM_LOCATION")
    fun testUriAndLocation() {
        listOf("DOM_URI", "DOM_BASE_URI", "DOM_LOCATION").forEach { fn ->
            val value = queryValue("SELECT $fn(DOM_LOAD('$jobsUrl'))")
            assertTrue(value != null && value.startsWith("http://127.0.0.1:17080"), "$fn was $value")
        }
    }

    @Test
    @DisplayName("DOM tree navigation functions")
    fun testTreeNavigation() {
        assertValue(
            "SELECT DOM_TAG_NAME(DOM_PARENT(DOM_SELECT_FIRST(DOM_LOAD('$jobsUrl'), '.job-card-list__title')))",
            "article"
        )
        assertValue(
            "SELECT DOM_TAG_NAME(DOM_ANCESTOR(DOM_SELECT_FIRST(DOM_LOAD('$jobsUrl'), '.job-card-list__title'), 1))",
            "article"
        )
        assertTrue(
            queryValue("SELECT DOM_PARENT_NAME(DOM_SELECT_FIRST(DOM_LOAD('$jobsUrl'), '.job-card-list__title'))")!!.isNotEmpty()
        )
        assertValue(
            "SELECT DOM_IS_NOT_NIL(DOM_OWNER_DOCUMENT(DOM_SELECT_FIRST(DOM_LOAD('$jobsUrl'), '.job-card-list__title')))",
            "true"
        )
        assertValue(
            "SELECT DOM_TAG_NAME(DOM_OWNER_BODY(DOM_SELECT_FIRST(DOM_LOAD('$jobsUrl'), '.job-card-list__title')))",
            "body"
        )
    }

    @Test
    @DisplayName("DOM child and sibling metrics")
    fun testChildAndSiblingMetrics() {
        val article = "DOM_SELECT_FIRST(DOM_LOAD('$jobsUrl'), '.job-card-container')"
        val title = "DOM_SELECT_FIRST(DOM_LOAD('$jobsUrl'), '.job-card-list__title')"
        assertValue("SELECT DOM_CHILD_ELEMENT_SIZE($article)", "4")
        assertTrue(queryValue("SELECT DOM_CHILD_NODE_SIZE($article)")!!.toInt() >= 4)
        assertValue("SELECT DOM_ELEMENT_SIBLING_SIZE($title)", "3")
        assertValue("SELECT DOM_ELEMENT_SIBLING_INDEX($title)", "0")
        assertTrue(queryValue("SELECT DOM_SIBLING_SIZE($title)")!!.toInt() >= 3)
        assertTrue(queryValue("SELECT DOM_SIBLING_INDEX($title)")!!.toInt() >= 0)
    }

    @Test
    @DisplayName("DOM text extraction")
    fun testTextExtraction() {
        val title = "DOM_SELECT_FIRST(DOM_LOAD('$jobsUrl'), '.job-card-list__title')"
        assertValue("SELECT DOM_TEXT($title)", "Senior Frontend Engineer")
        assertValue("SELECT DOM_TEXT($title, 5)", "Senio")
        assertValue("SELECT DOM_TEXT_LEN($title)", "23")
        assertValue("SELECT DOM_TEXT_LENGTH($title)", "23")
        assertValue("SELECT DOM_OWN_TEXT($title)", "Senior Frontend Engineer")
        assertValue("SELECT DOM_OWN_TEXT_LEN($title)", "23")
        assertValue("SELECT DOM_WHOLE_TEXT($title)", "Senior Frontend Engineer")
        assertValue("SELECT DOM_WHOLE_TEXT_LEN($title)", "23")
        assertValue("SELECT DOM_HAS_TEXT($title)", "true")
        assertValue("SELECT ARRAY_LENGTH(DOM_OWN_TEXTS($title))", "1")
    }

    @Test
    @DisplayName("DOM HTML serialization")
    fun testHtmlSerialization() {
        val title = "DOM_SELECT_FIRST(DOM_LOAD('$jobsUrl'), '.job-card-list__title')"
        assertValue("SELECT DOM_HTML($title)", "Senior Frontend Engineer")
        val outerHtml = queryValue("SELECT DOM_OUTER_HTML($title)")
        assertTrue(outerHtml != null && "job-card-list__title" in outerHtml && "Senior Frontend Engineer" in outerHtml)
        assertTrue(queryValue("SELECT DOM_SLIM_HTML($title)")!!.contains("Senior Frontend Engineer"))
        assertTrue(queryValue("SELECT DOM_MINIMAL_HTML($title)")!!.contains("Senior Frontend Engineer"))
    }

    @Test
    @DisplayName("DOM_DOM identity")
    fun testDomIdentity() {
        assertValue("SELECT DOM_IS_NOT_NIL(DOM_DOM(DOM_LOAD('$jobsUrl')))", "true")
    }

    @Test
    @DisplayName("DOM regex extraction")
    fun testRegexExtraction() {
        val title = "DOM_SELECT_FIRST(DOM_LOAD('$jobsUrl'), '.job-card-list__title')"
        assertValue("""SELECT DOM_RE1($title, 'Senior (\w+)')""", "Frontend")
        assertValue("""SELECT DOM_RE1($title, '(Senior) (\w+)', 2)""", "Frontend")
        assertValue("SELECT STR_JOIN(DOM_RE2($title, '(Senior) (Frontend)'), ':')", "Senior:Frontend")
    }

    @Test
    @DisplayName("DOM link and image properties")
    fun testLinkAndImageProperties() {
        assertValue(
            "SELECT DOM_HREF(DOM_SELECT_FIRST(DOM_LOAD('$newsUrl'), '.titleline a'))",
            "https://example.com/ai-breakthrough"
        )
        assertValue(
            "SELECT DOM_ABS_HREF(DOM_SELECT_FIRST(DOM_LOAD('$newsUrl'), '.titleline a'))",
            "https://example.com/ai-breakthrough"
        )
        assertValue(
            "SELECT DOM_SRC(DOM_SELECT_FIRST(DOM_LOAD('$seoUrl'), 'img'))",
            "/images/onpage-seo.png"
        )
        val absSrc = queryValue("SELECT DOM_ABS_SRC(DOM_SELECT_FIRST(DOM_LOAD('$seoUrl'), 'img'))")
        assertTrue(absSrc != null && "/images/onpage-seo.png" in absSrc)
        assertValue("SELECT ARRAY_LENGTH(DOM_LINKS(DOM_LOAD('$seoUrl')))", "10")
    }

    @Test
    @DisplayName("DOM_TITLE, DOM_DOC_TITLE, DOM_VALUE, DOM_DATA, DOM_STYLE")
    fun testTitleValueDataStyle() {
        assertValue(
            "SELECT DOM_TITLE(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), '#attrTest'))",
            "Test Title"
        )
        assertValue("SELECT DOM_DOC_TITLE(DOM_LOAD('$jobsUrl'))", "Job Search Results — Senior Frontend Engineer")
        assertValue(
            "SELECT DOM_VALUE(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), '#option1'))",
            "1"
        )
        val data = queryValue("SELECT DOM_DATA(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), '#attrTest'))")
        assertTrue(data != null && "custom-value" in data)
        assertValue(
            "SELECT DOM_STYLE(DOM_SELECT_FIRST(DOM_LOAD('$errorPageUrl'), '#hiddenDiv'), 'display')",
            "none"
        )
    }

    @Test
    @DisplayName("DOM_DOCUMENT_VARIABLES")
    fun testDocumentVariables() {
        // DOM_DOCUMENT_VARIABLES retrieves the Pulsar meta-information element injected by the parser.
        // The element may or may not be present; we verify the call completes without error.
        val result = queryValue("SELECT DOM_TAG_NAME(DOM_DOCUMENT_VARIABLES(DOM_LOAD('$seoUrl')))")
        assertTrue(result != null, "DOM_DOCUMENT_VARIABLES should return a result")
    }

    @Test
    @DisplayName("DOM computed features")
    fun testComputedFeatures() {
        val seoRoot = "DOM_LOAD('$seoUrl')"
        assertTrue(queryValue("SELECT DOM_FEATURE($seoRoot, 'CH')")!!.toDouble() >= 0)
        assertTrue(queryValue("SELECT DOM_CH($seoRoot)")!!.toDouble() >= 0)
        assertTrue(queryValue("SELECT DOM_TN($seoRoot)")!!.toDouble() >= 0)
        assertValue("SELECT DOM_IMG($seoRoot)", "4.0")
        assertTrue(queryValue("SELECT DOM_A($seoRoot)")!!.toDouble() >= 1)
        assertValue("SELECT DOM_SIB(DOM_LOAD('$jobsUrl'))", "0.0")
        assertValue("SELECT DOM_C(DOM_LOAD('$jobsUrl'))", "1.0")
        assertTrue(queryValue("SELECT DOM_DEP(DOM_LOAD('$jobsUrl'))")!!.toDouble() >= 0)
        assertTrue(queryValue("SELECT DOM_SEQ(DOM_LOAD('$jobsUrl'))")!!.toDouble() >= 0)
        assertTrue(queryValue("SELECT DOM_TOP($seoRoot)")!!.toDouble() >= 0)
        assertTrue(queryValue("SELECT DOM_LEFT($seoRoot)")!!.toDouble() >= 0)
        assertTrue(queryValue("SELECT DOM_WIDTH($seoRoot)")!!.toDouble() >= 1)
        assertTrue(queryValue("SELECT DOM_HEIGHT($seoRoot)")!!.toDouble() >= 1)
        assertTrue(queryValue("SELECT DOM_AREA($seoRoot)")!!.toDouble() >= 1)
        assertTrue(queryValue("SELECT DOM_ASPECT_RATIO($seoRoot)")!!.toDouble() >= 0)
    }

    @Test
    @DisplayName("DOM_LABELS and DOM_ABS_URL")
    fun testLabelsAndAbsUrl() {
        // DOM_LABELS returns the A_LABELS classification attribute assigned by the DOM engine
        val labels = queryValue("SELECT DOM_LABELS(DOM_SELECT_FIRST(DOM_LOAD('$jobsUrl'), '.job-card-container'))")
        assertTrue(labels != null, "DOM_LABELS should return a non-null string")

        // DOM_ABS_URL resolves a relative attribute value (e.g. 'src') to an absolute URL
        val absSrc = queryValue("SELECT DOM_ABS_URL(DOM_SELECT_FIRST(DOM_LOAD('$seoUrl'), 'img'), 'src')")
        assertTrue(absSrc != null && "/images/onpage-seo.png" in absSrc!!,
            "DOM_ABS_URL should resolve relative src, got: $absSrc")
    }
}
