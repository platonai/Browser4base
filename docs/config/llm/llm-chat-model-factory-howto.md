# ChatModelFactory — How-To Guide

The `ChatModelFactory` is the central entry point for creating and managing LLM chat models in Browser4.
It supports **26+ providers** across three protocols (OpenAI-compatible, Anthropic, Gemini) through a
unified, data-driven registry — no provider-specific code paths needed.

This guide demonstrates every way to use it, from the simplest auto-detection to custom provider registration.

> **Prerequisite:** Read [`llm-config.md`](llm-config.md) for provider configuration reference.

---

## Table of Contents

1. [Quick Start](#quick-start) — the simplest path
2. [Auto-Detection](#auto-detection) — set an env var and go
3. [Explicit Provider Selection](#explicit-provider-selection) — pick your provider
4. [Protocol-Specific Methods](#protocol-specific-methods) — OpenAI, Anthropic, Gemini
5. [Using the Model](#using-the-model) — call the created model
6. [Custom Providers](#custom-providers) — register your own
7. [Deny List](#deny-list) — block unwanted providers
8. [Error Handling](#error-handling) — graceful fallback
9. [Model Caching](#model-caching) — reuse instances
10. [Custom Messaging](#custom-messaging) — for embedded use
11. [Complete Examples](#complete-examples)

---

## Quick Start

The simplest usage: set one environment variable, then call `getOrCreate(conf)`.

```bash
export OPENROUTER_API_KEY=sk-or-v1-your-key
```

```kotlin
import ai.platon.pulsar.external.ChatModelFactory
import ai.platon.pulsar.common.config.ImmutableConfig
import kotlinx.coroutines.runBlocking

val conf = ImmutableConfig()
val model = ChatModelFactory.getOrCreate(conf)

val response = runBlocking { model.call("What is the capital of France?") }
println(response.content)  // "Paris"
```

That's it. The factory scans all known API keys, finds `OPENROUTER_API_KEY`, and creates an
OpenRouter model with the default `bytedance-seed/seed-1.6`.

---

## Auto-Detection

### How it works

`getOrCreate(conf)` scans built-in provider configurations in priority order.
The **first provider with a matching API key wins**:

| Priority | Provider    | API Key Env Var              | Protocol   |
|----------|-------------|------------------------------|------------|
| 1        | OpenRouter  | `OPENROUTER_API_KEY`         | OpenAI     |
| 2        | Groq        | `GROQ_API_KEY`               | OpenAI     |
| 3        | Together AI | `TOGETHER_API_KEY`           | OpenAI     |
| 4        | Mistral     | `MISTRAL_API_KEY`            | OpenAI     |
| 5        | xAI / Grok  | `XAI_API_KEY`                | OpenAI     |
| 6        | Perplexity  | `PERPLEXITY_API_KEY`         | OpenAI     |
| 7        | Fireworks   | `FIREWORKS_API_KEY`          | OpenAI     |
| 8        | DeepSeek    | `DEEPSEEK_API_KEY`           | OpenAI     |
| 9        | DashScope   | `DASHSCOPE_API_KEY`          | OpenAI     |
| 10       | Volcengine  | `VOLCENGINE_API_KEY`         | OpenAI     |
| 11       | Zhipu AI    | `ZHIPU_API_KEY`              | OpenAI     |
| 12       | Moonshot    | `MOONSHOT_API_KEY`           | OpenAI     |
| 13       | Baichuan    | `BAICHUAN_API_KEY`           | OpenAI     |
| 14       | 01.AI / Yi  | `YI_API_KEY`                 | OpenAI     |
| 15       | StepFun     | `STEPFUN_API_KEY`            | OpenAI     |
| 16       | Hunyuan     | `HUNYUAN_API_KEY`            | OpenAI     |
| 17       | Qianfan     | `QIANFAN_API_KEY`            | OpenAI     |
| 18       | OpenAI      | `OPENAI_API_KEY`             | OpenAI     |
| 19       | Anthropic   | `ANTHROPIC_API_KEY`          | Anthropic  |
| 20       | Gemini      | `GOOGLE_GENERATIVE_AI_API_KEY` | Gemini   |
| 21       | MiniMax     | `MINIMAX_API_KEY`            | Anthropic  |

### Aliases

Several alias key names are supported, mapped to canonical providers:

| Alias               | Canonical Provider |
|---------------------|--------------------|
| `KIMI_API_KEY`      | moonshot           |
| `LINGYI_API_KEY`    | yi (01.AI)         |
| `TENCENT_API_KEY`   | hunyuan            |
| `BAIDU_API_KEY`     | qianfan            |
| `GEMINI_API_KEY`    | gemini             |
| `GOOGLE_API_KEY`    | gemini             |

### Configuring specific keys

Set API keys via environment variables, system properties, or config files:

```properties
# application.properties — any format works (dots, dashes, underscores, camelCase)
openrouter.api.key=sk-or-v1-your-key
openrouter.model.name=anthropic/claude-sonnet-4-6
```

```bash
# Environment variable
export DEEPSEEK_API_KEY=sk-your-key
```

```bash
# Java system property
java -DDEEPSEEK_API_KEY=sk-your-key -jar Browser4.jar
```

### Checking configuration status

```kotlin
val conf = ImmutableConfig()

// Check if ANY provider is configured (non-verbose — no logging)
if (ChatModelFactory.isModelConfigured(conf, verbose = false)) {
    println("LLM is configured!")
}

// Shorthand alias
if (ChatModelFactory.hasModel(conf)) {
    println("LLM is configured!")
}
```

### Overriding the default model or base URL

Each provider has config keys to override the default model and base URL:

```properties
# Use a different model via OpenRouter
openrouter.api.key=sk-or-v1-your-key
openrouter.model.name=google/gemini-2.5-flash
openrouter.base.url=https://openrouter.ai/api/v1

# Or use a custom OpenAI-compatible endpoint
openai.api.key=sk-your-key
openai.model.name=gpt-4o
openai.base.url=https://your-proxy.example.com/v1
```

---

## Explicit Provider Selection

When you want to control which provider is used, use the 4-argument `getOrCreate`:

```kotlin
import ai.platon.pulsar.external.ChatModelFactory
import ai.platon.pulsar.common.config.ImmutableConfig

val conf = ImmutableConfig()

// OpenAI (with explicit model)
val openaiModel = ChatModelFactory.getOrCreate(
    provider = "openai",
    modelName = "gpt-4o",
    apiKey = "sk-your-openai-key",
    conf = conf
)

// DeepSeek
val deepseekModel = ChatModelFactory.getOrCreate(
    provider = "deepseek",
    modelName = "deepseek-chat",
    apiKey = "sk-your-deepseek-key",
    conf = conf
)

// Anthropic Claude
val claudeModel = ChatModelFactory.getOrCreate(
    provider = "anthropic",
    modelName = "claude-sonnet-4-6",
    apiKey = "sk-ant-your-key",
    conf = conf
)

// Google Gemini
val geminiModel = ChatModelFactory.getOrCreate(
    provider = "gemini",
    modelName = "gemini-3.1-flash-lite",
    apiKey = "your-gemini-key",
    conf = conf
)

// MiniMax (Anthropic protocol, not OpenAI-compatible)
val minimaxModel = ChatModelFactory.getOrCreate(
    provider = "minimax",
    modelName = "MiniMax-M3",
    apiKey = "your-minimax-key",
    conf = conf
)
```

### Provider name aliases

These aliases can be used anywhere a provider name is expected:

| Alias   | Canonical Name |
|---------|----------------|
| claude  | anthropic      |
| google  | gemini         |

```kotlin
// These are equivalent:
val model1 = ChatModelFactory.getOrCreate("anthropic", "claude-sonnet-4-6", apiKey, conf)
val model2 = ChatModelFactory.getOrCreate("claude", "claude-sonnet-4-6", apiKey, conf)
```

---

## Protocol-Specific Methods

For providers with known protocols, dedicated methods give you direct access:

### OpenAI-Compatible Providers

```kotlin
// Generic: works for any OpenAI-compatible provider
val model = ChatModelFactory.getOrCreateOpenAICompatibleModel(
    modelName = "gpt-4o",
    apiKey = "sk-your-key",
    baseUrl = "https://api.openai.com/v1",
    conf = conf
)

// Examples with other OpenAI-compatible endpoints:
val groq = ChatModelFactory.getOrCreateOpenAICompatibleModel(
    modelName = "llama-3.3-70b-versatile",
    apiKey = "gsk_your_key",
    baseUrl = "https://api.groq.com/openai/v1",
    conf = conf
)

val together = ChatModelFactory.getOrCreateOpenAICompatibleModel(
    modelName = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
    apiKey = "your_key",
    baseUrl = "https://api.together.xyz/v1",
    conf = conf
)

// Self-hosted or proxy:
val localModel = ChatModelFactory.getOrCreateOpenAICompatibleModel(
    modelName = "llama-3-70b",
    apiKey = "not-needed",
    baseUrl = "http://localhost:8080/v1",
    conf = conf
)
```

### Anthropic Claude (Native)

```kotlin
val claude = ChatModelFactory.getOrCreateAnthropicModel(
    modelName = "claude-sonnet-4-6",
    apiKey = "sk-ant-your-key",
    conf = conf
)
```

### Anthropic-Compatible (Custom Endpoint)

Any provider implementing the Anthropic Messages protocol with a custom base URL:

```kotlin
// Custom Anthropic gateway
val gateway = ChatModelFactory.getOrCreateAnthropicCompatibleModel(
    modelName = "claude-sonnet-4-6",
    apiKey = "your-key",
    baseUrl = "https://my-anthropic-gateway.example.com",
    conf = conf
)

// MiniMax (also Anthropic protocol, dedicated method also available)
val minimax = ChatModelFactory.getOrCreateAnthropicCompatibleModel(
    modelName = "MiniMax-M3",
    apiKey = "your-key",
    baseUrl = "https://api.minimaxi.com/anthropic",
    conf = conf
)
```

### Google Gemini (Native)

```kotlin
val gemini = ChatModelFactory.getOrCreateGeminiModel(
    modelName = "gemini-3.1-flash-lite",
    apiKey = "your-key",
    conf = conf
)
```

### MiniMax (Dedicated Method)

```kotlin
val minimax = ChatModelFactory.getOrCreateMinimaxModel(
    modelName = "MiniMax-M3",
    apiKey = "your-key",
    conf = conf
)
```

---

## Using the Model

All factory methods return a `BrowserChatModel`. Here's how to use it:

### Simple Text Call

```kotlin
import kotlinx.coroutines.runBlocking

val model = ChatModelFactory.getOrCreate(conf)

// Single user message
val response = runBlocking { model.call("Summarize the Pulsar project in one sentence.") }
println(response.content)
```

### System + User Message

```kotlin
val response = runBlocking {
    model.call(
        systemMessage = "You are a helpful coding assistant. Answer concisely.",
        userMessage = "What is the time complexity of quicksort?"
    )
}
println(response.content)
```

### Vision (Image Input)

```kotlin
// By URL
val response = runBlocking {
    model.call(
        systemMessage = "Describe the content of this image.",
        userMessage = "What do you see?",
        imageUrl = "https://example.com/photo.jpg"
    )
}

// By base64-encoded image
val response = runBlocking {
    model.call(
        systemMessage = "Extract text from this screenshot.",
        userMessage = "",
        b64Image = "iVBORw0KGgoAAAANSUhEUgAA...",
        mediaType = "image/png"
    )
}
```

> **Note:** Vision support depends on the provider and model. Use `ProviderConfig.supportVision`
> to declare whether a provider supports image inputs.

### LangChain4j Native API

```kotlin
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest

// Using ChatRequest
val request = ChatRequest.builder()
    .messages(
        SystemMessage("You are a helpful assistant."),
        UserMessage("Hello!")
    )
    .build()

val response = runBlocking { model.langChainChat(request) }
println(response.aiMessage().text())

// Using vararg messages
val response = runBlocking {
    model.langChainChat(
        SystemMessage("You are a helpful assistant."),
        UserMessage("Hello!")
    )
}
```

### Passing a Category for Analytics / Caching

```kotlin
val response = runBlocking {
    model.call("What is 2+2?", category = "math-query")
}
```

---

## Custom Providers

### Registering a Provider (Programmatic)

Register custom providers via `ChatModelFactory.registerProvider()`.
**Registered providers are checked before built-in providers** and take priority.

#### Kotlin

```kotlin
import ai.platon.pulsar.external.ChatModelFactory
import ai.platon.pulsar.external.ProviderConfig
import ai.platon.pulsar.external.ApiProtocol

// 1. OpenAI-compatible custom provider
ChatModelFactory.registerProvider(
    ProviderConfig(
        apiKeyName = "MY_PROVIDER_API_KEY",
        modelNameKey = "MY_PROVIDER_MODEL_NAME",
        baseUrlKey = "MY_PROVIDER_BASE_URL",
        defaultModel = "my-model-v2",
        defaultBaseUrl = "https://api.myprovider.com/v1",
        providerName = "myprovider"
        // apiProtocol defaults to ApiProtocol.OPENAI
        // supportVision defaults to true
    )
)

// 2. Text-only provider (no vision)
ChatModelFactory.registerProvider(
    ProviderConfig(
        apiKeyName = "TEXT_ONLY_API_KEY",
        modelNameKey = "TEXT_ONLY_MODEL_NAME",
        baseUrlKey = "TEXT_ONLY_BASE_URL",
        defaultModel = "text-model-v1",
        defaultBaseUrl = "https://api.textonly.com/v1",
        providerName = "textonly",
        supportVision = false
    )
)

// 3. Anthropic-compatible provider (custom gateway)
ChatModelFactory.registerProvider(
    ProviderConfig(
        apiKeyName = "MY_ANTHROPIC_GATEWAY_KEY",
        modelNameKey = "MY_ANTHROPIC_GATEWAY_MODEL",
        baseUrlKey = "MY_ANTHROPIC_GATEWAY_URL",
        defaultModel = "claude-sonnet-4-6",
        defaultBaseUrl = "https://my-anthropic-gateway.example.com",
        providerName = "my-anthropic-gateway",
        apiProtocol = ApiProtocol.ANTHROPIC
    )
)
```

#### Java

```java
import ai.platon.pulsar.external.ChatModelFactory;
import ai.platon.pulsar.external.ProviderConfig;
import ai.platon.pulsar.external.ApiProtocol;

// OpenAI-compatible
ChatModelFactory.registerProvider(new ProviderConfig(
    "MY_PROVIDER_API_KEY",       // apiKeyName
    "MY_PROVIDER_MODEL_NAME",    // modelNameKey
    "MY_PROVIDER_BASE_URL",      // baseUrlKey
    "my-model-v2",               // defaultModel
    "https://api.myprovider.com/v1", // defaultBaseUrl
    "myprovider",                // providerName
    true,                        // supportVision
    ApiProtocol.OPENAI           // apiProtocol
));

// Anthropic-compatible
ChatModelFactory.registerProvider(new ProviderConfig(
    "MY_ANTHROPIC_KEY",          // apiKeyName
    "MY_ANTHROPIC_MODEL",        // modelNameKey
    "MY_ANTHROPIC_URL",          // baseUrlKey
    "claude-sonnet-4-6",         // defaultModel
    "https://gateway.example.com", // defaultBaseUrl
    "my-anthropic-gateway",      // providerName
    true,                        // supportVision
    ApiProtocol.ANTHROPIC        // apiProtocol
));
```

Once registered, the provider works exactly like built-in ones:

```kotlin
// Auto-detection
System.setProperty("MY_PROVIDER_API_KEY", "sk-my-key")
val model = ChatModelFactory.getOrCreate(conf)  // picks up MY_PROVIDER_API_KEY

// Explicit creation
val model = ChatModelFactory.getOrCreate("myprovider", "my-model-v2", "sk-my-key", conf)
```

### Unregistering a Provider

```kotlin
// Remove by name (case-insensitive). Returns true if found.
val wasRemoved = ChatModelFactory.unregisterProvider("myprovider")

// Built-in providers cannot be unregistered — this returns false:
ChatModelFactory.unregisterProvider("openai")  // false
```

### Listing Registered Providers

```kotlin
val providers: List<ProviderConfig> = ChatModelFactory.registeredProviders
providers.forEach { println("${it.providerName} → ${it.defaultModel}") }
```

### Provider Priority

Registered providers are scanned **before** built-in providers. This means you can **shadow** a
built-in provider by registering one with the same API key name:

```kotlin
// Shadow OpenAI: use your own gateway when OPENAI_API_KEY is detected
ChatModelFactory.registerProvider(
    ProviderConfig(
        apiKeyName = "OPENAI_API_KEY",       // same as built-in
        modelNameKey = "CUSTOM_OPENAI_MODEL",
        baseUrlKey = "CUSTOM_OPENAI_BASE_URL",
        defaultModel = "my-custom-gpt",
        defaultBaseUrl = "https://my-openai-gateway.example.com/v1",
        providerName = "my-openai-gateway"   // different provider name
    )
)
```

> Attempting to register a provider with the **same canonical name** as a built-in (e.g. `"openai"`)
> throws `IllegalArgumentException`.

### Replacing the Built-in Provider List (JSON Override)

For bulk changes — updating default models, adding/removing providers, or changing priority
order — override the entire built-in provider list with an external `providers.json` file.
This avoids recompiling when providers ship new flagship models.

1. Copy the default `providers.json` from the classpath resource
   (`/ai/platon/pulsar/external/providers.json` inside the JAR) as a starting point.

2. Edit the file — change default models, add providers, reorder, or add aliases:

   ```json
   {
     "providers": [
       {
         "apiKeyName": "OPENAI_API_KEY",
         "modelNameKey": "OPENAI_MODEL_NAME",
         "baseUrlKey": "OPENAI_BASE_URL",
         "defaultModel": "gpt-6",
         "defaultBaseUrl": "https://api.openai.com/v1",
         "providerName": "openai",
         "supportVision": true,
         "apiProtocol": "OPENAI"
       }
     ],
     "aliases": {
       "GEMINI_API_KEY": "gemini"
     },
     "canonicalAliases": {
       "claude": "anthropic"
     }
   }
   ```

3. Point to your custom file:

   ```properties
   # application.properties, env var, or system property
   llm.provider.config.path=/etc/browser4/providers.json
   ```

   ```bash
   # Or as a system property
   java -Dllm.provider.config.path=/etc/browser4/providers.json -jar Browser4.jar
   ```

4. The external file **replaces** the built-in list entirely — include all providers
   you need, in the priority order you want.

> **Tip:** If you only need to add one or two providers without replacing the entire
> list, use [`registerProvider()`](#registering-a-provider-programmatic) instead.

At runtime, call `ChatModelFactory.resetProviders()` to force a reload after changing
the file path or content:

```kotlin
// Switch to a different providers.json at runtime
System.setProperty("llm.provider.config.path", "/new/path/providers.json")
ChatModelFactory.resetProviders()  // clears cached registry; next access reloads
```

---

## Deny List

Block specific providers from being used, even if their API keys are configured.

### Configuration

```properties
# Comma-separated list of provider names, aliases, or API key names
llm.provider.deny.list=openai,zhipu,claude
```

Each entry is resolved to a canonical provider:

| Entry              | Resolves To |
|--------------------|-------------|
| `openai`           | openai      |
| `zhipu`            | zhipu       |
| `claude`           | anthropic   |
| `ANTHROPIC_API_KEY`| anthropic   |
| `KIMI_API_KEY`     | moonshot    |

### Programmatic Queries

```kotlin
val conf = ImmutableConfig()

// Get the set of denied canonical provider names
val denied: Set<String> = ChatModelFactory.getDeniedProviders(conf)
println("Denied providers: $denied")  // e.g. {openai, zhipu}

// Check if a specific provider is denied
ChatModelFactory.isProviderDenied("openai", conf)         // true
ChatModelFactory.isProviderDenied("claude", conf)         // true (alias → anthropic)
ChatModelFactory.isProviderDenied("ANTHROPIC_API_KEY", conf) // true (API key → anthropic)
ChatModelFactory.isProviderDenied("groq", conf)           // false (not in list)
```

### Behavior

- **Auto-detection** (`getOrCreate(conf)`): denied providers are **skipped** — the factory
  falls through to the next provider with a matching API key.

- **Explicit creation** (`getOrCreate(provider, modelName, apiKey, conf)`): throws
  `IllegalArgumentException` with a message naming the denied provider:

```kotlin
System.setProperty("llm.provider.deny.list", "openai")

val conf = ImmutableConfig()
try {
    ChatModelFactory.getOrCreate("openai", "gpt-4o", "sk-...", conf)
} catch (e: IllegalArgumentException) {
    // "Provider 'openai' is on the deny list (llm.provider.deny.list).
    //  Remove it from the deny list to use this provider."
}
```

- **`getOrCreateOrNull(conf)`**: returns `null` if all configured providers are denied.

### Complete Deny List Example

```kotlin
// Block specific providers while allowing others
System.setProperty("OPENAI_API_KEY", "sk-openai-key")
System.setProperty("GROQ_API_KEY", "gsk_groq_key")
System.setProperty("llm.provider.deny.list", "openai,anthropic,zhipu")

val conf = ImmutableConfig()
// Skips OpenAI (denied), falls through to Groq (allowed)
val model = ChatModelFactory.getOrCreate(conf)
// Model uses Groq
```

---

## Error Handling

### Check-Before-Use Pattern

```kotlin
val conf = ImmutableConfig()

if (ChatModelFactory.isModelConfigured(conf)) {
    val model = ChatModelFactory.getOrCreate(conf)
    val response = runBlocking { model.call("Hello!") }
    println(response.content)
} else {
    println("LLM is not configured. AI features disabled.")
}
```

### getOrCreateOrNull — Fail Gracefully

```kotlin
val model: BrowserChatModel? = ChatModelFactory.getOrCreateOrNull(conf)

if (model != null) {
    val response = runBlocking { model.call("Hello!") }
    println(response.content)
} else {
    // No LLM configured — fall back to non-AI behavior
    println("AI features unavailable. Using default behavior.")
}
```

### Try-Catch Pattern

```kotlin
try {
    val model = ChatModelFactory.getOrCreate(conf)
    val response = runBlocking { model.call("Hello!") }
    println(response.content)
} catch (e: IllegalArgumentException) {
    // No LLM configured, or all providers are denied
    logger.warn("LLM not available: ${e.message}")
} catch (e: Exception) {
    // Network error, auth failure, etc.
    logger.error("LLM call failed", e)
}
```

### provider-filtered Approach

```kotlin
fun getModelOrNull(conf: ImmutableConfig): BrowserChatModel? {
    val denied = ChatModelFactory.getDeniedProviders(conf)

    return ChatModelFactory.SUPPORTED_API_KEY_NAMES
        .firstOrNull { keyName -> conf[keyName]?.length ?: 0 > 5 }
        ?.let { _ -> ChatModelFactory.getOrCreateOrNull(conf) }
}
```

---

## Model Caching

`ChatModelFactory` maintains an internal cache: calling the same method with the same parameters
returns the **same model instance**.

```kotlin
val conf = ImmutableConfig()

val model1 = ChatModelFactory.getOrCreate("openai", "gpt-4o", "sk-my-key", conf)
val model2 = ChatModelFactory.getOrCreate("openai", "gpt-4o", "sk-my-key", conf)

println(model1 === model2)  // true — same instance

// Different parameters create different instances:
val model3 = ChatModelFactory.getOrCreate("openai", "gpt-4o-mini", "sk-my-key", conf)
println(model1 === model3)  // false — different model name
```

**Cache key format (by method):**

| Method                              | Cache Key                                     |
|-------------------------------------|-----------------------------------------------|
| `getOrCreate(provider, model, key)` | `{canonical-provider}:{model}:{key}`          |
| `getOrCreateOpenAICompatibleModel()`| `{model}:{key}:{baseUrl}`                     |
| `getOrCreateAnthropicCompatibleModel()`| `{model}:{key}:{baseUrl}`                   |
| `getOrCreateAnthropicModel()`       | `anthropic:{model}:{key}`                     |
| `getOrCreateGeminiModel()`          | `gemini:{model}:{key}`                        |

This makes repeated calls cheap — no model objects are recreated.

---

## Custom Messaging

When embedding Browser4 as a library, customize the messages your end users see.

### Via Configuration (Recommended)

```properties
# Custom documentation URL in error messages
llm.document.path=https://docs.yourcompany.com/llm-setup

# Custom "not configured" message (logged on repeated checks)
llm.not.configured.message=AI features are disabled. Contact your admin to enable them.

# Custom developer guide (shown once when LLM is first detected as unconfigured)
# Set to empty string to suppress entirely.
llm.developer.guide=To enable AI features, set MY_APP_API_KEY and restart. See https://docs.yourcompany.com.
```

Config values take priority over the programmatic API below.

### Via Programmatic API

```kotlin
import ai.platon.pulsar.external.ChatModelFactory

// 1. Change the documentation URL shown in error messages
ChatModelFactory.documentPath = "https://docs.yourcompany.com/llm-setup"

// 2. Change the short "not configured" message (logged on repeated checks)
ChatModelFactory.llmNotConfiguredMessage =
    "AI features are disabled. Contact your admin to enable them."

// 3. Provide a custom one-time developer guide
ChatModelFactory.llmDeveloperGuide = """
    To enable AI features, set the MY_APP_API_KEY environment variable
    and restart the application. See https://docs.yourcompany.com for details.
""".trimIndent()

// 4. Suppress the one-time guide entirely
ChatModelFactory.llmDeveloperGuide = null

// 5. Reset everything back to factory defaults
ChatModelFactory.resetMessagesToDefaults()
```

---

## Complete Examples

### Example 1: Minimal App with Auto-Detection

```kotlin
import ai.platon.pulsar.external.ChatModelFactory
import ai.platon.pulsar.common.config.ImmutableConfig
import kotlinx.coroutines.runBlocking

fun main() {
    val conf = ImmutableConfig()

    // Check if any LLM is configured
    if (!ChatModelFactory.isModelConfigured(conf)) {
        println("No LLM configured. Set OPENAI_API_KEY or another supported key.")
        return
    }

    // Auto-detect and create
    val model = ChatModelFactory.getOrCreate(conf)
    val response = runBlocking { model.call("Explain quantum computing in one paragraph.") }
    println(response.content)
}
```

### Example 2: Multi-Provider Fallback

```kotlin
import ai.platon.pulsar.external.ChatModelFactory
import ai.platon.pulsar.external.BrowserChatModel
import ai.platon.pulsar.common.config.ImmutableConfig

fun getBestModel(conf: ImmutableConfig): BrowserChatModel? {
    // Just use auto-detection — it already scans in priority order.
    // Configure multiple API keys; the first detected provider wins.
    return ChatModelFactory.getOrCreateOrNull(conf)
}
```

### Example 3: Java — Explicit Provider

```java
import ai.platon.pulsar.external.ChatModelFactory;
import ai.platon.pulsar.external.BrowserChatModel;
import ai.platon.pulsar.external.ModelResponse;
import ai.platon.pulsar.common.config.ImmutableConfig;

public class LlmExample {
    public static void main(String[] args) throws Exception {
        ImmutableConfig conf = new ImmutableConfig();

        // Create a model with explicit provider
        BrowserChatModel model = ChatModelFactory.getOrCreate(
            "openai",
            "gpt-4o",
            System.getenv("OPENAI_API_KEY"),
            conf
        );

        // Call the model (blocking wrapper for Java)
        ModelResponse response = model.call("Hello, world!", null);
        System.out.println(response.getContent());
    }
}
```

### Example 4: Java — Auto-Detection with isModelConfigured

```java
import ai.platon.pulsar.external.ChatModelFactory;
import ai.platon.pulsar.external.BrowserChatModel;
import ai.platon.pulsar.common.config.ImmutableConfig;

public class AutoDetectExample {
    public static void main(String[] args) throws Exception {
        ImmutableConfig conf = new ImmutableConfig();

        if (!ChatModelFactory.isModelConfigured(conf, false)) {
            System.out.println("LLM not configured. Set an API key env var.");
            return;
        }

        BrowserChatModel model = ChatModelFactory.getOrCreate(conf);
        // ... use the model
    }
}
```

### Example 5: Switching Models at Runtime

```kotlin
import ai.platon.pulsar.external.ChatModelFactory
import ai.platon.pulsar.external.BrowserChatModel
import ai.platon.pulsar.common.config.ImmutableConfig

class ModelRouter(private val conf: ImmutableConfig) {

    private val cheapModel: BrowserChatModel by lazy {
        ChatModelFactory.getOrCreate("deepseek", "deepseek-chat", conf["DEEPSEEK_API_KEY"]!!, conf)
    }

    private val powerfulModel: BrowserChatModel by lazy {
        ChatModelFactory.getOrCreate("anthropic", "claude-sonnet-4-6", conf["ANTHROPIC_API_KEY"]!!, conf)
    }

    suspend fun answer(question: String, useExpensiveModel: Boolean = false): String {
        val model = if (useExpensiveModel) powerfulModel else cheapModel
        return model.call(question).content
    }
}
```

### Example 6: Custom Provider with Full Lifecycle

```kotlin
import ai.platon.pulsar.external.ChatModelFactory
import ai.platon.pulsar.external.ProviderConfig
import ai.platon.pulsar.external.ApiProtocol
import ai.platon.pulsar.common.config.ImmutableConfig

fun main() {
    // 1. Register a custom provider
    ChatModelFactory.registerProvider(
        ProviderConfig(
            apiKeyName = "ACME_LLM_API_KEY",
            modelNameKey = "ACME_LLM_MODEL_NAME",
            baseUrlKey = "ACME_LLM_BASE_URL",
            defaultModel = "acme-chat-v3",
            defaultBaseUrl = "https://api.acme.com/llm/v1",
            providerName = "acme",
            apiProtocol = ApiProtocol.OPENAI
        )
    )

    // 2. Set the API key
    System.setProperty("ACME_LLM_API_KEY", "sk-acme-key-12345")

    // 3. Use it
    val conf = ImmutableConfig()
    val model = ChatModelFactory.getOrCreate(conf)

    // 4. List registered providers
    println("Registered providers:")
    ChatModelFactory.registeredProviders.forEach {
        println("  ${it.providerName} → ${it.defaultModel} (${it.apiProtocol})")
    }

    // 5. Clean up when done
    ChatModelFactory.unregisterProvider("acme")
}
```

### Example 7: Create a Model from a Custom HTTP Proxy / Gateway

```kotlin
val conf = ImmutableConfig()

// Any OpenAI-compatible endpoint — self-hosted, proxy, or gateway
val model = ChatModelFactory.getOrCreateOpenAICompatibleModel(
    modelName = "codellama-70b",
    apiKey = "sk-local-key",
    baseUrl = "http://localhost:11434/v1",  // Ollama, for example
    conf = conf
)

val response = runBlocking { model.call("Write a hello-world in Rust.") }
println(response.content)
```

---

## Summary

| Task                                  | Method                                                                                      |
|---------------------------------------|---------------------------------------------------------------------------------------------|
| Auto-detect from env var              | `ChatModelFactory.getOrCreate(conf)`                                                        |
| Check if configured                   | `ChatModelFactory.isModelConfigured(conf)`                                                  |
| Create with explicit provider         | `ChatModelFactory.getOrCreate(provider, modelName, apiKey, conf)`                           |
| Create or return null                 | `ChatModelFactory.getOrCreateOrNull(conf)`                                                  |
| OpenAI-compatible with custom URL     | `ChatModelFactory.getOrCreateOpenAICompatibleModel(model, key, url, conf)`                  |
| Anthropic native                      | `ChatModelFactory.getOrCreateAnthropicModel(model, key, conf)`                              |
| Anthropic-compatible with custom URL  | `ChatModelFactory.getOrCreateAnthropicCompatibleModel(model, key, url, conf)`               |
| Gemini native                         | `ChatModelFactory.getOrCreateGeminiModel(model, key, conf)`                                 |
| MiniMax (Anthropic protocol)          | `ChatModelFactory.getOrCreateMinimaxModel(model, key, conf)`                                |
| Register a custom provider            | `ChatModelFactory.registerProvider(config)`                                                 |
| Unregister a custom provider          | `ChatModelFactory.unregisterProvider(name)`                                                 |
| List registered providers             | `ChatModelFactory.registeredProviders`                                                      |
| Check if a provider is denied         | `ChatModelFactory.isProviderDenied(provider, conf)`                                         |
| Get all denied providers              | `ChatModelFactory.getDeniedProviders(conf)`                                                 |
| Set custom doc URL                    | `ChatModelFactory.documentPath = "..."`                                                     |
| Set custom not-configured message     | `ChatModelFactory.llmNotConfiguredMessage = "..."`                                          |
| Set custom developer guide            | `ChatModelFactory.llmDeveloperGuide = "..."`                                                |
| Reset all messages to defaults        | `ChatModelFactory.resetMessagesToDefaults()`                                                |
| Reset cached provider registry        | `ChatModelFactory.resetProviders()`                                                         |
| Override provider list via JSON       | `llm.provider.config.path=/path/to/providers.json`                                          |
| Call the model                        | `model.call(userMessage)` / `model.call(systemMessage, userMessage)`                        |
| Call with vision                      | `model.call(systemMessage, userMessage, imageUrl = url)`                                    |

---

## Related Documents

- [`llm-config.md`](llm-config.md) — Full provider configuration reference
- [`llm-config-advanced.md`](llm-config-advanced.md) — Advanced configuration (generic config, token limits, caching)
- [`ChatModelFactory.kt`](../../../pulsar-core/pulsar-third/pulsar-llm/src/main/kotlin/ai/platon/pulsar/external/ChatModelFactory.kt) — Source code
- [`ChatModelFactoryTest.kt`](../../../pulsar-core/pulsar-third/pulsar-llm/src/test/kotlin/ai/platon/pulsar/external/ChatModelFactoryTest.kt) — Test coverage
