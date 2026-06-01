package ai.platon.pulsar.persist

import ai.platon.gora.mongodb.store.MongoStoreParameters.PROP_MONGO_SERVERS
import ai.platon.gora.persistency.Persistent
import ai.platon.gora.store.DataStore
import ai.platon.gora.util.GoraException
import ai.platon.pulsar.common.AppContext
import ai.platon.pulsar.common.IllegalApplicationStateException
import ai.platon.pulsar.common.ResourceLoader
import ai.platon.pulsar.common.Runtimes
import ai.platon.pulsar.common.config.AppConstants.FILE_BACKEND_STORE_CLASS
import ai.platon.pulsar.common.config.AppConstants.MONGO_STORE_CLASS
import ai.platon.pulsar.common.config.CapabilityTypes.STORAGE_DATA_STORE_CLASS
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.persist.gora.GoraStorage
import ai.platon.pulsar.persist.gora.generated.GWebPage
import ai.platon.pulsar.persist.mongo.MongoDBUtils
import org.apache.commons.lang3.SystemUtils
import org.slf4j.LoggerFactory

/**
 * Created by Vincent on 19-1-19.
 * Copyright @ 2013-2019 Platon AI. All rights reserved
 */
class MongoDataStorageFactory(val conf: ImmutableConfig) {
    private val pageStoreClass: Class<out DataStore<String, GWebPage>> get() = detectDataStoreClass(conf)

    private var _dataStore: DataStore<String, GWebPage>? = null

    val storeClassName: String get() = detectDataStoreClassName(conf)

    @get:Synchronized
    val schemaName: String get() = _dataStore?.schemaName ?: "(unknown, not initialized)"

    @Synchronized
    fun isInitialized() = _dataStore != null

    @Synchronized
    fun schemaAvailable() = runCatching { _dataStore?.schemaExists() == true }.getOrNull() ?: false

    @Synchronized
    fun getOrCreatePageStore(): DataStore<String, GWebPage> {
        if (_dataStore == null) {
            _dataStore = createPageStore0()
        }
        return _dataStore!!
    }

    private fun createPageStore0(): DataStore<String, GWebPage> {
        if (!AppContext.isActive) {
            throw IllegalApplicationStateException("Inactive application context")
        }

        val pageStore = GoraStorage.createDataStore(conf, String::class.java, GWebPage::class.java, pageStoreClass)
        logger.debug("Backend data store is created: {}, realSchema: {}", pageStoreClass.name, pageStore.schemaName)
        return pageStore
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MongoDataStorageFactory::class.java)

        fun checkIfMongoClientAvailable(conf: ImmutableConfig): Boolean {
            val mongoServers = conf[PROP_MONGO_SERVERS]
            if (mongoServers != null) {
                return MongoDBUtils.isMongoReachable(mongoServers)
            }
            return ResourceLoader.exists("gora-mongodb-mapping.xml")
        }

        /**
         * Return the DataStore persistent class used to persist WebPage.
         *
         * @param conf AppConstants configuration
         * @return the DataStore persistent class
         */
        fun detectDataStoreClassName(conf: ImmutableConfig): String {
            if (!AppContext.isActive) {
                throw IllegalApplicationStateException("Inactive application context")
            }

            val specified = conf[STORAGE_DATA_STORE_CLASS]
            if (specified != null) {
                return specified
            }

            patchGoraMongoServersConfig(conf)
            val mongoServers = conf[PROP_MONGO_SERVERS]
            if (mongoServers != null) {
                return MONGO_STORE_CLASS
            }

            var dataStoreClass = when {
                SystemUtils.IS_OS_WINDOWS -> when {
                    Runtimes.checkIfProcessRunning(".*mongod.exe .+") -> MONGO_STORE_CLASS
                    else -> FILE_BACKEND_STORE_CLASS
                }

                SystemUtils.IS_OS_LINUX -> when {
                    Runtimes.checkIfProcessRunning(".+/usr/bin/mongod .+") -> MONGO_STORE_CLASS
                    else -> FILE_BACKEND_STORE_CLASS
                }

                else -> FILE_BACKEND_STORE_CLASS
            }

            /**
             * Sometimes MongoClient is not available or not configured
             * */
            if (MONGO_STORE_CLASS == dataStoreClass && !checkIfMongoClientAvailable(conf)) {
                logger.info("MongoDB is running but mongo client is not available, fallback to FileBackendPageStore")
                dataStoreClass = FILE_BACKEND_STORE_CLASS
            }

            return dataStoreClass
        }

        /**
         * Return the DataStore persistent class used to persist webpages.
         *
         * @param conf AppConstants configuration
         * @return the DataStore persistent class
         */
        @Suppress("UNCHECKED_CAST")
        @Throws(ClassNotFoundException::class)
        fun <K, V : Persistent> detectDataStoreClass(conf: ImmutableConfig): Class<out DataStore<K, V>> {
            return Class.forName(detectDataStoreClassName(conf)) as Class<out DataStore<K, V>>
        }

        /**
         * Patches the MongoDB servers configuration for Gora.
         * Enable environment variable or system property `GORA_MONGODB_SERVERS`
         */
        @Throws(GoraException::class)
        fun patchGoraMongoServersConfig(conf: ImmutableConfig) {
            // Keep this assertion to remind us the real property name
            require("gora.mongodb.servers" == PROP_MONGO_SERVERS)

            var servers = System.getProperty("GORA_MONGODB_SERVERS")
            if (servers == null) {
                servers = System.getenv("GORA_MONGODB_SERVERS")
            }
            if (servers == null) {
                servers = System.getProperty(PROP_MONGO_SERVERS)
            }
            if (servers == null) {
                servers = System.getenv(PROP_MONGO_SERVERS)
            }
            if (servers == null) {
                servers = conf[PROP_MONGO_SERVERS]
            }

            if (servers != null) {
                GoraStorage.goraProperties.setProperty(PROP_MONGO_SERVERS, servers)
                conf.unbox()[PROP_MONGO_SERVERS] = servers
            }
        }
    }
}
