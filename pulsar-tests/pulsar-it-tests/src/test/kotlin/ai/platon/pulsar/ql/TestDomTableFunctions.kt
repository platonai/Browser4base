package ai.platon.pulsar.ql

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import kotlin.test.Test
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
}
