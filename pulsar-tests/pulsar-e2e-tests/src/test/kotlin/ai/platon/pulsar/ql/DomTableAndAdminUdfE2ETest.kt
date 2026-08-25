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
@Tag("Slow")
@DisplayName("DOM table, META, system and ADMIN UDFs")
class DomTableAndAdminUdfE2ETest : XSqlTestBase() {

    /**
     * H2 invokes table functions twice (column retrieval + execution) and the
     * column-retrieval connection can leak into the execution call on reused
     * connections, intermittently yielding an empty result set. Retry once to
     * shield the assertions from this framework-level race.
     */
    private fun countRowsStable(sql: String): Int {
        val n = countRows(sql)
        return if (n == 0) countRows(sql) else n
    }

    @Test
    @DisplayName("DOM_SELECT table function via CALL")
    fun testDomSelectTableFunction() {
        assertEquals(
            3,
            countRowsStable("CALL DOM_SELECT(DOM_LOAD('$jobsUrl'), '.job-card-list__title', 1, 3)")
        )
    }

    @Test
    @DisplayName("DOM_LINKS table function via CALL")
    fun testDomLinksTableFunction() {
        // The seo page uses a <nav> element without a class; query it by tag.
        assertEquals(2, countRowsStable("CALL DOM_LINKS(DOM_LOAD('$seoUrl'), 'nav a', 1, 2)"))
    }

    @Test
    @DisplayName("DOM_LOAD_AND_GET_LINKS and DOM_LOAD_AND_GET_ANCHORS")
    fun testLoadAndGetLinksAndAnchors() {
        assertEquals(
            2,
            countRowsStable("SELECT * FROM DOM_LOAD_AND_GET_LINKS('$seoUrl', 'nav a', 1, 2)")
        )
        assertEquals(
            2,
            countRowsStable("SELECT * FROM DOM_LOAD_AND_GET_ANCHORS('$seoUrl', 'nav a', 1, 2)")
        )
    }

    @Test
    @DisplayName("DOM_LOAD_AND_GET_FEATURES and DOM_FEATURES")
    fun testFeatures() {
        assertEquals(
            3,
            countRowsStable("SELECT * FROM DOM_LOAD_AND_GET_FEATURES('$seoUrl', 'h2', 1, 5)")
        )
        assertEquals(
            3,
            countRowsStable("CALL DOM_FEATURES(DOM_LOAD('$seoUrl'), 'h2', 1, 5)")
        )
    }

    @Test
    @DisplayName("DOM_LOAD_AND_GET_ELEMENTS_WITH_MOST_SIBLING and DOM_GET_ELEMENTS_WITH_MOST_SIBLING")
    fun testElementsWithMostSibling() {
        assertTrue(
            countRowsStable(
                "SELECT * FROM DOM_LOAD_AND_GET_ELEMENTS_WITH_MOST_SIBLING('$jobsUrl', 'DIV', 1, 3)"
            ) > 0
        )
        assertTrue(
            countRowsStable("CALL DOM_GET_ELEMENTS_WITH_MOST_SIBLING(DOM_LOAD('$jobsUrl'), 'DIV', 1, 3)") > 0
        )
    }

    @Test
    @DisplayName("LOAD_OUT_PAGES family follows mock e-commerce links")
    fun testLoadOutPages() {
        // The product links are <a class="product-link"> elements: pass the tag
        // explicitly so getLinks does not append 'a' as a descendant selector.
        val productLink = "a.product-link"
        assertEquals(
            3,
            countRowsStable("SELECT * FROM LOAD_OUT_PAGES('$ecCategoryUrl', '$productLink', 1, 3)")
        )
        assertEquals(
            3,
            countRowsStable("SELECT * FROM LOAD_OUT_PAGES_IGNORE_URL_QUERY('$ecCategoryUrl', '$productLink', 1, 3)")
        )
        assertEquals(
            3,
            countRowsStable(
                "SELECT * FROM LOAD_OUT_PAGES_AND_SELECT('$ecCategoryUrl', '$productLink', 1, 3, '#productTitle')"
            )
        )
        assertEquals(
            3,
            countRowsStable(
                "SELECT * FROM LOAD_OUT_PAGES_AND_SELECT_FIRST('$ecCategoryUrl', '$productLink', 1, 3, '#productTitle')"
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
        assertTrue(countRowsStable("SELECT * FROM LOAD_OPTIONS()") > 10)
        assertTrue(countRowsStable("SELECT * FROM XSQL_HELP()") > 100)
    }

    @Test
    @DisplayName("GAUGES and METERS")
    fun testGaugesAndMeters() {
        assertTrue(countRowsStable("SELECT * FROM GAUGES()") > 0)
        assertTrue(countRowsStable("SELECT * FROM METERS()") > 0)
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
