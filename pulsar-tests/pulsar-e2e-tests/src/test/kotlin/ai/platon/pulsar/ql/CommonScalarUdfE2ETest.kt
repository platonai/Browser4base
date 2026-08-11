package ai.platon.pulsar.ql

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end tests for the common (no namespace), ARRAY and TIME scalar UDFs,
 * all written as direct X-SQL with assertions on the final results.
 */
@Tag("E2ETest")
@DisplayName("Common, ARRAY and TIME scalar UDFs")
class CommonScalarUdfE2ETest : XSqlTestBase() {

    @Test
    @DisplayName("IS_NUMERIC")
    fun testIsNumeric() {
        assertEquals(listOf(listOf("true")), queryRows("SELECT IS_NUMERIC('12345')"))
        assertEquals(listOf(listOf("false")), queryRows("SELECT IS_NUMERIC('12a45')"))
    }

    @Test
    @DisplayName("GET_TOP_PRIVATE_DOMAIN")
    fun testGetTopPrivateDomain() {
        val rows = queryRows("SELECT GET_TOP_PRIVATE_DOMAIN('https://www.amazon.com/dp/B08PP5MSVB')")
        assertEquals(listOf(listOf("amazon.com")), rows)
    }

    @Test
    @DisplayName("RE1 and RE2")
    fun testRe1AndRe2() {
        assertEquals(
            listOf(listOf("99.99")),
            queryRows("""SELECT RE1('price: ${'$'}99.99', '(\d+\.\d+)')""")
        )
        assertEquals(
            listOf(listOf("08")),
            queryRows("SELECT RE1('2026-08-11', '(\\d{4})-(\\d{2})', 2)")
        )
        assertEquals(
            listOf(listOf("key:value")),
            queryRows("""SELECT STR_JOIN(RE2('key=value', '(\w+)=(\w+)'), ':')""")
        )
    }

    @Test
    @DisplayName("MAKE_ARRAY and MAKE_ARRAY_N")
    fun testMakeArray() {
        assertEquals(listOf(listOf("3")), queryRows("SELECT ARRAY_LENGTH(MAKE_ARRAY('a', 'b', 'c'))"))
        assertEquals(listOf(listOf("5")), queryRows("SELECT ARRAY_LENGTH(MAKE_ARRAY_N('x', 5))"))
    }

    @Test
    @DisplayName("TO_JSON")
    fun testToJson() {
        val json = queryValue("SELECT TO_JSON(MAP('a', 1, 'b', 2))")
        assertTrue(json != null && "a" in json && "1" in json && "b" in json && "2" in json)
    }

    @Test
    @DisplayName("MAKE_VALUE_STRING_JSON")
    fun testMakeValueStringJSON() {
        assertEquals(listOf(listOf("{}")), queryRows("SELECT MAKE_VALUE_STRING_JSON()"))
    }

    @Test
    @DisplayName("INT_ARRAY_MIN and INT_ARRAY_MAX")
    fun testIntArrayMinMax() {
        val array = "MAKE_ARRAY('a', 3, 1, 'b', 2)"
        assertEquals(listOf(listOf("1")), queryRows("SELECT INT_ARRAY_MIN($array)"))
        assertEquals(listOf(listOf("3")), queryRows("SELECT INT_ARRAY_MAX($array)"))
    }

    @Test
    @DisplayName("FLOAT_ARRAY_MIN and FLOAT_ARRAY_MAX")
    fun testFloatArrayMinMax() {
        val array = "MAKE_ARRAY(CAST(1.5 AS REAL), CAST(0.5 AS REAL))"
        assertEquals(listOf(listOf("0.5")), queryRows("SELECT FLOAT_ARRAY_MIN($array)"))
        assertEquals(listOf(listOf("1.5")), queryRows("SELECT FLOAT_ARRAY_MAX($array)"))
    }

    @Test
    @DisplayName("GET_STRING")
    fun testGetString() {
        assertEquals(listOf(listOf("123")), queryRows("SELECT GET_STRING(CAST(123 AS VARCHAR))"))
    }

    @Test
    @DisplayName("IS_EMPTY and IS_NOT_EMPTY")
    fun testIsEmpty() {
        assertEquals(listOf(listOf("true")), queryRows("SELECT IS_EMPTY(MAKE_ARRAY())"))
        assertEquals(listOf(listOf("true")), queryRows("SELECT IS_NOT_EMPTY(MAKE_ARRAY('a'))"))
    }

    @Test
    @DisplayName("FORMAT_TIMESTAMP")
    fun testFormatTimestamp() {
        assertEquals(listOf(listOf("1970")), queryRows("SELECT FORMAT_TIMESTAMP('0', 'yyyy')"))
    }

    @Test
    @DisplayName("ARRAY_JOIN_TO_STRING")
    fun testArrayJoinToString() {
        assertEquals(
            listOf(listOf("a-b-c")),
            queryRows("SELECT ARRAY_JOIN_TO_STRING(MAKE_ARRAY('a', 'b', 'c'), '-')")
        )
    }

    @Test
    @DisplayName("ARRAY_FIRST_NOT_BLANK and ARRAY_FIRST_NOT_EMPTY")
    fun testArrayFirstNotBlank() {
        assertEquals(
            listOf(listOf("x")),
            queryRows("SELECT ARRAY_FIRST_NOT_BLANK(MAKE_ARRAY('', '  ', 'x'))")
        )
        assertEquals(
            listOf(listOf("x")),
            queryRows("SELECT ARRAY_FIRST_NOT_EMPTY(MAKE_ARRAY('', 'x'))")
        )
    }

    @Test
    @DisplayName("TIME_FIRST_DATE_TIME and TIME_FIRST_MYSQL_DATE_TIME")
    fun testTimeFunctions() {
        assertEquals(
            listOf(listOf("2026")),
            queryRows("SELECT TIME_FIRST_DATE_TIME('2026-08-11T00:00:00Z', 'yyyy')")
        )
        assertEquals(
            listOf(listOf("2026-08-11 10:20")),
            queryRows("SELECT TIME_FIRST_MYSQL_DATE_TIME('2026-08-11 10:20:30', 'yyyy-MM-dd HH:mm')")
        )
        assertEquals(
            listOf(listOf("1970")),
            queryRows("SELECT TIME_FIRST_MYSQL_DATE_TIME('garbage', 'yyyy')")
        )
    }
}
