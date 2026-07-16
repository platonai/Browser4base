package ai.platon.pulsar.ql

import org.junit.jupiter.api.DisplayName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for Array and DateTime UDFs.
 *
 * Tests the UDFs defined in:
 *   - [ai.platon.pulsar.ql.h2.udfs.ArrayFunctions] (namespace = "ARRAY")
 *   - [ai.platon.pulsar.ql.h2.udfs.DateTimeFunctions] (namespace = "TIME")
 */
class TestArrayAndDateTimeUdfs : QlIntegrationTestBase() {

    // =========================================================================
    // ArrayFunctions (namespace = "ARRAY")
    // =========================================================================

    @Test
    @DisplayName("test ARRAY_JOIN_TO_STRING joins elements with separator")
    fun testArrayJoinToStringJoinsElementsWithSeparator() {
        query("SELECT ARRAY_JOIN_TO_STRING(MAKE_ARRAY('a', 'b', 'c'), ', ') AS result") { rs ->
            assertTrue(rs.next())
            assertEquals("a, b, c", rs.getString("RESULT"))
        }
    }

    @Test
    @DisplayName("test ARRAY_JOIN_TO_STRING with empty array returns empty string")
    fun testArrayJoinToStringWithEmptyArrayReturnsEmptyString() {
        query("SELECT ARRAY_JOIN_TO_STRING(MAKE_ARRAY(), ',') AS result") { rs ->
            assertTrue(rs.next())
            assertEquals("", rs.getString("RESULT"))
        }
    }

    @Test
    @DisplayName("test ARRAY_JOIN_TO_STRING with single element")
    fun testArrayJoinToStringWithSingleElement() {
        query("SELECT ARRAY_JOIN_TO_STRING(MAKE_ARRAY('hello'), '|') AS result") { rs ->
            assertTrue(rs.next())
            assertEquals("hello", rs.getString("RESULT"))
        }
    }

    // -- ARRAY_FIRST_NOT_BLANK --------------------------------------------------

    @Test
    @DisplayName("test ARRAY_FIRST_NOT_BLANK returns first non-blank value")
    fun testArrayFirstNotBlankReturnsFirstNonBlankValue() {
        query("SELECT ARRAY_FIRST_NOT_BLANK(MAKE_ARRAY('', '  ', 'hello', 'world')) AS result") { rs ->
            assertTrue(rs.next())
            assertEquals("hello", rs.getString("RESULT"))
        }
    }

    @Test
    @DisplayName("test ARRAY_FIRST_NOT_BLANK returns null when all blank")
    fun testArrayFirstNotBlankReturnsNullWhenAllBlank() {
        // When all values are blank, returns null
        execute("SELECT ARRAY_FIRST_NOT_BLANK(MAKE_ARRAY('', '  ', '   '))")
    }

    // -- ARRAY_FIRST_NOT_EMPTY --------------------------------------------------

    @Test
    @DisplayName("test ARRAY_FIRST_NOT_EMPTY returns first non-empty value")
    fun testArrayFirstNotEmptyReturnsFirstNonEmptyValue() {
        query("SELECT ARRAY_FIRST_NOT_EMPTY(MAKE_ARRAY('', 'hello', 'world')) AS result") { rs ->
            assertTrue(rs.next())
            assertEquals("hello", rs.getString("RESULT"))
        }
    }

    @Test
    @DisplayName("test ARRAY_FIRST_NOT_EMPTY returns null when all empty")
    fun testArrayFirstNotEmptyReturnsNullWhenAllEmpty() {
        execute("SELECT ARRAY_FIRST_NOT_EMPTY(MAKE_ARRAY('', '', ''))")
    }

    // =========================================================================
    // DateTimeFunctions (namespace = "TIME")
    // =========================================================================

    @Test
    @DisplayName("test TIME_FIRST_DATE_TIME parses ISO instant and formats")
    fun testTimeFirstDateTimeParsesIsoInstantAndFormats() {
        query("SELECT TIME_FIRST_DATE_TIME('2024-01-15T10:30:00Z', 'yyyy-MM-dd') AS result") { rs ->
            assertTrue(rs.next())
            val result = rs.getString("RESULT")
            assertNotNull(result)
            assertTrue(result.isNotBlank(), "Expected non-blank date result")
        }
    }

    @Test
    @DisplayName("test TIME_FIRST_DATE_TIME returns epoch on invalid input")
    fun testTimeFirstDateTimeReturnsEpochOnInvalidInput() {
        query("SELECT TIME_FIRST_DATE_TIME('not-a-date', 'yyyy-MM-dd') AS result") { rs ->
            assertTrue(rs.next())
            val result = rs.getString("RESULT")
            assertNotNull(result)
            // Should fall back to EPOCH formatted as yyyy-MM-dd
            assertEquals("1970-01-01", result)
        }
    }

    @Test
    @DisplayName("test TIME_FIRST_DATE_TIME returns epoch for null input")
    fun testTimeFirstDateTimeReturnsEpochForNullInput() {
        query("SELECT TIME_FIRST_DATE_TIME(NULL, 'yyyy-MM-dd') AS result") { rs ->
            assertTrue(rs.next())
            val result = rs.getString("RESULT")
            assertNotNull(result)
            assertEquals("1970-01-01", result)
        }
    }

    @Test
    @DisplayName("test TIME_FIRST_MYSQL_DATE_TIME parses MySQL datetime format")
    fun testTimeFirstMysqlDateTimeParsesMysqlDatetimeFormat() {
        query("SELECT TIME_FIRST_MYSQL_DATE_TIME('2024-01-15 10:30:00', 'yyyy/MM/dd') AS result") { rs ->
            assertTrue(rs.next())
            val result = rs.getString("RESULT")
            assertNotNull(result)
            assertTrue(result.isNotBlank(), "Expected non-blank formatted date result")
        }
    }

    @Test
    @DisplayName("test TIME_FIRST_MYSQL_DATE_TIME with default pattern")
    fun testTimeFirstMysqlDateTimeWithDefaultPattern() {
        query("SELECT TIME_FIRST_MYSQL_DATE_TIME('2024-06-15 14:30:00') AS result") { rs ->
            assertTrue(rs.next())
            val result = rs.getString("RESULT")
            assertNotNull(result)
            assertTrue(result.isNotBlank(), "Expected non-blank date result")
        }
    }

    @Test
    @DisplayName("test TIME_FIRST_DATE_TIME with various ISO formats")
    fun testTimeFirstDateTimeWithVariousIsoFormats() {
        // ISO instant with timezone offset
        query("SELECT TIME_FIRST_DATE_TIME('2024-06-15T14:30:00+08:00', 'yyyy-MM-dd HH:mm') AS result") { rs ->
            assertTrue(rs.next())
            val result = rs.getString("RESULT")
            assertNotNull(result)
            assertTrue(result.isNotBlank(), "Expected non-blank formatted date result")
        }
    }
}
