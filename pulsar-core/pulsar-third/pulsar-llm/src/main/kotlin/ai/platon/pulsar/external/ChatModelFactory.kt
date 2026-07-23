package ai.platon.pulsar.external

import ai.platon.pulsar.common.config.CapabilityTypes.*
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.common.logging.ThrottlingLogger
import ai.platon.pulsar.external.impl.CachedBrowserChatModel
import dev.langchain4j.model.anthropic.AnthropicChatModel
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Which API protocol a provider speaks.
 *
 * Determines the underlying LangChain4j model builder used in
 * [ChatModelFactory].  Most providers speak the OpenAI chat-completions
 * protocol; a growing number also implement Anthropic Messages.
 */
enum class ApiProtocol {
    /** OpenAI-compatible chat completions protocol (most providers). */
    OPENAI,

    /** Anthropic Messages protocol (Claude, MiniMax, etc.). */
    ANTHROPIC,

    /** Google Gemini protocol. */
    GEMINI,
}

/**
 * Configuration for an LLM provider.
 *
 * Used to register both built-in and custom providers in the data-driven
 * provider registry.  Each entry declares the configuration keys, default
 * model/base URL, capabilities, and API protocol of a provider.
 *
 * @property apiKeyName     The config key for the API key (e.g. `"OPENAI_API_KEY"`).
 * @property modelNameKey   The config key for the model name override (e.g. `"OPENAI_MODEL_NAME"`).
 * @property baseUrlKey     The config key for the base URL override (e.g. `"OPENAI_BASE_URL"`).
 * @property defaultModel   The default model name when none is configured.
 * @property defaultBaseUrl The default API base URL endpoint.
 * @property providerName   The canonical provider name for use with [getOrCreate] (provider, modelName, apiKey, conf).
 * @property supportVision  Whether the provider's default model supports vision (image input).
 *                          Defaults to `true`; set to `false` for text-only providers.
 * @property apiProtocol    The API protocol the provider speaks (defaults to [ApiProtocol.OPENAI]).
 */
data class ProviderConfig(
    val apiKeyName: String,
    val modelNameKey: String,
    val baseUrlKey: String,
    val defaultModel: String,
    val defaultBaseUrl: String,
    val providerName: String,
    val supportVision: Boolean = true,
    val apiProtocol: ApiProtocol = ApiProtocol.OPENAI,
)

/**
 * The factory to create models.
 *
 * Supports all major LLM providers through a data-driven registry keyed by
 * [ProviderConfig.apiProtocol].  OpenAI-compatible providers are handled via
 * [OpenAiChatModel], Anthropic-compatible via [AnthropicChatModel], and
 * Google Gemini via [GoogleAiGeminiChatModel] — each with the appropriate
 * base URL taken from the provider's configuration.
 */
object ChatModelFactory {
    private val logger = getLogger(this::class)
    private val throttlingLogger = ThrottlingLogger(logger, ttl = Duration.ofHours(4))
    private val models = ConcurrentHashMap<String, BrowserChatModel>()

    private val llmGuideReported = AtomicBoolean(false)

    private val defaultDocumentPath = "https://github.com/platonai/browser4base/blob/master/docs/config/llm/llm-config.md"

    /**
     * The URL pointing to the LLM configuration documentation.
     *
     * Customize this to point to your own documentation when embedding Browser4.
     */
    @JvmField
    var documentPath: String = defaultDocumentPath

    /**
     * A short message logged when the LLM is not configured (throttled, shown on
     * repeated checks).  Customize this for your own application's terminology.
     */
    @JvmField
    var llmNotConfiguredMessage: String = "No LLM configured. AI features turned off."

    /**
     * The full developer guide shown **once** when the LLM is detected as
     * unconfigured.  Set a custom value (or `null` to suppress) for your own
     * embedding, or leave the default which includes setup instructions.
     */
    @JvmField
    var llmDeveloperGuide: String? = buildDefaultDeveloperGuide(defaultDocumentPath)

    /**
     * Reset [documentPath], [llmNotConfiguredMessage], and [llmDeveloperGuide] to
     * their factory defaults.  Useful in tests and when re-initialising the factory.
     */
    @JvmStatic
    fun resetMessagesToDefaults() {
        documentPath = defaultDocumentPath
        llmNotConfiguredMessage = "No LLM configured. AI features turned off."
        llmDeveloperGuide = buildDefaultDeveloperGuide(defaultDocumentPath)
    }

    @PublishedApi
    internal fun buildDefaultDeveloperGuide(path: String): String {
        return $$"""
The LLM is not configured — AI-powered features are disabled.

To enable LLM features, set an API key for any supported provider.

### Set an Environment Variable

**Linux / macOS:**

```shell
export OPENROUTER_API_KEY=sk-or-v1-your-key-here
```

**Windows (PowerShell):**

```powershell
$env:OPENROUTER_API_KEY = "sk-or-v1-your-key-here"
```

**Windows (cmd.exe):**

```cmd
set OPENROUTER_API_KEY=sk-or-v1-your-key-here
```

Popular alternatives: `OPENAI_API_KEY`, `DEEPSEEK_API_KEY`, `ANTHROPIC_API_KEY`, `GEMINI_API_KEY`.
See the documentation for the full list.

### Run with Java

```shell
java -DOPENROUTER_API_KEY=sk-or-v1-your-key-here -jar Browser4.jar
```

### Run with Docker

```shell
docker run -d -p 8082:8082 \
  -e OPENROUTER_API_KEY=sk-or-v1-your-key-here \
  galaxyeye88/pulsar:latest
```

### Use a Configuration File

Place your API key in `config/application.properties`:

```properties
openrouter.api.key=sk-or-v1-your-key-here
```

For a complete list of supported providers and advanced configuration,
see the [LLM configuration documentation]($${path}).
"""
    }

