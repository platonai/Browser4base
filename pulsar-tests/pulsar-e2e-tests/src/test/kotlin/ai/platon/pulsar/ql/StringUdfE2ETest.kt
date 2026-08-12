package ai.platon.pulsar.ql

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * End-to-end tests for the STR (string manipulation) UDFs, all written as direct
 * X-SQL with assertions on the final results.
 */
@Tag("E2ETest")
@DisplayName("STR string UDFs")
class StringUdfE2ETest : XSqlTestBase() {

    private fun assertValue(sql: String, expected: String?) {
        assertEquals(listOf(listOf(expected)), queryRows(sql))
    }

    @Test
    @DisplayName("Case manipulation")
    fun testCaseManipulation() {
        assertValue("SELECT STR_CAPITALIZE('hello world')", "Hello world")
        assertValue("SELECT STR_UNCAPITALIZE('Hello')", "hello")
        assertValue("SELECT STR_SWAP_CASE('aBc')", "AbC")
        assertValue("SELECT STR_UPPER_CASE('abc')", "ABC")
        assertValue("SELECT STR_LOWER_CASE('ABC')", "abc")
    }

    @Test
    @DisplayName("Empty / blank checks")
    fun testEmptyAndBlankChecks() {
        assertValue("SELECT STR_IS_EMPTY('')", "TRUE")
        assertValue("SELECT STR_IS_NOT_EMPTY('a')", "TRUE")
        assertValue("SELECT STR_IS_BLANK('   ')", "TRUE")
        assertValue("SELECT STR_IS_NOT_BLANK(' a ')", "TRUE")
        assertValue("SELECT STR_IS_ANY_EMPTY(MAKE_ARRAY('a', ''))", "TRUE")
        assertValue("SELECT STR_IS_NONE_EMPTY(MAKE_ARRAY('a', 'b'))", "TRUE")
        assertValue("SELECT STR_IS_ANY_BLANK(MAKE_ARRAY('a', ' '))", "TRUE")
        assertValue("SELECT STR_IS_NONE_BLANK(MAKE_ARRAY('a', 'b'))", "TRUE")
    }

    @Test
    @DisplayName("Trimming and stripping")
    fun testTrimAndStrip() {
        assertValue("SELECT STR_TRIM('  a  ')", "a")
        assertValue("SELECT STR_TRIM_TO_NULL('   ')", null)
        assertValue("SELECT STR_TRIM_TO_EMPTY('   ')", "")
        assertValue("SELECT STR_STRIP('  x  ')", "x")
        assertValue("SELECT STR_STRIP('xxyxx', 'x')", "y")
        assertValue("SELECT STR_STRIP_TO_NULL('   ')", null)
        assertValue("SELECT STR_STRIP_TO_EMPTY('   ')", "")
        assertValue("SELECT STR_STRIP_START('xxabc', 'x')", "abc")
        assertValue("SELECT STR_STRIP_END('abcxx', 'x')", "abc")
        assertValue("SELECT STR_JOIN(STR_STRIP_ALL(MAKE_ARRAY(' a ', ' b ')), '|')", "a|b")
        assertValue("SELECT STR_STRIP_ACCENTS('café')", "cafe")
    }

    @Test
    @DisplayName("Substring extraction")
    fun testSubstring() {
        assertValue("SELECT STR_SUBSTRING('hello', 1)", "ello")
        assertValue("SELECT STR_SUBSTRING('hello', 1, 3)", "el")
        assertValue("SELECT STR_LEFT('hello', 2)", "he")
        assertValue("SELECT STR_RIGHT('hello', 2)", "lo")
        assertValue("SELECT STR_MID('hello', 1, 3)", "ell")
        assertValue("SELECT STR_SUBSTRING_BEFORE('abc-def', '-')", "abc")
        assertValue("SELECT STR_SUBSTRING_AFTER('abc-def', '-')", "def")
        assertValue("SELECT STR_SUBSTRING_BEFORE_LAST('a-b-c', '-')", "a-b")
        assertValue("SELECT STR_SUBSTRING_AFTER_LAST('a-b-c', '-')", "c")
        assertValue("SELECT STR_SUBSTRING_BETWEEN('a[b]c', '[', ']')", "b")
        assertValue("SELECT STR_JOIN(STR_SUBSTRINGS_BETWEEN('a[b]c[d]', '[', ']'), '|')", "b|d")
    }

