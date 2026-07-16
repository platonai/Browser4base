package ai.platon.pulsar.ql

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for DOM namespace table-returning UDFs.
 *
 * Tests the UDFs defined in:
 *   - [ai.platon.pulsar.ql.h2.udfs.DomFunctionTables]
 *
 * These UDFs return ResultSets and are typically used with
 * `SELECT * FROM function_name(...)` syntax.
 *
 * All pages are served by the local mock server.
 */
class TestDomTableFunctions : QlIntegrationTestBase() {

    // -- LOAD_AND_SELECT ----------------------------------------------------

    @Test
    @DisplayName("test LOAD_AND_SELECT returns all elements matching css query")
    fun testLoadAndSelectReturnsAllMatchingElements() {
        execute(
            "SELECT * FROM LOAD_AND_SELECT('$ecCategoryUrl', '.product-card')"
        )
    }

    @Test
    @DisplayName("test LOAD_AND_SELECT with offset and limit")
    fun testLoadAndSelectWithOffsetAndLimit() {
        execute(
            "SELECT * FROM LOAD_AND_SELECT('$ecCategoryUrl', '.product-card', 1, 3)"
        )
    }

    @Test
    @DisplayName("test LOAD_AND_SELECT on dom page selects by id")
    fun testLoadAndSelectOnDomPageSelectsById() {
        query(
            "SELECT * FROM LOAD_AND_SELECT('$domPageUrl', '#outer')"
        ) { rs ->
            assertTrue(rs.next(), "Expected at least 1 row for #outer selector")
        }
    }

    @Test
    @DisplayName("test LOAD_AND_SELECT product cards have expected columns")
    fun testLoadAndSelectProductCardsHaveExpectedColumns() {
        query(
            "SELECT * FROM LOAD_AND_SELECT('$ecCategoryUrl', '.product-card')"
        ) { rs ->
            val metaData = rs.metaData
            val columnCount = metaData.columnCount
            assertTrue(columnCount > 0, "Expected at least 1 column, got $columnCount")

            val columns = (1..columnCount).map { metaData.getColumnName(it) }
            logger.info("LOAD_AND_SELECT columns: $columns")

            var rowCount = 0
            while (rs.next()) rowCount++
            assertTrue(rowCount > 0, "Expected at least 1 row")
            logger.info("LOAD_AND_SELECT returned $rowCount rows")
        }
    }

    @Test
    @DisplayName("test LOAD_AND_SELECT on form page selects labels")
    fun testLoadAndSelectOnFormPageSelectsLabels() {
        query(
            "SELECT * FROM LOAD_AND_SELECT('$formPageUrl', 'label')"
        ) { rs ->
            var count = 0
            while (rs.next()) count++
            assertTrue(count > 0, "Expected at least 1 label element")
        }
    }

    // -- LOAD_ALL_AND_SELECT --------------------------------------------------

    @Test
    @DisplayName("test LOAD_ALL_AND_SELECT loads multiple URLs and selects elements")
    fun testLoadAllAndSelectFromMultipleUrls() {
        execute(
            "SELECT * FROM LOAD_ALL_AND_SELECT(MAKE_ARRAY('$ecCategoryUrl', '$ecProductUrl2'), 'div', 1, 5)"
        )
    }

    @Test
    @DisplayName("test LOAD_ALL_AND_SELECT with offset and limit per page")
    fun testLoadAllAndSelectWithOffsetAndLimit() {
        query(
            "SELECT * FROM LOAD_ALL_AND_SELECT(MAKE_ARRAY('$ecCategoryUrl', '$ecProductUrl2'), 'div', 1, 3)"
        ) { rs ->
            var count = 0
            while (rs.next()) count++
            assertTrue(count > 0, "Expected at least 1 row from multiple URLs")
            // limit=3 applied per page, 2 pages → max 6 rows total
            assertTrue(count <= 6, "Expected at most 6 rows with limit=3 per page, got $count")
        }
    }

