# LLM Configuration Guide

Browser4 supports all major LLM providers through a unified configuration system.
Set an API key environment variable or property, and the system auto-detects the provider.

## Quick Start

```bash
# Set your API key (any supported provider)
export OPENROUTER_API_KEY=sk-or-v1-your-key
# or
export DEEPSEEK_API_KEY=sk-your-key
# or
export OPENAI_API_KEY=sk-your-key

# Run Browser4
java -jar Browser4.jar
```

Or in Docker:

```bash
docker run -d -p 8082:8082 -e OPENROUTER_API_KEY=${OPENROUTER_API_KEY} galaxyeye88/pulsar:latest
```

## Provider Detection

The system scans for known API keys in this order — the first one found wins:

| Priority | Provider | API Key(s) | Model Config | Base URL Config |
|----------|----------|------------|--------------|-----------------|
| 1 | **OpenRouter** | `OPENROUTER_API_KEY` | `OPENROUTER_MODEL_NAME` | `OPENROUTER_BASE_URL` |
| 2 | **Groq** | `GROQ_API_KEY` | `GROQ_MODEL_NAME` | `GROQ_BASE_URL` |
| 3 | **Together AI** | `TOGETHER_API_KEY` | `TOGETHER_MODEL_NAME` | `TOGETHER_BASE_URL` |
| 4 | **Mistral** | `MISTRAL_API_KEY` | `MISTRAL_MODEL_NAME` | `MISTRAL_BASE_URL` |
| 5 | **xAI / Grok** | `XAI_API_KEY` | `XAI_MODEL_NAME` | `XAI_BASE_URL` |
| 6 | **Perplexity** | `PERPLEXITY_API_KEY` | `PERPLEXITY_MODEL_NAME` | `PERPLEXITY_BASE_URL` |
| 7 | **Fireworks AI** | `FIREWORKS_API_KEY` | `FIREWORKS_MODEL_NAME` | `FIREWORKS_BASE_URL` |
| 8 | **DeepSeek** | `DEEPSEEK_API_KEY` | `DEEPSEEK_MODEL_NAME` | `DEEPSEEK_BASE_URL` |
| 9 | **Alibaba DashScope** (Qwen) | `DASHSCOPE_API_KEY` | `DASHSCOPE_MODEL_NAME` | `DASHSCOPE_BASE_URL` |
| 10 | **ByteDance Volcengine** (Doubao) | `VOLCENGINE_API_KEY` | `VOLCENGINE_MODEL_NAME` | `VOLCENGINE_BASE_URL` |
| 11 | **Zhipu AI** (GLM) | `ZHIPU_API_KEY` | `ZHIPU_MODEL_NAME` | `ZHIPU_BASE_URL` |
| 12 | **Moonshot / Kimi** | `MOONSHOT_API_KEY`, `KIMI_API_KEY` | `MOONSHOT_MODEL_NAME` | `MOONSHOT_BASE_URL` |
| 13 | **Baichuan** | `BAICHUAN_API_KEY` | `BAICHUAN_MODEL_NAME` | `BAICHUAN_BASE_URL` |
| 14 | **01.AI / Yi** | `YI_API_KEY`, `LINGYI_API_KEY` | `YI_MODEL_NAME` | `YI_BASE_URL` |
| 15 | **StepFun** | `STEPFUN_API_KEY` | `STEPFUN_MODEL_NAME` | `STEPFUN_BASE_URL` |
| 16 | **Tencent Hunyuan** | `HUNYUAN_API_KEY`, `TENCENT_API_KEY` | `HUNYUAN_MODEL_NAME` | `HUNYUAN_BASE_URL` |
| 17 | **Baidu Qianfan** (Ernie) | `QIANFAN_API_KEY`, `BAIDU_API_KEY` | `QIANFAN_MODEL_NAME` | `QIANFAN_BASE_URL` |
| — | **MiniMax** | `MINIMAX_API_KEY` | `MINIMAX_MODEL_NAME` | (native protocol) |
| — | **Anthropic Claude** | `ANTHROPIC_API_KEY` | `ANTHROPIC_MODEL_NAME` | (native protocol) |
| — | **Google Gemini** | `GOOGLE_GENERATIVE_AI_API_KEY`, `GEMINI_API_KEY`, `GOOGLE_API_KEY` | `GEMINI_MODEL_NAME` | (native protocol) |
| 18 | **OpenAI** | `OPENAI_API_KEY` | `OPENAI_MODEL_NAME` | `OPENAI_BASE_URL` |

