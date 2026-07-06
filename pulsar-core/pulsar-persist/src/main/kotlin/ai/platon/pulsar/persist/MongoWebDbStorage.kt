package ai.platon.pulsar.persist

import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.persist.model.WebPageRecord
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

/**
 * MongoDB storage backend for [WebPageRecord] persistence.
 *
 * This is an enterprise edition feature. In the standard edition, this class is not
 * used — [LocalWebDbStorage] is the default.
 *
 * Connection configuration:
 * - `storage.mongodb.uri` — MongoDB connection URI (default: mongodb://localhost:27017)
 * - `storage.mongodb.database` — Database name (default: browser4)
 * - `storage.mongodb.collection` — Collection name (default: webpages)
 *
 * Thread safety: MongoDB driver operations are thread-safe.
 */
class MongoWebDbStorage(private val conf: ImmutableConfig) : WebDbStorage {

    private val logger = LoggerFactory.getLogger(MongoWebDbStorage::class.java)

    // Configuration keys
    companion object {
        const val CONFIG_KEY_MONGO_URI = "storage.mongodb.uri"
        const val CONFIG_KEY_MONGO_DATABASE = "storage.mongodb.database"
        const val CONFIG_KEY_MONGO_COLLECTION = "storage.mongodb.collection"

        const val DEFAULT_MONGO_URI = "mongodb://localhost:27017"
        const val DEFAULT_MONGO_DATABASE = "browser4"
        const val DEFAULT_MONGO_COLLECTION = "webpages"
    }

    private val mongoUri: String = conf.get(CONFIG_KEY_MONGO_URI, DEFAULT_MONGO_URI)
    private val databaseName: String = conf.get(CONFIG_KEY_MONGO_DATABASE, DEFAULT_MONGO_DATABASE)
    private val collectionName: String = conf.get(CONFIG_KEY_MONGO_COLLECTION, DEFAULT_MONGO_COLLECTION)

    init {
        logger.info("MongoWebDbStorage configured: uri={}, db={}, collection={}",
            mongoUri, databaseName, collectionName)
    }

    override fun read(key: String): WebPageRecord? {
        TODO("Enterprise feature — MongoDB read not yet implemented")
    }

    override fun readContent(key: String): ByteBuffer? {
        TODO("Enterprise feature — MongoDB readContent not yet implemented")
    }

    override fun write(record: WebPageRecord, key: String, replaceIfExists: Boolean): Boolean {
        TODO("Enterprise feature — MongoDB write not yet implemented")
    }

    override fun exists(key: String): Boolean {
        TODO("Enterprise feature — MongoDB exists not yet implemented")
    }

    override fun delete(key: String): Boolean {
        TODO("Enterprise feature — MongoDB delete not yet implemented")
    }

    override fun truncate(): Boolean {
        TODO("Enterprise feature — MongoDB truncate not yet implemented")
    }

    override fun close() {
        // TODO: Close MongoDB client connection when implemented
        logger.info("MongoWebDbStorage closed")
    }
}