    // ---------------------------------------------------------------------------
    // Provider registry — ordered by priority (first match wins)
    // ---------------------------------------------------------------------------

    /**
     * User-registered providers checked **before** built-in providers in
     * [getOrCreate], giving custom providers higher priority.
     *
     * Use [registerProvider] / [unregisterProvider] to add or remove entries.
     */
    private val _registeredProviders: MutableList<ProviderConfig> = mutableListOf()

    /** Read-only view of user-registered providers. */
    val registeredProviders: List<ProviderConfig>
        get() = synchronized(_registeredProviders) { _registeredProviders.toList() }

    /**
     * Built-in providers checked in [getOrCreate] by API key presence.
     * These are checked **after** [_registeredProviders], so custom registered
     * providers take precedence.
     *
     * **Order matters** — the first provider with a matching API key wins.
     * OpenRouter is checked first as the universal gateway; dedicated provider
     * keys follow.  Chinese domestic providers are grouped together for clarity.
     * Non-OpenAI-compatible providers (Anthropic, Gemini, MiniMax) are checked
     * last so dedicated API keys take priority over generic fallbacks.
     */
    private val builtinProviders: List<ProviderConfig> = listOf(
        // ---- Global / gateway ----
        ProviderConfig(
            apiKeyName = "OPENROUTER_API_KEY",
            modelNameKey = "OPENROUTER_MODEL_NAME",
            baseUrlKey = "OPENROUTER_BASE_URL",
            defaultModel = "bytedance-seed/seed-1.6",
            defaultBaseUrl = "https://openrouter.ai/api/v1",
            providerName = "openrouter"
        ),

        // ---- Global OpenAI-compatible providers ----
        ProviderConfig(
            apiKeyName = "GROQ_API_KEY",
            modelNameKey = "GROQ_MODEL_NAME",
            baseUrlKey = "GROQ_BASE_URL",
            defaultModel = "llama-3.3-70b-versatile",
            defaultBaseUrl = "https://api.groq.com/openai/v1",
            providerName = "groq",
            supportVision = false,  // Llama 3.3 70B is text-only
        ),
        ProviderConfig(
            apiKeyName = "TOGETHER_API_KEY",
            modelNameKey = "TOGETHER_MODEL_NAME",
            baseUrlKey = "TOGETHER_BASE_URL",
            defaultModel = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
            defaultBaseUrl = "https://api.together.xyz/v1",
            providerName = "together",
            supportVision = false,  // Llama 3.3 70B is text-only
        ),
        ProviderConfig(
            apiKeyName = "MISTRAL_API_KEY",
            modelNameKey = "MISTRAL_MODEL_NAME",
            baseUrlKey = "MISTRAL_BASE_URL",
            defaultModel = "mistral-large-latest",
            defaultBaseUrl = "https://api.mistral.ai/v1",
            providerName = "mistral"
        ),
        ProviderConfig(
            apiKeyName = "XAI_API_KEY",
            modelNameKey = "XAI_MODEL_NAME",
            baseUrlKey = "XAI_BASE_URL",
            defaultModel = "grok-4.5",
            defaultBaseUrl = "https://api.x.ai/v1",
            providerName = "xai"
        ),
        ProviderConfig(
            apiKeyName = "PERPLEXITY_API_KEY",
            modelNameKey = "PERPLEXITY_MODEL_NAME",
            baseUrlKey = "PERPLEXITY_BASE_URL",
            defaultModel = "llama-3.1-sonar-large-128k-online",
            defaultBaseUrl = "https://api.perplexity.ai",
            providerName = "perplexity",
            supportVision = false,  // online/search-grounded models are text-only
        ),
        ProviderConfig(
            apiKeyName = "FIREWORKS_API_KEY",
            modelNameKey = "FIREWORKS_MODEL_NAME",
            baseUrlKey = "FIREWORKS_BASE_URL",
            defaultModel = "accounts/fireworks/models/llama-v3p3-70b-instruct",
            defaultBaseUrl = "https://api.fireworks.ai/inference/v1",
            providerName = "fireworks",
            supportVision = false,  // Llama 3.3 70B is text-only
        ),

        // ---- Chinese domestic providers ----
        ProviderConfig(
            apiKeyName = "DEEPSEEK_API_KEY",
            modelNameKey = "DEEPSEEK_MODEL_NAME",
            baseUrlKey = "DEEPSEEK_BASE_URL",
            defaultModel = "deepseek-v4-flash",
            defaultBaseUrl = "https://api.deepseek.com/v1",
            providerName = "deepseek",
            supportVision = false,  // cloud API is text-only
        ),
        ProviderConfig(
            apiKeyName = "DASHSCOPE_API_KEY",
            modelNameKey = "DASHSCOPE_MODEL_NAME",
            baseUrlKey = "DASHSCOPE_BASE_URL",
            defaultModel = "qwen3.6-plus",
            defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            providerName = "bailian"
        ),
        ProviderConfig(
            apiKeyName = "VOLCENGINE_API_KEY",
            modelNameKey = "VOLCENGINE_MODEL_NAME",
            baseUrlKey = "VOLCENGINE_BASE_URL",
            defaultModel = "doubao-seed-2-0-pro-260215",
            defaultBaseUrl = "https://ark.cn-beijing.volces.com/api/v3",
            providerName = "volcengine"
        ),
        ProviderConfig(
            apiKeyName = "ZHIPU_API_KEY",
            modelNameKey = "ZHIPU_MODEL_NAME",
            baseUrlKey = "ZHIPU_BASE_URL",
            defaultModel = "glm-5.1",
            defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4/",
            providerName = "zhipu"
        ),
        ProviderConfig(
            apiKeyName = "MOONSHOT_API_KEY",
            modelNameKey = "MOONSHOT_MODEL_NAME",
            baseUrlKey = "MOONSHOT_BASE_URL",
            defaultModel = "kimi-k2.6",
            defaultBaseUrl = "https://api.moonshot.cn/v1",
            providerName = "moonshot"
        ),
        ProviderConfig(
            apiKeyName = "BAICHUAN_API_KEY",
            modelNameKey = "BAICHUAN_MODEL_NAME",
            baseUrlKey = "BAICHUAN_BASE_URL",
            defaultModel = "Baichuan4",
            defaultBaseUrl = "https://api.baichuan-ai.com/v1",
            providerName = "baichuan"
        ),
        ProviderConfig(
            apiKeyName = "YI_API_KEY",
            modelNameKey = "YI_MODEL_NAME",
            baseUrlKey = "YI_BASE_URL",
            defaultModel = "yi-large",
            defaultBaseUrl = "https://api.lingyiwanwu.com/v1",
            providerName = "yi",
            supportVision = false,  // yi-large is text-only; use yi-vision for images
        ),
        ProviderConfig(
            apiKeyName = "STEPFUN_API_KEY",
            modelNameKey = "STEPFUN_MODEL_NAME",
            baseUrlKey = "STEPFUN_BASE_URL",
            defaultModel = "step-3.5-flash",
            defaultBaseUrl = "https://api.stepfun.com/v1",
            providerName = "stepfun"
        ),
        ProviderConfig(
            apiKeyName = "HUNYUAN_API_KEY",
            modelNameKey = "HUNYUAN_MODEL_NAME",
            baseUrlKey = "HUNYUAN_BASE_URL",
            defaultModel = "hunyuan-pro",
            defaultBaseUrl = "https://api.lkeap.cloud.tencent.com/v1",
            providerName = "hunyuan"
        ),
        ProviderConfig(
            apiKeyName = "QIANFAN_API_KEY",
            modelNameKey = "QIANFAN_MODEL_NAME",
            baseUrlKey = "QIANFAN_BASE_URL",
            defaultModel = "ernie-4.0-8k",
            defaultBaseUrl = "https://qianfan.baidubce.com/v2",
            providerName = "qianfan"
        ),

        // ---- Global (checked last so dedicated keys win) ----
        ProviderConfig(
            apiKeyName = "OPENAI_API_KEY",
            modelNameKey = "OPENAI_MODEL_NAME",
            baseUrlKey = "OPENAI_BASE_URL",
            defaultModel = "gpt-5.6-sol",
            defaultBaseUrl = "https://api.openai.com/v1",
            providerName = "openai"
        ),

        // ---- Non-OpenAI-compatible providers ----
        ProviderConfig(
            apiKeyName = "ANTHROPIC_API_KEY",
            modelNameKey = "ANTHROPIC_MODEL_NAME",
            baseUrlKey = "ANTHROPIC_BASE_URL",
            defaultModel = "claude-sonnet-4-6",
            defaultBaseUrl = "https://api.anthropic.com",
            providerName = "anthropic",
            apiProtocol = ApiProtocol.ANTHROPIC,
        ),
        ProviderConfig(
            apiKeyName = "GOOGLE_GENERATIVE_AI_API_KEY",
            modelNameKey = "GEMINI_MODEL_NAME",
            baseUrlKey = "GEMINI_BASE_URL",
            defaultModel = "gemini-3.1-flash-lite",
            defaultBaseUrl = "https://generativelanguage.googleapis.com",
            providerName = "gemini",
            apiProtocol = ApiProtocol.GEMINI,
        ),
        // MiniMax uses Anthropic Messages protocol (not OpenAI-compatible).
        ProviderConfig(
            apiKeyName = "MINIMAX_API_KEY",
            modelNameKey = "MINIMAX_MODEL_NAME",
            baseUrlKey = "MINIMAX_BASE_URL",
            defaultModel = "MiniMax-M3",
            defaultBaseUrl = "https://api.minimaxi.com/anthropic",
            providerName = "minimax",
            apiProtocol = ApiProtocol.ANTHROPIC,
        ),
    )

