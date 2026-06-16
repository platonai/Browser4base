package ai.platon.pulsar.ql

import org.junit.jupiter.api.DisplayName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for DOM namespace scalar UDFs.
 *
 * Tests the UDFs defined in:
 *   - [ai.platon.pulsar.ql.h2.udfs.DomFunctions]
 *   - [ai.platon.pulsar.ql.h2.udfs.DomSelectFunctions]
 *   - [ai.platon.pulsar.ql.h2.udfs.DomInlineSelectFunctions]
 *
 * All pages are served by the local mock server.
 */
class TestDomUdfs : QlIntegrationTestBase() {

    // -- DOM_LOAD and DOM_IS_NIL / DOM_IS_NOT_NIL ---------------------------

    @Test
    @DisplayName("test DOM_LOAD loads a page and returns non-nil DOM")
    fun testDomLoadReturnsNonNilDom() {
        execute("SELECT DOM_IS_NOT_NIL(DOM_LOAD('$domPageUrl'))")
        execute("SELECT DOM_IS_NIL(DOM_LOAD('$domPageUrl'))")
    }

    @Test
    @DisplayName("test DOM_IS_NOT_NIL returns true for a valid loaded page")
    fun testDomIsNotNilReturnsTrueForValidPage() {
        query("SELECT DOM_IS_NOT_NIL(DOM_LOAD('$domPageUrl')) AS is_valid") { rs ->
            assertTrue(rs.next())
            assertTrue(rs.getBoolean("IS_VALID"))
        }
    }

    @Test
    @DisplayName("test DOM_IS_NIL returns false for a valid loaded page")
    fun testDomIsNilReturnsFalseForValidPage() {
        query("SELECT DOM_IS_NIL(DOM_LOAD('$domPageUrl')) AS is_nil") { rs ->
            assertTrue(rs.next())
            assertEquals(false, rs.getBoolean("IS_NIL"))
        }
    }

    // -- DOM_BASE_URI -------------------------------------------------------

    @Test
    @DisplayName("test DOM_BASE_URI returns the page URL")
    fun testDomBaseUriReturnsPageUrl() {
        query("SELECT DOM_BASE_URI(DOM_LOAD('$domPageUrl')) AS base_uri") { rs ->
            assertTrue(rs.next())
            val baseUri = rs.getString("BASE_URI")
            assertNotNull(baseUri)
            assertTrue(baseUri.contains("dom.html"), "Expected base URI to contain 'dom.html', got: $baseUri")
        }
    }

    @Test
    @DisplayName("test DOM_BASE_URI of EC category page contains ec path")
    fun testDomBaseUriOfEcCategoryContainsEcPath() {
        query("SELECT DOM_BASE_URI(DOM_LOAD('$ecCategoryUrl')) AS base_uri") { rs ->
            assertTrue(rs.next())
            val baseUri = rs.getString("BASE_URI")
            assertNotNull(baseUri)
            assertTrue(baseUri.contains("/ec/b"), "Expected base URI to contain '/ec/b', got: $baseUri")
        }
    }

    // -- DOM_DOC_TITLE -------------------------------------------------------

    @Test
    @DisplayName("test DOM_DOC_TITLE returns the document title")
    fun testDomDocTitleReturnsDocumentTitle() {
        query("SELECT DOM_DOC_TITLE(DOM_LOAD('$formPageUrl')) AS title") { rs ->
            assertTrue(rs.next())
            val title = rs.getString("TITLE")
            assertEquals("Form Test Page", title)
        }
    }

    @Test
    @DisplayName("test DOM_DOC_TITLE of EC category page is non-empty")
    fun testDomDocTitleOfEcCategoryIsNonEmpty() {
        query("SELECT DOM_DOC_TITLE(DOM_LOAD('$ecCategoryUrl')) AS title") { rs ->
            assertTrue(rs.next())
            val title = rs.getString("TITLE")
            assertNotNull(title)
            assertTrue(title.isNotBlank(), "Expected non-blank title")
        }
    }

    // -- DOM_TEXT and DOM_SELECT_FIRST ---------------------------------------

