package ai.platon.pulsar.persist

import ai.platon.pulsar.common.config.ImmutableConfig
import org.slf4j.LoggerFactory

/**
 * Factory for creating [WebDbStorage] instances based on configuration.
 *
 * Reads the `storage.backend` configuration key to determine which implementation to create:
 * - `local` (default): [LocalWebDbStorage] — filesystem + Avro serialization
 * - `mongodb`: [MongoWebDbStorage] — MongoDB-backed storage (enterprise edition)
 *
 * Usage:
 * ```kotlin
 * val storage = WebDbStorageFactory.create(conf)
 * val webDb = WebDb(conf, storage)
 * ```
 */
object WebDbStorageFactory {

    private val logger = LoggerFactory.getLogger(WebDbStorageFactory::class.java)

    /** Configuration key for selecting the storage backend. */
    const val CONFIG_KEY_BACKEND = "storage.backend"

    /** Backend type: local filesystem + Avro (standard edition). */
    const val BACKEND_LOCAL = "local"

    /** Backend type: MongoDB (enterprise edition). */
    const val BACKEND_MONGODB = "mongodb"

    /**
     * Creates the appropriate [WebDbStorage] implementation based on the
     * `storage.backend` configuration property.
     *
     * @param conf the application configuration
     * @return a [WebDbStorage] instance ready for use
     * @throws StorageException if the configured backend is unknown
     */
    fun create(conf: ImmutableConfig): WebDbStorage {
        val backend = conf.get(CONFIG_KEY_BACKEND, BACKEND_LOCAL).trim().lowercase()
        logger.info("Creating WebDbStorage backend: {}", backend)

        return when (backend) {
            BACKEND_LOCAL -> {
                logger.info("Using local filesystem storage (standard edition)")
                LocalWebDbStorage(conf)
            }
            BACKEND_MONGODB -> {
                logger.info("Using MongoDB storage (enterprise edition)")
                MongoWebDbStorage(conf)
            }
            else -> {
                throw StorageException(
                    "Unknown storage backend: '$backend'. " +
                    "Supported values: '$BACKEND_LOCAL', '$BACKEND_MONGODB'"
                )
            }
        }
    }
}
