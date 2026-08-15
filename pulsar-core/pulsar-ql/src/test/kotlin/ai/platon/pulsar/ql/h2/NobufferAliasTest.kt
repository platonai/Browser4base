package ai.platon.pulsar.ql.h2

import ai.platon.pulsar.ql.common.annotation.H2Context
import ai.platon.pulsar.ql.h2.utils.QueueRowSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.h2.engine.Constants
import org.h2.ext.pulsar.PulsarExtension
import org.h2.jdbc.JdbcConnection
import org.h2.tools.SimpleResultSet
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DisplayName
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Types
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Streaming functions registered as H2 aliases to verify NOBUFFER behavior.
 */
object NobufferStreamingTestFunctions {

    internal const val TOTAL_ROWS = 100

    internal val producedCount = AtomicInteger()
    internal val completedAllRows = AtomicBoolean()

    internal fun reset() {
        producedCount.set(0)
        completedAllRows.set(false)
    }

    @JvmStatic
    fun slowStreamingNumbers(@H2Context conn: JdbcConnection): ResultSet {
        // H2 invokes the function once with a column-list connection to learn the result columns.
        // Do not start the producer in that pass.
        if (conn.metaData.url.contains(Constants.CONN_URL_COLUMNLIST)) {
            val rs = SimpleResultSet()
            rs.addColumn("N", Types.INTEGER, 10, 0)
            return rs
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val rowSource = QueueRowSource(capacity = 4, onClose = { scope.cancel() })
        val rs = SimpleResultSet(rowSource)
        rs.addColumn("N", Types.INTEGER, 10, 0)

        scope.launch {
            try {
                repeat(TOTAL_ROWS) { i ->
                    producedCount.incrementAndGet()
                    rowSource.put(arrayOf<Any?>(i + 1))
                    delay(10)
                }
                completedAllRows.set(true)
                rowSource.finish()
            } catch (e: CancellationException) {
                rowSource.abort()
                throw e
            } catch (e: Exception) {
                rowSource.fail(e)
                scope.cancel()
            }
        }
        return rs
    }
}

@DisplayName("NOBUFFER alias registration")
class NobufferAliasTest {

    companion object {
        /**
         * This class intentionally opens raw DriverManager H2 connections. When it runs first in
         * the module JVM (surefire filesystem order), the `h2.sessionFactory` system property is
         * not set yet (it is set by AbstractSQLContext's initializer), so PulsarExtension caches
         * the plain `org.h2.engine.Engine` in a static field for the whole JVM lifetime. Every
         * later test would then get plain H2 sessions: no SQLSession, no UDF registration, and no
         * PulsarObjectSerializer - e.g. TestSQLFeatures fails with `Function "EXPLODE" not found`
         * and TestJavaObjectSerializer fails with NotSerializableException.
         *
         * Reset the cached engine after this class so the next session creation re-reads the
         * property and picks ai.platon.pulsar.ql.h2.H2SessionFactory.
         * */
        @JvmStatic
        @AfterAll
        fun resetPulsarEngineCache() {
            PulsarExtension.sessionFactory = null
        }
    }

    @Test
    @DisplayName("createAliasSql emits NOBUFFER and DETERMINISTIC when requested")
    fun testCreateAliasSqlEmitsModifiers() {
        val plain = createAliasSql(
            "DOM_LOAD_ALL_AND_SELECT",
            "ai.platon.pulsar.ql.h2.udfs.DomFunctionTables.loadAllAndSelect",
            nobuffer = true,
            deterministic = false
        )
        assertEquals(
            "CREATE ALIAS IF NOT EXISTS DOM_LOAD_ALL_AND_SELECT NOBUFFER " +
                "FOR \"ai.platon.pulsar.ql.h2.udfs.DomFunctionTables.loadAllAndSelect\"",
            plain
        )

        val deterministic = createAliasSql(
            "SHOW_LOAD_OPTIONS",
            "ai.platon.pulsar.ql.h2.udfs.CommonFunctionTables.showLoadOptions",
            nobuffer = true,
            deterministic = true
        )
        assertEquals(
            "CREATE ALIAS IF NOT EXISTS SHOW_LOAD_OPTIONS DETERMINISTIC NOBUFFER " +
                "FOR \"ai.platon.pulsar.ql.h2.udfs.CommonFunctionTables.showLoadOptions\"",
            deterministic
        )
    }

    @Test
    @DisplayName("NOBUFFER alias streams lazily: LIMIT stops the producer early")
    fun testNobufferAliasStopsProducerUnderLimit() {
        NobufferStreamingTestFunctions.reset()

        DriverManager.getConnection("jdbc:h2:mem:nobuffer_streaming").use { conn ->
            conn.createStatement().use { stat ->
                stat.execute(
                    createAliasSql(
                        "SLOW_STREAMING_NUMBERS",
                        "ai.platon.pulsar.ql.h2.NobufferStreamingTestFunctions.slowStreamingNumbers",
                        nobuffer = true,
                        deterministic = false
                    )
                )

                stat.executeQuery("SELECT * FROM SLOW_STREAMING_NUMBERS() LIMIT 3").use { rs ->
                    val values = mutableListOf<Int>()
                    while (rs.next()) {
                        values += rs.getInt(1)
                    }
                    assertEquals(listOf(1, 2, 3), values)
                }
            }
        }

        // H2 consumed only the first rows and closed the result set, cancelling the producer.
        assertFalse(
            NobufferStreamingTestFunctions.completedAllRows.get(),
            "the producer should be cancelled before emitting all rows"
        )
        assertTrue(
            NobufferStreamingTestFunctions.producedCount.get() < NobufferStreamingTestFunctions.TOTAL_ROWS,
            "produced=${NobufferStreamingTestFunctions.producedCount.get()} should be below " +
                NobufferStreamingTestFunctions.TOTAL_ROWS
        )
    }

    @Test
    @DisplayName("buffered alias (no NOBUFFER) drains the whole stream even under LIMIT")
    fun testBufferedAliasMaterializesAllRowsUnderLimit() {
        NobufferStreamingTestFunctions.reset()

        DriverManager.getConnection("jdbc:h2:mem:nobuffer_buffered").use { conn ->
            conn.createStatement().use { stat ->
                stat.execute(
                    "CREATE ALIAS IF NOT EXISTS SLOW_STREAMING_NUMBERS " +
                        "FOR \"ai.platon.pulsar.ql.h2.NobufferStreamingTestFunctions.slowStreamingNumbers\""
                )

                stat.executeQuery("SELECT * FROM SLOW_STREAMING_NUMBERS() LIMIT 3").use { rs ->
                    val values = mutableListOf<Int>()
                    while (rs.next()) {
                        values += rs.getInt(1)
                    }
                    assertEquals(listOf(1, 2, 3), values)
                }
            }
        }

        // H2 buffered the whole result set into a LocalResult before applying LIMIT.
        assertTrue(NobufferStreamingTestFunctions.completedAllRows.get())
        assertEquals(NobufferStreamingTestFunctions.TOTAL_ROWS, NobufferStreamingTestFunctions.producedCount.get())
    }
}
