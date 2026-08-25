package ai.platon.pulsar.ql

import ai.platon.pulsar.MockSiteAccess
import ai.platon.pulsar.common.AppPaths
import ai.platon.pulsar.common.browser.BrowserType
import ai.platon.pulsar.ql.context.SQLContext
import ai.platon.pulsar.skeleton.common.options.LoadOptionDefaults
import ai.platon.pulsar.skeleton.context.PulsarContexts
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.springframework.beans.factory.annotation.Autowired
import java.nio.file.Files
import java.util.Comparator
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Base class for X-SQL UDF end-to-end tests running against the local mock site.
 *
 * Every test writes X-SQL directly and verifies the final result rows.
 */
abstract class XSqlTestBase : MockSiteAccess() {

    @Autowired
    lateinit var sqlContext: SQLContext

    protected val htmlSnapshotBaseURL get() = "$baseURL/htmlsnapshot-test"
    protected val newsUrl get() = "$htmlSnapshotBaseURL/news"
    protected val seoUrl get() = "$htmlSnapshotBaseURL/seo"
    protected val jobsUrl get() = "$htmlSnapshotBaseURL/jobs"
    protected val complianceUrl get() = "$htmlSnapshotBaseURL/compliance"
    protected val researchUrl get() = "$htmlSnapshotBaseURL/research"
    protected val realEstateUrl get() = "$htmlSnapshotBaseURL/real-estate"

    protected val formPageUrl get() = "$baseURL/assets/test-pages/form-page.html"
    protected val errorPageUrl get() = "$baseURL/assets/test-pages/error-page.html"

    /**
     * The mock e-commerce category page, whose product cards link to mock detail pages.
     */
    protected val ecCategoryUrl get() = "$baseURL/ec/b?node=1292115012"

    /**
     * Run an X-SQL statement and return every result row as a list of string values.
     */
    protected fun queryRows(sql: String): List<List<String?>> {
        return sqlContext.executeQuery(sql).use { rs ->
            val columnCount = rs.metaData.columnCount
            buildList {
                while (rs.next()) {
                    add(List(columnCount) { i -> rs.getString(i + 1) })
                }
            }
        }
    }

    /**
     * Run an X-SQL statement and return the first column of the first row, or null if there is no row.
     */
    protected fun queryValue(sql: String): String? = queryRows(sql).firstOrNull()?.firstOrNull()

    /**
     * Run an X-SQL statement and count the result rows without materializing their values.
     */
    protected fun countRows(sql: String): Int {
        return sqlContext.executeQuery(sql).use { rs ->
            var n = 0
            while (rs.next()) {
                ++n
            }
            n
        }
    }

    companion object {
        private val pageStoreCleaned = AtomicBoolean(false)

        init {
            LoadOptionDefaults.apply {
                // The default expiry is decades, so a page fetched by an earlier
                // test is reused by later tests within the same run. This keeps
                // the tests deterministic as long as the page store is cleaned
                // before the first test (see cleanPageStore).
                parse = true
                ignoreFailure = true
                nJitRetry = 3
                test = 1
                browser = BrowserType.PULSAR_CHROME
            }
        }

        /**
         * Clean the local page store (disk + in-memory caches) once per JVM
         * before the first test, so every URL is fetched for real at least once,
         * even when the suite is re-run locally against a stale store.
         *
         * Deleting the disk files is safe at this point: the in-memory store
         * (MemStore) is still empty before any test fetches a page, and
         * FileBackendPageStore recreates missing directories on demand.
         */
        @JvmStatic
        @BeforeAll
        fun cleanPageStore() {
            if (pageStoreCleaned.compareAndSet(false, true)) {
                runCatching {
                    val storeDir = AppPaths.LOCAL_STORAGE_DIR
                    if (Files.exists(storeDir)) {
                        Files.walk(storeDir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                    }
                    PulsarContexts.getOrCreate().globalCache.clearPDCaches()
                }.onFailure { it.printStackTrace() }
            }
        }

        /**
         * Stop the background task loop after all tests in the class.
         *
         * The loop is started on demand by loadAll/loadAllAsync (via
         * startLoopIfNecessary) and would otherwise poll the url pool forever,
         * holding the only Chrome browser (max browser number = 1) and its CDP
         * connections while the rest of the suite runs. Stopping it here
         * releases those resources; the next loadAll restarts the loop.
         *
         * The loop is stopped idempotently: it is a no-op if it is not running.
         */
        @JvmStatic
        @AfterAll
        fun stopTaskLoops() {
            runCatching { PulsarContexts.getOrCreate().taskLoops.stop() }
                .onFailure { it.printStackTrace() }
        }
    }
}
