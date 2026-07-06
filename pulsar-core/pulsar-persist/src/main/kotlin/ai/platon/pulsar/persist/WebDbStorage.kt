package ai.platon.pulsar.persist

import ai.platon.pulsar.persist.model.WebPageRecord
import java.nio.ByteBuffer

/**
 * Storage backend contract for [WebPageRecord] persistence.
 *
 * Implementations must be thread-safe. The lifecycle is managed by [WebDb].
 *
 * @see LocalWebDbStorage
 * @see MongoWebDbStorage
 */
interface WebDbStorage : AutoCloseable {

    /**
     * Reads the full [WebPageRecord] for the given key, or null if absent.
     */
    fun read(key: String): WebPageRecord?

    /**
     * Reads the raw content bytes for the given key, or null if absent.
     */
    fun readContent(key: String): ByteBuffer?

    /**
     * Writes a [WebPageRecord] to storage.
     *
     * @param record the record to persist
     * @param key the key identifying the page
     * @param replaceIfExists if false and the page already exists, no write occurs
     * @return true if the write succeeded, false if [replaceIfExists] is false and the page already exists
     */
    fun write(record: WebPageRecord, key: String, replaceIfExists: Boolean): Boolean

    /**
     * Checks whether a page exists for the given key.
     */
    fun exists(key: String): Boolean

    /**
     * Deletes the record for the given key.
     *
     * @return true if the page existed and was deleted
     */
    fun delete(key: String): Boolean

    /**
     * Removes all records from storage.
     *
     * @return true if the operation completed without errors
     */
    fun truncate(): Boolean

    /**
     * Flushes any pending writes. Default is no-op.
     */
    fun flush() = Unit

    /**
     * Releases any resources held by this storage backend. Default is no-op.
     */
    override fun close() = Unit
}
