# Advanced LLM Configuration

For providers not in the [built-in registry](llm-config.md), use the generic configuration format.

> **How-To Guide:** See [`llm-chat-model-factory-howto.md`](llm-chat-model-factory-howto.md) for
> a comprehensive walkthrough of `ChatModelFactory` — auto-detection, explicit provider selection,
> protocol-specific methods, custom providers, deny lists, error handling, and complete examples.

## Generic Provider Configuration

```properties
llm.provider=<provider-name>
llm.name=<model-name>
llm.api.key=<your-api-key>
```

### Examples

**Custom OpenAI-compatible provider:**

```properties
llm.provider=openai
llm.name=gpt-4o
llm.api.key=sk-your-key
```

This is equivalent to the dedicated `openai.api.key` style configuration.

**Custom proxy or self-hosted model:**

```properties
llm.provider=my-llm-proxy
llm.name=llama-3-70b
llm.api.key=not-needed

# The provider name must match one of the known providers
# in ChatModelFactory, or it falls back to OpenAI-compatible
# with the default OpenAI base URL.
```

## Programmatic Usage

### Kotlin

```kotlin
import ai.platon.pulsar.external.ChatModelFactory
import ai.platon.pulsar.common.config.ImmutableConfig

val conf = ImmutableConfig().apply {
    // Set via system properties or configuration files
}
System.setProperty("OPENAI_API_KEY", "sk-your-key")
val model = ChatModelFactory.getOrCreate(conf)
val response = runBlocking { model.call("Hello!") }
println(response.content)
```

### Explicit Provider Selection

```kotlin
// Create a model with explicit parameters
val model = ChatModelFactory.getOrCreate(
    provider = "deepseek",
    modelName = "deepseek-chat",
    apiKey = "sk-your-key",
    conf = ImmutableConfig()
)

// OpenAI-compatible with custom base URL
val customModel = ChatModelFactory.getOrCreateOpenAICompatibleModel(
    modelName = "my-model",
    apiKey = "my-key",
    baseUrl = "https://my-custom-endpoint/v1",
    conf = ImmutableConfig()
)

// Anthropic Claude
val claudeModel = ChatModelFactory.getOrCreateAnthropicModel(
    modelName = "claude-sonnet-4-6",
    apiKey = "sk-ant-your-key",
    conf = ImmutableConfig()
)

// Anthropic-compatible with custom base URL (e.g. MiniMax, Bedrock proxy)
val anthropicCompatModel = ChatModelFactory.getOrCreateAnthropicCompatibleModel(
    modelName = "claude-sonnet-4-6",
    apiKey = "your-key",
    baseUrl = "https://my-anthropic-gateway.example.com",
    conf = ImmutableConfig()
)

// Google Gemini
val geminiModel = ChatModelFactory.getOrCreateGeminiModel(
    modelName = "gemini-3.1-flash-lite",
    apiKey = "your-key",
    conf = ImmutableConfig()
)

// MiniMax (Anthropic protocol)
val minimaxModel = ChatModelFactory.getOrCreateMinimaxModel(
    modelName = "MiniMax-M3",
    apiKey = "your-key",
    conf = ImmutableConfig()
)
```

## Provider Architecture

### Protocol Types

The `ChatModelFactory` supports three protocol types, declared via the `ApiProtocol` enum
on each `ProviderConfig` entry:

1. **`ApiProtocol.OPENAI`** — Most providers (Groq, Together, Mistral, xAI, Chinese providers, etc.)
   - Uses LangChain4j `OpenAiChatModel` with provider-specific base URLs
   - Default protocol for all `ProviderConfig` entries

2. **`ApiProtocol.ANTHROPIC`** — Anthropic Claude, MiniMax, and custom Anthropic-compatible gateways
   - Uses LangChain4j `AnthropicChatModel` natively
   - MiniMax uses this protocol with a custom endpoint

3. **`ApiProtocol.GEMINI`** — Google Gemini
   - Uses LangChain4j `GoogleAiGeminiChatModel` natively