> **Notes:**
> - OpenAI is checked last so that dedicated provider keys win over a generic `OPENAI_API_KEY`.
> - **Custom registered providers** (via `ChatModelFactory.registerProvider()`) are checked **before** all built-in providers and take priority.
> - Providers on the `llm.provider.deny.list` are skipped during auto-detection.
> - Non-OpenAI-compatible providers (MiniMax, Anthropic, Gemini) are checked after the
>   OpenAI-compatible list.

## Provider Details

### Global Providers

#### OpenRouter — Universal Gateway
Access 300+ models through a single API key.

```properties
openrouter.api.key=sk-or-v1-your-key
openrouter.model.name=bytedance-seed/seed-1.6
openrouter.base.url=https://openrouter.ai/api/v1/
```

Default model: `bytedance-seed/seed-1.6`
Context window: 256K tokens

#### OpenAI
```properties
openai.api.key=sk-your-key
openai.model.name=gpt-4o
openai.base.url=https://api.openai.com/v1
```

Default model: `gpt-4o`
Context window: 128K tokens

#### Groq — Fast Inference
```properties
groq.api.key=your-key
groq.model.name=llama-3.3-70b-versatile
```

Default model: `llama-3.3-70b-versatile`
Base URL: `https://api.groq.com/openai/v1`
Context window: 128K tokens

#### Together AI
```properties
together.api.key=your-key
together.model.name=meta-llama/Llama-3.3-70B-Instruct-Turbo
```

Default model: `meta-llama/Llama-3.3-70B-Instruct-Turbo`
Base URL: `https://api.together.xyz/v1`
Context window: 128K tokens

#### Mistral
```properties
mistral.api.key=your-key
mistral.model.name=mistral-large-latest
```

Default model: `mistral-large-latest`
Base URL: `https://api.mistral.ai/v1`
Context window: 128K tokens

#### xAI / Grok
```properties
xai.api.key=your-key
xai.model.name=grok-2-1212
```

Default model: `grok-2-1212`
Base URL: `https://api.x.ai/v1`
Context window: 128K tokens

#### Perplexity
```properties
perplexity.api.key=your-key
perplexity.model.name=llama-3.1-sonar-large-128k-online
```

Default model: `llama-3.1-sonar-large-128k-online`
Base URL: `https://api.perplexity.ai`
Context window: 128K tokens

#### Fireworks AI
```properties
fireworks.api.key=your-key
fireworks.model.name=accounts/fireworks/models/llama-v3p3-70b-instruct
```

Default model: `accounts/fireworks/models/llama-v3p3-70b-instruct`
Base URL: `https://api.fireworks.ai/inference/v1`
Context window: 128K tokens

### Chinese Domestic Providers

#### DeepSeek
```properties
deepseek.api.key=sk-your-key
deepseek.model.name=deepseek-chat
deepseek.base.url=https://api.deepseek.com/v1
```

Default model: `deepseek-chat`
Context window: 64K tokens

#### Alibaba DashScope / Qwen (阿里云-百炼)
```properties
dashscope.api.key=sk-your-key
dashscope.model.name=qwen-plus
dashscope.base.url=https://dashscope.aliyuncs.com/compatible-mode/v1
```

Default model: `qwen-plus`
Context window: 131K tokens

#### ByteDance Volcengine / Doubao (字节跳动-火山引擎)
```properties
volcengine.api.key=your-key
volcengine.model.name=doubao-1.5-pro-32k-250115
volcengine.base.url=https://ark.cn-beijing.volces.com/api/v3
```

Default model: `doubao-1.5-pro-32k-250115`
Context window: 256K tokens

#### Zhipu AI / GLM (智谱AI)
```properties
zhipu.api.key=your-key
zhipu.model.name=glm-4-plus
zhipu.base.url=https://open.bigmodel.cn/api/paas/v4/
```

Default model: `glm-4-plus`
Context window: 128K tokens

