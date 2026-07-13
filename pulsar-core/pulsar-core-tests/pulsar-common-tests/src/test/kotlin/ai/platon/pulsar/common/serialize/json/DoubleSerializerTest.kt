package ai.platon.pulsar.common.serialize.json

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringWriter
import org.junit.jupiter.api.DisplayName

class DoubleSerializerTest {

    private fun mapper(includeNulls: Boolean = true): ObjectMapper =
        jacksonObjectMapper().apply {
            if (includeNulls) {
                // ObjectMapper#setDefaultPropertyInclusion is deprecated; use default property inclusion instead.
                setDefaultPropertyInclusion(
                    JsonInclude.Value.construct(JsonInclude.Include.ALWAYS, JsonInclude.Include.ALWAYS)
                )
            }
            val module = SimpleModule().apply {
                addSerializer(Double::class.java, DoubleSerializer())
                addSerializer(Double::class.javaPrimitiveType, DoubleSerializer())
            }
            registerModule(module)
        }

    private fun serializeWithCustom(value: Double?): String {
        val writer = StringWriter()
        val gen = JsonFactory().createGenerator(writer)
        // Provider is not used by serializer, but Jackson API requires it
        val provider = mapper().serializerProvider
        DoubleSerializer().serialize(value, gen, provider)
        gen.flush()
        return writer.toString()
    }

    data class Holder(val a: Double, val b: Double?, val c: Double)

    @Test
    @DisplayName("direct serialize - rounding and trimming basic numbers")
    fun directSerializeRoundingAndTrimmingBasicNumbers() {
        assertEquals("1.1", serializeWithCustom(1.101))
        assertEquals("1.23", serializeWithCustom(1.234))
        assertEquals("1.2", serializeWithCustom(1.2))
        assertEquals("1", serializeWithCustom(1.0))
        assertEquals("1", serializeWithCustom(1.000000))
        assertEquals("1", serializeWithCustom(1.0000001))
        assertEquals("1.1", serializeWithCustom(1.1000000))
        assertEquals("1.1", serializeWithCustom(1.1000001))
        assertEquals("0", serializeWithCustom(0.0))
        assertEquals("2.5", serializeWithCustom(2.5))
        assertEquals("-1.23", serializeWithCustom(-1.234))
        assertEquals("-1.2", serializeWithCustom(-1.2))
        assertEquals("2", serializeWithCustom(1.999))
    }

    @Test
    @DisplayName("direct serialize - null value")
    fun directSerializeNullValue() {
        assertEquals("null", serializeWithCustom(null))
    }

    @Test
    @DisplayName("direct serialize - rounding boundaries")
    fun directSerializeRoundingBoundaries() {
        // HALF_UP rounding
        assertEquals("0.01", serializeWithCustom(0.005))
        assertEquals("0", serializeWithCustom(0.0049))
        assertEquals("-0.01", serializeWithCustom(-0.005))
        assertEquals("123456789.56", serializeWithCustom(123_456_789.555))
    }

    @Test
    @DisplayName("direct serialize - non-finite numbers are serialized as null by default")
    fun directSerializeNonFiniteNumbersAreSerializedAsNullByDefault() {
        assertEquals("null", serializeWithCustom(Double.NaN))
        assertEquals("null", serializeWithCustom(Double.POSITIVE_INFINITY))
        assertEquals("null", serializeWithCustom(Double.NEGATIVE_INFINITY))
    }

    @Test
    @DisplayName("integration - serialize primitive and boxed fields")
    fun integrationSerializePrimitiveAndBoxedFields() {
        val m = mapper()
        val h = Holder(1.234, 1.0, 2.0)
        val json = m.writeValueAsString(h)
        assertEquals("{" +
                "\"a\":1.23," +
                "\"b\":1.0," +
                "\"c\":2" +
                "}", json)
    }

    @Test
    @DisplayName("integration - serialize with null boxed field included")
    fun integrationSerializeWithNullBoxedFieldIncluded() {
        val m = mapper(includeNulls = true)
        val h = Holder(1.234, null, 2.0)
        val json = m.writeValueAsString(h)
        assertEquals("{" +
                "\"a\":1.23," +
                "\"b\":null," +
                "\"c\":2" +
                "}", json)
    }