    /**
     * Maps alias API key names to the canonical provider name whose config
     * (default model / base URL) should be used.  Used in [getOrCreate] to
     * resolve alternate key names without duplicating ProviderConfig entries.
     */
    private val ALIAS_KEY_TO_PROVIDER: Map<String, String> = mapOf(
        "KIMI_API_KEY" to "moonshot",
        "LINGYI_API_KEY" to "yi",
        "TENCENT_API_KEY" to "hunyuan",
        "BAIDU_API_KEY" to "qianfan",
        "GEMINI_API_KEY" to "gemini",
        "GOOGLE_API_KEY" to "gemini",
    )

    // ---------------------------------------------------------------------------
    // Cached derived collections (invalidated on provider register/unregister)
    // ---------------------------------------------------------------------------

    @Volatile
    private var cachedSupportedApiKeyNames: List<String>? = null
    @Volatile
    private var cachedApiKeyToProvider: Map<String, String>? = null
    @Volatile
    private var cachedKnownProviderNames: Set<String>? = null

    /** All supported API key names checked in [isModelConfigured0]. */
    @JvmStatic
    val SUPPORTED_API_KEY_NAMES: List<String>
        get() {
            cachedSupportedApiKeyNames?.let { return it }
            return buildList {
                val providers = synchronized(_registeredProviders) {
                    _registeredProviders + builtinProviders
                }
                addAll(providers.map { it.apiKeyName })
                // Aliases (Chinese providers + Gemini) — these keys resolve to
                // a canonical provider via ALIAS_KEY_TO_PROVIDER but are not
                // themselves apiKeyNames of any ProviderConfig entry.
                ALIAS_KEY_TO_PROVIDER.keys.forEach { add(it) }
            }.also { cachedSupportedApiKeyNames = it }
        }

