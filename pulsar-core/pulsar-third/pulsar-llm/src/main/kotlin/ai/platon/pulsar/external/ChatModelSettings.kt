package ai.platon.pulsar.external

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