    @Test
    @DisplayName("Search and contains")
    fun testSearchAndContains() {
        assertValue("SELECT STR_CONTAINS22('abc', 98)", "TRUE")
        assertValue("SELECT STR_CONTAINS_WHITESPACE('a b')", "TRUE")
        assertValue("SELECT STR_CONTAINS_ANY('abc', 'bx')", "TRUE")
        assertValue("SELECT STR_CONTAINS_ONLY('abc', 'abc')", "TRUE")
        assertValue("SELECT STR_CONTAINS_NONE('abc', 'xyz')", "TRUE")
        assertValue("SELECT STR_INDEX_OF_ANY('hello', 'ae')", "1")
        assertValue("SELECT STR_INDEX_OF_ANY_BUT('abc', 'ab')", "2")
        assertValue("SELECT STR_ORDINAL_INDEX_OF('a.b.c', '.', 2)", "3")
        assertValue("SELECT STR_LAST_ORDINAL_INDEX_OF('a.b.c', '.', 2)", "1")
        assertValue("SELECT STR_INDEX_OF_DIFFERENCE(MAKE_ARRAY('abc', 'abd'))", "2")
        assertValue("SELECT STR_INDEX_OF_DIFFERENCE('abc', 'abd')", "2")
        assertValue("SELECT STR_COUNT_MATCHES('abab', 'ab')", "2")
        assertValue("SELECT STR_GET_COMMON_PREFIX(MAKE_ARRAY('abc', 'abd'))", "ab")
    }

    @Test
    @DisplayName("Splitting and joining")
    fun testSplitAndJoin() {
        assertValue("SELECT STR_JOIN(STR_SPLIT('a,b,c', ','), '|')", "a|b|c")
        assertValue("SELECT STR_JOIN(STR_SPLIT('a,b,c', ',', 2), '|')", "a|b,c")
        assertValue("SELECT STR_JOIN(STR_SPLIT_BY_WHOLE_SEPARATOR('a--b--c', '--'), '|')", "a|b|c")
        assertValue(
            "SELECT STR_JOIN(STR_SPLIT_BY_WHOLE_SEPARATOR_PRESERVE_ALL_TOKENS('a--b', '--'), '|')",
            "a|b"
        )
        assertValue("SELECT STR_JOIN(STR_SPLIT_PRESERVE_ALL_TOKENS('a,b', ','), '|')", "a|b")
        assertValue("SELECT STR_JOIN(STR_SPLIT_BY_CHARACTER_TYPE('abc123'), '|')", "abc|123")
        assertValue("SELECT STR_JOIN(STR_SPLIT_BY_CHARACTER_TYPE_CAMEL_CASE('helloWorld'), '|')", "hello|World")
        assertValue("SELECT STR_JOIN(MAKE_ARRAY('a', 'b'), '-')", "a-b")
        assertValue("SELECT STR_JOIN(MAKE_ARRAY('a', 'b'))", "ab")
    }

    @Test
    @DisplayName("Replace and remove")
    fun testReplaceAndRemove() {
        assertValue(
            "SELECT STR_REPLACE_EACH('abc', MAKE_ARRAY('a', 'b'), MAKE_ARRAY('x', 'y'))",
            "xyc"
        )
        assertValue(
            "SELECT STR_REPLACE_EACH_REPEATEDLY('aba', MAKE_ARRAY('a', 'b'), MAKE_ARRAY('x', 'y'))",
            "xyx"
        )
        assertValue("SELECT STR_REPLACE_CHARS('abc', 'b', 'x')", "axc")
        assertValue("SELECT STR_REPLACE_CHARS('abc', 'bc', 'xy')", "axy")
        assertValue("SELECT STR_OVERLAY('hello', 'X', 1, 3)", "hXlo")
        assertValue("SELECT STR_DELETE_WHITESPACE('a b c')", "abc")
        assertValue("SELECT STR_CHOMP('abc' || CHAR(10))", "abc")
        assertValue("SELECT STR_CHOP('abc')", "ab")
        assertValue("SELECT STR_NORMALIZE_SPACE('  a   b  ')", "a b")
    }

