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

    // =========================================================================
    // Additional DomFunctions tests
    // =========================================================================

    // -- DOM_TAG_NAME --------------------------------------------------------

    @Test
    @DisplayName("test DOM_TAG_NAME returns element tag name")
    fun testDomTagNameReturnsElementTagName() {
        query("""SELECT DOM_TAG_NAME(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), 'h1')) AS tag""") { rs ->
            assertTrue(rs.next())
            assertEquals("h1", rs.getString("TAG").lowercase())
        }
    }

    @Test
    @DisplayName("test DOM_TAG_NAME for div element")
    fun testDomTagNameForDivElement() {
        query("""SELECT DOM_TAG_NAME(DOM_SELECT_FIRST(DOM_LOAD('$domPageUrl'), '#outer')) AS tag""") { rs ->
            assertTrue(rs.next())
            assertEquals("div", rs.getString("TAG").lowercase())
        }
    }

    // -- DOM_HREF / DOM_ABS_HREF ---------------------------------------------

    @Test
    @DisplayName("test DOM_HREF returns raw href attribute")
    fun testDomHrefReturnsRawHrefAttribute() {
        query("""SELECT DOM_HREF(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), 'a')) AS href""") { rs ->
            assertTrue(rs.next())
            val href = rs.getString("HREF")
            assertNotNull(href)
            assertTrue(href.isNotBlank(), "Expected non-blank href")
        }
    }

    // -- DOM_SRC / DOM_ABS_SRC -----------------------------------------------

    @Test
    @DisplayName("test DOM_SRC returns src attribute from img")
    fun testDomSrcReturnsSrcAttributeFromImg() {
        query("""SELECT DOM_SRC(DOM_SELECT_FIRST(DOM_LOAD('$ecCategoryUrl'), 'img')) AS src""") { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getString("SRC"))
        }
    }

    // -- DOM_TEXT / DOM_TEXT_LENGTH / DOM_OWN_TEXT / DOM_WHOLE_TEXT ----------

    @Test
    @DisplayName("test DOM_TEXT returns element text content")
    fun testDomTextReturnsElementTextContent() {
        query("""SELECT DOM_TEXT(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), 'h1')) AS text""") { rs ->
            assertTrue(rs.next())
            assertEquals("Form Test Page", rs.getString("TEXT"))
        }
    }

    @Test
    @DisplayName("test DOM_TEXT with truncate limits text length")
    fun testDomTextWithTruncateLimitsTextLength() {
        query("""SELECT DOM_TEXT(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), 'form'), 10) AS short_text""") { rs ->
            assertTrue(rs.next())
            val text = rs.getString("SHORT_TEXT")
            assertNotNull(text)
            assertTrue(text!!.length <= 10, "Expected truncated text with length <= 10, got: ${text.length}")
        }
    }

    @Test
    @DisplayName("test DOM_TEXT_LENGTH returns text length")
    fun testDomTextLengthReturnsTextLength() {
        query("""SELECT DOM_TEXT_LENGTH(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), 'h1')) AS len""") { rs ->
            assertTrue(rs.next())
            assertTrue(rs.getInt("LEN") > 0, "Expected positive text length")
        }
    }

    // -- DOM_HAS_TEXT --------------------------------------------------------

    @Test
    @DisplayName("test DOM_HAS_TEXT returns true for element with text")
    fun testDomHasTextReturnsTrueForElementWithText() {
        query("""SELECT DOM_HAS_TEXT(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), 'h1')) AS has""") { rs ->
            assertTrue(rs.next())
            assertTrue(rs.getBoolean("HAS"))
        }
    }

    // -- DOM_TITLE -----------------------------------------------------------

    @Test
    @DisplayName("test DOM_TITLE returns title attribute")
    fun testDomTitleReturnsTitleAttribute() {
        query("""SELECT DOM_TITLE(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), '#attrTest')) AS title""") { rs ->
            assertTrue(rs.next())
            assertEquals("Test Title", rs.getString("TITLE"))
        }
    }

    // -- DOM_ID / DOM_CLASS_NAME / DOM_CLASS_NAMES / DOM_HAS_CLASS ----------

    @Test
    @DisplayName("test DOM_ID returns element id")
    fun testDomIdReturnsElementId() {
        query("""SELECT DOM_ID(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), '#attrTest')) AS id""") { rs ->
            assertTrue(rs.next())
            assertEquals("attrTest", rs.getString("ID"))
        }
    }

    @Test
    @DisplayName("test DOM_CLASS_NAME returns element class")
    fun testDomClassNameReturnsElementClass() {
        query("""SELECT DOM_CLASS_NAME(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), '#attrTest')) AS cls""") { rs ->
            assertTrue(rs.next())
            val cls = rs.getString("CLS")
            assertNotNull(cls)
            assertTrue(cls.contains("test-class"), "Expected class containing 'test-class', got: $cls")
        }
    }

    @Test
    @DisplayName("test DOM_HAS_CLASS returns true for existing class")
    fun testDomHasClassReturnsTrueForExistingClass() {
        query("""SELECT DOM_HAS_CLASS(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), '#attrTest'), 'test-class') AS has""") { rs ->
            assertTrue(rs.next())
            assertTrue(rs.getBoolean("HAS"))
        }
    }

    @Test
    @DisplayName("test DOM_HAS_CLASS returns false for non-existing class")
    fun testDomHasClassReturnsFalseForNonExistingClass() {
        query("""SELECT DOM_HAS_CLASS(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), '#attrTest'), 'non-existent') AS has""") { rs ->
            assertTrue(rs.next())
            assertEquals(false, rs.getBoolean("HAS"))
        }
    }

    // -- DOM_HAS_ATTR --------------------------------------------------------

    @Test
    @DisplayName("test DOM_HAS_ATTR returns true for existing attribute")
    fun testDomHasAttrReturnsTrueForExistingAttribute() {
        query("""SELECT DOM_HAS_ATTR(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), '#attrTest'), 'data-custom') AS has""") { rs ->
            assertTrue(rs.next())
            assertTrue(rs.getBoolean("HAS"))
        }
    }

    @Test
    @DisplayName("test DOM_HAS_ATTR returns false for non-existing attribute")
    fun testDomHasAttrReturnsFalseForNonExistingAttribute() {
        query("""SELECT DOM_HAS_ATTR(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), '#attrTest'), 'data-nonexistent') AS has""") { rs ->
            assertTrue(rs.next())
            assertEquals(false, rs.getBoolean("HAS"))
        }
    }

    // -- DOM_DEPTH / DOM_SEQUENCE -------------------------------------------

    @Test
    @DisplayName("test DOM_DEPTH returns element depth")
    fun testDomDepthReturnsElementDepth() {
        query("""SELECT DOM_DEPTH(DOM_SELECT_FIRST(DOM_LOAD('$domPageUrl'), '#inner')) AS depth""") { rs ->
            assertTrue(rs.next())
            val depth = rs.getInt("DEPTH")
            assertTrue(depth > 0, "Expected positive depth, got: $depth")
        }
    }

    @Test
    @DisplayName("test DOM_SEQUENCE returns element sequence number")
    fun testDomSequenceReturnsElementSequenceNumber() {
        query("""SELECT DOM_SEQUENCE(DOM_SELECT_FIRST(DOM_LOAD('$domPageUrl'), '#outer')) AS seq""") { rs ->
            assertTrue(rs.next())
            assertTrue(rs.getInt("SEQ") >= 0, "Expected non-negative sequence number")
        }
    }

    // -- DOM_CSS_SELECTOR / DOM_CSS_PATH ------------------------------------

    @Test
    @DisplayName("test DOM_CSS_SELECTOR returns unique CSS path")
    fun testDomCssSelectorReturnsUniqueCssPath() {
        query("""SELECT DOM_CSS_SELECTOR(DOM_SELECT_FIRST(DOM_LOAD('$domPageUrl'), '#inner')) AS css""") { rs ->
            assertTrue(rs.next())
            val css = rs.getString("CSS")
            assertNotNull(css)
            assertTrue(css.contains("#"), "Expected CSS selector with '#', got: $css")
        }
    }

    // -- DOM_HTML / DOM_OUTER_HTML / DOM_SLIM_HTML / DOM_MINIMAL_HTML -------

    @Test
    @DisplayName("test DOM_HTML returns inner HTML")
    fun testDomHtmlReturnsInnerHtml() {
        query("""SELECT DOM_HTML(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), '#attrTest')) AS html""") { rs ->
            assertTrue(rs.next())
            val html = rs.getString("HTML")
            assertNotNull(html)
            assertTrue(html.contains("Attributes Test"), "Expected HTML containing 'Attributes Test', got: $html")
        }
    }

    @Test
    @DisplayName("test DOM_OUTER_HTML returns outer HTML including element text")
    fun testDomOuterHtmlReturnsOuterHtmlIncludingElementText() {
        query("""SELECT DOM_OUTER_HTML(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), '#attrTest')) AS html""") { rs ->
            assertTrue(rs.next())
            val html = rs.getString("HTML")
            assertNotNull(html)
            // slimCopy() strips some attributes; check for text content instead
            assertTrue(html.contains("Attributes Test"),
                "Expected outer HTML containing 'Attributes Test', got: $html")
        }
    }

    // -- DOM_VALUE ----------------------------------------------------------

    @Test
    @DisplayName("test DOM_VALUE returns form element value")
    fun testDomValueReturnsFormElementValue() {
        query(
            """SELECT DOM_VALUE(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), '#username')) AS val"""
        ) { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getString("VAL"))
        }
    }

    // -- DOM_UNIQUE_NAME ----------------------------------------------------

    @Test
    @DisplayName("test DOM_UNIQUE_NAME returns unique element identifier")
    fun testDomUniqueNameReturnsUniqueElementIdentifier() {
        query("""SELECT DOM_UNIQUE_NAME(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), 'h1')) AS name""") { rs ->
            assertTrue(rs.next())
            val name = rs.getString("NAME")
            assertNotNull(name)
            assertTrue(name.isNotBlank(), "Expected non-blank unique name")
        }
    }

    // -- DOM_LINKS ----------------------------------------------------------

    @Test
    @DisplayName("test DOM_LINKS returns anchor elements as array")
    fun testDomLinksReturnsAnchorElementsAsArray() {
        query("""SELECT DOM_LINKS(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), 'body')) AS links""") { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getArray("LINKS"))
        }
    }

    // -- DOM_SIBLING_SIZE / DOM_SIBLING_INDEX -------------------------------

    @Test
    @DisplayName("test DOM_SIBLING_SIZE returns number of sibling nodes")
    fun testDomSiblingSizeReturnsNumberOfSiblingNodes() {
        query("""SELECT DOM_SIBLING_SIZE(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), '#clickButton')) AS size""") { rs ->
            assertTrue(rs.next())
            assertTrue(rs.getInt("SIZE") > 0, "Expected positive sibling size")
        }
    }

    // -- DOM_CHILD_NODE_SIZE / DOM_CHILD_ELEMENT_SIZE -----------------------

    @Test
    @DisplayName("test DOM_CHILD_NODE_SIZE returns count of child nodes")
    fun testDomChildNodeSizeReturnsCountOfChildNodes() {
        query("""SELECT DOM_CHILD_NODE_SIZE(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), '#testForm')) AS size""") { rs ->
            assertTrue(rs.next())
            assertTrue(rs.getInt("SIZE") > 0, "Expected positive child node size")
        }
    }

    @Test
    @DisplayName("test DOM_CHILD_ELEMENT_SIZE returns count of child elements")
    fun testDomChildElementSizeReturnsCountOfChildElements() {
        query("""SELECT DOM_CHILD_ELEMENT_SIZE(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), '#testForm')) AS size""") { rs ->
            assertTrue(rs.next())
            assertTrue(rs.getInt("SIZE") > 0, "Expected positive child element size")
        }
    }

    // -- DOM_LABELS ---------------------------------------------------------

    @Test
    @DisplayName("test DOM_LABELS returns node classification labels")
    fun testDomLabelsReturnsNodeClassificationLabels() {
        query("""SELECT DOM_LABELS(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), 'h1')) AS labels""") { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getString("LABELS"))
        }
    }

    // -- DOM_ABS_URL --------------------------------------------------------

    @Test
    @DisplayName("test DOM_ABS_URL resolves relative URL attribute")
    fun testDomAbsUrlResolvesRelativeUrlAttribute() {
        query("""SELECT DOM_ABS_URL(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), 'a'), 'href') AS url""") { rs ->
            assertTrue(rs.next())
            val url = rs.getString("URL")
            assertNotNull(url)
            assertTrue(url.startsWith("http"), "Expected absolute URL starting with 'http', got: $url")
        }
    }

    // -- DOM_PARENT / DOM_PARENT_NAME ---------------------------------------

    @Test
    @DisplayName("test DOM_PARENT returns parent element")
    fun testDomParentReturnsParentElement() {
        query("""SELECT DOM_TAG_NAME(DOM_PARENT(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), '#clickButton'))) AS parent_tag""") { rs ->
            assertTrue(rs.next())
            val tag = rs.getString("PARENT_TAG")
            assertNotNull(tag)
            assertTrue(tag.isNotBlank(), "Expected non-blank parent tag")
        }
    }

    @Test
    @DisplayName("test DOM_PARENT_NAME returns parent unique name")
    fun testDomParentNameReturnsParentUniqueName() {
        query("""SELECT DOM_PARENT_NAME(DOM_SELECT_FIRST(DOM_LOAD('$domPageUrl'), '#inner')) AS name""") { rs ->
            assertTrue(rs.next())
            val name = rs.getString("NAME")
            assertNotNull(name)
            assertTrue(name.isNotBlank(), "Expected non-blank parent name")
        }
    }

    // -- DOM_OWNER_DOCUMENT ------------------------------------------------

    @Test
    @DisplayName("test DOM_OWNER_DOCUMENT returns owner document")
    fun testDomOwnerDocumentReturnsOwnerDocument() {
        query("""SELECT DOM_DOC_TITLE(DOM_OWNER_DOCUMENT(DOM_SELECT_FIRST(DOM_LOAD('$formPageUrl'), '#clickButton'))) AS title""") { rs ->
            assertTrue(rs.next())
            assertEquals("Form Test Page", rs.getString("TITLE"))
        }
    }

    // -- DOM_DOM (identity function) ----------------------------------------

    @Test
    @DisplayName("test DOM_DOM returns itself (identity)")
    fun testDomDomReturnsItself() {
        query("""SELECT DOM_IS_NOT_NIL(DOM_DOM(DOM_LOAD('$domPageUrl'))) AS is_valid""") { rs ->
            assertTrue(rs.next())
            assertTrue(rs.getBoolean("IS_VALID"))
        }
    }

    // -- DOM_ALL_HREFS (DomSelectFunctions) -----------------------------------

    @Test
    @DisplayName("test DOM_ALL_HREFS returns array of absolute hrefs")
    fun testDomAllHrefsReturnsArrayOfAbsoluteHrefs() {
        query(
            """SELECT DOM_ALL_HREFS(DOM_LOAD('$formPageUrl'), 'a') AS hrefs"""
        ) { rs ->
            assertTrue(rs.next())
            val hrefs = rs.getArray("HREFS")
            assertNotNull(hrefs)
        }
    }

    // -- DOM_FIRST_HREF (DomSelectFunctions) ----------------------------------

    @Test
    @DisplayName("test DOM_FIRST_HREF returns first absolute href")
    fun testDomFirstHrefReturnsFirstAbsoluteHref() {
        query(
            """SELECT DOM_FIRST_HREF(DOM_LOAD('$formPageUrl'), 'a') AS href"""
        ) { rs ->
            assertTrue(rs.next())
            val href = rs.getString("HREF")
            assertNotNull(href)
            assertTrue(href.startsWith("http"), "Expected absolute URL, got: $href")
        }
    }

    // -- DOM_FIRST_IMG / DOM_ALL_IMGS -----------------------------------------

    @Test
    @DisplayName("test DOM_FIRST_IMG returns first image src")
    fun testDomFirstImgReturnsFirstImageSrc() {
        query(
            """SELECT DOM_FIRST_IMG(DOM_LOAD('$ecCategoryUrl'), '.product-card') AS img"""
        ) { rs ->
            assertTrue(rs.next())
            val img = rs.getString("IMG")
            assertNotNull(img)
            assertTrue(img.isNotBlank(), "Expected non-blank image URL")
        }
    }

    @Test
    @DisplayName("test DOM_ALL_IMGS returns array of image URLs")
    fun testDomAllImgsReturnsArrayOfImageUrls() {
        query(
            """SELECT DOM_ALL_IMGS(DOM_LOAD('$ecCategoryUrl'), '.product-card') AS imgs"""
        ) { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getArray("IMGS"))
        }
    }

    // -- DOM_FIRST_ATTR / DOM_ALL_ATTRS ---------------------------------------

    @Test
    @DisplayName("test DOM_FIRST_ATTR returns attribute of first matched element")
    fun testDomFirstAttrReturnsAttributeOfFirstMatchedElement() {
        query(
            """SELECT DOM_FIRST_ATTR(DOM_LOAD('$formPageUrl'), 'button', 'data-testid') AS testid"""
        ) { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getString("TESTID"))
        }
    }

    @Test
    @DisplayName("test DOM_ALL_ATTRS returns array of attributes")
    fun testDomAllAttrsReturnsArrayOfAttributes() {
        query(
            """SELECT DOM_ALL_ATTRS(DOM_LOAD('$formPageUrl'), 'label', 'for') AS attrs"""
        ) { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getArray("ATTRS"))
        }
    }

    // -- DOM_FIRST_OWN_TEXT / DOM_ALL_OWN_TEXTS -------------------------------

    @Test
    @DisplayName("test DOM_FIRST_OWN_TEXT returns own text of first element")
    fun testDomFirstOwnTextReturnsOwnTextOfFirstElement() {
        query(
            """SELECT DOM_FIRST_OWN_TEXT(DOM_LOAD('$formPageUrl'), '#attrTest') AS text"""
        ) { rs ->
            assertTrue(rs.next())
            val text = rs.getString("TEXT")
            assertNotNull(text)
            assertTrue(text.contains("Attributes Test"), "Expected 'Attributes Test', got: $text")
        }
    }

    // -- DOM_SELECT_NTH / DOM_NTH_TEXT ---------------------------------------

    @Test
    @DisplayName("test DOM_SELECT_NTH returns nth matched element")
    fun testDomSelectNthReturnsNthMatchedElement() {
        query(
            """SELECT DOM_ATTR(DOM_SELECT_NTH(DOM_LOAD('$formPageUrl'), 'input', 1), 'id') AS id"""
        ) { rs ->
            assertTrue(rs.next())
            assertEquals("username", rs.getString("ID"))
        }
    }

    @Test
    @DisplayName("test DOM_SELECT_NTH second input returns email input")
    fun testDomSelectNthSecondInputReturnsEmailInput() {
        query(
            """SELECT DOM_ATTR(DOM_SELECT_NTH(DOM_LOAD('$formPageUrl'), 'input[type=text], input[type=email]', 2), 'id') AS id"""
        ) { rs ->
            assertTrue(rs.next())
            assertEquals("email", rs.getString("ID"))
        }
    }

    // -- DOM_ALL_INTEGERS / DOM_FIRST_INTEGER / DOM_NTH_INTEGER ---------------

    @Test
    @DisplayName("test DOM_FIRST_INTEGER extracts first int from element text")
    fun testDomFirstIntegerExtractsFirstIntFromElementText() {
        query(
            """
            SELECT DOM_FIRST_INTEGER(DOM_LOAD('$ecCategoryUrl'), '.product-price', 0) AS price
            """.trimIndent()
        ) { rs ->
            assertTrue(rs.next())
            assertTrue(rs.getInt("PRICE") > 0, "Expected positive price integer")
        }
    }

    // -- DOM_RE1 / DOM_RE2 (regex on element text) ----------------------------

    @Test
    @DisplayName("test DOM_FIRST_RE1 extracts regex group from first element")
    fun testDomFirstRe1ExtractsRegexGroupFromFirstElement() {
        query(
            """SELECT DOM_FIRST_RE1(DOM_LOAD('$domPageUrl'), 'div', '(\\w+)') AS match"""
        ) { rs ->
            assertTrue(rs.next())
            val match = rs.getString("MATCH")
            assertNotNull(match)
        }
    }

    @Test
    @DisplayName("test DOM_ALL_RE1 extracts regex from all matching elements")
    fun testDomAllRe1ExtractsRegexFromAllMatchingElements() {
        query(
            """SELECT DOM_ALL_RE1(DOM_LOAD('$formPageUrl'), 'label', '(\\w+)') AS matches"""
        ) { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getArray("MATCHES"))
        }
    }

    // -- DOM_FIRST_RE2 extracts key-value pair via regex --------------------

    @Test
    @DisplayName("test DOM_FIRST_RE2 extracts key-value pair")
    fun testDomFirstRe2ExtractsKeyValuePair() {
        query(
            """SELECT DOM_FIRST_RE2(DOM_LOAD('$domPageUrl'), 'div', '(\\w+)') AS pair"""
        ) { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getArray("PAIR"))
        }
    }

    // -- Multiple DomSelectFunctions tested via LOAD_AND_SELECT chaining -----

    @Test
    @DisplayName("test LOAD_AND_SELECT with multiple DomSelectFunctions chained")
    fun testLoadAndSelectWithMultipleDomSelectFunctionsChained() {
        query(
            """
            SELECT
                DOM_FIRST_TEXT(DOM, '#breadcrumb li:first-child a') AS first_crumb,
                DOM_DOC_TITLE(DOM) AS page_title
            FROM LOAD_AND_SELECT('$ecCategoryUrl', 'body')
            """.trimIndent()
        ) { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getString("FIRST_CRUMB"))
            assertNotNull(rs.getString("PAGE_TITLE"))
        }
    }

    // -- DOM_LOAD with ecCategoryUrl and test DOM_VARIABLES -----------------

    @Test
    @DisplayName("test DOM_LOAD on EC category page and test feature-based UDFs")
    fun testFeatureBasedUdfsOnPage() {
        query(
            """
            SELECT
                DOM_CH(DOM_SELECT_FIRST(DOM_LOAD('$ecCategoryUrl'), '.product-card')) AS ch,
                DOM_TN(DOM_SELECT_FIRST(DOM_LOAD('$ecCategoryUrl'), '.product-card')) AS tn
            """.trimIndent()
        ) { rs ->
            assertTrue(rs.next())
            assertTrue(rs.getDouble("CH") >= 0, "Expected non-negative character count")
            assertTrue(rs.getDouble("TN") >= 0, "Expected non-negative text node count")
        }
    }
}
