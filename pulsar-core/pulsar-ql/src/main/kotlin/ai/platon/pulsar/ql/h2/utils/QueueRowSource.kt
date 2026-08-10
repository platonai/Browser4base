package ai.platon.pulsar.ql.h2.utils

import kotlinx.coroutines.delay
import org.h2.tools.SimpleRowSource
import java.sql.SQLException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.AtomicReference

/**
 * A [SimpleRowSource] that streams rows produced asynchronously from a bounded queue.
 *
 * Combined with [org.h2.tools.SimpleResultSet], this allows a table-valued H2 UDF to
 * stream rows to the caller without materializing the whole result set in memory.
 * Producers enqueue rows via [put] while H2 consumes them via [readRow], so the memory
 * footprint is bounded by the queue capacity plus the rows currently in flight.
 *
 * The row source is forward-only: [reset] is not supported.
 */
class QueueRowSource(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val onClose: (() -> Unit)? = null,
) : SimpleRowSource {

    private val queue: BlockingQueue<Any?> = ArrayBlockingQueue(capacity)
    private val error = AtomicReference<Throwable?>(null)
    private val endMarker = Any()

    @Volatile
    private var ended = false

    @Volatile
    private var closed = false

    /**
     * Enqueue a row, applying back pressure when the queue is full.
     *
     * This method is cancellation-safe: it yields while waiting for space, so the
     * calling coroutine can be cancelled (for example when the result set is closed).
     */
    suspend fun put(row: Array<Any?>) {
        while (!closed) {
            if (queue.offer(row)) {
                return
            }
            delay(10)
        }
    }

    /**
     * Signal that no more rows are coming. Idempotent and cancellation-safe.
     */
    suspend fun finish() {
        while (!closed) {
            if (queue.offer(endMarker)) {
                ended = true
                return
            }
            delay(10)
        }
    }

    /**
     * Signal that the producer failed. The exception is rethrown to the consumer
     * as a [SQLException] by [readRow].
     */
    fun fail(e: Throwable) {
        error.set(e)
        ended = true
        queue.offer(endMarker)
    }

    /**
     * Abort the stream without emitting an end marker. Used when the producing
     * coroutines are cancelled, for example because the result set was closed early.
     */
    fun abort() {
        ended = true
        closed = true
    }

    override fun readRow(): Array<Any?>? {
        val t = error.get()
        if (t != null) {
            throw SQLException("Failed to stream rows: ${t.message}", t)
        }
        if (ended && queue.isEmpty()) {
            return null
        }

        val value = queue.take()
        if (value === endMarker) {
            error.get()?.let {
                throw SQLException("Failed to stream rows: ${it.message}", it)
            }
            return null
        }

        @Suppress("UNCHECKED_CAST")
        return value as Array<Any?>
    }

    override fun close() {
        closed = true
        ended = true
        onClose?.invoke()
    }

    override fun reset() {
        throw SQLException("Queue row source is forward-only and cannot be reset")
    }

    companion object {
        /**
         * The default number of rows buffered between producers and the H2 consumer.
         */
        const val DEFAULT_CAPACITY = 32
    }
}
