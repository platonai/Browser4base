package ai.platon.pulsar.external

import ai.platon.pulsar.common.config.ImmutableConfig

class ChatModelSettings(conf: ImmutableConfig) {

    /**
     * The maximum length of the prompt in tokens.
     *
     * Default is 64,000 tokens — a safe floor across all supported providers.
     * Override via `llm.max.input.token.length` in configuration for models with
     * larger context windows (e.g. GPT-4o: 128K, Doubao-Seed-1.6: 224K).
     *
     * Supported model context windows:
     * - DeepSeek: 64K
     * - GPT-4o: 128K
     * - Qwen-Plus: 131K
     * - Doubao-1.5-pro: 256K
     * - OpenRouter/Seed-1.6: 256K
     * */
    val maximumInputTokenLength = conf.getInt("llm.max.input.token.length", 64_000)
}
