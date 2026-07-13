package ai.platon.pulsar.ql

import ai.platon.pulsar.boot.autoconfigure.PulsarAutoConfiguration
import ai.platon.pulsar.common.browser.BrowserType
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.common.printlnPro
import ai.platon.pulsar.common.sql.ResultSetFormatter
import ai.platon.pulsar.ql.context.SQLContext
import ai.platon.pulsar.skeleton.common.options.LoadOptionDefaults
import ai.platon.pulsar.util.server.EnableMockServerApplication
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import java.sql.ResultSet
import java.time.Duration
import java.util.*

/**
 * Base class for QL integration tests that start the mock server
 * and provide an H2 SQL context with all UDFs registered.
 *
 * Follows the pattern from [ai.platon.pulsar.heavy.ql.TestBase]
 * but uses the Spring-managed application context for the mock server.
 */
@SpringBootTest(
    classes = [EnableMockServerApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT
)
@Import(PulsarAutoConfiguration::class)
open class QlIntegrationTestBase {

    companion object {
        init {
            /**
             * Load options are in webpage scope, so they should be initialized
             * after PulsarContextInitializer.
             */
            LoadOptionDefaults.apply {
                expires = Duration.ofSeconds(30)
                parse = true
                ignoreFailure = true
                nJitRetry = 3
                test = 1
                browser = BrowserType.PULSAR_CHROME
            }
        }

        val history = mutableListOf<String>()
    }

    val logger = getLogger(this)

    @Autowired
    lateinit var applicationContext: ApplicationContext

    /**
     * The SQL context wrapping the Spring application context.
     * All UDFs (DOM_*, LOAD_*, etc.) are registered in the H2 database.
     */
    @Autowired
    lateinit var sqlContext: SQLContext

    val session get() = sqlContext.getOrCreateSession()

    /** Port the mock server is listening on (matches server.port=17080 in application.properties). */
    open val port: Int get() = 17080

    // -- Mock server URLs ----------------------------------------------------

    val baseURL get() = "http://127.0.0.1:$port"

    val assetsBaseURL get() = "$baseURL/assets"

    val generatedAssetsBaseURL get() = "$baseURL/generated"

    /** Simple DOM test page: contains div, input, textarea, select elements. */
    val domPageUrl get() = "$assetsBaseURL/dom.html"

    /** Form test page: contains form elements with data-testid attributes. */
    val formPageUrl get() = "$assetsBaseURL/test-pages/form-page.html"

    /** EC category page (Electronics) with product cards, links, and structured data. */
    val ecCategoryUrl get() = "$baseURL/ec/b?node=1292115012"

    /** EC product detail page (4K OLED TV). */
    val ecProductUrl get() = "$baseURL/ec/dp/B0E000001"

    /** Another EC product (Wireless Headphones). */
    val ecProductUrl2 get() = "$baseURL/ec/dp/B0E000002"

    /** Amazon home page copy. */
    val amazonHomeUrl get() = "$baseURL/amazon/home.htm"

    /** Simple text/plain endpoint. */
    val textUrl get() = "$baseURL/text"

    /** Simple JSON endpoint. */
    val jsonUrl get() = "$baseURL/json"

    // -- Helper methods (mirror ai.platon.pulsar.heavy.ql.TestBase) ---------

    /**
     * Execute a SQL statement and optionally print the result.
     */
    fun execute(sql: String, printResult: Boolean = true) {
        sqlContext.run { connection ->
            connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)
                .use { stat ->
                    val regex = "^(SELECT|CALL).+".toRegex()
                    val normalized = sql.uppercase(Locale.getDefault())
                        .filter { it != '\n' }
                        .trimIndent()
                    if (normalized.matches(regex)) {
                        val rs = stat.executeQuery(sql)
                        if (printResult) {
                            printlnPro(ResultSetFormatter(rs, withHeader = true))
                        }
                    } else {
                        val r = stat.execute(sql)
                        if (printResult) {
                            printlnPro(r)
                        }
                    }
                    history.add("${sql.trim { it.isWhitespace() }};")
                }
        }
    }

    /**
     * Execute a SQL query and perform an action on the result set.
     */
    fun query(sql: String, action: (ResultSet) -> Unit): ResultSet {
        return sqlContext.runQuery { connection ->
            val stat = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)
            val rs = stat.executeQuery(sql)
            action(rs)
            history.add("${sql.trim { it.isWhitespace() }};")
            rs.beforeFirst()
            rs
        }
    }

    /**
     * Execute a SQL query and optionally print the result.
     */
    fun query(sql: String, printResult: Boolean = true): ResultSet {
        return sqlContext.runQuery { connection ->
            val stat = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)
            val rs = stat.executeQuery(sql)
            if (printResult) {
                printlnPro(ResultSetFormatter(rs, withHeader = true))
            }
            history.add("${sql.trim { it.isWhitespace() }};")
            rs.beforeFirst()
            rs
        }
    }
}
