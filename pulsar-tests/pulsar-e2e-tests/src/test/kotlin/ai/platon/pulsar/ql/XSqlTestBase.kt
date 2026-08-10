package ai.platon.pulsar.ql

import ai.platon.pulsar.MockSiteAccess
import ai.platon.pulsar.common.browser.BrowserType
import ai.platon.pulsar.ql.context.SQLContext
import ai.platon.pulsar.skeleton.common.options.LoadOptionDefaults
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration

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

    companion object {
        init {
            LoadOptionDefaults.apply {
                // Force a real fetch on every run so tests are deterministic regardless
                // of what was persisted to the local page store by previous runs.
                expires = Duration.ZERO
                parse = true
                ignoreFailure = true
                nJitRetry = 3
                test = 1
                browser = BrowserType.PULSAR_CHROME
            }
        }
    }
}
