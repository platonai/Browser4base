package ai.platon.pulsar.external

import ai.platon.pulsar.common.config.CapabilityTypes.*
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.config.MutableConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class IsolatedLlmConfig : MutableConfig(false) {
    private val values = mutableMapOf<String, String>()

    override operator fun set(name: String, value: String?) {
        if (value == null) values.remove(name) else values[name] = value
    }

    override operator fun get(name: String): String? = values[name]
}

class LLMConfigTest {

    @AfterEach
    fun cleanUp() {
        System.clearProperty("OPENROUTER_API_KEY")
        System.clearProperty("openrouter.api.key")
        System.clearProperty("LLM_REASONING_EFFORT")
        System.clearProperty("LLM_PROVIDER_OPENAI_REASONING_EFFORT")
    }

    @Test
    fun testEnvStyleConfig() {
        System.setProperty("OPENROUTER_API_KEY", "testAPI.key")
        val conf = ImmutableConfig()
        assertEquals("testAPI.key", conf["OPENROUTER_API_KEY"])
        val configured = ChatModelFactory.isModelConfigured(conf)
        assertTrue(configured, "Model should be configured")
    }

    @Test
    fun testSpringStyleConfig() {
        System.setProperty("openrouter.api.key", "testAPI.key")
        val conf = ImmutableConfig()
        assertTrue { ChatModelFactory.isModelConfigured(conf) }
    }

    @Test
    @DisplayName("Empty generation configuration should keep every setting unset")
    fun emptyGenerationConfigurationShouldKeepEverySettingUnset() {
        val settings = ChatGenerationSettings.from(IsolatedLlmConfig())

        assertNull(settings.temperature)
        assertNull(settings.topP)
        assertNull(settings.maxOutputTokens)
        assertNull(settings.stopSequences)
        assertNull(settings.reasoningEffort)
        assertNull(settings.reasoningBudgetTokens)
    }

    @Test
    @DisplayName("Global generation configuration should parse all supported types")
    fun globalGenerationConfigurationShouldParseAllSupportedTypes() {
        val conf = IsolatedLlmConfig().apply {
            set(LLM_TEMPERATURE, "0.0")
            set(LLM_TOP_P, "0.9")
            set(LLM_MAX_OUTPUT_TOKENS, "4096")
            set(LLM_STOP_SEQUENCES, " END,STOP , ,")
            set(LLM_REASONING_EFFORT, "MeDiUm")
            set(LLM_REASONING_BUDGET_TOKENS, "2048")
        }

        val settings = ChatGenerationSettings.from(conf)

        assertEquals(0.0, settings.temperature)
        assertEquals(0.9, settings.topP)
        assertEquals(4096, settings.maxOutputTokens)
        assertEquals(listOf("END", "STOP"), settings.stopSequences)
        assertEquals(ReasoningEffort.MEDIUM, settings.reasoningEffort)
        assertEquals(2048, settings.reasoningBudgetTokens)
    }

    @Test
    @DisplayName("Canonical provider generation configuration should override global values")
    fun canonicalProviderGenerationConfigurationShouldOverrideGlobalValues() {
        val conf = IsolatedLlmConfig().apply {
            set(LLM_TEMPERATURE, "0.2")
            set(LLM_REASONING_EFFORT, "low")
            set("llm.provider.openai.temperature", "0.7")
            set("llm.provider.openai.reasoning.effort", "HIGH")
        }

        val openAi = ChatGenerationSettings.from(conf, "openai")
        val gemini = ChatGenerationSettings.from(conf, "gemini")

        assertEquals(0.7, openAi.temperature)
        assertEquals(ReasoningEffort.HIGH, openAi.reasoningEffort)
        assertEquals(0.2, gemini.temperature)
        assertEquals(ReasoningEffort.LOW, gemini.reasoningEffort)
    }

    @Test
    @DisplayName("Generation configuration should support environment variable style keys")
    fun generationConfigurationShouldSupportEnvironmentVariableStyleKeys() {
        System.setProperty("LLM_REASONING_EFFORT", "HIGH")
        System.setProperty("LLM_PROVIDER_OPENAI_REASONING_EFFORT", "minimal")
        val conf = MutableConfig(false)

        assertEquals(ReasoningEffort.HIGH, ChatGenerationSettings.from(conf).reasoningEffort)
        assertEquals(ReasoningEffort.MINIMAL, ChatGenerationSettings.from(conf, "openai").reasoningEffort)
    }

    @Test
    @DisplayName("Invalid generation configuration should identify the offending key")
    fun invalidGenerationConfigurationShouldIdentifyTheOffendingKey() {
        val invalidValues = listOf(
            LLM_TEMPERATURE to "hot",
            LLM_TOP_P to "NaN",
            LLM_MAX_OUTPUT_TOKENS to "0",
            LLM_REASONING_EFFORT to "extreme",
            LLM_REASONING_BUDGET_TOKENS to "-1",
        )

        invalidValues.forEach { (key, value) ->
            val conf = IsolatedLlmConfig().apply { set(key, value) }
            val error = assertFailsWith<IllegalArgumentException> { ChatGenerationSettings.from(conf) }
            assertTrue(error.message!!.contains(key), "Error should contain '$key': ${error.message}")
        }
    }

    @Test
    @DisplayName("Stop sequence cache keys should preserve element boundaries")
    fun stopSequenceCacheKeysShouldPreserveElementBoundaries() {
        val single = ChatGenerationSettings(stopSequences = listOf("A\u001fB"))
        val multiple = ChatGenerationSettings(stopSequences = listOf("A", "B"))

        assertNotEquals(single.cacheKey(), multiple.cacheKey())
    }
}
