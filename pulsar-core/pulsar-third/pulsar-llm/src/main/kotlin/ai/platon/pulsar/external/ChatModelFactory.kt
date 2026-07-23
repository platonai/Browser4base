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
 * Configuration for an OpenAI-compatible LLM provider.
 *
 * @param apiKeyName The config key for the API key (e.g. "OPENAI_API_KEY").
 * @param modelNameKey The config key for the model name override (e.g. "OPENAI_MODEL_NAME").
 * @param baseUrlKey The config key for the base URL override (e.g. "OPENAI_BASE_URL").
 * @param defaultModel The default model name when none is configured.
 * @param defaultBaseUrl The default API base URL.
 * @param providerName The canonical provider name for use with [getOrCreate] (provider, modelName, apiKey, conf).
 */
data class ProviderConfig(
    val apiKeyName: String,
    val modelNameKey: String,
    val baseUrlKey: String,
    val defaultModel: String,
    val defaultBaseUrl: String,
    val providerName: String
)

/**
 * The factory to create models.
 *
 * Supports all major LLM providers through a data-driven registry.
 * OpenAI-compatible providers are handled via [OpenAiChatModel] with the
 * appropriate base URL. Anthropic and Google Gemini use their native
 * LangChain4j modules ([AnthropicChatModel] / [GoogleAiGeminiChatModel]).
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
    var llmNotConfiguredMessage: String =
        "The LLM is not configured, the LLM feature is disabled. " +
                "See docs/config/llm/llm-config.md for more details."

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
        llmNotConfiguredMessage =
            "The LLM is not configured, the LLM feature is disabled. " +
                    "See docs/config/llm/llm-config.md for more details."
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
     * User-registered OpenAI-compatible providers checked **before** built-in
     * providers in [getOrCreate], giving custom providers higher priority.
     *
     * Use [registerProvider] / [unregisterProvider] to add or remove entries.
     */
    private val _registeredProviders: MutableList<ProviderConfig> = mutableListOf()

    /** Read-only view of user-registered providers. */
    val registeredProviders: List<ProviderConfig>
        get() = synchronized(_registeredProviders) { _registeredProviders.toList() }

    /**
     * Built-in OpenAI-compatible providers checked in [getOrCreate] by API key
     * presence.  These are checked **after** [_registeredProviders], so custom
     * registered providers take precedence.
     *
     * **Order matters** — the first provider with a matching API key wins.
     * OpenRouter is checked first as the universal gateway; dedicated provider
     * keys follow. Chinese domestic providers are grouped together for clarity.
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
            providerName = "groq"
        ),
        ProviderConfig(
            apiKeyName = "TOGETHER_API_KEY",
            modelNameKey = "TOGETHER_MODEL_NAME",
            baseUrlKey = "TOGETHER_BASE_URL",
            defaultModel = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
            defaultBaseUrl = "https://api.together.xyz/v1",
            providerName = "together"
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
            providerName = "perplexity"
        ),
        ProviderConfig(
            apiKeyName = "FIREWORKS_API_KEY",
            modelNameKey = "FIREWORKS_MODEL_NAME",
            baseUrlKey = "FIREWORKS_BASE_URL",
            defaultModel = "accounts/fireworks/models/llama-v3p3-70b-instruct",
            defaultBaseUrl = "https://api.fireworks.ai/inference/v1",
            providerName = "fireworks"
        ),

        // ---- Chinese domestic providers ----
        ProviderConfig(
            apiKeyName = "DEEPSEEK_API_KEY",
            modelNameKey = "DEEPSEEK_MODEL_NAME",
            baseUrlKey = "DEEPSEEK_BASE_URL",
            defaultModel = "deepseek-v4-flash",
            defaultBaseUrl = "https://api.deepseek.com/v1",
            providerName = "deepseek"
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
            providerName = "yi"
        ),
        // MiniMax uses Anthropic Messages protocol (not OpenAI-compatible).
        // See dedicated handling in getOrCreate() → createMinimaxChatModel().
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
    )

    /** All supported API key names checked in [isModelConfigured0]. */
    @JvmStatic
    val SUPPORTED_API_KEY_NAMES: List<String>
        get() = buildList {
            val providers = synchronized(_registeredProviders) {
                _registeredProviders + builtinProviders
            }
            addAll(providers.map { it.apiKeyName })
            // Non-OpenAI-compatible providers
            add("ANTHROPIC_API_KEY")
            add("GOOGLE_GENERATIVE_AI_API_KEY")  // primary Google key name
            add("GEMINI_API_KEY")                 // alias for Google
            add("GOOGLE_API_KEY")                 // alias for Google
            // MiniMax — uses Anthropic protocol (not in builtinProviders)
            add("MINIMAX_API_KEY")
            // Aliases for Chinese providers
            add("KIMI_API_KEY")         // alias for MOONSHOT_API_KEY
            add("LINGYI_API_KEY")       // alias for YI_API_KEY
            add("TENCENT_API_KEY")      // alias for HUNYUAN_API_KEY
            add("BAIDU_API_KEY")        // alias for QIANFAN_API_KEY
        }

    /**
     * Maps API key names to canonical provider names for deny-list resolution.
     * Built from [_registeredProviders] + [builtinProviders] plus non-OpenAI-compatible providers and aliases.
     */
    private val API_KEY_TO_PROVIDER: Map<String, String>
        get() = buildMap {
            val providers = synchronized(_registeredProviders) {
                _registeredProviders + builtinProviders
            }
            providers.forEach { put(it.apiKeyName, it.providerName) }
            put("ANTHROPIC_API_KEY", "anthropic")
            put("GOOGLE_GENERATIVE_AI_API_KEY", "gemini")
            put("GEMINI_API_KEY", "gemini")
            put("GOOGLE_API_KEY", "gemini")
            put("MINIMAX_API_KEY", "minimax")
            // Aliases
            put("KIMI_API_KEY", "moonshot")
            put("LINGYI_API_KEY", "yi")
            put("TENCENT_API_KEY", "hunyuan")
            put("BAIDU_API_KEY", "qianfan")
        }

    /** Canonical provider names from the registry, used for deny-list entry resolution. */
    private val KNOWN_PROVIDER_NAMES: Set<String>
        get() = buildSet {
            val providers = synchronized(_registeredProviders) {
                _registeredProviders + builtinProviders
            }
            addAll(providers.map { it.providerName })
            add("anthropic")
            add("claude")     // alias for anthropic
            add("gemini")
            add("google")     // alias for gemini
            add("minimax")
            // Legacy aliases (matched in doCreateModel's when branch)
            // Already in builtinProviders: deepseek, bailian, volcengine
        }

    /** Maps alias names to their canonical provider name. */
    private val ALIAS_TO_CANONICAL: Map<String, String> = mapOf(
        "claude" to "anthropic",
        "google" to "gemini",
    )

    // ---------------------------------------------------------------------------
    // Provider registration
    // ---------------------------------------------------------------------------

    /**
     * Register a custom OpenAI-compatible LLM provider.
     *
     * Registered providers are checked **before** built-in providers in
     * [getOrCreate], so they take priority over built-in providers with the
     * same API key name.
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
     * 1. OpenAI-compatible providers ([_registeredProviders] + [builtinProviders]) — by API key presence
     * 2. Anthropic (`ANTHROPIC_API_KEY`)
     * 3. Google Gemini (`GEMINI_API_KEY` or `GOOGLE_API_KEY`)
     * 4. Generic fallback via `LLM_PROVIDER` / `LLM_NAME` / `LLM_API_KEY`
     *
     * @return The created model.
     * @throws IllegalArgumentException If the configuration is not configured.
     */
    @Throws(IllegalArgumentException::class)
    fun getOrCreate(conf: ImmutableConfig): BrowserChatModel {
        if (!isModelConfigured(conf, verbose = false)) {
            val effectiveShortMessage = conf[LLM_NOT_CONFIGURED_MESSAGE] ?: llmNotConfiguredMessage
            val effectiveDocumentPath = conf[LLM_DOCUMENT_PATH] ?: documentPath
            throw IllegalArgumentException("$effectiveShortMessage — see $effectiveDocumentPath")
        }

        val denyList = parseDenyList(conf)

        // 1. Check all OpenAI-compatible providers (data-driven):
        //    registered first (higher priority), then built-in
        val allProviders = synchronized(_registeredProviders) {
            _registeredProviders + builtinProviders
        }
        for (provider in allProviders) {
            if (provider.providerName in denyList) continue
            val apiKey = conf[provider.apiKeyName]
            if (apiKey != null) {
                val modelName = conf[provider.modelNameKey] ?: provider.defaultModel
                val baseURL = conf[provider.baseUrlKey] ?: provider.defaultBaseUrl
                return getOrCreateOpenAICompatibleModel(modelName, apiKey, baseURL, conf)
            }
        }

        // 1b. Alias resolution for Chinese providers (check alternate key names)
        if ("moonshot" !in denyList) {
            val kimiKey = conf["KIMI_API_KEY"]
            if (kimiKey != null) {
                val config = builtinProviders.find { it.providerName == "moonshot" }!!
                val modelName = conf[config.modelNameKey] ?: config.defaultModel
                val baseURL = conf[config.baseUrlKey] ?: config.defaultBaseUrl
                return getOrCreateOpenAICompatibleModel(modelName, kimiKey, baseURL, conf)
            }
        }
        if ("yi" !in denyList) {
            val lingyiKey = conf["LINGYI_API_KEY"]
            if (lingyiKey != null) {
                val config = builtinProviders.find { it.providerName == "yi" }!!
                val modelName = conf[config.modelNameKey] ?: config.defaultModel
                val baseURL = conf[config.baseUrlKey] ?: config.defaultBaseUrl
                return getOrCreateOpenAICompatibleModel(modelName, lingyiKey, baseURL, conf)
            }
        }
        if ("hunyuan" !in denyList) {
            val tencentKey = conf["TENCENT_API_KEY"]
            if (tencentKey != null) {
                val config = builtinProviders.find { it.providerName == "hunyuan" }!!
                val modelName = conf[config.modelNameKey] ?: config.defaultModel
                val baseURL = conf[config.baseUrlKey] ?: config.defaultBaseUrl
                return getOrCreateOpenAICompatibleModel(modelName, tencentKey, baseURL, conf)
            }
        }
        if ("qianfan" !in denyList) {
            val baiduKey = conf["BAIDU_API_KEY"]
            if (baiduKey != null) {
                val config = builtinProviders.find { it.providerName == "qianfan" }!!
                val modelName = conf[config.modelNameKey] ?: config.defaultModel
                val baseURL = conf[config.baseUrlKey] ?: config.defaultBaseUrl
                return getOrCreateOpenAICompatibleModel(modelName, baiduKey, baseURL, conf)
            }
        }

        // 2. MiniMax (Anthropic Messages protocol — uses AnthropicChatModel)
        if ("minimax" !in denyList) {
            val minimaxApiKey = conf["MINIMAX_API_KEY"]
            if (minimaxApiKey != null) {
                val modelName = conf["MINIMAX_MODEL_NAME"] ?: "MiniMax-M3"
                return getOrCreateMinimaxModel(modelName, minimaxApiKey, conf)
            }
        }

        // 3. Anthropic (native AnthropicChatModel)
        if ("anthropic" !in denyList && "claude" !in denyList) {
            val anthropicApiKey = conf["ANTHROPIC_API_KEY"]
            if (anthropicApiKey != null) {
                val modelName = conf["ANTHROPIC_MODEL_NAME"] ?: "claude-sonnet-4-6"
                return getOrCreateAnthropicModel(modelName, anthropicApiKey, conf)
            }
        }

        // 4. Google Gemini (non-OpenAI-compatible — uses native GoogleAiGeminiChatModel)
        if ("gemini" !in denyList && "google" !in denyList) {
            val geminiApiKey = conf["GOOGLE_GENERATIVE_AI_API_KEY"] ?: conf["GEMINI_API_KEY"] ?: conf["GOOGLE_API_KEY"]
            if (geminiApiKey != null) {
                val modelName = conf["GEMINI_MODEL_NAME"] ?: "gemini-3.1-flash-lite"
                return getOrCreateGeminiModel(modelName, geminiApiKey, conf)
            }
        }

        // 5. Generic fallback via LLM_PROVIDER / LLM_NAME / LLM_API_KEY
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

        return getOrCreate(provider, modelName, apiKey, conf)
    }

    /**
     * Create a model from explicit provider parameters.
     *
     * @param provider The provider name (e.g. "openai", "deepseek", "anthropic", "gemini", "groq", ...).
     * @param modelName The name of model to create.
     * @param apiKey The API key to use.
     * @return The created model.
     */
    @Throws(IllegalArgumentException::class)
    fun getOrCreate(provider: String, modelName: String, apiKey: String, conf: ImmutableConfig) =
        getOrCreateModel0(provider, modelName, apiKey, conf)

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
        if (lower in KNOWN_PROVIDER_NAMES) return lower

        // 3. Case-insensitive API key name lookup (e.g. "zhipu_api_key" → "zhipu")
        API_KEY_TO_PROVIDER.entries.find { it.key.equals(lower, ignoreCase = true) }?.let {
            return it.value
        }

        return null
    }

    private fun isModelConfigured0(conf: ImmutableConfig): Boolean {
        val minKeyLen = 5
        val denyList = parseDenyList(conf)

        // Check all supported API key names (both OpenAI-compatible and native)
        SUPPORTED_API_KEY_NAMES.forEach { keyName ->
            val apiKey = conf[keyName] ?: ""
            if (apiKey.length > minKeyLen) {
                // Skip if the corresponding provider is denied
                val providerName = API_KEY_TO_PROVIDER[keyName]
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
        provider: String, modelName: String, apiKey: String, conf: ImmutableConfig
    ): BrowserChatModel {
        // Block denied providers at the earliest entry point for explicit creation
        val canonical = resolveCanonicalProviderName(provider) ?: provider.lowercase()
        val denyList = parseDenyList(conf)
        if (canonical in denyList) {
            throw IllegalArgumentException(
                "Provider '$provider' is on the deny list (${LLM_PROVIDER_DENY_LIST}). " +
                        "Remove it from the deny list to use this provider."
            )
        }

        val key = "$provider:$modelName:$apiKey"
        return models.computeIfAbsent(key) { doCreateModel(provider, modelName, apiKey, conf) }
    }

    /**
     * Route to the appropriate model builder based on the provider name.
     *
     * - OpenAI-compatible providers are looked up in [builtinProviders] and
     *   [_registeredProviders]; if found, their default base URL is used
     *   (can be overridden per call — not exposed here; use
     *   [getOrCreateOpenAICompatibleModel] for custom base URLs).
     * - "anthropic" / "claude" uses the native [AnthropicChatModel].
     * - "gemini" / "google" uses the native [GoogleAiGeminiChatModel].
     * - Unknown providers fall back to the DeepSeek-compatible builder.
     */
    private fun doCreateModel(
        provider: String, modelName: String, apiKey: String, conf: ImmutableConfig
    ): BrowserChatModel {
        logger.info(
            "Creating LLM with provider and model name | {} {} {}",
            provider, modelName, encodeSecretKey(apiKey)
        )

        // Look up in the OpenAI-compatible registry (registered first, then built-in)
        val allProviders = synchronized(_registeredProviders) {
            _registeredProviders + builtinProviders
        }
        val config = allProviders.find {
            it.providerName.equals(provider, ignoreCase = true)
        }
        if (config != null) {
            return createOpenAICompatibleModel0(modelName, apiKey, config.defaultBaseUrl, conf)
        }

        return when (provider.lowercase()) {
            // Native LangChain4j providers (non-OpenAI-compatible)
            "anthropic", "claude" -> createAnthropicChatModel(modelName, apiKey, conf)
            "gemini", "google" -> createGeminiChatModel(modelName, apiKey, conf)
            "minimax" -> createMinimaxChatModel(modelName, apiKey, conf)

            // Legacy aliases for backward compatibility
            "bailian" -> createOpenAICompatibleModel0(
                modelName, apiKey,
                "https://dashscope.aliyuncs.com/compatible-mode/v1", conf
            )
            "volcengine" -> createOpenAICompatibleModel0(
                modelName, apiKey,
                "https://ark.cn-beijing.volces.com/api/v3", conf
            )
            "deepseek" -> createOpenAICompatibleModel0(
                modelName, apiKey,
                "https://api.deepseek.com/v1", conf
            )

            // Unknown provider — best-effort: treat as OpenAI-compatible with a
            // generic base URL; the caller is responsible for ensuring correctness.
            else -> {
                logger.warn(
                    "Unknown provider '{}', treating as OpenAI-compatible. " +
                            "Set the base URL via configuration or use getOrCreateOpenAICompatibleModel().",
                    provider
                )
                createOpenAICompatibleModel0(modelName, apiKey, "https://api.openai.com/v1", conf)
            }
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
     * MiniMax uses the Anthropic Messages protocol, so the [AnthropicChatModel]
     * builder is pointed at MiniMax's endpoint. International endpoint is
     * `https://api.minimax.io/anthropic/v1`; China endpoint is
     * `https://api.minimaxi.com/anthropic/v1`. The China endpoint is the default.
     *
     * @see <a href="https://platform.minimax.io/docs">MiniMax API</a>
     */
    private fun createMinimaxChatModel(
        modelName: String, apiKey: String, conf: ImmutableConfig
    ): BrowserChatModel {
        val lm = AnthropicChatModel.builder()
            .apiKey(apiKey)
            .baseUrl("https://api.minimaxi.com/anthropic")
            .modelName(modelName)
            .maxRetries(2)
            .timeout(Duration.ofSeconds(90))
            .build()
        return CachedBrowserChatModel(lm, conf)
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
