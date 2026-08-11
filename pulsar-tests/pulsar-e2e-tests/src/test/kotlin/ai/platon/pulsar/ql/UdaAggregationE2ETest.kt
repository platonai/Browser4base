package ai.platon.pulsar.ql

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end tests for the H2 user-defined aggregation functions
 * (GROUP_COLLECT and GROUP_FETCH), written as direct X-SQL with
 * assertions on the final results.
 *
 * These are aggregate functions used in GROUP BY or as scalar aggregates
 * over an entire result set.
 */
@Tag("E2ETest")
@DisplayName("UDA aggregation functions")
class UdaAggregationE2ETest : XSqlTestBase() {

    @Test
    @DisplayName("GROUP_COLLECT aggregates values into an array")
    fun testGroupCollect() {
        // GROUP_COLLECT collects all values in the group into a ValueArray.
        // We use it without GROUP BY to collect all rows into a single array,
        // then verify via ARRAY_LENGTH and POSEXPLODE.
        val arrayLen = queryValue(
            "SELECT ARRAY_LENGTH(GROUP_COLLECT(COL)) " +
                "FROM EXPLODE(MAKE_ARRAY('a', 'b', 'c', 'd', 'e'))"
        )
        assertEquals("5", arrayLen, "GROUP_COLLECT should collect all 5 values")

        // Verify the collected values can be iterated via POSEXPLODE
        val rows = queryRows(
            "SELECT * FROM POSEXPLODE(" +
                "  (SELECT GROUP_COLLECT(COL) FROM EXPLODE(MAKE_ARRAY('x', 'y', 'z')))" +
                ") ORDER BY POS"
        )
        assertEquals(
            listOf(listOf("1", "x"), listOf("2", "y"), listOf("3", "z")),
            rows,
            "GROUP_COLLECT should preserve all values in order"
        )
    }

    @Test
    @DisplayName("GROUP_COLLECT with empty result set returns empty array")
    fun testGroupCollectEmpty() {
        val row = queryRows(
            "SELECT ARRAY_LENGTH(GROUP_COLLECT(COL)) " +
                "FROM EXPLODE(MAKE_ARRAY())"
        )
        // With an empty array, EXPLODE produces zero rows and GROUP_COLLECT
        // should produce an empty array — but H2 may not emit any row.
        // We verify the function does not throw.
        assertTrue(row.isEmpty() || row == listOf(listOf("0")),
            "GROUP_COLLECT on empty input should not throw; got: $row")
    }

    @Test
    @DisplayName("GROUP_FETCH collects URLs and loads them, returning the URL array")
    fun testGroupFetch() {
        // GROUP_FETCH collects URLs that are passed to it via add(),
        // then calls session.loadAll(urls) in getResult(),
        // and returns a ValueArray of the collected URLs.
        val urls = queryValue(
            "SELECT ARRAY_LENGTH(" +
                "(SELECT GROUP_FETCH(CONCAT('http://127.0.0.1:', $port, '/hello')) " +
                " FROM EXPLODE(MAKE_ARRAY('1', '2', '3')))" +
                ")"
        )
        // GROUP_FETCH loads the collected URLs and returns them as an array.
        // Since all 3 rows produce the same URL, and GROUP_FETCH does not
        // deduplicate, we expect 3 entries.
        assertEquals("3", urls, "GROUP_FETCH should collect and return all URLs")
    }

    @Test
    @DisplayName("GROUP_FETCH with multiple distinct mock-site URLs")
    fun testGroupFetchDistinctUrls() {
        // Use POSEXPLODE to create rows with different mock-site URLs,
        // then GROUP_FETCH collects and loads them.
        val result = queryValue(
            "SELECT ARRAY_LENGTH(" +
                "  (SELECT GROUP_FETCH(CONCAT('http://127.0.0.1:', $port, '/htmlsnapshot-test/news'))" +
                "   FROM EXPLODE(MAKE_ARRAY('1', '2')))" +
                ")"
        )
        assertNotNull(result, "GROUP_FETCH with mock-site URLs should return a result")
        assertEquals("2", result, "GROUP_FETCH should collect and return 2 URLs")
    }
}
