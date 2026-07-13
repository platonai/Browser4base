package ai.platon.pulsar.common.serialize.json

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.time.LocalDateTime
import org.junit.jupiter.api.DisplayName

class PulsarObjectMapperTest {

    @Test
    @DisplayName("test ObjectMapper configuration")
    fun testObjectmapperConfiguration() {
        val objectMapper = pulsarObjectMapper()

        assertFalse(objectMapper.serializationConfig.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
        assertTrue(objectMapper.factory.isEnabled(JsonParser.Feature.ALLOW_TRAILING_COMMA))
        assertTrue(objectMapper.factory.isEnabled(JsonParser.Feature.ALLOW_SINGLE_QUOTES))
        assertTrue(objectMapper.factory.isEnabled(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS))
        assertFalse(objectMapper.deserializationConfig.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
        assertTrue(objectMapper.deserializationConfig.isEnabled(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT))
        assertTrue(objectMapper.registeredModuleIds.contains(JavaTimeModule().typeId))

        assertEquals(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY,
            objectMapper.serializationConfig.defaultPropertyInclusion.valueInclusion
        )
    }

    @Test
    @DisplayName("test date serialization without timestamp")
    fun testDateSerializationWithoutTimestamp() {
        val objectMapper = pulsarObjectMapper()
        val date = LocalDateTime.of(2023, 1, 1, 12, 0)
        val json = objectMapper.writeValueAsString(date)

        assertFalse(json.contains("\"timestamp\""))
    }

    @Test
    @DisplayName("test JSON parsing with trailing comma")
    fun testJsonParsingWithTrailingComma() {
        val objectMapper = pulsarObjectMapper()
        val json = """{"name": "John", "age": 30,}"""
        val map = objectMapper.readValue(json, Map::class.java)

        assertEquals("John", map["name"])
        assertEquals(30, map["age"])
    }

    @Test
    @DisplayName("test JSON parsing with single quotes")
    fun testJsonParsingWithSingleQuotes() {
        val objectMapper = pulsarObjectMapper()
        val json = "{'name': 'John', 'age': 30}"
        val map = objectMapper.readValue(json, Map::class.java)

        assertEquals("John", map["name"])
        assertEquals(30, map["age"])
    }

    @Test
    @DisplayName("test JSON parsing with unquoted control chars")
    fun testJsonParsingWithUnquotedControlChars() {
        val objectMapper = pulsarObjectMapper()
        val json = "{\"name\": \"John\", \"age\": 30, \"description\": \"This is a test\n\"}"
        val map = objectMapper.readValue(json, Map::class.java)

        assertEquals("John", map["name"])
        assertEquals(30, map["age"])
        assertEquals("This is a test\n", map["description"])
    }

    @Test
    @DisplayName("test deserialization with unknown properties")
    fun testDeserializationWithUnknownProperties() {
        val objectMapper = pulsarObjectMapper()
        val json = """{"name": "John", "age": 30}"""
        val map = objectMapper.readValue(json, Map::class.java)

        assertEquals("John", map["name"])
        assertEquals(30, map["age"])
        assertNull(map["unknown"])
    }

    @Test
    @DisplayName("test JavaTimeModule registration")
    fun testJavatimemoduleRegistration() {
        val objectMapper = pulsarObjectMapper()
        val date = LocalDateTime.of(2023, 1, 1, 12, 0)
        val json = objectMapper.writeValueAsString(date)
        val deserializedDate = objectMapper.readValue(json, LocalDateTime::class.java)

        assertEquals(date, deserializedDate)
    }

    @Test
    @DisplayName("test extract json with special chars")
    fun testExtractJsonWithSpecialChars() {
        val json = """
            {
              "product_name": "Huawei P60 Pro Dual SIM 8GB + 256GB Global Model MNA-LX9 Factory Unlocked Mobile Cellphone - Black",
              "price": "$595.00",
              "ratings": "3.6 out of 5 stars (36 ratings)"
            }
        """.trimIndent()

        val obj: Map<String, Any?> = pulsarObjectMapper().readValue(json)
        assertEquals("Huawei P60 Pro Dual SIM 8GB + 256GB Global Model MNA-LX9 Factory Unlocked Mobile Cellphone - Black", obj["product_name"])
        assertEquals("$595.00", obj["price"])
        assertEquals("3.6 out of 5 stars (36 ratings)", obj["ratings"])
    }

    @Test
    @DisplayName("pulsarObjectMapper formats doubles in containers")
    fun pulsarobjectmapperFormatsDoublesInContainers() {
        val objectMapper = pulsarObjectMapper()
        val listNum: List<Number> = listOf(1.234, 1.0, 2.5)
        val mapAny: Map<String, Any> = mapOf("x" to 1.234, "y" to 1.0, "z" to 2.5)

        assertEquals("[1.23,1,2.5]", objectMapper.writeValueAsString(listNum))
        assertEquals("{\"x\":1.23,\"y\":1,\"z\":2.5}", objectMapper.writeValueAsString(mapAny))
    }
}

/**
 * Tests for the [PulsarJackson] singleton and its convenience methods.
 *
 * Covers: toJson, toPrettyJson, toYaml, their null-safe / fallback variants,
 * readTree/readTreeOrNull, and the Pson typealias.
 */
class PulsarJacksonTest {

    // ── mapper reuse ──────────────────────────────────────────────

    @Test
    @DisplayName("PulsarJackson reuses ObjectMapper singletons via lazy init")
    fun reusesObjectMapperSingletonsViaLazyInit() {
        // Multiple calls produce consistent results — the lazy mapper is reused
        assertEquals("\"hello\"", PulsarJackson.toJson("hello"))
        assertEquals("\"world\"", PulsarJackson.toJson("world"))

        // Same for YAML
        val yaml1 = PulsarJackson.toYaml("a")
        val yaml2 = PulsarJackson.toYaml("b")
        assertFalse(yaml1.startsWith("---"))
        assertFalse(yaml2.startsWith("---"))
    }

    // ── toJson ────────────────────────────────────────────────────

    @Test
    @DisplayName("toJson with vararg pairs")
    fun toJsonWithVarargPairs() {
        val json = PulsarJackson.toJson("a" to 1, "b" to "hello")
        assertEquals("{\"a\":1,\"b\":\"hello\"}", json)
    }

    @Test
    @DisplayName("toJson with map formats doubles")
    fun toJsonWithMapFormatsDoubles() {
        val json = PulsarJackson.toJson(mapOf("x" to 1.0, "y" to 2.5))
        assertEquals("{\"x\":1,\"y\":2.5}", json)
    }

    @Test
    @DisplayName("toJson propagates Jackson exception on serialization failure")
    fun toJsonPropagatesJacksonExceptionOnSerializationFailure() {
        // An object with a property getter that throws causes Jackson to fail
        // during serialization. PulsarJackson propagates the exception rather
        // than masking it with an NPE.
        class ThrowingBean {
            val ok: String = "fine"
            val bad: String get() = throw RuntimeException("deliberate serialization failure")
        }
        try {
            PulsarJackson.toJson(ThrowingBean())
            fail("Expected an exception from toJson")
        } catch (e: Exception) {
            // Exception is propagated — not swallowed as an NPE
            assertTrue(e.message?.contains("deliberate") == true || e.cause?.message?.contains("deliberate") == true,
                "Expected exception chain to mention 'deliberate', got: ${e.message}")
        }
    }

    // ── toPrettyJson ──────────────────────────────────────────────

    @Test
    @DisplayName("toPrettyJson produces indented output for complex objects")
    fun toPrettyJsonProducesIndentedOutput() {
        val json = PulsarJackson.toPrettyJson(mapOf("a" to 1, "b" to listOf(1, 2, 3)))
        assertTrue(json.contains("\n"), "Expected multi-line pretty JSON, got: $json")
    }

    // ── toJsonOrNull / toJsonOrString ─────────────────────────────

    @Test
    @DisplayName("toJsonOrNull returns serialized valid object")
    fun toJsonOrNullReturnsSerializedValidObject() {
        assertEquals("\"hello\"", PulsarJackson.toJsonOrNull("hello"))
        assertEquals("{\"a\":1}", PulsarJackson.toJsonOrNull(mapOf("a" to 1)))
    }

    @Test
    @DisplayName("toJsonOrNull returns null when serialization throws")
    fun toJsonOrNullReturnsNullWhenSerializationThrows() {
        class ThrowingBean {
            val bad: String get() = throw RuntimeException("boom")
        }
        assertNull(PulsarJackson.toJsonOrNull(ThrowingBean()))
    }

    @Test
    @DisplayName("toJsonOrString falls back to toString on failure")
    fun toJsonOrStringFallsBackToToString() {
        class ThrowingBean {
            val bad: String get() = throw RuntimeException("boom")
            override fun toString() = "ThrowingBean-fallback"
        }
        val result = PulsarJackson.toJsonOrString(ThrowingBean())
        assertEquals("ThrowingBean-fallback", result)
    }

    @Test
    @DisplayName("toPrettyJsonOrNull returns null when serialization throws")
    fun toPrettyJsonOrNullReturnsNullWhenSerializationThrows() {
        class ThrowingBean {
            val bad: String get() = throw RuntimeException("boom")
        }
        assertNull(PulsarJackson.toPrettyJsonOrNull(ThrowingBean()))
    }

    @Test
    @DisplayName("toPrettyJsonOrString falls back to toString on failure")
    fun toPrettyJsonOrStringFallsBackToToString() {
        class ThrowingBean {
            val bad: String get() = throw RuntimeException("boom")
            override fun toString() = "ThrowingBean-fallback"
        }
        val result = PulsarJackson.toPrettyJsonOrString(ThrowingBean())
        assertEquals("ThrowingBean-fallback", result)
    }

    // ── YAML ──────────────────────────────────────────────────────

    @Test
    @DisplayName("toYaml serializes correctly")
    fun toYamlSerializesCorrectly() {
        val yaml = PulsarJackson.toYaml(mapOf("name" to "test", "value" to 42))
        assertTrue(yaml.contains("name:"), "Expected YAML to contain 'name:', got: $yaml")
        assertTrue(yaml.contains("test"), "Expected YAML to contain 'test', got: $yaml")
        assertTrue(yaml.contains("value:") && yaml.contains("42"), "Expected value:42, got: $yaml")
    }

    @Test
    @DisplayName("toYamlOrNull returns null when serialization throws")
    fun toYamlOrNullReturnsNullWhenSerializationThrows() {
        class ThrowingBean {
            val bad: String get() = throw RuntimeException("boom")
        }
        assertNull(PulsarJackson.toYamlOrNull(ThrowingBean()))
    }

    @Test
    @DisplayName("toYamlOrString falls back to toString on failure")
    fun toYamlOrStringFallsBackToToString() {
        class ThrowingBean {
            val bad: String get() = throw RuntimeException("boom")
            override fun toString() = "ThrowingBean-fallback"
        }
        val result = PulsarJackson.toYamlOrString(ThrowingBean())
        assertEquals("ThrowingBean-fallback", result)
    }

    // ── readTree ──────────────────────────────────────────────────

    @Test
    @DisplayName("readTree parses valid JSON")
    fun readTreeParsesValidJson() {
        val node = PulsarJackson.readTree("""{"a": 1, "b": "hello"}""")
        assertNotNull(node)
        assertEquals(1, node!!.get("a").asInt())
        assertEquals("hello", node.get("b").asText())
    }

    @Test
    @DisplayName("readTreeOrNull returns null for invalid JSON")
    fun readTreeOrNullReturnsNullForInvalidJson() {
        assertNull(PulsarJackson.readTreeOrNull("{invalid json"))
    }

    @Test
    @DisplayName("readTreeOrNull returns node for valid JSON")
    fun readTreeOrNullReturnsNodeForValidJson() {
        val node = PulsarJackson.readTreeOrNull("""[1, 2, 3]""")
        assertNotNull(node)
        assertTrue(node!!.isArray)
        assertEquals(3, node.size())
    }

    // ── Pson alias ────────────────────────────────────────────────

    @Test
    @DisplayName("Pson alias works identically to PulsarJackson")
    fun psonAliasWorksIdenticallyToPulsarJackson() {
        assertEquals(PulsarJackson.toJson("test"), Pson.toJson("test"))
        assertEquals(PulsarJackson.toJsonOrNull("test"), Pson.toJsonOrNull("test"))
    }
}
