package ai.platon.pulsar.ql.h2.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@DisplayName("QueueRowSource")
class QueueRowSourceTest {

    @Test
    fun testStreamsRowsInOrderFromProducer() = runBlocking {
        val source = QueueRowSource()
        val producer = launch(Dispatchers.Default) {
            repeat(5) { i -> source.put(arrayOf<Any?>(i + 1)) }
            source.finish()
        }

        val rows = generateSequence { source.readRow() }.toList()
        producer.join()

        assertEquals(listOf(1, 2, 3, 4, 5), rows.map { it[0] as Int })
        assertNull(source.readRow())
    }

    @Test
    fun testStreamsMultiColumnRows() = runBlocking {
        val source = QueueRowSource()
        val producer = launch(Dispatchers.Default) {
            source.put(arrayOf<Any?>("a", 1))
            source.put(arrayOf<Any?>("b", 2))
            source.finish()
        }

        val rows = generateSequence { source.readRow() }.toList()
        producer.join()

        assertEquals(listOf("a" to 1, "b" to 2), rows.map { it[0] as String to it[1] as Int })
    }

    @Test
    fun testProducerFailureIsRethrownAsSqlException() = runBlocking {
        val source = QueueRowSource()
        source.fail(RuntimeException("boom"))

        val e = assertFailsWith<SQLException> { source.readRow() }
        assertEquals("boom", e.cause?.message)
    }

    @Test
    fun testCloseAllowsPutToReturn() = runBlocking {
        val source = QueueRowSource(capacity = 1)
        source.put(arrayOf<Any?>(1))

        source.close()

        // put must not block forever once the row source is closed
        source.put(arrayOf<Any?>(2))
    }

    @Test
    fun testAbortStopsPendingRows() {
        val source = QueueRowSource()
        source.abort()
        assertNull(source.readRow())
    }

    @Test
    fun testResetIsNotSupported() {
        val source = QueueRowSource()
        assertFailsWith<SQLException> { source.reset() }
    }
}
