package ai.platon.pulsar.ql

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for common (non-DOM-specific) UDFs.
 *
 * Tests the UDFs defined in:
 *   - [ai.platon.pulsar.ql.h2.udfs.CommonFunctions] (no namespace)
 *   - [ai.platon.pulsar.ql.h2.udfs.CommonFunctionTables] (no namespace)
 *   - [ai.platon.pulsar.ql.h2.udfs.StringFunctions] (namespace = "STR")
 *   - [ai.platon.pulsar.ql.h2.udfs.AdminFunctions] (namespace = "ADMIN")
 *
 * All pages are served by the local mock server.
 */
class TestCommonUdfs : QlIntegrationTestBase() {

    // -- String functions (namespace STR) ------------------------------------

    @Test
    fun `IS_NUMERIC returns true for numeric string`() {
        query("SELECT IS_NUMERIC('12345') AS result") { rs ->
            assertTrue(rs.next())
            assertTrue(rs.getBoolean("RESULT"))
        }
    }

    @Test
    fun `IS_NUMERIC returns false for non-numeric string`() {
        query("SELECT IS_NUMERIC('abc123') AS result") { rs ->
            assertTrue(rs.next())
            assertEquals(false, rs.getBoolean("RESULT"))
        }
    }

    @Test
    fun `STR_LENGTH returns string length`() {
        query("SELECT STR_LENGTH('hello') AS len") { rs ->
            assertTrue(rs.next())
            assertEquals(5, rs.getInt("LEN"))
        }
    }

    @Test
    fun `STR_UPPER_CASE converts to uppercase`() {
        query("SELECT STR_UPPER_CASE('hello') AS result") { rs ->
            assertTrue(rs.next())
            assertEquals("HELLO", rs.getString("RESULT"))
        }
    }

    @Test
    fun `STR_TRIM removes whitespace`() {
        query("SELECT STR_TRIM('  hello  ') AS result") { rs ->
            assertTrue(rs.next())
            assertEquals("hello", rs.getString("RESULT"))
        }
    }

    // -- Regex functions (CommonFunctions, no namespace) ----------------------

    @Test
    fun `RE1 extracts first regex group`() {
        // In H2 SQL, backslash has no special meaning in string literals.
        // Kotlin string "\\d+" → SQL string \d+ → regex \d+
        query("SELECT RE1('hello world 123', '\\d+') AS num") { rs ->
            assertTrue(rs.next())
            assertEquals("123", rs.getString("NUM"))
        }
    }

    @Test
    fun `RE2 extracts two regex groups`() {
        query("SELECT RE2('key=value', '(\\w+)=(\\w+)') AS pair") { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getArray("PAIR"))
        }
    }

    // -- URL functions (CommonFunctions, no namespace) -------------------------

    @Test
    fun `GET_TOP_PRIVATE_DOMAIN extracts domain from URL`() {
        query("SELECT GET_TOP_PRIVATE_DOMAIN('http://www.example.com/path') AS domain") { rs ->
            assertTrue(rs.next())
            assertEquals("example.com", rs.getString("DOMAIN"))
        }
    }

    // -- DOM_BASE_URI on loaded pages ----------------------------------------

    @Test
    fun `DOM_BASE_URI of loaded page contains localhost`() {
        query("SELECT DOM_BASE_URI(DOM_LOAD('$domPageUrl')) AS uri") { rs ->
            assertTrue(rs.next())
            val uri = rs.getString("URI")
            assertNotNull(uri)
            assertTrue(uri.contains("127.0.0.1") || uri.contains("localhost"),
                "Expected URI to contain localhost, got: $uri")
        }
    }

    // -- MAKE_ARRAY (CommonFunctions, no namespace) --------------------------

    @Test
    fun `MAKE_ARRAY creates array from values`() {
        query("SELECT MAKE_ARRAY('a', 'b', 'c') AS arr") { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getArray("ARR"))
        }
    }

    // -- XSQL_HELP (CommonFunctionTables, no namespace) ------------------------

    @Test
    fun `XSQL_HELP shows available functions`() {
        query("SELECT * FROM XSQL_HELP()") { rs ->
            assertTrue(rs.metaData.columnCount >= 3,
                "Expected at least 3 columns (NAMESPACE, XSQL FUNCTION, etc.)")

            var count = 0
            while (rs.next()) count++
            assertTrue(count > 0, "Expected at least 1 registered function")
        }
    }

    // -- ADMIN_SAVE (AdminFunctions, namespace ADMIN) -------------------------

    @Test
    fun `ADMIN_SAVE persists a loaded page`() {
        // This saves the page to disk for inspection; should not throw
        execute("CALL ADMIN_SAVE('$domPageUrl', 'ql-it-dom-page.html')")
    }

    // -- STRING functions on loaded content ----------------------------------

    @Test
    fun `DOM_TEXT combined with string UDFs`() {
        query(
            """SELECT STR_LENGTH(DOM_FIRST_TEXT(DOM_LOAD('$formPageUrl'), 'h1')) AS len"""
        ) { rs ->
            assertTrue(rs.next())
            val len = rs.getInt("LEN")
            assertTrue(len > 0, "Expected positive length for h1 text, got $len")
        }
    }

    // -- Test loading different content types from the mock server ------------

    @Test
    fun `LOAD_AND_SELECT on a text page`() {
        execute("SELECT * FROM LOAD_AND_SELECT('$textUrl', 'body')")
    }

    @Test
    fun `load multiple different pages from mock server`() {
        query(
            """
            SELECT
                DOM_DOC_TITLE(DOM_LOAD('$formPageUrl')) AS form_title,
                DOM_DOC_TITLE(DOM_LOAD('$ecCategoryUrl')) AS ec_title
            """.trimIndent()
        ) { rs ->
            assertTrue(rs.next())
            assertEquals("Form Test Page", rs.getString("FORM_TITLE"))
            val ecTitle = rs.getString("EC_TITLE")
            assertNotNull(ecTitle)
            assertTrue(ecTitle.isNotBlank(), "EC page should have a non-blank title")
        }
    }
}
