package ai.platon.pulsar.ql.h2.utils

import kotlinx.coroutines.runBlocking
import org.h2.tools.SimpleResultSet
import org.junit.jupiter.api.DisplayName
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Static functions registered as H2 aliases in [H2StreamingRowSourceTest].
 */
object H2StreamingRowSourceFunctions {

    @JvmStatic
    fun streamNumbers(): ResultSet {
        val rowSource = QueueRowSource()
        runBlocking {
            repeat(3) { i -> rowSource.put(arrayOf<Any?>(i + 1)) }
            rowSource.finish()
        }

        val rs = SimpleResultSet(rowSource)
        rs.addColumn("N", Types.INTEGER, 10, 0)
        return rs
    }
}

@DisplayName("H2 streaming row source compatibility")
class H2StreamingRowSourceTest {

    @Test
    fun testH2ConsumesStreamingResultSet() {
        DriverManager.getConnection("jdbc:h2:mem:streaming").use { conn ->
            conn.createStatement().use { stat ->
                stat.execute(
                    "CREATE ALIAS STREAM_NUMBERS FOR " +
                        "\"ai.platon.pulsar.ql.h2.utils.H2StreamingRowSourceFunctions.streamNumbers\""
                )

                stat.executeQuery("SELECT * FROM STREAM_NUMBERS()").use { rs ->
                    val values = mutableListOf<Int>()
                    while (rs.next()) {
                        values += rs.getInt(1)
                    }
                    assertEquals(listOf(1, 2, 3), values)
                }
            }
        }
    }
}