    /**
     * Maps API key names to canonical provider names for deny-list resolution.
     * Built from [_registeredProviders] + [builtinProviders] plus non-OpenAI-compatible providers and aliases.
     */
    private fun getApiKeyToProvider(): Map<String, String> {
        cachedApiKeyToProvider?.let { return it }
        return buildMap {
            val providers = synchronized(_registeredProviders) {
                _registeredProviders + builtinProviders
            }
            providers.forEach { put(it.apiKeyName, it.providerName) }
            ALIAS_KEY_TO_PROVIDER.forEach { (aliasKey, canonical) -> put(aliasKey, canonical) }
        }.also { cachedApiKeyToProvider = it }
    }

    /** Canonical provider names from the registry, used for deny-list entry resolution. */
    private fun getKnownProviderNames(): Set<String> {
        cachedKnownProviderNames?.let { return it }
        return buildSet {
            val providers = synchronized(_registeredProviders) {
                _registeredProviders + builtinProviders
            }
            addAll(providers.map { it.providerName })
            add("claude")     // alias for anthropic
            add("google")     // alias for gemini
        }.also { cachedKnownProviderNames = it }
    }

    /** Maps alias names to their canonical provider name. */
    private val ALIAS_TO_CANONICAL: Map<String, String> = mapOf(
        "claude" to "anthropic",
        "google" to "gemini",
    )

    /** Invalidate the cached derived collections when the provider set changes. */
    private fun invalidateCaches() {
        cachedSupportedApiKeyNames = null
        cachedApiKeyToProvider = null
        cachedKnownProviderNames = null
    }

    // ---------------------------------------------------------------------------
    // Provider registration
    // ---------------------------------------------------------------------------

    /**
     * Register a custom LLM provider.
     *
     * Registered providers are checked **before** built-in providers in
     * [getOrCreate], so they take priority over built-in providers with the
     * same API key name.  The provider's [ProviderConfig.apiProtocol] determines
     * which model builder is used (OpenAI, Anthropic, or Gemini).
     *
     * Thread-safe.
     *
     * @param config The [ProviderConfig] describing the custom provider.
     * @throws IllegalArgumentException if a provider with the same canonical
     *   name is already registered (built-in or previously registered).
     */
    @JvmStatic
    fun registerProvider(config: ProviderConfig) {
        val canonical = config.providerName.lowercase().trim()
        synchronized(_registeredProviders) {
            // Check against built-in names
            val builtinNames = builtinProviders.map { it.providerName.lowercase() }.toSet()
            require(canonical !in builtinNames) {
                "Provider '${config.providerName}' conflicts with a built-in provider"
            }
            // Check against already registered names
            val registeredNames = _registeredProviders.map { it.providerName.lowercase() }
            require(canonical !in registeredNames) {
                "Provider '${config.providerName}' is already registered"
            }
            _registeredProviders.add(config)
            invalidateCaches()
            logger.info("Registered LLM provider: {} ({})", config.providerName, config.defaultModel)
        }
    }

