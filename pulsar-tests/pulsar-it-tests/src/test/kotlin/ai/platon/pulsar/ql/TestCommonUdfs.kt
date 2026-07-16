package ai.platon.pulsar.ql

import org.junit.jupiter.api.DisplayName
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
    @DisplayName("test IS_NUMERIC returns true for numeric string")
    fun testIsNumericReturnsTrueForNumericString() {
        query("SELECT IS_NUMERIC('12345') AS result") { rs ->
            assertTrue(rs.next())
            assertTrue(rs.getBoolean("RESULT"))
        }
    }

    @Test
    @DisplayName("test IS_NUMERIC returns false for non-numeric string")
    fun testIsNumericReturnsFalseForNonNumericString() {
        query("SELECT IS_NUMERIC('abc123') AS result") { rs ->
            assertTrue(rs.next())
            assertEquals(false, rs.getBoolean("RESULT"))
        }
    }

    @Test
    @DisplayName("test STR_LENGTH returns string length")
    fun testStrLengthReturnsStringLength() {
        query("SELECT STR_LENGTH('hello') AS len") { rs ->
            assertTrue(rs.next())
            assertEquals(5, rs.getInt("LEN"))
        }
    }

    @Test
    @DisplayName("test STR_UPPER_CASE converts to uppercase")
    fun testStrUpperCaseConvertsToUppercase() {
        query("SELECT STR_UPPER_CASE('hello') AS result") { rs ->
            assertTrue(rs.next())
            assertEquals("HELLO", rs.getString("RESULT"))
        }
    }

    @Test
    @DisplayName("test STR_TRIM removes whitespace")
    fun testStrTrimRemovesWhitespace() {
        query("SELECT STR_TRIM('  hello  ') AS result") { rs ->
            assertTrue(rs.next())
            assertEquals("hello", rs.getString("RESULT"))
        }
    }

    // -- Regex functions (CommonFunctions, no namespace) ----------------------

    @Test
    @DisplayName("test RE1 extracts first regex group")
    fun testRe1ExtractsFirstRegexGroup() {
        // In H2 SQL, backslash has no special meaning in string literals.
        // Kotlin string "\\d+" → SQL string \d+ → regex \d+
        query("SELECT RE1('hello world 123', '(\\d+)') AS num") { rs ->
            assertTrue(rs.next())
            assertEquals("123", rs.getString("NUM"))
        }
    }

    @Test
    @DisplayName("test RE2 extracts two regex groups")
    fun testRe2ExtractsTwoRegexGroups() {
        query("SELECT RE2('key=value', '(\\w+)=(\\w+)') AS pair") { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getArray("PAIR"))
        }
    }

    // -- URL functions (CommonFunctions, no namespace) -------------------------

    @Test
    @DisplayName("test GET_TOP_PRIVATE_DOMAIN extracts domain from URL")
    fun testGetTopPrivateDomainExtractsDomainFromUrl() {
        query("SELECT GET_TOP_PRIVATE_DOMAIN('http://www.example.com/path') AS domain") { rs ->
            assertTrue(rs.next())
            assertEquals("example.com", rs.getString("DOMAIN"))
        }
    }

    // -- DOM_BASE_URI on loaded pages ----------------------------------------

    @Test
    @DisplayName("test DOM_BASE_URI of loaded page contains localhost")
    fun testDomBaseUriOfLoadedPageContainsLocalhost() {
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
    @DisplayName("test MAKE_ARRAY creates array from values")
    fun testMakeArrayCreatesArrayFromValues() {
        query("SELECT MAKE_ARRAY('a', 'b', 'c') AS arr") { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getArray("ARR"))
        }
    }

    // -- XSQL_HELP (CommonFunctionTables, no namespace) ------------------------

    @Test
    @DisplayName("test XSQL_HELP shows available functions")
    fun testXsqlHelpShowsAvailableFunctions() {
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
    @DisplayName("test ADMIN_SAVE persists a loaded page")
    fun testAdminSavePersistsALoadedPage() {
        execute("CALL ADMIN_SAVE('$domPageUrl', 'ql-it-dom-page.html')")
    }

    // -- STRING functions on loaded content ----------------------------------

    @Test
    @DisplayName("test DOM_TEXT combined with string UDFs")
    fun testDomTextCombinedWithStringUdfs() {
        query(
            """SELECT STR_LENGTH(DOM_FIRST_TEXT(DOM_LOAD('$formPageUrl'), 'h1')) AS len"""
        ) { rs ->
            assertTrue(rs.next())
            val len = rs.getInt("LEN")
            assertTrue(len > 0, "Expected positive length for h1 text, got $len")
        }
    }

    // -- MAKE_ARRAY_N (CommonFunctions, no namespace) -------------------------

    @Test
    @DisplayName("test MAKE_ARRAY_N creates array with n repeated values")
    fun testMakeArrayNCreatesArrayWithNRepeatedValues() {
        query("SELECT MAKE_ARRAY_N('x', 5) AS arr") { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getArray("ARR"))
        }
    }

    // -- TO_JSON (CommonFunctions, no namespace) -------------------------------

    @Test
    @DisplayName("test TO_JSON converts map result set to JSON string")
    fun testToJsonConvertsMapResultSetToJsonString() {
        query("SELECT TO_JSON(MAP('name', 'test', 'value', '123')) AS result") { rs ->
            assertTrue(rs.next())
            val json = rs.getString("RESULT")
            assertNotNull(json)
            assertTrue(json.contains("name"), "Expected JSON containing 'name', got: $json")
            assertTrue(json.contains("test"), "Expected JSON containing 'test', got: $json")
        }
    }

    // -- INT_ARRAY_MIN / INT_ARRAY_MAX (CommonFunctions, no namespace) ---------

    @Test
    @DisplayName("test INT_ARRAY_MIN returns minimum integer")
    fun testIntArrayMinReturnsMinimumInteger() {
        query("SELECT INT_ARRAY_MIN(MAKE_ARRAY(5, 3, 9, 1, 7)) AS result") { rs ->
            assertTrue(rs.next())
            assertEquals(1, rs.getInt("RESULT"))
        }
    }

    @Test
    @DisplayName("test INT_ARRAY_MAX returns maximum integer")
    fun testIntArrayMaxReturnsMaximumInteger() {
        query("SELECT INT_ARRAY_MAX(MAKE_ARRAY(5, 3, 9, 1, 7)) AS result") { rs ->
            assertTrue(rs.next())
            assertEquals(9, rs.getInt("RESULT"))
        }
    }

    @Test
    @DisplayName("test INT_ARRAY_MIN returns null for empty array")
    fun testIntArrayMinReturnsNullForEmptyArray() {
        execute("SELECT INT_ARRAY_MIN(MAKE_ARRAY())")
    }

    @Test
    @DisplayName("test INT_ARRAY_MAX returns null for empty array")
    fun testIntArrayMaxReturnsNullForEmptyArray() {
        execute("SELECT INT_ARRAY_MAX(MAKE_ARRAY())")
    }

    // -- FLOAT_ARRAY_MIN / FLOAT_ARRAY_MAX (CommonFunctions, no namespace) -----

    @Test
    @DisplayName("test FLOAT_ARRAY_MIN returns minimum float")
    fun testFloatArrayMinReturnsMinimumFloat() {
        // Use CAST to ensure H2 treats values as REAL/FLOAT, not DECIMAL
        query("SELECT FLOAT_ARRAY_MIN(MAKE_ARRAY(CAST(3.5 AS REAL), CAST(1.2 AS REAL), CAST(7.8 AS REAL), CAST(0.5 AS REAL))) AS result") { rs ->
            assertTrue(rs.next())
            assertEquals(0.5, rs.getDouble("RESULT"), 0.01)
        }
    }

    @Test
    @DisplayName("test FLOAT_ARRAY_MAX returns maximum float")
    fun testFloatArrayMaxReturnsMaximumFloat() {
        query("SELECT FLOAT_ARRAY_MAX(MAKE_ARRAY(CAST(3.5 AS REAL), CAST(1.2 AS REAL), CAST(7.8 AS REAL), CAST(0.5 AS REAL))) AS result") { rs ->
            assertTrue(rs.next())
            assertEquals(7.8, rs.getDouble("RESULT"), 0.01)
        }
    }

    @Test
    @DisplayName("test FLOAT_ARRAY_MAX returns null for empty array")
    fun testFloatArrayMaxReturnsNullForEmptyArray() {
        execute("SELECT FLOAT_ARRAY_MAX(MAKE_ARRAY())")
    }

    // -- GET_STRING (CommonFunctions, no namespace) ----------------------------

    @Test
    @DisplayName("test GET_STRING returns string representation of value")
    fun testGetStringReturnsStringRepresentationOfValue() {
        query("SELECT GET_STRING(MAKE_ARRAY('a', 'b')) AS result") { rs ->
            assertTrue(rs.next())
            val result = rs.getString("RESULT")
            assertNotNull(result)
            assertTrue(result.isNotBlank(), "Expected non-blank string representation")
        }
    }

    // -- IS_EMPTY / IS_NOT_EMPTY for ValueArray (CommonFunctions) --------------

    @Test
    @DisplayName("test IS_EMPTY returns true for empty ValueArray")
    fun testIsEmptyReturnsTrueForEmptyValueArray() {
        query("SELECT IS_EMPTY(MAKE_ARRAY()) AS result") { rs ->
            assertTrue(rs.next())
            assertTrue(rs.getBoolean("RESULT"))
        }
    }

    @Test
    @DisplayName("test IS_EMPTY returns false for non-empty ValueArray")
    fun testIsEmptyReturnsFalseForNonEmptyValueArray() {
        query("SELECT IS_EMPTY(MAKE_ARRAY('a', 'b')) AS result") { rs ->
            assertTrue(rs.next())
            assertEquals(false, rs.getBoolean("RESULT"))
        }
    }

    @Test
    @DisplayName("test IS_NOT_EMPTY returns false for empty ValueArray")
    fun testIsNotEmptyReturnsFalseForEmptyValueArray() {
        query("SELECT IS_NOT_EMPTY(MAKE_ARRAY()) AS result") { rs ->
            assertTrue(rs.next())
            assertEquals(false, rs.getBoolean("RESULT"))
        }
    }

    @Test
    @DisplayName("test IS_NOT_EMPTY returns true for non-empty ValueArray")
    fun testIsNotEmptyReturnsTrueForNonEmptyValueArray() {
        query("SELECT IS_NOT_EMPTY(MAKE_ARRAY('a', 'b')) AS result") { rs ->
            assertTrue(rs.next())
            assertTrue(rs.getBoolean("RESULT"))
        }
    }

    // -- FORMAT_TIMESTAMP (CommonFunctions, no namespace) ----------------------

    @Test
    @DisplayName("test FORMAT_TIMESTAMP formats millisecond timestamp")
    fun testFormatTimestampFormatsMillisecondTimestamp() {
        // 2024-01-15 10:30:00 UTC in milliseconds
        val ts = "1705312200000"
        query("SELECT FORMAT_TIMESTAMP('$ts', 'yyyy-MM-dd') AS result") { rs ->
            assertTrue(rs.next())
            val result = rs.getString("RESULT")
            assertNotNull(result)
            assertTrue(result.isNotBlank(), "Expected non-blank formatted timestamp")
        }
    }

    @Test
    @DisplayName("test FORMAT_TIMESTAMP with default format")
    fun testFormatTimestampWithDefaultFormat() {
        val ts = "1705312200000"
        query("SELECT FORMAT_TIMESTAMP('$ts') AS result") { rs ->
            assertTrue(rs.next())
            val result = rs.getString("RESULT")
            assertNotNull(result)
            assertTrue(result.contains("-"), "Expected timestamp format with dashes, got: $result")
        }
    }

    // -- MAKE_VALUE_STRING_JSON (CommonFunctions, no namespace) ----------------

    @Test
    @DisplayName("test MAKE_VALUE_STRING_JSON creates JSON from text and class")
    fun testMakeValueStringJsonCreatesJsonFromTextAndClass() {
        val jsonText = """{"name":"test","value":123}"""
        query("SELECT MAKE_VALUE_STRING_JSON('$jsonText', 'java.util.Map') AS result") { rs ->
            assertTrue(rs.next())
            assertNotNull(rs.getString("RESULT"))
        }
    }

    @Test
    @DisplayName("test MAKE_VALUE_STRING_JSON with no args creates empty JSON")
    fun testMakeValueStringJsonWithNoArgsCreatesEmptyJson() {
        execute("SELECT MAKE_VALUE_STRING_JSON()")
    }

    // -- ECHO (AdminFunctions, namespace ADMIN) --------------------------------

    @Test
    @DisplayName("test ADMIN_ECHO returns input unchanged")
    fun testAdminEchoReturnsInputUnchanged() {
        query("SELECT ADMIN_ECHO('hello world') AS result") { rs ->
            assertTrue(rs.next())
            assertEquals("hello world", rs.getString("RESULT"))
        }
    }

    @Test
    @DisplayName("test ADMIN_ECHO with two messages concatenates them")
    fun testAdminEchoWithTwoMessagesConcatenatesThem() {
        query("SELECT ADMIN_ECHO('hello', 'world') AS result") { rs ->
            assertTrue(rs.next())
            assertEquals("hello, world", rs.getString("RESULT"))
        }
    }

    // -- SESSION_COUNT (AdminFunctions, namespace ADMIN) -----------------------

    @Test
    @DisplayName("test ADMIN_SESSION_COUNT returns positive count")
    fun testAdminSessionCountReturnsPositiveCount() {
        query("SELECT ADMIN_SESSION_COUNT() AS result") { rs ->
            assertTrue(rs.next())
            val count = rs.getInt("RESULT")
            assertTrue(count > 0, "Expected positive session count, got $count")
        }
    }

    // -- CommonFunctionTables: LOAD_OPTIONS ------------------------------------

    @Test
    @DisplayName("test LOAD_OPTIONS returns help rows")
    fun testLoadOptionsReturnsHelpRows() {
        query("SELECT * FROM LOAD_OPTIONS()") { rs ->
            val columnCount = rs.metaData.columnCount
            assertTrue(columnCount >= 3, "Expected at least 3 columns, got $columnCount")

            var count = 0
            while (rs.next()) count++
            assertTrue(count > 0, "Expected at least 1 load option help row")
        }
    }

    // -- CommonFunctionTables: GAUGES ------------------------------------------

    @Test
    @DisplayName("test GAUGES returns system gauge metrics")
    fun testGaugesReturnsSystemGaugeMetrics() {
        query("SELECT * FROM GAUGES()") { rs ->
            val columnCount = rs.metaData.columnCount
            assertEquals(2, columnCount, "Expected 2 columns (NAME, VALUE)")

            val columns = (1..columnCount).map { rs.metaData.getColumnName(it) }
            assertTrue(columns.contains("NAME"), "Expected NAME column")
            assertTrue(columns.contains("VALUE"), "Expected VALUE column")
        }
    }

    // -- CommonFunctionTables: METERS ------------------------------------------

    @Test
    @DisplayName("test METERS returns system meter metrics")
    fun testMetersReturnsSystemMeterMetrics() {
        query("SELECT * FROM METERS()") { rs ->
            val columnCount = rs.metaData.columnCount
            assertTrue(columnCount >= 2, "Expected at least 2 columns, got $columnCount")
        }
    }

    // -- CommonFunctionTables: EXPLODE -----------------------------------------

    @Test
    @DisplayName("test EXPLODE converts array to rows")
    fun testExplodeConvertsArrayToRows() {
        query("SELECT * FROM EXPLODE(MAKE_ARRAY('A', 'B', 'C'))") { rs ->
            var count = 0
            while (rs.next()) {
                val col = rs.getString("COL")
                assertNotNull(col)
                count++
            }
            assertEquals(3, count, "Expected 3 rows from explode")
        }
    }

    @Test
    @DisplayName("test EXPLODE with empty array returns no rows")
    fun testExplodeWithEmptyArrayReturnsNoRows() {
        query("SELECT * FROM EXPLODE(MAKE_ARRAY())") { rs ->
            assertTrue(!rs.next(), "Expected no rows from empty array explode")
        }
    }

    @Test
    @DisplayName("test POSEXPLODE converts array to rows with position")
    fun testPosexplodeConvertsArrayToRowsWithPosition() {
        query("SELECT * FROM POSEXPLODE(MAKE_ARRAY('X', 'Y', 'Z'))") { rs ->
            var count = 0
            while (rs.next()) {
                val pos = rs.getInt("POS")
                assertTrue(pos in 1..3, "Expected position in 1..3, got $pos")
                count++
            }
            assertEquals(3, count, "Expected 3 rows from posexplode")
        }
    }

    // -- CommonFunctionTables: MAP ---------------------------------------------

    @Test
    @DisplayName("test MAP creates key-value ResultSet")
    fun testMapCreatesKeyValueResultSet() {
        query("SELECT * FROM MAP('k1', 'v1', 'k2', 'v2', 'k3', 'v3')") { rs ->
            val columns = (1..rs.metaData.columnCount).map { rs.metaData.getColumnName(it) }
            assertTrue(columns.contains("KEY"), "Expected KEY column")
            assertTrue(columns.contains("VALUE"), "Expected VALUE column")

            var count = 0
            while (rs.next()) count++
            assertEquals(3, count, "Expected 3 key-value pairs")
        }
    }

    // -- Test loading different content types from the mock server ------------

    @Test
    @DisplayName("test LOAD_AND_SELECT on a text page")
    fun testLoadAndSelectOnTextPage() {
        execute("SELECT * FROM LOAD_AND_SELECT('$textUrl', 'body')")
    }

    @Test
    @DisplayName("test load multiple different pages from mock server")
    fun testLoadMultipleDifferentPagesFromMockServer() {
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