    @Test
    @DisplayName("integration - serialize arrays and lists use default for elements")
    fun integrationSerializeArraysAndListsUseDefaultForElements() {
        val m = mapper()
        val arr = arrayOf(1.234, 1.0, 2.5)
        val list = listOf(1.234, 1.0, 2.5)

        val arrJson = m.writeValueAsString(arr)
        val listJson = m.writeValueAsString(list)

        assertEquals("[1.234,1.0,2.5]", arrJson)
        assertEquals("[1.234,1.0,2.5]", listJson)
    }

    // ── NonFiniteDoubleStrategy.STRING ────────────────────────────

    @Test
    @DisplayName("direct serialize - STRING strategy writes NaN/Infinity as JSON strings")
    fun directSerializeStringStrategyWritesNaninfinityAsJsonStrings() {
        val stringSerializer = DoubleSerializer(decimals = 2, nonFiniteStrategy = NonFiniteDoubleStrategy.STRING)
        val provider = mapper().serializerProvider

        fun serializeWith(d: Double): String {
            val writer = StringWriter()
            val gen = JsonFactory().createGenerator(writer)
            stringSerializer.serialize(d, gen, provider)
            gen.flush()
            return writer.toString()
        }

        assertEquals("\"NaN\"", serializeWith(Double.NaN))
        assertEquals("\"Infinity\"", serializeWith(Double.POSITIVE_INFINITY))
        assertEquals("\"-Infinity\"", serializeWith(Double.NEGATIVE_INFINITY))
    }

    @Test
    @DisplayName("direct serialize - NULL strategy writes NaN/Infinity as null")
    fun directSerializeNullStrategyWritesNaninfinityAsNull() {
        val nullSerializer = DoubleSerializer(decimals = 2, nonFiniteStrategy = NonFiniteDoubleStrategy.NULL)
        val provider = mapper().serializerProvider

        fun serializeWith(d: Double): String {
            val writer = StringWriter()
            val gen = JsonFactory().createGenerator(writer)
            nullSerializer.serialize(d, gen, provider)
            gen.flush()
            return writer.toString()
        }

        assertEquals("null", serializeWith(Double.NaN))
        assertEquals("null", serializeWith(Double.POSITIVE_INFINITY))
    }

    // ── large and small numbers ───────────────────────────────────

    @Test
    @DisplayName("direct serialize - very large and very small numbers")
    fun directSerializeVeryLargeAndVerySmallNumbers() {
        // Small values that round to 0 with 2 decimals
        assertEquals("0", serializeWithCustom(0.0001))
        assertEquals("0.01", serializeWithCustom(0.005))
        assertEquals("0", serializeWithCustom(0.004))
        assertEquals("0", serializeWithCustom(-0.004))
        assertEquals("-0.01", serializeWithCustom(-0.005))
    }

    @Test
    @DisplayName("direct serialize - values that produce exact integer after rounding")
    fun directSerializeValuesThatProduceExactIntegerAfterRounding() {
        assertEquals("1", serializeWithCustom(0.999))
        assertEquals("2", serializeWithCustom(1.999))
        assertEquals("100", serializeWithCustom(99.999))
        assertEquals("-1", serializeWithCustom(-0.999))
    }

    @Test
    @DisplayName("direct serialize - trailing zeros stripped correctly")
    fun directSerializeTrailingZerosStrippedCorrectly() {
        assertEquals("1.5", serializeWithCustom(1.50))
        assertEquals("1.5", serializeWithCustom(1.500))
        assertEquals("10", serializeWithCustom(10.00))
        assertEquals("10", serializeWithCustom(10.0))
    }

    @Test
    @DisplayName("direct serialize - configure different decimals")
    fun directSerializeConfigureDifferentDecimals() {
        val serializer = DoubleSerializer(decimals = 4)
        val writer = StringWriter()
        val gen = JsonFactory().createGenerator(writer)
        val provider = mapper().serializerProvider

        serializer.serialize(3.14159, gen, provider)
        gen.flush()
        assertEquals("3.1416", writer.toString())
    }
}
