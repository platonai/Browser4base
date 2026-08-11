package ai.platon.pulsar.ql

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end tests for the load-and-select family of H2 UDFs.
 *
 * All pages are served by the local mock site; all tests write X-SQL directly
 * and assert on the final result rows.
 */
@Tag("E2ETest")
@DisplayName("Load and select UDFs against the mock site")
class LoadAndSelectUdfE2ETest : XSqlTestBase() {

    @Test
    @DisplayName("LOAD_AND_SELECT extracts job titles from the mock jobs page")
    fun testLoadAndSelectExtractsTitlesFromJobsPage() {
        val sql = """
            SELECT DOM_TEXT(DOM) AS title
            FROM LOAD_AND_SELECT('$jobsUrl', '.job-card-list__title')
            ORDER BY DOM_TEXT(DOM)
        """.trimIndent()

        val rows = queryRows(sql)

        assertEquals(
            listOf(
                listOf("Frontend Architect"),
                listOf("Lead Frontend Developer"),
                listOf("Senior Frontend Engineer"),
                listOf("Senior React Engineer"),
                listOf("Staff Frontend Engineer"),
            ),
            rows
        )
    }

    @Test
    @DisplayName("LOAD_AND_SELECT applies per-page offset and limit")
    fun testLoadAndSelectAppliesOffsetAndLimit() {
        val sql = """
            SELECT DOM_TEXT(DOM) AS title
            FROM LOAD_AND_SELECT('$jobsUrl', '.job-card-list__title', 1, 3)
        """.trimIndent()

        val rows = queryRows(sql)

        assertEquals(
            listOf(
                listOf("Senior Frontend Engineer"),
                listOf("Lead Frontend Developer"),
                listOf("Senior React Engineer"),
            ),
            rows
        )
    }

    @Test
    @DisplayName("LOAD_AND_SELECT counts matching elements")
    fun testLoadAndSelectCountsElements() {
        val productCards = queryRows(
            "SELECT COUNT(*) FROM LOAD_AND_SELECT('$complianceUrl', '.product-card')"
        )
        assertEquals(listOf(listOf("2")), productCards)

        val newsRows = queryRows(
            "SELECT COUNT(*) FROM LOAD_AND_SELECT('$newsUrl', '.athing')"
        )
        assertEquals(listOf(listOf("6")), newsRows)
    }

    @Test
    @DisplayName("LOAD_AND_SELECT extracts anchors with DOM_TEXT and DOM_HREF")
    fun testLoadAndSelectExtractsAnchorsFromNewsPage() {
        val sql = """
            SELECT DOM_TEXT(DOM) AS title, DOM_HREF(DOM) AS href
            FROM LOAD_AND_SELECT('$newsUrl', '.titleline a', 1, 3)
        """.trimIndent()

        val rows = queryRows(sql)

        assertEquals(
            listOf(
                listOf("New AI Breakthrough in Natural Language Understanding", "https://example.com/ai-breakthrough"),
                listOf("Rust 2.0 Released with Major Async Improvements", "https://example.com/rust-2-release"),
                listOf("WebAssembly Performance Tuning Guide", "https://example.com/webassembly-perf"),
            ),
            rows
        )
    }

    @Test
    @DisplayName("LOAD_ALL_AND_SELECT aggregates matching elements across pages")
    fun testLoadAllAndSelectAggregatesMultiplePages() {
        val sql = """
            SELECT DOM_TEXT(DOM) AS heading
            FROM LOAD_ALL_AND_SELECT(MAKE_ARRAY('$jobsUrl', '$complianceUrl', '$seoUrl'), 'h2')
            ORDER BY DOM_TEXT(DOM)
        """.trimIndent()

        val rows = queryRows(sql)

        assertEquals(
            listOf(
                listOf("Frontend Architect"),
                listOf("Lead Frontend Developer"),
                listOf("Savings Products"),
                listOf("Section A — On-Page SEO"),
                listOf("Section B — Technical SEO"),
                listOf("Section C — Link Building"),
                listOf("Senior Frontend Engineer"),
                listOf("Senior React Engineer"),
                listOf("Staff Frontend Engineer"),
            ),
            rows
        )
    }

    @Test
    @DisplayName("LOAD_ALL_AND_SELECT applies offset and limit per page, not globally")
    fun testLoadAllAndSelectAppliesOffsetAndLimitPerPage() {
        val sql = """
            SELECT DOM_TEXT(DOM) AS heading
            FROM LOAD_ALL_AND_SELECT(MAKE_ARRAY('$jobsUrl', '$seoUrl'), 'h2', 1, 2)
            ORDER BY DOM_TEXT(DOM)
        """.trimIndent()

        val rows = queryRows(sql)

        assertEquals(
            listOf(
                listOf("Lead Frontend Developer"),
                listOf("Section A — On-Page SEO"),
                listOf("Section B — Technical SEO"),
                listOf("Senior Frontend Engineer"),
            ),
            rows
        )
    }