    /**
     * Remove a previously registered provider by its canonical name.
     *
     * Built-in providers cannot be unregistered.
     *
     * Thread-safe.
     *
     * @param providerName The canonical provider name (case-insensitive).
     * @return `true` if a registered provider was found and removed, `false` otherwise.
     */
    @JvmStatic
    fun unregisterProvider(providerName: String): Boolean {
        val canonical = providerName.lowercase().trim()
        synchronized(_registeredProviders) {
            val removed = _registeredProviders.removeAll { it.providerName.equals(canonical, ignoreCase = true) }
            if (removed) {
                invalidateCaches()
                logger.info("Unregistered LLM provider: {}", providerName)
            }
            return removed
        }
    }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Check if the model is configured.
     *
     * @param conf The configuration to check.
     * @param verbose Whether to log a message if the model is not configured.
     * @return True if the model is configured, false otherwise.
     */
    fun isModelConfigured(conf: ImmutableConfig, verbose: Boolean = true): Boolean {
        if (!isModelConfigured0(conf)) {
            if (verbose) {
                // Config overrides take priority over the field defaults
                val effectiveShortMessage = conf[LLM_NOT_CONFIGURED_MESSAGE]
                    ?: llmNotConfiguredMessage
                val effectiveGuide = conf[LLM_DEVELOPER_GUIDE]?.ifEmpty { null }
                    ?: llmDeveloperGuide

                if (llmGuideReported.get()) {
                    throttlingLogger.info(effectiveShortMessage)
                }

                if (llmGuideReported.compareAndSet(false, true)) {
                    effectiveGuide?.let { throttlingLogger.info(it) }
                }
            }
            return false
        }

        return true
    }

    /**
     * Check if the model is configured.
     *
     * @param conf The configuration to check.
     * @return True if the model is configured, false otherwise.
     */
    fun hasModel(conf: ImmutableConfig): Boolean {
        return isModelConfigured0(conf)
    }

    /**
     * Check whether a provider is on the deny list.
     *
     * The provider name can be a canonical name (e.g. "openai", "zhipu"),
     * an alias (e.g. "claude" → "anthropic"), or an API key name
     * (e.g. "OPENAI_API_KEY").
     *
     * @param provider The provider name, alias, or API key name to check.
     * @param conf The configuration to read the deny list from.
     * @return True if the provider is denied.
     */
    fun isProviderDenied(provider: String, conf: ImmutableConfig): Boolean {
        val denyList = parseDenyList(conf)
        if (denyList.isEmpty()) return false
        val canonical = resolveCanonicalProviderName(provider) ?: provider.lowercase()
        return canonical in denyList
    }

    /**
     * Get the set of denied provider names from the configuration.
     *
     * @param conf The configuration to read the deny list from.
     * @return A set of canonical provider names that are denied (may be empty).
     */
    fun getDeniedProviders(conf: ImmutableConfig): Set<String> {
        return parseDenyList(conf)
    }

    /**
     * Create a default model by scanning the configuration for known API keys.
     *
     * Checks providers in this order:
     * 1. Registered + built-in providers — by API key presence, dispatched by
     *    [ProviderConfig.apiProtocol] (OpenAI, Anthropic, or Gemini)
     * 2. Alias key resolution (e.g. `GEMINI_API_KEY` → gemini, `KIMI_API_KEY` → moonshot)
     * 3. Generic fallback via `LLM_PROVIDER` / `LLM_NAME` / `LLM_API_KEY`
     *
     * @return The created model.
     * @throws IllegalArgumentException If the configuration is not configured.
     */
    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun getOrCreate(conf: ImmutableConfig): BrowserChatModel {
        // Parse deny list once; thread through to avoid redundant re-parsing
        val denyList = parseDenyList(conf)

        if (!isModelConfigured0(conf, denyList)) {
            val effectiveShortMessage = conf[LLM_NOT_CONFIGURED_MESSAGE] ?: llmNotConfiguredMessage
            val effectiveDocumentPath = conf[LLM_DOCUMENT_PATH] ?: documentPath
            throw IllegalArgumentException("$effectiveShortMessage — see $effectiveDocumentPath")
        }

        // 1. Check all providers (data-driven, all protocols):
        //    registered first (higher priority), then built-in
        val allProviders = synchronized(_registeredProviders) {
            _registeredProviders + builtinProviders
        }
        for (provider in allProviders) {
            if (provider.providerName in denyList) continue
            val apiKey = conf[provider.apiKeyName] ?: continue
            val modelName = conf[provider.modelNameKey] ?: provider.defaultModel
            val baseURL = conf[provider.baseUrlKey] ?: provider.defaultBaseUrl
            return when (provider.apiProtocol) {
                ApiProtocol.OPENAI -> getOrCreateOpenAICompatibleModel(modelName, apiKey, baseURL, conf)
                ApiProtocol.ANTHROPIC -> getOrCreateAnthropicCompatibleModel(modelName, apiKey, baseURL, conf)
                ApiProtocol.GEMINI -> getOrCreateGeminiModel(modelName, apiKey, conf)
            }
        }

        // 1b. Alias resolution (check alternate key names)
        for ((aliasKey, canonicalName) in ALIAS_KEY_TO_PROVIDER) {
            if (canonicalName in denyList) continue
            val key = conf[aliasKey] ?: continue
            val config = builtinProviders.find { it.providerName == canonicalName }!!
            val modelName = conf[config.modelNameKey] ?: config.defaultModel
            val baseURL = conf[config.baseUrlKey] ?: config.defaultBaseUrl
            return when (config.apiProtocol) {
                ApiProtocol.OPENAI -> getOrCreateOpenAICompatibleModel(modelName, key, baseURL, conf)
                ApiProtocol.ANTHROPIC -> getOrCreateAnthropicCompatibleModel(modelName, key, baseURL, conf)
                ApiProtocol.GEMINI -> getOrCreateGeminiModel(modelName, key, conf)
            }
        }

        // 2. Generic fallback via LLM_PROVIDER / LLM_NAME / LLM_API_KEY
        val effectiveDocumentPath = conf[LLM_DOCUMENT_PATH] ?: documentPath
        val provider = requireNotNull(conf[LLM_PROVIDER]) {
            "$LLM_PROVIDER is not set, see $effectiveDocumentPath"
        }
        val modelName = requireNotNull(conf[LLM_NAME]) {
            "$LLM_NAME is not set, see $effectiveDocumentPath"
        }
        val apiKey = requireNotNull(conf[LLM_API_KEY]) {
            "$LLM_API_KEY is not set, see $effectiveDocumentPath"
        }

        return getOrCreateModel0(provider, modelName, apiKey, conf, denyList)
    }

