package ai.platon.pulsar.external

import ai.platon.pulsar.common.config.CapabilityTypes.*
import ai.platon.pulsar.common.config.ImmutableConfig

class ChatModelSettings(conf: ImmutableConfig) {

    /**
     * The maximum length of the prompt in tokens.
     *
     * Default is 64,000 tokens — a safe floor across all supported providers.
     * Override via `llm.max.input.token.length` in configuration for models with
     * larger context windows (e.g. GPT-4o: 128K, Gemini 2.0 Flash: 1M).
     *
     * Supported model context windows:
     *
     * **Global OpenAI-compatible providers:**
     * - GPT-4o / GPT-4.1: 128K
     * - OpenRouter/Seed-1.6: 256K
     * - Groq (Llama 3.3 70B): 128K
     * - Together AI (Llama 3.3 70B): 128K
     * - Mistral Large: 128K
     * - xAI Grok-2: 128K
     * - Perplexity Sonar: 128K
     * - Fireworks (Llama 3.3 70B): 128K
     *
     * **Chinese domestic providers:**
     * - DeepSeek-Chat: 64K
     * - Qwen-Plus (DashScope): 131K
     * - Doubao-1.5-pro (Volcengine): 256K
     * - GLM-4-Plus (Zhipu): 128K
     * - Moonshot-v1: 32K
     * - Baichuan4: 32K
     * - Yi-Large (01.AI): 32K
     * - MiniMax abab6.5s: 128K
     * - StepFun Step-1: 8K
     * - Tencent Hunyuan-Pro: 32K
     * - Baidu Ernie-4.0 (Qianfan): 8K
     *
     * **Anthropic-protocol providers (via AnthropicChatModel):**
     * - Anthropic Claude Sonnet 4/4.5: 200K
     * - Anthropic Claude 3.5 Haiku: 200K
     * - MiniMax M2.5 (via Anthropic Messages protocol): 1M
     * - Google Gemini 2.0 Flash: 1M
     * - Google Gemini 2.5 Pro: 1M
     * - Google Gemini 1.5 Pro: 2M
     *
     * @see ChatModelFactory
     * */
    val maximumInputTokenLength = conf.getInt("llm.max.input.token.length", 64_000)
}

internal enum class ReasoningEffort(val wireValue: String) {
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    companion object {
        fun parse(key: String, value: String): ReasoningEffort {
            return entries.firstOrNull { it.wireValue.equals(value.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Invalid value '$value' for '$key': expected one of ${entries.joinToString { it.wireValue }}"
                )
        }
    }
}

internal data class ChatGenerationSettings(
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxOutputTokens: Int? = null,
    val stopSequences: List<String>? = null,
    val reasoningEffort: ReasoningEffort? = null,
    val reasoningBudgetTokens: Int? = null,
) {
    fun cacheKey(): String {
        return listOf(
            temperature?.toString(),
            topP?.toString(),
            maxOutputTokens?.toString(),
            stopSequences?.joinToString("|") { "${it.length}:$it" },
            reasoningEffort?.wireValue,
            reasoningBudgetTokens?.toString(),
        ).joinToString("|") { value -> value?.let { "${it.length}:$it" } ?: "null" }
    }

    companion object {
        fun from(conf: ImmutableConfig, provider: String? = null): ChatGenerationSettings {
            fun read(key: String): Pair<String, String>? {
                val providerKey = provider?.let { "llm.provider.$it.${key.removePrefix("llm.")}" }
                return listOfNotNull(providerKey, key).firstNotNullOfOrNull { candidate ->
                    conf[candidate]?.trim()?.takeIf { it.isNotEmpty() }?.let { candidate to it }
                }
            }

            fun double(key: String): Double? = read(key)?.let { (actualKey, value) ->
                value.toDoubleOrNull()?.takeIf { it.isFinite() }
                    ?: throw IllegalArgumentException("Invalid value '$value' for '$actualKey': expected a finite number")
            }

            fun positiveInt(key: String): Int? = read(key)?.let { (actualKey, value) ->
                value.toIntOrNull()?.takeIf { it > 0 }
                    ?: throw IllegalArgumentException("Invalid value '$value' for '$actualKey': expected a positive integer")
            }

            return ChatGenerationSettings(
                temperature = double(LLM_TEMPERATURE),
                topP = double(LLM_TOP_P),
                maxOutputTokens = positiveInt(LLM_MAX_OUTPUT_TOKENS),
                stopSequences = read(LLM_STOP_SEQUENCES)?.second
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.takeIf { it.isNotEmpty() },
                reasoningEffort = read(LLM_REASONING_EFFORT)?.let { (key, value) -> ReasoningEffort.parse(key, value) },
                reasoningBudgetTokens = positiveInt(LLM_REASONING_BUDGET_TOKENS),
            )
        }
    }
}
