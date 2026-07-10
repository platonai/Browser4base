package ai.platon.pulsar.common.collect.queue

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.*
import java.util.concurrent.ConcurrentSkipListSet

open class ConcurrentNEntrantQueue<E>(
        val n: Int
): AbstractQueue<E>() {
    private val set = ConcurrentSkipListSet<E>()
    private val historyHash = ConcurrentHashMap<Int, AtomicInteger>()

    open fun count(e: E) = historyHash[e.hashCode()]?.get() ?: 0

    override fun add(e: E) = offer(e)

    override fun offer(e: E): Boolean {
        val hashCode = e.hashCode()

        synchronized(this) {
            if (historyHash[hashCode]?.get() ?: 0 <= n) {
                historyHash.computeIfAbsent(hashCode) { AtomicInteger() }.incrementAndGet()
                return set.add(e)
            }
        }

        return false
    }

    override fun iterator(): MutableIterator<E> = set.iterator()

    override fun peek(): E? = set.firstOrNull()

    override fun poll(): E? = set.pollFirst()

    override val size: Int get() = set.size
}