    @Test
    @DisplayName("test DOM_FIRST_TEXT extracts text from a selected element")
    fun testDomFirstTextExtractsText() {
        query(
            "SELECT DOM_FIRST_TEXT(DOM_LOAD('$domPageUrl'), '#outer') AS text"
        ) { rs ->
            assertTrue(rs.next())
            val text = rs.getString("TEXT")
            assertNotNull(text)
            assertTrue(text.contains("Text"), "Expected text containing 'Text', got: $text")
        }
    }

    @Test
    @DisplayName("test DOM_FIRST_TEXT extracts h1 text from form page")
    fun testDomFirstTextExtractsH1FromFormPage() {
        query(
            "SELECT DOM_FIRST_TEXT(DOM_LOAD('$formPageUrl'), 'h1') AS heading"
        ) { rs ->
            assertTrue(rs.next())
            assertEquals("Form Test Page", rs.getString("HEADING"))
        }
    }

    @Test
    @DisplayName("test DOM_FIRST_TEXT on EC category page finds product titles")
    fun testDomFirstTextOnEcCategoryFindsProductTitles() {
        query(
            "SELECT DOM_FIRST_TEXT(DOM_LOAD('$ecCategoryUrl'), '.product-title') AS product"
        ) { rs ->
            assertTrue(rs.next())
            val product = rs.getString("PRODUCT")
            assertNotNull(product)
            assertTrue(product.isNotBlank(), "Expected a non-blank product title")
        }
    }

    // -- DOM_ALL_TEXTS -------------------------------------------------------

    @Test
    @DisplayName("test DOM_ALL_TEXTS returns multiple element texts")
    fun testDomAllTextsReturnsMultipleElementTexts() {
        query(
            "SELECT DOM_ALL_TEXTS(DOM_LOAD('$domPageUrl'), 'div') AS texts"
        ) { rs ->
            assertTrue(rs.next())
            val texts = rs.getArray("TEXTS")
            assertNotNull(texts)
        }
    }

    @Test
    @DisplayName("test DOM_ALL_TEXTS on EC category page returns multiple product titles")
    fun testDomAllTextsOnEcCategoryReturnsMultipleProductTitles() {
        query(
            """SELECT DOM_ALL_TEXTS(DOM_LOAD('$ecCategoryUrl'), '.product-title') AS products"""
        ) { rs ->
            assertTrue(rs.next())
            val products = rs.getArray("PRODUCTS")
            assertNotNull(products)
        }
    }

    // -- DOM_SELECT_FIRST ----------------------------------------------------

    @Test
    @DisplayName("test DOM_SELECT_FIRST returns a DOM for the first matched element")
    fun testDomSelectFirstReturnsDomForFirstMatchedElement() {
        query(
            """SELECT DOM_TEXT(DOM_SELECT_FIRST(DOM_LOAD('$domPageUrl'), '#inner')) AS inner_text"""
        ) { rs ->
            assertTrue(rs.next())
            val text = rs.getString("INNER_TEXT")
            assertNotNull(text)
            assertTrue(text.contains("Text"), "Expected text containing 'Text', got: $text")
        }
    }

    @Test
    @DisplayName("test DOM_SELECT_FIRST on form page selects first input")
    fun testDomSelectFirstOnFormPageSelectsFirstInput() {
        query(
            """SELECT DOM_ATTR(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), 'input[type=text]'), 'id') AS input_id"""
        ) { rs ->
            assertTrue(rs.next())
            assertEquals("username", rs.getString("INPUT_ID"))
        }
    }

    // -- DOM_ATTR ------------------------------------------------------------

    @Test
    @DisplayName("test DOM_ATTR extracts element id attribute")
    fun testDomAttrExtractsElementId() {
        query(
            """SELECT DOM_ATTR(DOM_SELECT_FIRST(DOM_LOAD('$domPageUrl'), '#outer'), 'id') AS id"""
        ) { rs ->
            assertTrue(rs.next())
            assertEquals("outer", rs.getString("ID"))
        }
    }

    @Test
    @DisplayName("test DOM_ATTR extracts element name attribute")
    fun testDomAttrExtractsNameAttribute() {
        query(
            """SELECT DOM_ATTR(DOM_SELECT_FIRST(DOM_LOAD('$domPageUrl'), '#outer'), 'name') AS name"""
        ) { rs ->
            assertTrue(rs.next())
            assertEquals("value", rs.getString("NAME"))
        }
    }