    /**
     * Create a model from explicit provider parameters.
     *
     * @param provider The provider name (e.g. "openai", "deepseek", "anthropic", "gemini", "groq", ...).
     * @param modelName The name of model to create.
     * @param apiKey The API key to use.
     * @return The created model.
     */
    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun getOrCreate(provider: String, modelName: String, apiKey: String, conf: ImmutableConfig) =
        getOrCreateModel0(provider, modelName, apiKey, conf, parseDenyList(conf))

    /**
     * Create a default model, returning null on failure.
     *
     * @return The created model, or null if not configured or creation fails.
     */
    fun getOrCreateOrNull(conf: ImmutableConfig): BrowserChatModel? {
        if (!isModelConfigured(conf)) {
            return null
        }

        return kotlin.runCatching { getOrCreate(conf) }
            .onFailure { logger.warn("Failed to create chat model ", it) }
            .getOrNull()
    }

    /**
     * Create or retrieve a cached [BrowserChatModel] for an OpenAI-compatible provider.
     *
     * @param modelName The model name.
     * @param apiKey The API key.
     * @param baseUrl The base URL of the OpenAI-compatible API.
     * @param conf The immutable configuration.
     */
    fun getOrCreateOpenAICompatibleModel(
        modelName: String, apiKey: String, baseUrl: String, conf: ImmutableConfig
    ): BrowserChatModel {
        val key = "$modelName:$apiKey:$baseUrl"
        return models.computeIfAbsent(key) { createOpenAICompatibleModel0(modelName, apiKey, baseUrl, conf) }
    }

    /**
     * Create or retrieve a cached [BrowserChatModel] for Anthropic Claude.
     *
     * @param modelName The Anthropic model name (e.g. "claude-sonnet-4-5-20250901").
     * @param apiKey The Anthropic API key.
     * @param conf The immutable configuration.
     */
    fun getOrCreateAnthropicModel(
        modelName: String, apiKey: String, conf: ImmutableConfig
    ): BrowserChatModel {
        val key = "anthropic:$modelName:$apiKey"
        return models.computeIfAbsent(key) { createAnthropicChatModel(modelName, apiKey, conf) }
    }

    /**
     * Create or retrieve a cached [BrowserChatModel] for Google Gemini.
     *
     * @param modelName The Gemini model name (e.g. "gemini-2.0-flash").
     * @param apiKey The Google AI API key.
     * @param conf The immutable configuration.
     */
    fun getOrCreateGeminiModel(
        modelName: String, apiKey: String, conf: ImmutableConfig
    ): BrowserChatModel {
        val key = "gemini:$modelName:$apiKey"
        return models.computeIfAbsent(key) { createGeminiChatModel(modelName, apiKey, conf) }
    }

    /**
     * Create or retrieve a cached [BrowserChatModel] for MiniMax.
     *
     * MiniMax uses the Anthropic Messages protocol (not OpenAI-compatible),
     * so this builds an [AnthropicChatModel] pointed at MiniMax's endpoint.
     *
     * @param modelName The MiniMax model name (e.g. "MiniMax-M2.5").
     * @param apiKey The MiniMax API key.
     * @param conf The immutable configuration.
     */
    fun getOrCreateMinimaxModel(
        modelName: String, apiKey: String, conf: ImmutableConfig
    ): BrowserChatModel {
        val key = "minimax:$modelName:$apiKey"
        return models.computeIfAbsent(key) { createMinimaxChatModel(modelName, apiKey, conf) }
    }