### Adding a Custom Provider

For one-off use, the generic `llm.provider` / `llm.name` / `llm.api.key` format works.
For permanent additions to the built-in registry, override the default provider list by
creating a custom `providers.json` and pointing to it via `llm.provider.config.path`:

```json
{
  "providers": [
    {
      "apiKeyName": "MY_PROVIDER_API_KEY",
      "modelNameKey": "MY_PROVIDER_MODEL_NAME",
      "baseUrlKey": "MY_PROVIDER_BASE_URL",
      "defaultModel": "my-default-model",
      "defaultBaseUrl": "https://api.my-provider.com/v1",
      "providerName": "my-provider",
      "supportVision": true,
      "apiProtocol": "OPENAI"
    }
  ],
  "aliases": {},
  "canonicalAliases": {}
}
```

```properties
# Point to your custom providers.json
llm.provider.config.path=/etc/browser4/providers.json
```

The default `providers.json` is bundled as a classpath resource
(`/ai/platon/pulsar/external/providers.json`).  Your external file **replaces**
(rather than merges with) the built-in list, so include all providers you need.

To switch provider lists at runtime, call `resetProviders()` after updating the path:

```kotlin
System.setProperty("llm.provider.config.path", "/new/path/providers.json")
ChatModelFactory.resetProviders()
```

For runtime-only additions (no file needed), use the programmatic API:

```kotlin
ChatModelFactory.registerProvider(
    ProviderConfig(
        apiKeyName = "MY_PROVIDER_API_KEY",
        modelNameKey = "MY_PROVIDER_MODEL_NAME",
        baseUrlKey = "MY_PROVIDER_BASE_URL",
        defaultModel = "my-default-model",
        defaultBaseUrl = "https://api.my-provider.com/v1",
        providerName = "my-provider",
        apiProtocol = ApiProtocol.OPENAI
    )
)
```

This automatically enables:
- Auto-detection via `MY_PROVIDER_API_KEY` env var
- Explicit creation via `ChatModelFactory.getOrCreate("my-provider", modelName, apiKey, conf)`
- Configuration via `my-provider.model.name` and `my-provider.base.url` properties
- Correct protocol dispatch (OpenAI / Anthropic / Gemini) based on `apiProtocol`

## Response Caching

LLM responses are cached in-memory to reduce costs and latency for repeated queries.

```properties
# Cache TTL in seconds (default: 600 = 10 minutes)
llm.response.cache.ttl=600

# Maximum cache entries (default: 1000)
# Not configurable via properties; edit CachedBrowserChatModel.maxCacheEntries
```

## Timeouts and Retries

Default settings applied to all providers:

- **Timeout**: 90 seconds per request
- **Max retries**: 2 attempts (with exponential backoff)
- **Request logging**: Enabled

## Context Window Sizes

| Provider | Model | Context Window |
|----------|-------|----------------|
| OpenAI | gpt-4o | 128K |
| Anthropic | Claude Sonnet 4/4.5 | 200K |
| Google | Gemini 2.0 Flash | 1M |
| Google | Gemini 2.5 Pro | 1M |
| DeepSeek | deepseek-chat | 64K |
| Alibaba | qwen-plus | 131K |
| ByteDance | doubao-1.5-pro | 256K |
| Zhipu | glm-4-plus | 128K |
| Moonshot | moonshot-v1-8k | 32K |
| Baichuan | Baichuan4 | 32K |
| 01.AI | yi-large | 32K |
| MiniMax | MiniMax-M2.5 | 1M |
| StepFun | step-1-8k | 8K |
| Tencent | hunyuan-pro | 32K |
| Baidu | ernie-4.0-8k | 8K |
| Groq | llama-3.3-70b | 128K |
| Together | Llama 3.3 70B | 128K |
| Mistral | mistral-large | 128K |
| xAI | grok-2-1212 | 128K |
| Perplexity | sonar | 128K |
| Fireworks | llama-v3p3 | 128K |

Override via:

```properties
llm.max.input.token.length=200000
```