    @Test
    @DisplayName("test LOAD_ALL_AND_SELECT returns expected DOM column")
    fun testLoadAllAndSelectReturnsExpectedColumns() {
        query(
            "SELECT * FROM LOAD_ALL_AND_SELECT(MAKE_ARRAY('$ecCategoryUrl', '$ecProductUrl2'), 'div', 1, 3)"
        ) { rs ->
            val metaData = rs.metaData
            val columnCount = metaData.columnCount
            assertTrue(columnCount >= 1, "Expected at least 1 column, got $columnCount")

            val columns = (1..columnCount).map { metaData.getColumnName(it) }
            logger.info("LOAD_ALL_AND_SELECT columns: $columns")
            assertTrue(columns.contains("DOM"), "Expected 'DOM' column, got $columns")

            var rowCount = 0
            while (rs.next()) rowCount++
            assertTrue(rowCount > 0, "Expected at least 1 row")
            logger.info("LOAD_ALL_AND_SELECT returned $rowCount rows")
        }
    }

    @Test
    @DisplayName("test LOAD_ALL_AND_SELECT chained with DOM_TEXT extracts text across pages")
    fun testLoadAllAndSelectChainedWithDomText() {
        query(
            """
            SELECT DOM_TEXT(DOM) AS text
            FROM LOAD_ALL_AND_SELECT(MAKE_ARRAY('$ecCategoryUrl', '$ecProductUrl2'), 'a', 1, 3)
            """.trimIndent()
        ) { rs ->
            var count = 0
            while (rs.next()) {
                val text = rs.getString("TEXT")
                assertNotNull(text)
                count++
            }
            assertTrue(count > 0, "Expected at least 1 anchor text from multiple pages")
            logger.info("LOAD_ALL_AND_SELECT chained with DOM_TEXT returned $count rows")
        }
    }

    @Test
    @DisplayName("test LOAD_ALL_AND_SELECT with single URL array still works")
    fun testLoadAllAndSelectWithSingleUrlArray() {
        query(
            "SELECT * FROM LOAD_ALL_AND_SELECT(MAKE_ARRAY('$ecCategoryUrl'), '.product-card')"
        ) { rs ->
            assertTrue(rs.next(), "Expected at least 1 row for single URL in array")
        }
    }

    @Test
    @DisplayName("test LOAD_ALL_AND_SELECT chained with DOM_ATTR extracts hrefs")
    fun testLoadAllAndSelectChainedWithDomAttr() {
        query(
            """
            SELECT
                DOM_ATTR(DOM, 'href') AS href,
                DOM_TEXT(DOM) AS text
            FROM LOAD_ALL_AND_SELECT(MAKE_ARRAY('$ecCategoryUrl', '$ecProductUrl2'), 'a[href]', 1, 5)
            """.trimIndent()
        ) { rs ->
            var count = 0
            var hasHref = false
            while (rs.next()) {
                val href = rs.getString("HREF")
                if (href != null && href.isNotBlank()) hasHref = true
                count++
            }
            assertTrue(count > 0, "Expected at least 1 anchor from multiple pages")
            assertTrue(hasHref, "Expected at least 1 non-blank href")
        }
    }

    // -- DOM_SELECT (table function, takes a DOM value) ----------------------

    @Test
    @DisplayName("test DOM_SELECT table function selects elements from a loaded DOM")
    fun testDomSelectTableFunctionSelectsElementsFromDom() {
        execute(
            """
            SELECT * FROM DOM_SELECT(DOM_LOAD('$ecCategoryUrl'), '.product-card', 1, 5)
            """.trimIndent()
        )
    }

    @Test
    @DisplayName("test DOM_SELECT with DOM_LOAD chain extracts product info")
    fun testDomSelectWithDomLoadChainExtractsProductInfo() {
        query(
            """
            SELECT
                DOM_TEXT(DOM) AS text
            FROM DOM_SELECT(DOM_LOAD('$ecCategoryUrl'), '.product-title', 1, 3)
            """.trimIndent()
        ) { rs ->
            var count = 0
            while (rs.next()) {
                val text = rs.getString("TEXT")
                assertNotNull(text)
                assertTrue(text.isNotBlank(), "Product title should not be blank")
                count++
            }
            assertTrue(count > 0, "Expected at least 1 product title")
        }
    }

