package ai.platon.pulsar.persist.mongo

import ai.platon.pulsar.common.config.AppConstants
import ai.platon.pulsar.common.config.CapabilityTypes
import ai.platon.pulsar.common.config.MutableConfig
import ai.platon.pulsar.persist.MongoDataStorageFactory
import ai.platon.pulsar.persist.gora.generated.GWebPage
import org.apache.commons.lang3.RandomStringUtils
import org.apache.gora.mongodb.store.MongoStore
import org.apache.gora.mongodb.store.MongoStoreParameters.PROP_MONGO_SERVERS
import org.junit.jupiter.api.Assumptions
import kotlin.test.*

@Ignore("Only test when MongoDB is started")
class TestMongoStore : MongoTestBase() {
    companion object {
        private val crawlId = "test_" + RandomStringUtils.secure().nextAlphanumeric(5)
        private val conf = MutableConfig().also {
            it[CapabilityTypes.STORAGE_DATA_STORE_CLASS] = AppConstants.MONGO_STORE_CLASS
            it[CapabilityTypes.STORAGE_CRAWL_ID] = crawlId
        }
    }

    @BeforeTest
    fun ensureMongoDBAvailable() {
        val store = MongoStore<String, GWebPage>()
        assertNull(store.schemaName)

        val provider = MongoDataStorageFactory(conf)
        val store2 = provider.getOrCreatePageStore()

        Assumptions.assumeTrue(MongoDataStorageFactory.checkIfMongoClientAvailable(conf)) {
            "MongoDB is not available at ${conf[PROP_MONGO_SERVERS]}"
        }
        assertEquals(AppConstants.MONGO_STORE_CLASS, provider.storeClassName)
        assertTrue("Actual schema name: ${store2.schemaName}") { crawlId in store2.schemaName }
    }

    @Test
    fun testOperation() {
        val store = MongoDataStorageFactory(conf).getOrCreatePageStore()

        val key = System.currentTimeMillis().toString()
        store.put(key, GWebPage())
        // why ?
        // assertTrue { store.exists(key) }
        store.delete(key)
        assertFalse { store.exists(key) }
    }
}
