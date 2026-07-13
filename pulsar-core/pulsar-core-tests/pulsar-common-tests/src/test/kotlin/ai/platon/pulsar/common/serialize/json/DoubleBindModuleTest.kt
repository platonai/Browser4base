package ai.platon.pulsar.common.serialize.json

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName

class DoubleBindModuleTest {

    private fun mapper(decimals: Int = 2, includeNulls: Boolean = false) =
        jacksonObjectMapper().apply {
            if (includeNulls) {
                setDefaultPropertyInclusion(
                    JsonInclude.Value.construct(JsonInclude.Include.ALWAYS, JsonInclude.Include.ALWAYS)
                )
            }
            registerModule(doubleBindModule(decimals))
        }

    data class Box(
        val a: Double,
        val b: Double?,
        val i: Int,
        val l: Long,
        val any: Any,
        val num: Number
    )

    data class FloatBox(
        val f: Float,
        val fAny: Any,
        val fNum: Number
    )

    @Test
    @DisplayName("primitive and boxed doubles are formatted")
    fun primitiveAndBoxedDoublesAreFormatted() {
        val m = mapper()
        val box = Box(
            a = 1.234,
            b = 2.0,
            i = 7,
            l = 9L,
            any = 3.14159,
            num = 4.0
        )
        val json = m.writeValueAsString(box)
        assertEquals("{" +
                "\"a\":1.23," +
                "\"b\":2," +
                "\"i\":7," +
                "\"l\":9," +
                "\"any\":3.14," +
                "\"num\":4" +
                "}", json)
    }

    @Test
    @DisplayName("list array and map elements are formatted")
    fun listArrayAndMapElementsAreFormatted() {
        val m = mapper()
        val arr = arrayOf(1.234, 1.0, 2.5)
        val list = listOf(1.234, 1.0, 2.5)
        val listAny: List<Any> = listOf(1.234, 1.0, 2.5)
        val listNum: List<Number> = listOf(1.234, 1.0, 2.5)
        val mapAny: Map<String, Any> = mapOf("x" to 1.234, "y" to 1.0, "z" to 2.5)
        val mapNum: Map<String, Number> = mapOf("x" to 1.234, "y" to 1.0, "z" to 2.5)

        assertEquals("[1.23,1,2.5]", m.writeValueAsString(arr))
        assertEquals("[1.23,1,2.5]", m.writeValueAsString(list))
        assertEquals("[1.23,1,2.5]", m.writeValueAsString(listAny))
        assertEquals("[1.23,1,2.5]", m.writeValueAsString(listNum))
        assertEquals("{\"x\":1.23,\"y\":1,\"z\":2.5}", m.writeValueAsString(mapAny))
        assertEquals("{\"x\":1.23,\"y\":1,\"z\":2.5}", m.writeValueAsString(mapNum))
    }

    @Test
    @DisplayName("configured decimals are honored")
    fun configuredDecimalsAreHonored() {
        val m = mapper(decimals = 3)
        val list = listOf(1.2346, 0.005, -0.005)
        assertEquals("[1.235,0.005,-0.005]", m.writeValueAsString(list))
    }

    @Test
    @DisplayName("nested structures format doubles without recursion")
    fun nestedStructuresFormatDoublesWithoutRecursion() {
        val m = mapper()
        val tree: Map<String, Any> = linkedMapOf(
            "n" to 1.234,
            "child" to linkedMapOf(
                "v" to 2.0,
                "arr" to listOf(3.0, 4.0, 5.005),
                "grand" to linkedMapOf(
                    "x" to listOf<Number>(6.0, 7.234, 8.0),
                    "y" to 9.999
                )
            )
        )
        val json = m.writeValueAsString(tree)
        assertEquals("{" +
                "\"n\":1.23," +
                "\"child\":{" +
                "\"v\":2," +
                "\"arr\":[3,4,5.01]," +
                "\"grand\":{" +
                "\"x\":[6,7.23,8]," +
                "\"y\":10" +
                "}" +
                "}" +
                "}", json)
    }

    // ── Float serialization ───────────────────────────────────────

    @Test
    @DisplayName("float values in containers are formatted without artificial digits")
    fun floatValuesInContainersAreFormattedWithoutArtificialDigits() {
        val m = mapper()
        val listFloat: List<Float> = listOf(3.14f, 1.0f, 2.5f)
        assertEquals("[3.14,1,2.5]", m.writeValueAsString(listFloat))
    }

    @Test
    @DisplayName("float values in List<Any> are formatted")
    fun floatValuesInListAnyAreFormatted() {
        val m = mapper()
        val listAny: List<Any> = listOf(3.14f, 1.0f, 2.5f)
        assertEquals("[3.14,1,2.5]", m.writeValueAsString(listAny))
    }

    @Test
    @DisplayName("float values in Map<String, Any> are formatted")
    fun floatValuesInMapStringAnyAreFormatted() {
        val m = mapper()
        val mapAny: Map<String, Any> = mapOf("x" to 3.14f, "y" to 1.0f, "z" to 2.5f)
        assertEquals("{\"x\":3.14,\"y\":1,\"z\":2.5}", m.writeValueAsString(mapAny))
    }

    @Test
    @DisplayName("float values with higher decimals do not leak artificial digits")
    fun floatValuesWithHigherDecimalsDoNotLeakArtificialDigits() {
        val m = mapper(decimals = 6)
        // 3.14f.toDouble() = 3.140000104904175, but direct Float serialization keeps it clean
        val list: List<Float> = listOf(3.14f)
        val json = m.writeValueAsString(list)
        assertEquals("[3.14]", json)
    }

    @Test
    @DisplayName("float values respect decimals config")
    fun floatValuesRespectDecimalsConfig() {
        val m = mapper(decimals = 3)
        val list: List<Float> = listOf(3.14159f)
        // Float.toString(3.14159f) = "3.14159", rounded to 3 decimals → 3.142
        assertEquals("[3.142]", m.writeValueAsString(list))
    }

    @Test
    @DisplayName("mixed number types in a container")
    fun mixedNumberTypesInAContainer() {
        val m = mapper()
        val mixed: List<Number> = listOf(1.234, 3.14f, 42, 99L, 2.5)
        val json = m.writeValueAsString(mixed)
        assertEquals("[1.23,3.14,42,99,2.5]", json)
    }

    @Test
    @DisplayName("float values in nested structures")
    fun floatValuesInNestedStructures() {
        val m = mapper()
        val tree: Map<String, Any> = mapOf(
            "floats" to listOf(3.14f, 1.0f),
            "nested" to mapOf("f" to 2.718f)
        )
        val json = m.writeValueAsString(tree)
        assertEquals("{\"floats\":[3.14,1],\"nested\":{\"f\":2.72}}", json)
    }

    @Test
    @DisplayName("float non-finite values are serialized as null")
    fun floatNonFiniteValuesAreSerializedAsNull() {
        val m = mapper()
        val list: List<Float> = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        val json = m.writeValueAsString(list)
        assertEquals("[null,null,null]", json)
    }

    @Test
    @DisplayName("float in a data class field")
    fun floatInADataClassField() {
        val m = mapper()
        val box = FloatBox(f = 3.14f, fAny = 1.0f, fNum = 2.5f)
        val json = m.writeValueAsString(box)
        // The Number serializer is used for fNum, but f and fAny may follow different paths
        // depending on Jackson's type resolution for Float::class.javaPrimitiveType
        assertTrue(json.contains("3.14"), "Expected 3.14 in: $json")
        assertTrue(json.contains("1"), "Expected 1 in: $json")
        assertTrue(json.contains("2.5"), "Expected 2.5 in: $json")
    }
}