    @Test
    @DisplayName("test DOM_SELECT on form page with chained UDFs")
    fun testDomSelectOnFormPageWithChainedUdfs() {
        query(
            """
            SELECT
                DOM_ATTR(DOM, 'id') AS id,
                DOM_ATTR(DOM, 'data-testid') AS testid,
                DOM_TEXT(DOM) AS text
            FROM DOM_SELECT(DOM_LOAD('$formPageUrl'), 'button')
            """.trimIndent()
        ) { rs ->
            var foundClickButton = false
            var foundSubmitButton = false
            while (rs.next()) {
                val id = rs.getString("ID")
                if (id == "clickButton") foundClickButton = true
                if (id == "submitButton") foundSubmitButton = true
            }
            assertTrue(foundClickButton, "Expected to find clickButton")
            assertTrue(foundSubmitButton, "Expected to find submitButton")
        }
    }

    // -- LOAD_AND_GET_LINKS --------------------------------------------------

    @Test
    @DisplayName("test LOAD_AND_GET_LINKS extracts links from loaded page")
    fun testLoadAndGetLinksExtractsLinksFromPage() {
        execute(
            "SELECT * FROM LOAD_AND_GET_LINKS('$ecCategoryUrl', '.product-link')"
        )
    }

    @Test
    @Disabled("Disabled temporarily due to test instability, needs investigation")
    @DisplayName("test LOAD_AND_GET_LINKS/loadAndGetLinks returns non-empty result")
    fun testLoadAndGetLinksReturnsNonEmptyResult() {
        query(
            "SELECT * FROM LOAD_AND_GET_LINKS('$ecCategoryUrl', '.product-link')"
        ) { rs ->
            assertTrue(rs.metaData.columnCount > 0, "Expected at least 1 column")

            var count = 0
            while (rs.next()) count++
            assertTrue(count > 0, "Expected at least 1 link from EC category page")
        }
    }

    @Test
    @DisplayName("test LOAD_AND_GET_LINKS with offset and limit")
    fun testLoadAndGetLinksWithOffsetAndLimit() {
        query(
            "SELECT * FROM LOAD_AND_GET_LINKS('$ecCategoryUrl', '.product-link', 1, 3)"
        ) { rs ->
            var count = 0
            while (rs.next()) count++
            assertTrue(count <= 3, "Expected at most 3 links with limit=3, got $count")
        }
    }

    @Test
    @DisplayName("test LOAD_AND_GET_LINKS from form page extracts anchor href")
    fun testLoadAndGetLinksFromFormPage() {
        query(
            "SELECT * FROM LOAD_AND_GET_LINKS('$formPageUrl', 'a')"
        ) { rs ->
            assertTrue(rs.next(), "Expected at least 1 link from form page")
        }
    }

    // -- LOAD_AND_GET_FEATURES -----------------------------------------------

    @Test
    @DisplayName("test LOAD_AND_GET_FEATURES returns element features")
    fun testLoadAndGetFeaturesReturnsElementFeatures() {
        execute(
            "SELECT * FROM LOAD_AND_GET_FEATURES('$ecCategoryUrl', '.product-card', 1, 5)"
        )
    }

    @Test
    @DisplayName("test LOAD_AND_GET_FEATURES returns columns for product cards")
    fun testLoadAndGetFeaturesReturnsColumnsForProductCards() {
        query(
            "SELECT * FROM LOAD_AND_GET_FEATURES('$ecCategoryUrl', '.product-card', 1, 5)"
        ) { rs ->
            val metaData = rs.metaData
            val columnCount = metaData.columnCount
            assertTrue(columnCount > 1, "Expected multiple feature columns, got $columnCount")

            val columns = (1..columnCount).map { metaData.getColumnName(it) }
            logger.info("LOAD_AND_GET_FEATURES columns: $columns")

            var count = 0
            while (rs.next()) count++
            assertTrue(count > 0, "Expected at least 1 product card with features")
        }
    }

    // -- LOAD_AND_GET_ANCHORS -----------------------------------------------