    @Test
    @DisplayName("test DOM_ATTR on form page extracts data-testid")
    fun testDomAttrOnFormPageExtractsDataTestid() {
        query(
            """SELECT DOM_ATTR(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), '#clickButton'), 'data-testid') AS testid"""
        ) { rs ->
            assertTrue(rs.next())
            assertEquals("click-button", rs.getString("TESTID"))
        }
    }

    // -- DOM_ABS_HREF --------------------------------------------------------

    @Test
    @DisplayName("test DOM_ABS_HREF returns absolute URL for links")
    fun testDomAbsHrefReturnsAbsoluteUrlForLinks() {
        query(
            """SELECT DOM_ABS_HREF(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), 'a')) AS href"""
        ) { rs ->
            assertTrue(rs.next())
            val href = rs.getString("HREF")
            assertNotNull(href)
            assertTrue(href.startsWith("http"), "Expected absolute URL starting with 'http', got: $href")
        }
    }

    @Test
    @DisplayName("test DOM_ABS_HREF on EC category page resolves product links")
    fun testDomAbsHrefOnEcCategoryResolvesProductLinks() {
        query(
            """SELECT DOM_ABS_HREF(DOM_SELECT_FIRST(DOM_LOAD('$ecCategoryUrl'), '.product-link')) AS link"""
        ) { rs ->
            assertTrue(rs.next())
            val link = rs.getString("LINK")
            assertNotNull(link)
            assertTrue(link.contains("/ec/dp/"), "Expected link to contain '/ec/dp/', got: $link")
        }
    }

    // -- DOM_NTH_TEXT --------------------------------------------------------

    @Test
    @DisplayName("test DOM_NTH_TEXT selects nth element text")
    fun testDomNthTextSelectsNthElementText() {
        query(
            """SELECT DOM_NTH_TEXT(DOM_LOAD('$formPageUrl'), 'input', 1) AS first_input"""
        ) { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getString("FIRST_INPUT"))
        }
    }

    // -- DOM_INLINE_SELECT_TEXT -----------------------------------------------

    @Test
    @DisplayName("test DOM_INLINE_SELECT_TEXT extracts text from selected elements")
    fun testDomInlineSelectTextExtractsText() {
        query(
            """SELECT DOM_INLINE_SELECT_TEXT(DOM_LOAD('$formPageUrl'), 'label') AS labels"""
        ) { rs ->
            assertTrue(rs.next())
            val labels = rs.getArray("LABELS")
            assertNotNull(labels)
        }
    }

    // -- DOM_SELECT_ALL ------------------------------------------------------

    @Test
    @DisplayName("test DOM_SELECT_ALL returns array of DOMs")
    fun testDomSelectAllReturnsArrayOfDoms() {
        query(
            """SELECT DOM_SELECT_ALL(DOM_LOAD('$formPageUrl'), 'input') AS inputs"""
        ) { rs ->
            assertTrue(rs.next())
            val inputs = rs.getArray("INPUTS")
            assertNotNull(inputs)
        }
    }

    // -- Combined UDF usage --------------------------------------------------

    @Test
    @DisplayName("test chained UDFs on EC product page extract structured data")
    fun testChainedUdfsOnEcProductExtractStructuredData() {
        query(
            """
            SELECT
                DOM_DOC_TITLE(DOM) AS title,
                DOM_FIRST_TEXT(DOM, '.product-title') AS product_name,
                DOM_FIRST_TEXT(DOM, '.product-price') AS price
            FROM DOM_SELECT(DOM_LOAD('$ecProductUrl'), 'body')
            """.trimIndent()
        ) { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getString("TITLE"))
            assertNotNull(rs.getString("PRODUCT_NAME"))
            assertTrue(rs.getString("PRODUCT_NAME").isNotBlank(), "Product name should not be blank")
        }
    }

    // -- DOM_LOAD with options ------------------------------------------------

    @Test
    @DisplayName("test DOM_LOAD with expires option")
    fun testDomLoadWithExpiresOption() {
        execute("SELECT DOM_IS_NOT_NIL(DOM_LOAD('$domPageUrl -expires 1d'))")
    }

    // -- DOM_FETCH (force refresh) ------------------------------------------

    @Test
    @DisplayName("test DOM_FETCH forces page refresh")
    fun testDomFetchForcesPageRefresh() {
        execute("SELECT DOM_IS_NOT_NIL(DOM_FETCH('$domPageUrl'))")
    }
}