#### Moonshot / Kimi (月之暗面)
```properties
moonshot.api.key=your-key
# or use the alias:
# kimi.api.key=your-key
moonshot.model.name=moonshot-v1-8k
moonshot.base.url=https://api.moonshot.cn/v1
```

Default model: `moonshot-v1-8k`
Context window: 32K tokens

#### Baichuan (百川智能)
```properties
baichuan.api.key=your-key
baichuan.model.name=Baichuan4
baichuan.base.url=https://api.baichuan-ai.com/v1
```

Default model: `Baichuan4`
Context window: 32K tokens

#### 01.AI / Yi (零一万物)
```properties
yi.api.key=your-key
# or use the alias:
# lingyi.api.key=your-key
yi.model.name=yi-large
yi.base.url=https://api.lingyiwanwu.com/v1
```

Default model: `yi-large`
Context window: 32K tokens

#### MiniMax (稀宇科技)
Uses the **Anthropic Messages protocol** (not OpenAI-compatible). Configured via `AnthropicChatModel` pointed at MiniMax endpoints.

```properties
minimax.api.key=your-key
minimax.model.name=MiniMax-M2.5
```

Default model: `MiniMax-M3`
Base URL (China): `https://api.minimaxi.com/anthropic`
Base URL (International): `https://api.minimax.io/anthropic`
Context window: 1M tokens

#### StepFun (阶跃星辰)
```properties
stepfun.api.key=your-key
stepfun.model.name=step-1-8k
stepfun.base.url=https://api.stepfun.com/v1
```

Default model: `step-1-8k`
Context window: 8K tokens

#### Tencent Hunyuan (腾讯混元)
```properties
hunyuan.api.key=your-key
# or use the alias:
# tencent.api.key=your-key
hunyuan.model.name=hunyuan-pro
hunyuan.base.url=https://api.lkeap.cloud.tencent.com/v1
```

Default model: `hunyuan-pro`
Context window: 32K tokens

#### Baidu Qianfan / Ernie (百度千帆/文心)
```properties
qianfan.api.key=your-key
# or use the alias:
# baidu.api.key=your-key
qianfan.model.name=ernie-4.0-8k
qianfan.base.url=https://qianfan.baidubce.com/v2
```

Default model: `ernie-4.0-8k`
Context window: 8K tokens

### Native Providers (Non-OpenAI-Compatible)

These providers use LangChain4j's native model builders rather than the OpenAI-compatible protocol.

#### Anthropic Claude
```properties
anthropic.api.key=sk-ant-your-key
anthropic.model.name=claude-sonnet-4-5-20250901
```

Default model: `claude-sonnet-4-5-20250901`
Context window: 200K tokens

#### Google Gemini
```properties
# Primary key name:
google.generative.ai.api.key=your-key
# Aliases also supported:
# gemini.api.key=your-key
# google.api.key=your-key
gemini.model.name=gemini-2.0-flash
```

Default model: `gemini-2.0-flash`
Context window: 1M tokens

## Configuration Format

Browser4 uses relaxed configuration binding. All key names are normalized (dots, dashes, underscores, camelCase are equivalent):

```properties
# All of these are equivalent:
OPENROUTER_API_KEY=sk-...
openrouter.api.key=sk-...
openrouter-api-key=sk-...
```

## Advanced: Generic Provider Configuration

For providers not in the built-in registry, use the generic configuration format.
See [`llm-config-advanced.md`](llm-config-advanced.md) for details.

```properties
llm.provider=my-provider
llm.name=my-model
llm.api.key=my-api-key
```

## Advanced: Registering Custom Providers

You can register custom OpenAI-compatible providers programmatically via `ChatModelFactory`.
This is useful when embedding Browser4 as a library and you need to add a provider not in the
built-in registry, or when you want to override a built-in provider's defaults.

### Registering a Provider (Kotlin)

```kotlin
import ai.platon.pulsar.external.ChatModelFactory
import ai.platon.pulsar.external.ProviderConfig

ChatModelFactory.registerProvider(ProviderConfig(
    apiKeyName = "MY_PROVIDER_API_KEY",
    modelNameKey = "MY_PROVIDER_MODEL_NAME",
    baseUrlKey = "MY_PROVIDER_BASE_URL",
    defaultModel = "my-model",
    defaultBaseUrl = "https://api.myprovider.com/v1",
    providerName = "myprovider"
))
```