    @Test
    @DisplayName("test LOAD_AND_GET_ANCHORS extracts anchor elements")
    fun testLoadAndGetAnchorsExtractsAnchorElements() {
        execute(
            "SELECT * FROM LOAD_AND_GET_ANCHORS('$ecCategoryUrl', '.product-link')"
        )
    }

    // -- XSQL_HELP -----------------------------------------------------------

    @Test
    @DisplayName("test XSQL_HELP returns list of registered functions")
    fun testXsqlHelpReturnsListOfRegisteredFunctions() {
        query("SELECT * FROM XSQL_HELP()") { rs ->
            val metaData = rs.metaData
            val columns = (1..metaData.columnCount).map { metaData.getColumnName(it) }
            logger.info("XSQL_HELP columns: $columns")

            var count = 0
            while (rs.next()) count++
            assertTrue(count > 0, "Expected at least 1 registered function")
            logger.info("XSQL_HELP returned $count registered functions")
        }
    }

    // -- Project pattern (from TestCases example) ----------------------------

    @Test
    @DisplayName("test project fields from EC category page")
    fun testProjectFieldsFromEcCategoryPage() {
        execute(
            """
            SELECT
                DOM_FIRST_TEXT(DOM, '.product-title') AS product_name,
                DOM_FIRST_TEXT(DOM, '.product-price') AS price
            FROM DOM_SELECT(DOM_LOAD('$ecCategoryUrl'), '.product-card', 1, 3)
            """.trimIndent()
        )
    }

    @Test
    @DisplayName("test extract title and href from EC product links")
    fun testExtractTitleAndHrefFromEcProductLinks() {
        execute(
            """
            SELECT
                DOM_DOC_TITLE(DOM) AS title,
                DOM_ABS_HREF(DOM) AS link
            FROM DOM_SELECT(DOM_LOAD('$ecCategoryUrl'), '.product-link', 1, 3)
            """.trimIndent()
        )
    }

    // -- LOAD_AND_SELECT on UDF test pages -----------------------------------

    @Test
    @DisplayName("test LOAD_AND_SELECT on product listing page")
    fun testLoadAndSelectOnProductListingPage() {
        val productListingUrl = "$baseURL/udf-test/product-listing.html"
        query(
            "SELECT * FROM LOAD_AND_SELECT('$productListingUrl', '.product-card')"
        ) { rs ->
            val metaData = rs.metaData
            assertTrue(metaData.columnCount > 0, "Expected at least 1 column")
            var count = 0
            while (rs.next()) count++
            assertTrue(count > 0, "Expected at least 1 product card")
        }
    }

    @Test
    @DisplayName("test extract structured data from product listing page")
    fun testExtractStructuredDataFromProductListingPage() {
        val productListingUrl = "$baseURL/udf-test/product-listing.html"
        query(
            """
            SELECT
                DOM_FIRST_TEXT(DOM, '.product-title') AS name,
                DOM_FIRST_TEXT(DOM, '.price-value') AS price,
                DOM_FIRST_TEXT(DOM, '.rating-value') AS rating
            FROM LOAD_AND_SELECT('$productListingUrl', '.product-card', 1, 3)
            """.trimIndent()
        ) { rs ->
            var count = 0
            while (rs.next()) {
                val name = rs.getString("NAME")
                val price = rs.getString("PRICE")
                assertNotNull(name)
                assertTrue(name.isNotBlank(), "Product name should not be blank")
                assertNotNull(price)
                assertTrue(price.isNotBlank(), "Price should not be blank")
                count++
            }
            assertEquals(3, count, "Expected 3 products")
        }
    }