    @Test
    @DisplayName("LOAD_ALL_AND_SELECT combines selectors and extraction across heterogeneous pages")
    fun testLoadAllAndSelectCombinesSelectorsAcrossPages() {
        val sql = """
            SELECT DOM_FIRST_TEXT(DOM, '[data-test="property-card-address"]') AS address
            FROM LOAD_ALL_AND_SELECT(
                MAKE_ARRAY('$realEstateUrl', '$researchUrl'),
                'article[data-test="property-card"], .docsum-content',
                1, 3
            )
            ORDER BY address
        """.trimIndent()

        val rows = queryRows(sql)

        assertEquals(
            listOf(
                listOf(""),
                listOf(""),
                listOf(""),
                listOf("123 Main St, San Francisco, CA 94102"),
                listOf("456 Oak Avenue, San Francisco, CA 94110"),
                listOf("789 Pine Street, San Francisco, CA 94108"),
            ),
            rows
        )
    }

    @Test
    @DisplayName("LOAD_ALL_AND_SELECT deduplicates repeated urls")
    fun testLoadAllAndSelectDeduplicatesUrls() {
        val rows = queryRows(
            """
            SELECT COUNT(*)
            FROM LOAD_ALL_AND_SELECT(MAKE_ARRAY('$jobsUrl', '$jobsUrl'), '.job-card-list__title')
            """.trimIndent()
        )

        assertEquals(listOf(listOf("5")), rows)
    }

    @Test
    @DisplayName("LOAD_ALL_AND_SELECT with an empty url array returns no rows")
    fun testLoadAllAndSelectWithEmptyUrlArray() {
        val rows = queryRows(
            "SELECT COUNT(*) FROM LOAD_ALL_AND_SELECT(MAKE_ARRAY(), 'h2')"
        )

        assertEquals(listOf(listOf("0")), rows)
    }

    @Test
    @DisplayName("LOAD_ALL returns one row per page")
    fun testLoadAllReturnsOneRowPerPage() {
        val sql = """
            SELECT DOM_DOC_TITLE(DOM) AS page_title
            FROM LOAD_ALL(MAKE_ARRAY('$jobsUrl', '$seoUrl'))
            ORDER BY DOM_DOC_TITLE(DOM)
        """.trimIndent()

        val rows = queryRows(sql)

        assertEquals(
            listOf(
                listOf("Job Search Results — Senior Frontend Engineer"),
                listOf("SEO Health Audit Test Page"),
            ),
            rows
        )
    }

    @Test
    @DisplayName("LOAD_AND_SELECT extracts multiple fields from each element")
    fun testLoadAndSelectExtractsMultipleFieldsFromEachElement() {
        val sql = """
            SELECT
                DOM_FIRST_TEXT(DOM, '.job-card-container__company-name') AS company,
                DOM_FIRST_TEXT(DOM, '.job-search-card__salary-info') AS salary
            FROM LOAD_AND_SELECT('$jobsUrl', '.job-card-container', 1, 5)
            ORDER BY company
        """.trimIndent()

        val rows = queryRows(sql)

        assertEquals(
            listOf(
                listOf("BigData Inc.", "$160k - $210k"),
                listOf("CloudNative Ltd.", ""),
                listOf("FinanceHub", "$170k - $220k"),
                listOf("StartupXYZ", "$140k - $180k"),
                listOf("TechCorp", "$150k - $200k"),
            ),
            rows
        )
    }

    @Test
    @Tag("ManualOnly")
    @DisplayName("LOAD_ALL_AND_SELECT handles a large URL set within bounded memory")
    fun testLoadAllAndSelectLargeUrlSet() {
        // Build ~200 distinct URLs pointing at the mock site news page by
        // appending query params that the controller ignores.
        val urlCount = 200
        val urls = (1..urlCount).joinToString(", ") { "'$newsUrl?id=$it'" }
        val sql = """
            SELECT COUNT(*) AS cnt
            FROM LOAD_ALL_AND_SELECT(MAKE_ARRAY($urls), '.athing', 1, 2)
        """.trimIndent()

        val rows = queryRows(sql)

        // Each of the 200 pages has 6 .athing elements, with limit=2 we
        // expect 2 per page × 200 pages = 400 rows.
        val count = rows.firstOrNull()?.firstOrNull()?.toIntOrNull() ?: 0
        assertEquals(400, count, "Expected 2 elements per page × $urlCount pages")

        // Sanity: the call completed without OOM/timeout.
        // The framework's concurrency cap (32) ensures memory stays bounded
        // regardless of URL count.
        assertTrue(count > 0, "Should return rows even for a large URL set")
    }
}
