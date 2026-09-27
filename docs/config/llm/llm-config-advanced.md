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

Context windows belong to the model, not to Browser4, and the shipped defaults move faster than
this page.  The authoritative list is the registry itself —
[`providers.json`](../../../pulsar-core/pulsar-third/pulsar-llm/src/main/resources/ai/platon/pulsar/external/providers.json) —
and `ChatModelFactory.describeActiveProvider(conf)` reports the model a configuration resolves to.

Rough sizes of the shipped defaults, for planning: Gemini and MiniMax ~1M; Volcengine ~256K;
Anthropic ~200K; DashScope ~131K; OpenAI, Groq, Together, Mistral, xAI, Perplexity, Fireworks and
Zhipu ~128K; DeepSeek ~64K; Moonshot, Baichuan, Yi and Hunyuan ~32K; StepFun and Qianfan ~8K.

Override via:

```properties
llm.max.input.token.length=200000
```
