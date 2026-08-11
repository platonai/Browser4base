package ai.platon.pulsar.ql

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end tests for the DOM table functions, META, system helper tables and
 * ADMIN UDFs, all written as direct X-SQL against the local mock site.
 */
@Tag("E2ETest")
@DisplayName("DOM table, META, system and ADMIN UDFs")
class DomTableAndAdminUdfE2ETest : XSqlTestBase() {

    @Test
    @DisplayName("DOM_SELECT table function via CALL")
    fun testDomSelectTableFunction() {
        assertEquals(
            3,
            countRows("CALL DOM_SELECT(DOM_LOAD('$jobsUrl'), '.job-card-list__title', 1, 3)")
        )
    }

    @Test
    @DisplayName("DOM_LINKS table function via CALL")
    fun testDomLinksTableFunction() {
        assertEquals(2, countRows("CALL DOM_LINKS(DOM_LOAD('$seoUrl'), '.nav', 1, 2)"))
    }

    @Test
    @DisplayName("DOM_LOAD_AND_GET_LINKS and DOM_LOAD_AND_GET_ANCHORS")
    fun testLoadAndGetLinksAndAnchors() {
        assertEquals(
            2,
            countRows("SELECT COUNT(*) FROM DOM_LOAD_AND_GET_LINKS('$seoUrl', '.nav', 1, 2)")
        )
        assertEquals(
            2,
            countRows("SELECT COUNT(*) FROM DOM_LOAD_AND_GET_ANCHORS('$seoUrl', '.nav', 1, 2)")
        )
    }

    @Test
    @DisplayName("DOM_LOAD_AND_GET_FEATURES and DOM_FEATURES")
    fun testFeatures() {
        assertEquals(
            3,
            countRows("SELECT COUNT(*) FROM DOM_LOAD_AND_GET_FEATURES('$seoUrl', 'h2', 1, 5)")
        )
        assertEquals(
            3,
            countRows("CALL DOM_FEATURES(DOM_LOAD('$seoUrl'), 'h2', 1, 5)")
        )
    }

    @Test
    @DisplayName("DOM_LOAD_AND_GET_ELEMENTS_WITH_MOST_SIBLING and DOM_GET_ELEMENTS_WITH_MOST_SIBLING")
    fun testElementsWithMostSibling() {
        assertTrue(
            countRows(
                "SELECT COUNT(*) FROM DOM_LOAD_AND_GET_ELEMENTS_WITH_MOST_SIBLING('$jobsUrl', 'DIV', 1, 3)"
            ) > 0
        )
        assertTrue(
            countRows("CALL DOM_GET_ELEMENTS_WITH_MOST_SIBLING(DOM_LOAD('$jobsUrl'), 'DIV', 1, 3)") > 0
        )
    }

    @Test
    @DisplayName("LOAD_OUT_PAGES family follows mock e-commerce links")
    fun testLoadOutPages() {
        assertEquals(
            3,
            countRows("SELECT COUNT(*) FROM LOAD_OUT_PAGES('$ecCategoryUrl', '.product-link', 1, 3)")
        )
        assertEquals(
            3,
            countRows("SELECT COUNT(*) FROM LOAD_OUT_PAGES_IGNORE_URL_QUERY('$ecCategoryUrl', '.product-link', 1, 3)")
        )
        assertEquals(
            3,
            countRows(
                "SELECT COUNT(*) FROM LOAD_OUT_PAGES_AND_SELECT('$ecCategoryUrl', '.product-link', 1, 3, '.product-title')"
            )
        )
        assertEquals(
            3,
            countRows(
                "SELECT COUNT(*) FROM LOAD_OUT_PAGES_AND_SELECT_FIRST('$ecCategoryUrl', '.product-link', 1, 3, '.product-title')"
            )
        )
    }

    @Test
    @DisplayName("META_LOAD, META_FETCH and META_GET")
    fun testMetadataFunctions() {
        assertTrue(countRows("SELECT COUNT(*) FROM META_LOAD('$jobsUrl')") > 0)
        assertTrue(countRows("SELECT COUNT(*) FROM META_FETCH('$jobsUrl')") > 0)
        val formatted = queryValue("SELECT META_GET('$jobsUrl')")
        assertTrue(formatted != null && "htmlsnapshot-test/jobs" in formatted)
    }

    @Test
    @DisplayName("LOAD_OPTIONS and XSQL_HELP")
    fun testLoadOptionsAndHelp() {
        assertTrue(countRows("SELECT COUNT(*) FROM LOAD_OPTIONS()") > 10)
        assertTrue(countRows("SELECT COUNT(*) FROM XSQL_HELP()") > 100)
    }

    @Test
    @DisplayName("GAUGES and METERS")
    fun testGaugesAndMeters() {
        assertTrue(countRows("SELECT COUNT(*) FROM GAUGES()") > 0)
        assertTrue(countRows("SELECT COUNT(*) FROM METERS()") > 0)
    }

    @Test
    @DisplayName("MAP, EXPLODE and POSEXPLODE")
    fun testMapExplodePosexplode() {
        assertEquals(
            listOf(listOf("a", "1"), listOf("b", "2")),
            queryRows("SELECT * FROM MAP('a', 1, 'b', 2) ORDER BY KEY")
        )
        assertEquals(
            listOf(listOf("a"), listOf("b"), listOf("c")),
            queryRows("SELECT * FROM EXPLODE(MAKE_ARRAY('a', 'b', 'c'))")
        )
        assertEquals(
            listOf(listOf("1", "a"), listOf("2", "b")),
            queryRows("SELECT * FROM POSEXPLODE(MAKE_ARRAY('a', 'b'))")
        )
    }

    @Test
    @DisplayName("ADMIN_ECHO and ADMIN_SESSION_COUNT")
    fun testAdminEchoAndSessionCount() {
        assertEquals(listOf(listOf("hello")), queryRows("SELECT ADMIN_ECHO('hello')"))
        assertEquals(listOf(listOf("a, b")), queryRows("SELECT ADMIN_ECHO('a', 'b')"))
        assertTrue(queryValue("SELECT ADMIN_SESSION_COUNT()")!!.toInt() >= 1)
    }

    @Test
    @DisplayName("ADMIN_SAVE persists a page to the web cache directory")
    fun testAdminSave() {
        val path = queryValue("SELECT ADMIN_SAVE('$complianceUrl')")
        assertTrue(path != null && path.isNotEmpty() && path.endsWith(".htm"), "Unexpected save path: $path")
    }

    @Test
    @Disabled("Closes the current H2 session and would break the pooled test connection")
    @DisplayName("ADMIN_CLOSE_SESSION")
    fun testAdminCloseSession() {
        // intentionally disabled
    }

    @Test
    @Disabled("Transpose is documented as not correctly implemented (TODO in source)")
    @DisplayName("TRANSPOSE")
    fun testTranspose() {
        // intentionally disabled
    }

    @Test
    @Disabled("Requires a configured external LLM service")
    @DisplayName("LLM and DOM_CHAT")
    fun testLlmFunctions() {
        // intentionally disabled
    }
}