### Registering a Provider (Java)

```java
import ai.platon.pulsar.external.ChatModelFactory;
import ai.platon.pulsar.external.ProviderConfig;

ChatModelFactory.registerProvider(new ProviderConfig(
    "MY_PROVIDER_API_KEY",
    "MY_PROVIDER_MODEL_NAME",
    "MY_PROVIDER_BASE_URL",
    "my-model",
    "https://api.myprovider.com/v1",
    "myprovider"
));
```

Once registered, the provider participates in auto-detection (API key scanning) and
can be used explicitly:

```kotlin
val model = ChatModelFactory.getOrCreate("myprovider", "my-model", "sk-...", conf)
```

### Provider Priority

**Registered providers take priority over built-in providers.** When scanning for API keys
in `getOrCreate(conf)`, registered providers are checked first. This means you can shadow a
built-in provider by registering a custom provider with the same API key name.

For example, if you register a custom provider with `apiKeyName = "OPENAI_API_KEY"`,
your custom configuration (model name, base URL) will be used instead of the built-in
OpenAI defaults whenever `OPENAI_API_KEY` is detected.

### Unregistering a Provider

```kotlin
// Remove a previously registered provider (returns true if found)
val removed = ChatModelFactory.unregisterProvider("myprovider")
```

Built-in providers cannot be unregistered — attempting to register a provider with the
same canonical name as a built-in will throw `IllegalArgumentException`.

### Listing Registered Providers

```kotlin
// Read-only list of currently registered providers
val providers = ChatModelFactory.registeredProviders
```

## Customizing Messages and Documentation URL

When embedding Browser4, you can customize the log messages and documentation
links shown to your end users — either via configuration properties or the
programmatic API.

### Via Configuration Properties (Recommended)

Set these in `application.properties`, environment variables, or system properties:

```properties
# Override the documentation URL shown in error messages
llm.document.path=https://docs.yourcompany.com/llm-setup

# Override the short "LLM not configured" message (logged on repeated checks)
llm.not.configured.message=AI features are disabled. Contact your admin to enable them.

# Override the one-time developer guide (set to empty to suppress)
llm.developer.guide=To enable AI features, set MY_APP_API_KEY and restart. See https://docs.yourcompany.com.
```

Config values take priority over the programmatic defaults set via
`ChatModelFactory.documentPath`, `llmNotConfiguredMessage`, and `llmDeveloperGuide`.

### Via Programmatic API

Alternatively, set these at runtime from code:

This URL appears in exception messages when the LLM is not configured, and in the
developer guide logged on first detection.

### Custom "Not Configured" Message

```kotlin
ChatModelFactory.llmNotConfiguredMessage =
    "AI features are disabled. Contact your admin to enable them."
```

This short message is logged (throttled) each time the LLM is checked and found
unconfigured.

### Custom Developer Guide

```kotlin
// Provide a custom one-time setup guide
ChatModelFactory.llmDeveloperGuide = """
    To enable AI features, set the MY_APP_API_KEY environment variable
    and restart the application. See https://docs.yourcompany.com for details.
""".trimIndent()

// Or suppress the guide entirely
ChatModelFactory.llmDeveloperGuide = null
```

The developer guide is shown **once** when the LLM is first detected as unconfigured.
Set it to `null` to suppress this one-time message.

### Resetting to Factory Defaults

```kotlin
// Restore the original documentPath, llmNotConfiguredMessage, and llmDeveloperGuide
ChatModelFactory.resetMessagesToDefaults()
```

## Token Limit Configuration

Override the default maximum input token length:

```properties
llm.max.input.token.length=128000
```

Default is 64,000 tokens — a safe floor across all providers.

## Response Cache

LLM responses are cached to reduce costs and latency:

```properties
# Cache TTL in seconds (default: 600 = 10 minutes)
llm.response.cache.ttl=600
```

## Verification

Verify your configuration:

```bash
# Check if the model is configured
curl http://localhost:8082/api/llm/status
```

Or run the built-in health check:

```sql
SELECT LLM_CHAT('What is 11 squared? Return only the number.');
```
