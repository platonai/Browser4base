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
 * The provider selection that [ChatModelFactory] resolves for a configuration.
 *
 * The same resolution is used both to create the model ([ChatModelFactory.getOrCreate])
 * and to report it ([ChatModelFactory.describeActiveProvider]), so the provider a
 * user is told about is always the provider that will be called.
 *
 * This is the answer to "why did my request go there?": the selection names the
 * provider, the model, the base URL, and the configuration key that won.
 *
 * @property provider     The canonical provider name (e.g. `"deepseek"`, `"openai"`).
 * @property modelName    The model name the client will request.
 * @property baseUrl      The API base URL the client will call.
 * @property apiKeyName   The configuration key that supplied the API key, e.g.
 *                        `"DEEPSEEK_API_KEY"` or `"llm.apiKey"`.
 * @property apiProtocol  The protocol the client is built with.
 * @property explicit     `true` when the provider was named by `llm.provider`,
 *                        `false` when it was auto-detected from the configured keys.
 * @property otherConfiguredApiKeyNames API key names that are configured but were
 *                        **not** selected, in priority order.  A non-empty value
 *                        means the effective routing depends on the built-in
 *                        priority list — the classic surprise when a leftover key
 *                        of another provider shadows the provider the user just
 *                        configured.
 */
data class ProviderSelection(
    val provider: String,
    val modelName: String,
    val baseUrl: String,
    val apiKeyName: String,
    val apiProtocol: ApiProtocol,
    val explicit: Boolean = false,
    val otherConfiguredApiKeyNames: List<String> = emptyList(),
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

    /**
     * The minimum length an API key must have to be considered usable.
     *
     * A key that is absent, blank, or shorter than this is treated as "not
     * configured" — see [isUsableApiKey].
     */
    private const val MIN_API_KEY_LENGTH = 5

    /** The generic OpenAI base URL used for unknown OpenAI-compatible providers. */
    private const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"

    /**
     * Signatures of the provider selections already logged, so that resolving the
     * selection on every LLM call does not turn into a log storm.
     */
    private val reportedSelections = ConcurrentHashMap.newKeySet<String>()

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

    /**
     * Reset the cached provider registry so the next access reloads from
     * the classpath resource (or external override).  Also clears all
     * cached derived collections.
     *
     * Useful in tests and when the external [LLM_PROVIDER_CONFIG_PATH] has
     * changed at runtime.
     */
    @JvmStatic
    fun resetProviders() {
        defaultRegistry = null
        cachedSupportedApiKeyNames = null
        cachedApiKeyToProvider = null
        cachedKnownProviderNames = null
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
     * The default provider registry, loaded lazily from the built-in classpath
     * resource (`providers.json`) on first access.  Cached indefinitely unless
     * [resetProviders] is called.
     *
     * Use [getRegistry] for conf-aware loading that supports external overrides
     * via [LLM_PROVIDER_CONFIG_PATH].
     */
    @Volatile
    private var defaultRegistry: ProviderConfigLoader.Registry? = null

    /** @see ProviderConfigLoader.loadDefault */
    private fun getDefaultRegistry(): ProviderConfigLoader.Registry {
        defaultRegistry?.let { return it }
        return ProviderConfigLoader.loadDefault().also { defaultRegistry = it }
    }

    /**
     * Load the provider registry, preferring an external override file when
     * [LLM_PROVIDER_CONFIG_PATH] is configured.
     */
    private fun getRegistry(conf: ImmutableConfig): ProviderConfigLoader.Registry {
        val overridePath = conf[LLM_PROVIDER_CONFIG_PATH]
        if (!overridePath.isNullOrBlank()) {
            return ProviderConfigLoader.load(conf)
        }
        return getDefaultRegistry()
    }

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
            val registry = getDefaultRegistry()
            return buildList {
                val providers = synchronized(_registeredProviders) {
                    _registeredProviders + registry.providers
                }
                addAll(providers.map { it.apiKeyName })
                // Aliases — these keys resolve to a canonical provider via the
                // alias map but are not themselves apiKeyNames of any ProviderConfig.
                registry.aliases.keys.forEach { add(it) }
            }.also { cachedSupportedApiKeyNames = it }
        }

    /**
     * Maps API key names to canonical provider names for deny-list resolution.
     * Built from registered + built-in providers plus alias map entries.
     */
    private fun getApiKeyToProvider(): Map<String, String> {
        cachedApiKeyToProvider?.let { return it }
        val registry = getDefaultRegistry()
        return buildMap {
            val providers = synchronized(_registeredProviders) {
                _registeredProviders + registry.providers
            }
            providers.forEach { put(it.apiKeyName, it.providerName) }
            registry.aliases.forEach { (aliasKey, canonical) -> put(aliasKey, canonical) }
        }.also { cachedApiKeyToProvider = it }
    }

    /** Canonical provider names from the registry, used for deny-list entry resolution. */
    private fun getKnownProviderNames(): Set<String> {
        cachedKnownProviderNames?.let { return it }
        val registry = getDefaultRegistry()
        return buildSet {
            val providers = synchronized(_registeredProviders) {
                _registeredProviders + registry.providers
            }
            addAll(providers.map { it.providerName })
            addAll(registry.canonicalAliases.keys)
        }.also { cachedKnownProviderNames = it }
    }

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
            val builtinNames = getDefaultRegistry().providers.map { it.providerName.lowercase() }.toSet()
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
        val canonical = resolveCanonicalProviderName(provider, conf) ?: provider.lowercase()
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
     * The provider is resolved by [describeActiveProvider], in this order:
     * 1. `llm.provider` — the explicit selection always wins when it names a
     *    provider that has a usable API key
     * 2. Registered + built-in providers — the first provider with a usable API
     *    key wins, dispatched by [ProviderConfig.apiProtocol] (OpenAI, Anthropic,
     *    or Gemini)
     * 3. Alias key resolution (e.g. `GEMINI_API_KEY` → gemini, `KIMI_API_KEY` → moonshot)
     *
     * A key that is absent, blank, or too short never participates, so a
     * placeholder such as `deepseek.api.key=` cannot shadow a valid key of
     * another provider.  When more than one provider is configured, the winner
     * is logged together with the ignored keys.
     *
     * @return The created model.
     * @throws IllegalArgumentException If the configuration is not configured.
     */
    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun getOrCreate(conf: ImmutableConfig): BrowserChatModel {
        // Parse deny list once; thread through to avoid redundant re-parsing
        val denyList = parseDenyList(conf)
        val effectiveDocumentPath = conf[LLM_DOCUMENT_PATH] ?: documentPath

        if (!isModelConfigured0(conf, denyList)) {
            val effectiveShortMessage = conf[LLM_NOT_CONFIGURED_MESSAGE] ?: llmNotConfiguredMessage
            throw IllegalArgumentException("$effectiveShortMessage — see $effectiveDocumentPath")
        }

        val selection = requireNotNull(resolveSelection(conf, denyList)) {
            "No usable LLM provider found in the configuration, see $effectiveDocumentPath"
        }

        return createModel(selection, conf)
    }

    /**
     * Describe the provider that [getOrCreate] would select for [conf], without
     * creating a model and without any network access.
     *
     * Diagnostics (`doctor`, health endpoints, CLI output) should use this instead
     * of re-implementing the detection rules: it reports the effective provider,
     * model, base URL, and the configuration key that won the selection, plus the
     * configured keys that were ignored.  It is the authoritative answer to
     * "which provider will my requests actually go to?".
     *
     * @param conf The configuration to resolve.
     * @return The resolved [ProviderSelection], or `null` when no provider is
     *         configured, or every configured provider is on the deny list.
     */
    @JvmStatic
    fun describeActiveProvider(conf: ImmutableConfig): ProviderSelection? =
        resolveSelection(conf, parseDenyList(conf))

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
            .mapNotNull { resolveCanonicalProviderName(it, conf) }
            .toSet()
    }

    /**
     * Resolve a user-supplied name (provider name, alias, or API key name) to a
     * canonical provider name.
     *
     * Resolution order:
     * 1. Direct alias → canonical mapping from the registry (e.g. "claude" → "anthropic")
     * 2. Direct match in known provider names (e.g. "openai", "zhipu")
     * 3. Case-insensitive API key name lookup (e.g. "OPENAI_API_KEY" → "openai")
     *
     * Uses [getRegistry] for conf-aware alias resolution, falling back
     * to [getDefaultRegistry] for the API-key-to-provider index.
     *
     * @param name The provider name, alias, or API key name to resolve.
     * @param conf The configuration, used to select the active registry.
     * @return The canonical provider name, or null if unrecognized.
     */
    private fun resolveCanonicalProviderName(name: String, conf: ImmutableConfig): String? {
        val lower = name.lowercase().trim()

        // 1. Alias → canonical from the active registry (supports external overrides)
        getRegistry(conf).canonicalAliases[lower]?.let { return it }

        // 2. Direct match: known provider name (from default registry)
        if (lower in getKnownProviderNames()) return lower

        // 3. Case-insensitive API key name lookup (from default registry)
        getApiKeyToProvider().entries.find { it.key.equals(lower, ignoreCase = true) }?.let {
            return it.value
        }

        return null
    }

    private fun isModelConfigured0(conf: ImmutableConfig): Boolean {
        return isModelConfigured0(conf, parseDenyList(conf))
    }

    /**
     * A provider is configured exactly when [resolveSelection] can select one.
     *
     * Sharing the resolution keeps "is the LLM configured?" and "which provider
     * is used?" from ever disagreeing — the split between them is what made an
     * empty key report as configured while another provider did the routing.
     */
    private fun isModelConfigured0(conf: ImmutableConfig, denyList: Set<String>): Boolean {
        return resolveSelection(conf, denyList) != null
    }

    /** A provider together with one of the configuration keys that can supply its API key. */
    private data class ProviderCandidate(val config: ProviderConfig, val apiKeyName: String)

    /**
     * Resolve the effective provider selection for [conf].
     *
     * Precedence — first match wins:
     * 1. `llm.provider` (explicit), when it names a provider with a usable API key
     * 2. Providers in registry order (registered first, then built-in), by usable
     *    API key presence
     * 3. Provider aliases (`KIMI_API_KEY` → moonshot, ...) in registry order
     *
     * Providers on the deny list never win.  A denied or key-less `llm.provider`
     * logs a warning and falls back to auto-detection instead of failing a
     * configuration that used to work.
     *
     * @return The selection, or `null` when nothing usable is configured.
     */
    private fun resolveSelection(conf: ImmutableConfig, denyList: Set<String>): ProviderSelection? {
        val registry = getRegistry(conf)
        val allProviders = synchronized(_registeredProviders) {
            _registeredProviders + registry.providers
        }

        // Providers first, aliases after — the same order as the documented
        // priority list, so the first usable key wins.
        val candidates = buildList {
            allProviders.forEach { add(ProviderCandidate(it, it.apiKeyName)) }
            registry.aliases.forEach { (aliasKey, canonicalName) ->
                allProviders.find { it.providerName == canonicalName }
                    ?.let { add(ProviderCandidate(it, aliasKey)) }
            }
        }
        val configured = candidates.filter { conf.usableApiKey(it.apiKeyName) != null }

        resolveExplicitSelection(conf, allProviders, configured, denyList)?.let { return it }

        val winner = configured.firstOrNull { it.config.providerName !in denyList } ?: return null

        return ProviderSelection(
            provider = winner.config.providerName,
            modelName = conf[winner.config.modelNameKey]?.takeIf { it.isNotBlank() }
                ?: winner.config.defaultModel,
            baseUrl = conf[winner.config.baseUrlKey]?.takeIf { it.isNotBlank() }
                ?: winner.config.defaultBaseUrl,
            apiKeyName = winner.apiKeyName,
            apiProtocol = winner.config.apiProtocol,
            explicit = false,
            otherConfiguredApiKeyNames = configured.filter { it !== winner }.map { it.apiKeyName },
        )
    }

    /**
     * Resolve an explicit `llm.provider` selection.
     *
     * For a known provider the provider's own key (or `llm.apiKey`) supplies the
     * API key, `llm.name` overrides the model, and the configured base URL of the
     * provider overrides its default.  For an unknown provider the generic
     * `llm.provider` + `llm.name` + `llm.apiKey` form is honored and treated as
     * OpenAI-compatible.
     *
     * @return The selection, or `null` to let auto-detection decide.
     */
    private fun resolveExplicitSelection(
        conf: ImmutableConfig,
        allProviders: List<ProviderConfig>,
        configured: List<ProviderCandidate>,
        denyList: Set<String>,
    ): ProviderSelection? {
        val requested = conf[LLM_PROVIDER]?.takeIf { it.isNotBlank() } ?: return null
        val canonical = resolveCanonicalProviderName(requested, conf) ?: requested.lowercase().trim()

        if (canonical in denyList) {
            logger.warn(
                "{} is set to '{}' which is on the deny list ({}); falling back to auto-detection",
                LLM_PROVIDER, requested, LLM_PROVIDER_DENY_LIST
            )
            return null
        }

        val config = allProviders.find { it.providerName.equals(canonical, ignoreCase = true) }

        // A known provider prefers its own key name; the generic key is accepted as a fallback.
        val keyNames = if (config != null) listOf(config.apiKeyName, LLM_API_KEY) else listOf(LLM_API_KEY)
        val apiKeyName = keyNames.firstOrNull { conf.usableApiKey(it) != null }
        if (apiKeyName == null) {
            logger.warn(
                "{} is set to '{}' but no usable API key is configured for it ({}); " +
                        "falling back to auto-detection",
                LLM_PROVIDER, requested, keyNames.joinToString(", ")
            )
            return null
        }

        val modelName = conf[LLM_NAME]?.takeIf { it.isNotBlank() }
            ?: config?.let { conf[it.modelNameKey]?.takeIf { v -> v.isNotBlank() } ?: it.defaultModel }
        if (modelName == null) {
            logger.warn(
                "{} is set to '{}' but {} is not set and the provider is unknown; " +
                        "falling back to auto-detection",
                LLM_PROVIDER, requested, LLM_NAME
            )
            return null
        }

        return ProviderSelection(
            provider = canonical,
            modelName = modelName,
            baseUrl = config?.let { conf[it.baseUrlKey]?.takeIf { v -> v.isNotBlank() } ?: it.defaultBaseUrl }
                ?: DEFAULT_OPENAI_BASE_URL,
            apiKeyName = apiKeyName,
            apiProtocol = config?.apiProtocol ?: ApiProtocol.OPENAI,
            explicit = true,
            otherConfiguredApiKeyNames = configured.filter { it.apiKeyName != apiKeyName }.map { it.apiKeyName },
        )
    }

    /**
     * Build — or fetch from the cache — the client described by [selection].
     *
     * The selected provider is logged once per distinct selection, together with
     * the keys that lost, so the effective routing is always discoverable without
     * enabling debug logging.
     */
    private fun createModel(selection: ProviderSelection, conf: ImmutableConfig): BrowserChatModel {
        reportSelection(selection)

        val apiKey = conf.usableApiKey(selection.apiKeyName)
        if (apiKey == null) {
            // Only reachable when the configuration is mutated between resolution and
            // creation; fail loudly instead of silently calling with an empty key.
            throw IllegalArgumentException(
                "The API key '${selection.apiKeyName}' of the selected LLM provider " +
                        "'${selection.provider}' disappeared before the client was created"
            )
        }

        return when (selection.apiProtocol) {
            ApiProtocol.OPENAI ->
                getOrCreateOpenAICompatibleModel(selection.modelName, apiKey, selection.baseUrl, conf)
            ApiProtocol.ANTHROPIC ->
                getOrCreateAnthropicCompatibleModel(selection.modelName, apiKey, selection.baseUrl, conf)
            ApiProtocol.GEMINI ->
                getOrCreateGeminiModel(selection.modelName, apiKey, conf)
        }
    }

    /**
     * Log the resolved selection once, and warn when several providers are
     * configured so that the ignored keys are never a silent surprise.
     */
    private fun reportSelection(selection: ProviderSelection) {
        val signature = "${selection.provider}|${selection.modelName}|${selection.baseUrl}|" +
                "${selection.apiKeyName}|${selection.explicit}"
        if (!reportedSelections.add(signature)) {
            return
        }

        logger.info(
            "Using LLM provider | provider={} model={} baseUrl={} apiKey={} selectedBy={}",
            selection.provider,
            selection.modelName,
            selection.baseUrl,
            selection.apiKeyName,
            if (selection.explicit) LLM_PROVIDER else "auto-detection"
        )

        if (selection.otherConfiguredApiKeyNames.isNotEmpty()) {
            logger.warn(
                "Multiple LLM providers are configured; the first key in the built-in priority list wins | " +
                        "using={} ({}); ignored={} | set {} to choose explicitly, or add the unused " +
                        "provider to {}",
                selection.provider,
                selection.apiKeyName,
                selection.otherConfiguredApiKeyNames.joinToString(", "),
                LLM_PROVIDER,
                LLM_PROVIDER_DENY_LIST
            )
        }
    }

    /**
     * An API key is usable when it is present, not blank, and long enough to be a
     * real key.
     *
     * Configuration templates routinely ship empty placeholders
     * (`deepseek.api.key=`, or an unresolved `${DEEPSEEK_API_KEY}`), and a blank
     * value must never win the auto-detection race and shadow a valid key.
     */
    private fun isUsableApiKey(apiKey: String?): Boolean {
        return !apiKey.isNullOrBlank() && apiKey.length > MIN_API_KEY_LENGTH
    }

    /** The configured API key for [keyName], or `null` when it is missing or unusable. */
    private fun ImmutableConfig.usableApiKey(keyName: String): String? {
        return this[keyName]?.takeIf { isUsableApiKey(it) }
    }

    private fun getOrCreateModel0(
        provider: String, modelName: String, apiKey: String, conf: ImmutableConfig, denyList: Set<String>
    ): BrowserChatModel {
        // Block denied providers at the earliest entry point for explicit creation
        val canonical = resolveCanonicalProviderName(provider, conf) ?: provider.lowercase()
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
            _registeredProviders + getRegistry(conf).providers
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
        return createOpenAICompatibleModel0(modelName, apiKey, DEFAULT_OPENAI_BASE_URL, conf)
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
