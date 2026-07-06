package ai.platon.pulsar.persist

import ai.platon.pulsar.common.config.MutableConfig
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class WebDbStorageFactoryTest {

    @Test
    fun `test factory creates LocalWebDbStorage by default`() {
        val conf = MutableConfig()
        val storage = WebDbStorageFactory.create(conf)

        assertIs<LocalWebDbStorage>(storage, "Default backend should be LocalWebDbStorage")
    }

    @Test
    fun `test factory creates LocalWebDbStorage when backend is local`() {
        val conf = MutableConfig()
        conf.set(WebDbStorageFactory.CONFIG_KEY_BACKEND, "local")
        val storage = WebDbStorageFactory.create(conf)

        assertIs<LocalWebDbStorage>(storage, "Backend 'local' should create LocalWebDbStorage")
    }

    @Test
    fun `test factory creates MongoWebDbStorage when backend is mongodb`() {
        val conf = MutableConfig()
        conf.set(WebDbStorageFactory.CONFIG_KEY_BACKEND, "mongodb")
        val storage = WebDbStorageFactory.create(conf)

        assertIs<MongoWebDbStorage>(storage, "Backend 'mongodb' should create MongoWebDbStorage")
    }

    @Test
    fun `test factory throws for unknown backend`() {
        val conf = MutableConfig()
        conf.set(WebDbStorageFactory.CONFIG_KEY_BACKEND, "redis")

        assertThrows<StorageException> {
            WebDbStorageFactory.create(conf)
        }
    }

    @Test
    fun `test WebDb uses factory through default parameter`() {
        val conf = MutableConfig()
        val webDb = WebDb(conf)

        // Should work without specifying storage (uses default LocalWebDbStorage)
        assertFalse(webDb.exists("https://test.example.com"))
        webDb.close()
    }

    @Test
    fun `test WebDb with explicit MongoWebDbStorage`() {
        val conf = MutableConfig()
        val mongoStorage = MongoWebDbStorage(conf)
        val webDb = WebDb(conf, mongoStorage)

        // Calling methods on MongoWebDbStorage should throw NotImplementedError (TODO)
        assertFailsWith<NotImplementedError> {
            webDb.exists("https://test.example.com")
        }

        webDb.close()
    }

    @Test
    fun `test factory constants match expected values`() {
        assertEquals("storage.backend", WebDbStorageFactory.CONFIG_KEY_BACKEND)
        assertEquals("local", WebDbStorageFactory.BACKEND_LOCAL)
        assertEquals("mongodb", WebDbStorageFactory.BACKEND_MONGODB)
    }

    @Test
    fun `test MongoWebDbStorage config keys`() {
        assertEquals("storage.mongodb.uri", MongoWebDbStorage.CONFIG_KEY_MONGO_URI)
        assertEquals("storage.mongodb.database", MongoWebDbStorage.CONFIG_KEY_MONGO_DATABASE)
        assertEquals("storage.mongodb.collection", MongoWebDbStorage.CONFIG_KEY_MONGO_COLLECTION)
    }
}