    /**
     * Create or retrieve a cached [BrowserChatModel] for an Anthropic-compatible provider.
     *
     * Any provider that implements the Anthropic Messages protocol can use this
     * generic factory.  Built on [AnthropicChatModel] with a customisable base URL.
     *
     * @param modelName The model name (e.g. "claude-sonnet-4-6").
     * @param apiKey The API key for the provider.
     * @param baseUrl The base URL of the Anthropic-compatible API endpoint.
     * @param conf The immutable configuration.
     */
    fun getOrCreateAnthropicCompatibleModel(
        modelName: String, apiKey: String, baseUrl: String, conf: ImmutableConfig
    ): BrowserChatModel {
        val key = "$modelName:$apiKey:$baseUrl"
        return models.computeIfAbsent(key) { createAnthropicCompatibleModel0(modelName, apiKey, baseUrl, conf) }
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    /**
     * Parse the deny list from configuration.
     *
     * Accepts a comma-separated list of provider names (canonical names, aliases,
     * or API key names). Each entry is trimmed, lowercased, and resolved to a
     * canonical provider name.
     *
     * @return A set of canonical provider names that are denied (may be empty).
     */
    private fun parseDenyList(conf: ImmutableConfig): Set<String> {
        val raw = conf[LLM_PROVIDER_DENY_LIST] ?: return emptySet()
        return raw.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .mapNotNull { resolveCanonicalProviderName(it) }
            .toSet()
    }

    /**
     * Resolve a user-supplied name (provider name, alias, or API key name) to a
     * canonical provider name.
     *
     * Resolution order:
     * 1. Direct alias → canonical mapping (e.g. "claude" → "anthropic")
     * 2. Direct match in [KNOWN_PROVIDER_NAMES] (e.g. "openai", "zhipu")
     * 3. Case-insensitive API key name lookup (e.g. "OPENAI_API_KEY" → "openai")
     *
     * @return The canonical provider name, or null if unrecognized.
     */
    private fun resolveCanonicalProviderName(name: String): String? {
        val lower = name.lowercase().trim()

        // 1. Alias → canonical (e.g. "claude" → "anthropic")
        ALIAS_TO_CANONICAL[lower]?.let { return it }

        // 2. Direct match: known provider name
        if (lower in getKnownProviderNames()) return lower

        // 3. Case-insensitive API key name lookup (e.g. "zhipu_api_key" → "zhipu")
        getApiKeyToProvider().entries.find { it.key.equals(lower, ignoreCase = true) }?.let {
            return it.value
        }

        return null
    }

    private fun isModelConfigured0(conf: ImmutableConfig): Boolean {
        return isModelConfigured0(conf, parseDenyList(conf))
    }

    private fun isModelConfigured0(conf: ImmutableConfig, denyList: Set<String>): Boolean {
        val minKeyLen = 5

        // Check all supported API key names (both OpenAI-compatible and native)
        SUPPORTED_API_KEY_NAMES.forEach { keyName ->
            val apiKey = conf[keyName] ?: ""
            if (apiKey.length > minKeyLen) {
                // Skip if the corresponding provider is denied
                val providerName = getApiKeyToProvider()[keyName]
                if (providerName != null && providerName in denyList) {
                    return@forEach
                }
                return true
            }
        }

        // Check legacy configuration
        val provider = conf[LLM_PROVIDER]
        val llm = conf[LLM_NAME]
        val apiKey = conf[LLM_API_KEY] ?: ""

        if (provider != null && llm != null && apiKey.length > minKeyLen) {
            val canonicalProvider = resolveCanonicalProviderName(provider) ?: provider.lowercase()
            if (canonicalProvider !in denyList) {
                return true
            }
        }

        return false
    }

    private fun getOrCreateModel0(
        provider: String, modelName: String, apiKey: String, conf: ImmutableConfig, denyList: Set<String>
    ): BrowserChatModel {
        // Block denied providers at the earliest entry point for explicit creation
        val canonical = resolveCanonicalProviderName(provider) ?: provider.lowercase()
        if (canonical in denyList) {
            throw IllegalArgumentException(
                "Provider '$provider' is on the deny list (${LLM_PROVIDER_DENY_LIST}). " +
                        "Remove it from the deny list to use this provider."
            )
        }

        val key = "$canonical:$modelName:$apiKey"
        return models.computeIfAbsent(key) { doCreateModel(canonical, modelName, apiKey, conf) }
    }

    /**
     * Route to the appropriate model builder based on the provider's [ApiProtocol].
     *
     * The provider name is expected to be canonical at this point (aliases like
     * "claude" / "google" are resolved by [getOrCreateModel0] via
     * [resolveCanonicalProviderName]).
     *
     * - Known providers are looked up in the combined registry and dispatched
     *   according to their [ProviderConfig.apiProtocol].
     * - Unknown providers fall back to OpenAI-compatible as a best-effort default.
     */
    private fun doCreateModel(
        provider: String, modelName: String, apiKey: String, conf: ImmutableConfig
    ): BrowserChatModel {
        logger.info(
            "Creating LLM with provider and model name | {} {} {}",
            provider, modelName, encodeSecretKey(apiKey)
        )

        // Look up in the combined registry (registered first, then built-in)
        val allProviders = synchronized(_registeredProviders) {
            _registeredProviders + builtinProviders
        }
        val config = allProviders.find {
            it.providerName.equals(provider, ignoreCase = true)
        }
        if (config != null) {
            return dispatchProtocol(config.apiProtocol, modelName, apiKey, config.defaultBaseUrl, conf)
        }

        // Unknown provider — best-effort: treat as OpenAI-compatible with a
        // generic base URL; the caller is responsible for ensuring correctness.
        logger.warn(
            "Unknown provider '{}', treating as OpenAI-compatible. " +
                    "Set the base URL via configuration or use getOrCreateOpenAICompatibleModel().",
            provider
        )
        return createOpenAICompatibleModel0(modelName, apiKey, "https://api.openai.com/v1", conf)
    }

    /**
     * Dispatch model creation to the correct builder based on [ApiProtocol].
     *
     * Bypasses the public [models] cache — callers are responsible for caching.
     * This is intentional because [doCreateModel] is called inside
     * [ConcurrentHashMap.computeIfAbsent], which forbids recursive updates.
     */
    private fun dispatchProtocol(
        protocol: ApiProtocol, modelName: String, apiKey: String, baseUrl: String, conf: ImmutableConfig
    ): BrowserChatModel {
        return when (protocol) {
            ApiProtocol.OPENAI -> createOpenAICompatibleModel0(modelName, apiKey, baseUrl, conf)
            ApiProtocol.ANTHROPIC -> createAnthropicCompatibleModel0(modelName, apiKey, baseUrl, conf)
            ApiProtocol.GEMINI -> createGeminiChatModel(modelName, apiKey, conf)
        }
    }

    // ---------------------------------------------------------------------------
    // Provider-specific builders
    // ---------------------------------------------------------------------------

    /**
     * Anthropic Claude via the native [AnthropicChatModel].
     *
     * @see <a href="https://docs.anthropic.com/en/api">Anthropic API</a>
     */
    private fun createAnthropicChatModel(
        modelName: String, apiKey: String, conf: ImmutableConfig
    ): BrowserChatModel {
        val lm = AnthropicChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .maxRetries(2)
            .timeout(Duration.ofSeconds(90))
            .build()
        return CachedBrowserChatModel(lm, conf)
    }

    /**
     * Generic Anthropic-compatible model builder.
     *
     * Any provider that implements the Anthropic Messages protocol (e.g.
     * MiniMax, Bedrock proxy, custom gateways) can be accessed through this
     * builder by supplying the appropriate [baseUrl].
     */
    private fun createAnthropicCompatibleModel0(
        modelName: String, apiKey: String, baseUrl: String, conf: ImmutableConfig
    ): BrowserChatModel {
        val lm = AnthropicChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .modelName(modelName)
            .maxRetries(2)
            .timeout(Duration.ofSeconds(90))
            .build()
        return CachedBrowserChatModel(lm, conf)
    }

    /**
     * Google Gemini via the native [GoogleAiGeminiChatModel].
     *
     * @see <a href="https://ai.google.dev/gemini-api/docs">Gemini API</a>
     */
    private fun createGeminiChatModel(
        modelName: String, apiKey: String, conf: ImmutableConfig
    ): BrowserChatModel {
        val lm = GoogleAiGeminiChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .maxRetries(2)
            .timeout(Duration.ofSeconds(90))
            .build()
        return CachedBrowserChatModel(lm, conf)
    }

    /**
     * MiniMax via [AnthropicChatModel].
     *
     * MiniMax uses the Anthropic Messages protocol.  International endpoint is
     * `https://api.minimax.io/anthropic/v1`; China endpoint is
     * `https://api.minimaxi.com/anthropic/v1`.  The China endpoint is the default.
     *
     * @see <a href="https://platform.minimax.io/docs">MiniMax API</a>
     */
    private fun createMinimaxChatModel(
        modelName: String, apiKey: String, conf: ImmutableConfig
    ): BrowserChatModel {
        return createAnthropicCompatibleModel0(
            modelName, apiKey, "https://api.minimaxi.com/anthropic", conf
        )
    }

    /**
     * Generic OpenAI-compatible model builder.
     *
     * Most modern LLM providers (Groq, Together, Mistral, xAI, Perplexity,
     * Fireworks, DeepSeek, etc.) expose an OpenAI-compatible chat completions
     * endpoint, so [OpenAiChatModel] works for all of them.
     */
    private fun createOpenAICompatibleModel0(
        modelName: String, apiKey: String, baseUrl: String, conf: ImmutableConfig
    ): BrowserChatModel {
        val lm = OpenAiChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .modelName(modelName)
            .logRequests(true)
            .logResponses(true)
            .maxRetries(2)
            .timeout(Duration.ofSeconds(90))
            .build()
        return CachedBrowserChatModel(lm, conf)
    }

    /**
     * Replace characters in the secret key with asterisks except the latest 4 characters for logging.
     */
    private fun encodeSecretKey(key: String): String {
        return if (key.length <= 4) {
            key.replace(Regex("."), "*")
        } else {
            val visiblePart = key.takeLast(4)
            val hiddenPart = key.dropLast(4).replace(Regex("."), "*")
            "$hiddenPart$visiblePart"
        }
    }
}