    @Test
    @DisplayName("Padding")
    fun testPadding() {
        assertValue("SELECT STR_LEFT_PAD('ab', 4)", "  ab")
        assertValue("SELECT STR_LEFT_PAD('ab', 4, '0')", "00ab")
        assertValue("SELECT STR_LEFT_PAD('ab', 4, 'x')", "xxab")
        assertValue("SELECT STR_RIGHT_PAD('ab', 4)", "ab  ")
        assertValue("SELECT STR_RIGHT_PAD('ab', 4, '0')", "ab00")
        assertValue("SELECT STR_RIGHT_PAD('ab', 4, 'x')", "abxx")
        assertValue("SELECT STR_CENTER('ab', 4)", " ab ")
        assertValue("SELECT STR_CENTER('ab', 4, '0')", "0ab0")
        assertValue("SELECT STR_CENTER('ab', 4, 'x')", "xabx")
    }

    @Test
    @DisplayName("Other string utilities")
    fun testOtherUtilities() {
        assertValue("SELECT STR_REPEAT('ab', 3)", "ababab")
        assertValue("SELECT STR_REPEAT('ab', '-', 3)", "ab-ab-ab")
        assertValue("SELECT STR_REVERSE('abc')", "cba")
        assertValue("SELECT STR_DIFFERENCE('hello', 'hallo')", "lo")
        assertValue("SELECT STR_LENGTH('abc')", "3")
        assertValue("SELECT STR_ABBREVIATE('hello world', 8)", "hello...")
        assertValue("SELECT STR_ABBREVIATE('hello world', 3, 8)", "hel...")
        assertValue("SELECT STR_ABBREVIATE_MIDDLE('abcdefghijkl', '*', 8)", "abcd*jkl")
        assertValue("SELECT STR_DEFAULT_STRING(NULL)", "")
        assertValue("SELECT STR_DEFAULT_IF_BLANK(' ', 'x')", "x")
        assertValue("SELECT STR_DEFAULT_IF_EMPTY('', 'x')", "x")
        assertValue("SELECT STR_TO_ENCODED_STRING(CAST('abc' AS VARBINARY), 'UTF-8')", "abc")
    }

    @Test
    @DisplayName("Character classification")
    fun testCharacterClassification() {
        assertValue("SELECT STR_IS_ALPHA('abc')", "TRUE")
        assertValue("SELECT STR_IS_NUMERIC('123')", "TRUE")
        assertValue("SELECT STR_IS_WHITESPACE('  ')", "TRUE")
        assertValue("SELECT STR_IS_ALPHA_SPACE('abc ')", "TRUE")
        assertValue("SELECT STR_IS_ALPHANUMERIC('abc123')", "TRUE")
        assertValue("SELECT STR_IS_ALPHANUMERIC_SPACE('abc 123')", "TRUE")
        assertValue("SELECT STR_IS_ASCII_PRINTABLE('abc')", "TRUE")
        assertValue("SELECT STR_IS_NUMERIC_SPACE('12 3')", "TRUE")
        assertValue("SELECT STR_IS_ALL_LOWER_CASE('abc')", "TRUE")
        assertValue("SELECT STR_IS_ALL_UPPER_CASE('ABC')", "TRUE")
    }

    @Test
    @DisplayName("Number extraction")
    fun testNumberExtraction() {
        assertValue("SELECT STR_FIRST_INTEGER('price 42 dollars', 0)", "42")
        assertValue("""SELECT STR_FIRST_FLOAT('${'$'}99.99', 0.0)""", "99.99")
        assertValue("SELECT STR_GET_FIRST_FLOAT_NUMBER('1.5x', 0.0)", "1.5")
    }
}