    @Test
    @DisplayName("test comparison table extraction from product listing")
    fun testComparisonTableExtractionFromProductListing() {
        val productListingUrl = "$baseURL/udf-test/product-listing.html"
        query(
            """
            SELECT
                DOM_ALL_TEXTS(DOM, '#comparison_price_row td') AS prices,
                DOM_ALL_TEXTS(DOM, '#comparison_rating_row td') AS ratings
            FROM LOAD_AND_SELECT('$productListingUrl', '#comparison-section')
            """.trimIndent()
        ) { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getArray("PRICES"))
            assertNotNull(rs.getArray("RATINGS"))
        }
    }

    @Test
    @DisplayName("test breadcrumb extraction from product listing")
    fun testBreadcrumbExtractionFromProductListing() {
        val productListingUrl = "$baseURL/udf-test/product-listing.html"
        query(
            """
            SELECT
                DOM_ALL_TEXTS(DOM, '#breadcrumb li a') AS breadcrumbs
            FROM LOAD_AND_SELECT('$productListingUrl', 'body')
            """.trimIndent()
        ) { rs ->
            assertTrue(rs.next())
            val breadcrumbs = rs.getArray("BREADCRUMBS")
            assertNotNull(breadcrumbs)
        }
    }

    @Test
    @DisplayName("test link extraction from product listing sidebar")
    fun testLinkExtractionFromProductListingSidebar() {
        val productListingUrl = "$baseURL/udf-test/product-listing.html"
        query(
            """
            SELECT * FROM LOAD_AND_GET_LINKS('$productListingUrl', '.category-links a', 1, 10)
            """
        ) { rs ->
            var count = 0
            while (rs.next()) count++
            assertTrue(count > 0, "Expected at least 1 category link")
        }
    }

    // -- LOAD_AND_SELECT on article page ------------------------------------

    @Test
    @DisplayName("test LOAD_AND_SELECT on article page extracts headings")
    fun testLoadAndSelectOnArticlePageExtractsHeadings() {
        val articleUrl = "$baseURL/udf-test/article.html"
        query(
            """
            SELECT
                DOM_FIRST_TEXT(DOM, 'h1') AS title,
                DOM_ALL_TEXTS(DOM, 'h2') AS sections
            FROM LOAD_AND_SELECT('$articleUrl', 'body')
            """.trimIndent()
        ) { rs ->
            assertTrue(rs.next())
            assertEquals("Understanding Web Scraping with X-SQL", rs.getString("TITLE"))
            assertNotNull(rs.getArray("SECTIONS"))
        }
    }

    @Test
    @DisplayName("test article page tag extraction")
    fun testArticlePageTagExtraction() {
        val articleUrl = "$baseURL/udf-test/article.html"
        query(
            """
            SELECT
                DOM_ALL_TEXTS(DOM, '.tag') AS tags,
                DOM_ALL_HREFS(DOM, '.tag') AS tag_links
            FROM LOAD_AND_SELECT('$articleUrl', '.tags')
            """.trimIndent()
        ) { rs ->
            assertTrue(rs.next())
            val tags = rs.getArray("TAGS")
            assertNotNull(tags)
            val tagLinks = rs.getArray("TAG_LINKS")
            assertNotNull(tagLinks)
        }
    }

    @Test
    @DisplayName("test article related posts links extraction")
    fun testArticleRelatedPostsLinksExtraction() {
        val articleUrl = "$baseURL/udf-test/article.html"
        query(
            """
            SELECT * FROM LOAD_AND_GET_LINKS('$articleUrl', '#article-sidebar a')
            """
        ) { rs ->
            var count = 0
            while (rs.next()) count++
            assertTrue(count > 0, "Expected at least 1 related post link")
        }
    }

    // -- LOAD_AND_SELECT on link directory page -----------------------------

    @Test
    @DisplayName("test link directory internal links extraction")
    fun testLinkDirectoryInternalLinksExtraction() {
        val linkDirectoryUrl = "$baseURL/udf-test/link-directory.html"
        query(
            """
            SELECT DOM_ALL_HREFS(DOM, '#internal-links a') AS links
            FROM LOAD_AND_SELECT('$linkDirectoryUrl', 'body')
            """.trimIndent()
        ) { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getArray("LINKS"))
        }
    }

    @Test
    @DisplayName("test link directory category links extraction")
    fun testLinkDirectoryCategoryLinksExtraction() {
        val linkDirectoryUrl = "$baseURL/udf-test/link-directory.html"
        query(
            """
            SELECT DOM_ALL_ATTRS(DOM, '.category-link', 'href') AS hrefs
            FROM LOAD_AND_SELECT('$linkDirectoryUrl', '#category-links')
            """.trimIndent()
        ) { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getArray("HREFS"))
        }
    }

    @Test
    @DisplayName("test link directory pagination links")
    fun testLinkDirectoryPaginationLinks() {
        val linkDirectoryUrl = "$baseURL/udf-test/link-directory.html"
        query(
            """
            SELECT STR_LENGTH(DOM_FIRST_HREF(DOM, '.pagination a.next')) AS len
            FROM LOAD_AND_SELECT('$linkDirectoryUrl', 'body')
            """.trimIndent()
        ) { rs ->
            assertTrue(rs.next())
            assertTrue(rs.getInt("LEN") > 0, "Expected non-empty next page link")
        }
    }

    // -- DOM_SELECT table function with product listing ---------------------

    @Test
    @DisplayName("test DOM_SELECT on product listing page extracts product names")
    fun testDomSelectOnProductListingExtractsProductNames() {
        val productListingUrl = "$baseURL/udf-test/product-listing.html"
        query(
            """
            SELECT
                DOM_FIRST_TEXT(DOM, '.product-title') AS name,
                DOM_FIRST_TEXT(DOM, '.brand') AS brand,
                DOM_FIRST_TEXT(DOM, '.sku') AS sku
            FROM DOM_SELECT(DOM_LOAD('$productListingUrl'), '.product-card', 1, 3)
            """.trimIndent()
        ) { rs ->
            var count = 0
            while (rs.next()) {
                assertNotNull(rs.getString("NAME"))
                assertNotNull(rs.getString("BRAND"))
                assertNotNull(rs.getString("SKU"))
                count++
            }
            assertEquals(3, count, "Expected 3 products")
        }
    }

    // -- LOAD_AND_GET_FEATURES on product listing page ----------------------

    @Test
    @DisplayName("test LOAD_AND_GET_FEATURES on product listing page")
    fun testLoadAndGetFeaturesOnProductListingPage() {
        val productListingUrl = "$baseURL/udf-test/product-listing.html"
        execute(
            "SELECT * FROM LOAD_AND_GET_FEATURES('$productListingUrl', '.product-card', 1, 5)"
        )
    }

    // -- LOAD_AND_GET_ANCHORS on product listing page -----------------------

    @Test
    @DisplayName("test LOAD_AND_GET_ANCHORS on product listing page")
    fun testLoadAndGetAnchorsOnProductListingPage() {
        val productListingUrl = "$baseURL/udf-test/product-listing.html"
        execute(
            "SELECT * FROM LOAD_AND_GET_ANCHORS('$productListingUrl', '.product-link')"
        )
    }

    // -- LOAD_ALL_AND_SELECT on product listing page ------------------------

    @Test
    @DisplayName("test LOAD_ALL_AND_SELECT on product listing page")
    fun testLoadAllAndSelectOnProductListingPage() {
        val productListingUrl = "$baseURL/udf-test/product-listing.html"
        query(
            """
            SELECT DOM_FIRST_TEXT(DOM, '.product-title') AS name
            FROM LOAD_ALL_AND_SELECT(
                MAKE_ARRAY('$productListingUrl'),
                '.product-card',
                1,
                3
            )
            """.trimIndent()
        ) { rs ->
            var count = 0
            while (rs.next()) {
                assertTrue(rs.getString("NAME").isNotBlank(), "Product name should not be blank")
                count++
            }
            assertTrue(count > 0, "Expected at least 1 product from LOAD_ALL_AND_SELECT")
        }
    }

    // -- DOM_OWN_TEXT / DOM_OWN_TEXTS via LOAD_AND_SELECT ------------------

    @Test
    @DisplayName("test DOM_OWN_TEXT via LOAD_AND_SELECT on product listing")
    fun testDomOwnTextViaLoadAndSelectOnProductListing() {
        val productListingUrl = "$baseURL/udf-test/product-listing.html"
        query(
            """
            SELECT DOM_OWN_TEXT(DOM) AS text
            FROM LOAD_AND_SELECT('$productListingUrl', '.product-price', 1, 3)
            """.trimIndent()
        ) { rs ->
            var count = 0
            while (rs.next()) {
                val text = rs.getString("TEXT")
                assertNotNull(text)
                count++
            }
            assertEquals(3, count, "Expected 3 prices")
        }
    }
}
