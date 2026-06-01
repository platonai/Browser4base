package ai.platon.pulsar.persist.mongo

import ai.platon.pulsar.common.printlnPro
import com.mongodb.client.model.Filters
import org.bson.Document
import org.junit.jupiter.api.DisplayName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The driver com.mongodb.MongoClient is a Legacy Driver.
 * Introduced In: MongoDB Java driver 3.x and earlier.
 *
 * The driver com.mongodb.client.MongoClient is a New Driver.
 * Introduced in: MongoDB Java Driver 3.7 and later.
 * */
class MongoClientNewTest : MongoTestBase() {

    @Test
    fun testGetCollectionName() {
        // Use the database (for example, get a collection)
        printlnPro("Connected to the database: " + database.name)
        assertNotNull(database)
        assertEquals(databaseName, database.name)
    }

    @Test
        @DisplayName("when querying a document using cursor then it works")
    fun whenQueryingADocumentUsingCursorThenItWorks() {
        val document: Document = Document("name", "John Doe")
            .append("age", 30)
            .append("city", "New York")

        collection.insertOne(document)

        val cursor = collection.find().iterator()

        cursor.use {
            while (it.hasNext()) {
                val json = it.next().toJson()
                printlnPro(json)
                assertTrue { json.contains("New York") }
            }
        }
    }

    @Test
        @DisplayName("when inserting a document into MongoDB then it should be inserted successfully")
    fun whenInsertingADocumentIntoMongodbThenItShouldBeInsertedSuccessfully() {
        val document: Document = Document("name", "John Doe")
            .append("age", 30)
            .append("city", "New York")

        collection.insertOne(document)
        printlnPro("Document inserted")
    }

    @Test
        @DisplayName("when deleting a document then it should be deleted successfully")
    fun whenDeletingADocumentThenItShouldBeDeletedSuccessfully() {
        collection.deleteOne(Filters.eq("name", "John Doe"))
        printlnPro("Document deleted")
    }
}
