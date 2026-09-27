package ai.platon.pulsar.external

import ai.platon.pulsar.common.AppFiles
import ai.platon.pulsar.common.AppPaths
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
 *                          Registry metadata: it is parsed and reported, but the request path
 *                          does not consult it yet, so an image sent to a text-only provider
 *                          still fails at the provider rather than here.
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

    /**
     * The number of created models kept alive.
     *
     * The cache used to be an unbounded [ConcurrentHashMap]: every distinct
     * (provider, model, API key, base URL) combination stayed reachable for the
     * lifetime of the JVM — including keys rotated by an embedder — and the map
     * keys themselves hold the secrets.  The bound is generous enough that an
     * ordinary configuration never evicts anything.
     *
     * Evicted instances are **not** closed: the factory does not own their
     * lifecycle and a caller may still hold a reference.  Closing is the job of
     * whoever obtained the model.
     */
    @JvmField
    var maxCachedModels: Int = 128

    /**
     * Access-ordered (LRU) model cache, guarded by its own monitor in
     * [cachedModel].
     */
    private val models = object : LinkedHashMap<String, BrowserChatModel>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BrowserChatModel>?): Boolean {
            return size > maxCachedModels
        }
    }

    /**
     * Look up [key] in the bounded model cache, creating and caching it on a miss.
     *
     * The lookup and the creation run under the cache monitor, so concurrent
     * callers still get one instance per key — what
     * [ConcurrentHashMap.computeIfAbsent] provided before the cache was bounded.
     */
    private fun cachedModel(key: String, create: () -> BrowserChatModel): BrowserChatModel {
        return synchronized(models) {
            models[key] ?: create().also { models[key] = it }
        }
    }

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
     * The default MiniMax endpoint.
     *
     * MiniMax speaks the Anthropic Messages protocol.  The international endpoint
     * is `https://api.minimax.io/anthropic`; the China endpoint is
     * `https://api.minimaxi.com/anthropic` and is the default used here.
     */
    private const val DEFAULT_MINIMAX_BASE_URL = "https://api.minimaxi.com/anthropic"

    /**
     * Signatures of the provider selections already logged, so that resolving the
     * selection on every LLM call does not turn into a log storm.
     */
    private val reportedSelections = ConcurrentHashMap.newKeySet<String>()

    /**
     * Bound on [reportedSelections].  Unbounded, a long-lived process that varies
     * its base URLs would accumulate signatures forever; forgetting one at worst
     * re-logs a single line.
     */
    private const val MAX_REPORTED_SELECTIONS = 512

    private val defaultDocumentPath = "https://github.com/platonai/Browser4base/blob/main/docs/config/llm/llm-config.md"

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
        ProviderConfigLoader.resetCache()
        cachedSupportedApiKeyNames = null
        cachedApiKeyToProvider = null
        cachedKnownProviderNames = null
    }

    @PublishedApi
    internal fun buildDefaultDeveloperGuide(path: String): String {
        // The file that is actually read: configuration is loaded once, at startup,
        // from <config>/conf-enabled/ — a path a user cannot guess, and the reason
        // this guide names it instead of a relative "config/application.properties".
        val configFilePath = AppPaths.CONFIG_ENABLED_DIR.resolve(AppFiles.CONFIG_FILE_NAME)
        val configAvailableDir = AppPaths.CONFIG_AVAILABLE_DIR

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

Place your API key in:

```
$$configFilePath
```

```properties
openrouter.api.key=sk-or-v1-your-key-here
```

That file is read once, when the application starts, so restart it afterwards.
A commented template can be written there and enabled with:

```
browser4-cli doctor --fix
```

(or by copying a file from `$$configAvailableDir` into its `conf-enabled` sibling).

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
     * The built-in provider registry (`providers.json` on the classpath).
     *
     * Caching lives in [ProviderConfigLoader], which keeps one parsed registry per
     * source and re-reads an external file when it changes on disk.  Use
     * [getRegistry] for conf-aware loading that supports external overrides via
     * [LLM_PROVIDER_CONFIG_PATH].
     */
    private fun getDefaultRegistry(): ProviderConfigLoader.Registry = ProviderConfigLoader.loadDefault()

    /**
     * The registry [conf] resolves to: the external override file when
     * [LLM_PROVIDER_CONFIG_PATH] is set and present, the built-in list otherwise.
     *
     * Resolving a selection happens on every chat call, so this must stay cached —
     * it used to re-read and re-parse the JSON file on each call.
     */
    private fun getRegistry(conf: ImmutableConfig): ProviderConfigLoader.Registry =
        ProviderConfigLoader.load(conf)

    /**
     * The provider config registered under [providerName] — registered providers
     * first, then the built-in ones — or `null` when the name is unknown.
     */
    private fun findProviderConfig(providerName: String, conf: ImmutableConfig): ProviderConfig? {
        val allProviders = synchronized(_registeredProviders) {
            _registeredProviders + getRegistry(conf).providers
        }
        return allProviders.find { it.providerName.equals(providerName, ignoreCase = true) }
    }

    /** The base URL [config] resolves to for [conf]: the configured override, else its default. */
    private fun effectiveBaseUrl(config: ProviderConfig, conf: ImmutableConfig): String =
        conf[config.baseUrlKey]?.takeIf { it.isNotBlank() } ?: config.defaultBaseUrl

    /** The configured base URL for [providerName], or `null` when none is set. */
    private fun configuredBaseUrl(providerName: String, conf: ImmutableConfig): String? =
        findProviderConfig(providerName, conf)?.let { conf[it.baseUrlKey]?.takeIf { v -> v.isNotBlank() } }

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
            val registry = getDefaultRegistry()
            // Check against built-in names
            val builtinNames = registry.providers.map { it.providerName.lowercase() }.toSet()
            require(canonical !in builtinNames) {
                "Provider '${config.providerName}' conflicts with a built-in provider"
            }
            // Check against already registered names
            val registeredNames = _registeredProviders.map { it.providerName.lowercase() }
            require(canonical !in registeredNames) {
                "Provider '${config.providerName}' is already registered"
            }
            // A canonical alias is translated before the provider lookup, so a
            // provider named after one could never be selected — reject the
            // registration instead of accepting one that silently never wins.
            require(canonical !in registry.canonicalAliases.keys) {
                "Provider '${config.providerName}' conflicts with the provider alias " +
                        "'$canonical' (→ ${registry.canonicalAliases[canonical]}); use a different providerName"
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
        if (isModelConfigured0(conf)) {
            return true
        }

        if (verbose) {
            reportNotConfigured(conf)
        }
        return false
    }

    /**
     * Report an unconfigured LLM: the short message on every check (throttled),
     * and the full developer guide once per JVM.
     *
     * Config values take priority over the field defaults set by an embedder.
     */
    private fun reportNotConfigured(conf: ImmutableConfig) {
        val effectiveShortMessage = conf[LLM_NOT_CONFIGURED_MESSAGE] ?: llmNotConfiguredMessage
        val effectiveGuide = conf[LLM_DEVELOPER_GUIDE]?.ifEmpty { null } ?: llmDeveloperGuide

        if (llmGuideReported.get()) {
            throttlingLogger.info(effectiveShortMessage)
        }

        if (llmGuideReported.compareAndSet(false, true)) {
            effectiveGuide?.let { throttlingLogger.info(it) }
        }
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
        // Parse the deny list once and resolve the selection once: this used to
        // resolve twice (isModelConfigured0, then again for the selection) on a
        // path that runs on every chat call.
        val denyList = parseDenyList(conf)
        val effectiveDocumentPath = conf[LLM_DOCUMENT_PATH] ?: documentPath
        val selection = resolveSelection(conf, denyList)

        if (selection == null) {
            val effectiveShortMessage = conf[LLM_NOT_CONFIGURED_MESSAGE] ?: llmNotConfiguredMessage
            throw IllegalArgumentException("$effectiveShortMessage — see $effectiveDocumentPath")
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
     * This is the entry point behind `PulsarContext.chat()`, so it resolves the
     * selection once and reuses it — it used to resolve in [isModelConfigured]
     * and again in [getOrCreate] on every chat call.
     *
     * @return The created model, or null if not configured or creation fails.
     */
    fun getOrCreateOrNull(conf: ImmutableConfig): BrowserChatModel? {
        val selection = resolveSelection(conf, parseDenyList(conf))
        if (selection == null) {
            reportNotConfigured(conf)
            return null
        }

        return kotlin.runCatching { createModel(selection, conf) }
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
        // Protocol-prefixed: the Anthropic-compatible builder below used to share
        // this exact key, so a gateway serving both protocols could hand back an
        // OpenAI client for an Anthropic request.
        val key = "openai:$modelName:$apiKey:$baseUrl"
        return cachedModel(key) { createOpenAICompatibleModel0(modelName, apiKey, baseUrl, conf) }
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
        // ANTHROPIC_BASE_URL is honored on the configuration path, so it must be
        // honored here too: a gateway configured for the registry used to be
        // bypassed by this entry point.  When unset, the builder's own default is
        // kept, because the provider default (`https://api.anthropic.com`) and that
        // default are not guaranteed to be spelled the same way.
        val configuredBaseUrl = configuredBaseUrl("anthropic", conf)
        if (configuredBaseUrl == null) {
            val key = "anthropic:$modelName:$apiKey"
            return cachedModel(key) { createAnthropicChatModel(modelName, apiKey, conf) }
        }

        val key = "anthropic:$modelName:$apiKey:$configuredBaseUrl"
        return cachedModel(key) { createAnthropicCompatibleModel0(modelName, apiKey, configuredBaseUrl, conf) }
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
        // Only an explicitly configured endpoint is passed through: the builder's
        // own default already carries the right API version, and re-stating the
        // registry default here could silently drop it.
        val configuredBaseUrl = configuredBaseUrl("gemini", conf)
        val key = "gemini:$modelName:$apiKey:${configuredBaseUrl ?: ""}"
        return cachedModel(key) { createGeminiChatModel(modelName, apiKey, configuredBaseUrl, conf) }
    }

    /**
     * Create or retrieve a cached [BrowserChatModel] for MiniMax.
     *
     * MiniMax uses the Anthropic Messages protocol (not OpenAI-compatible),
     * so this builds an [AnthropicChatModel] pointed at MiniMax's endpoint.
     * The endpoint is [DEFAULT_MINIMAX_BASE_URL] unless `MINIMAX_BASE_URL` selects
     * the international one — the China endpoint used to be hard-coded here, so
     * the key worked on the registry path but not through this entry point.
     *
     * @param modelName The MiniMax model name (e.g. "MiniMax-M3").
     * @param apiKey The MiniMax API key.
     * @param conf The immutable configuration.
     */
    fun getOrCreateMinimaxModel(
        modelName: String, apiKey: String, conf: ImmutableConfig
    ): BrowserChatModel {
        val config = findProviderConfig("minimax", conf)
        val baseUrl = config?.let { effectiveBaseUrl(it, conf) } ?: DEFAULT_MINIMAX_BASE_URL
        val key = "minimax:$modelName:$apiKey:$baseUrl"
        return cachedModel(key) { createAnthropicCompatibleModel0(modelName, apiKey, baseUrl, conf) }
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
        // Must stay distinct from the OpenAI-compatible key for the same tuple: a
        // gateway can serve both protocols under one base URL and one key.
        val key = "anthropic:$modelName:$apiKey:$baseUrl"
        return cachedModel(key) { createAnthropicCompatibleModel0(modelName, apiKey, baseUrl, conf) }
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

    /**
     * A provider is configured exactly when [resolveSelection] can select one.
     *
     * Sharing the resolution keeps "is the LLM configured?" and "which provider
     * is used?" from ever disagreeing — the split between them is what made an
     * empty key report as configured while another provider did the routing.
     */
    private fun isModelConfigured0(conf: ImmutableConfig): Boolean {
        return resolveSelection(conf, parseDenyList(conf)) != null
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
            baseUrl = effectiveBaseUrl(winner.config, conf),
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
            baseUrl = config?.let { effectiveBaseUrl(it, conf) } ?: DEFAULT_OPENAI_BASE_URL,
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
        if (reportedSelections.size > MAX_REPORTED_SELECTIONS) {
            reportedSelections.clear()
        }
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

        // Resolve the endpoint and the protocol before the cache lookup: the key
        // used to be provider + model + key only, so a configured base URL was
        // ignored (a built-in provider always got its default endpoint) and a
        // provider that changed protocol on a registry reload kept its old client.
        val config = findProviderConfig(canonical, conf)
        val baseUrl = config?.let { effectiveBaseUrl(it, conf) } ?: DEFAULT_OPENAI_BASE_URL

        val key = "$canonical:${config?.apiProtocol ?: ApiProtocol.OPENAI}:$modelName:$apiKey:$baseUrl"
        return cachedModel(key) { doCreateModel(canonical, config, modelName, apiKey, baseUrl, conf) }
    }

    /**
     * Route to the appropriate model builder based on the provider's [ApiProtocol].
     *
     * The provider name is expected to be canonical at this point (aliases like
     * "claude" / "google" are resolved by [getOrCreateModel0] via
     * [resolveCanonicalProviderName]).
     *
     * [config] is the registry entry the cache key was derived from: the protocol
     * is read from it rather than re-resolved, so the client can never disagree
     * with the key that produced it.
     *
     * - Known providers are dispatched according to their [ProviderConfig.apiProtocol].
     * - Unknown providers fall back to OpenAI-compatible as a best-effort default.
     */
    private fun doCreateModel(
        provider: String, config: ProviderConfig?, modelName: String, apiKey: String,
        baseUrl: String, conf: ImmutableConfig
    ): BrowserChatModel {
        logger.info(
            "Creating LLM with provider and model name | {} {} {}",
            provider, modelName, encodeSecretKey(apiKey)
        )

        if (config == null) {
            // Unknown provider — best-effort: treat as OpenAI-compatible with a
            // generic base URL; the caller is responsible for ensuring correctness.
            logger.warn(
                "Unknown provider '{}', treating as OpenAI-compatible. " +
                        "Set the base URL via configuration or use getOrCreateOpenAICompatibleModel().",
                provider
            )
        }

        return dispatchProtocol(config?.apiProtocol ?: ApiProtocol.OPENAI, modelName, apiKey, baseUrl, conf)
    }

    /**
     * Dispatch model creation to the correct builder based on [ApiProtocol].
     *
     * Bypasses the model cache — callers are responsible for caching.  This is
     * intentional: every creation is already keyed by its caller, and building a
     * nested cache entry while the cache monitor is held would only invite
     * surprises.
     */
    private fun dispatchProtocol(
        protocol: ApiProtocol, modelName: String, apiKey: String, baseUrl: String, conf: ImmutableConfig
    ): BrowserChatModel {
        return when (protocol) {
            ApiProtocol.OPENAI -> createOpenAICompatibleModel0(modelName, apiKey, baseUrl, conf)
            ApiProtocol.ANTHROPIC -> createAnthropicCompatibleModel0(modelName, apiKey, baseUrl, conf)
            ApiProtocol.GEMINI ->
                createGeminiChatModel(modelName, apiKey, configuredBaseUrl("gemini", conf), conf)
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
     * [baseUrl] is applied only when non-null, so the builder keeps its own
     * default otherwise.  A value must include the API version path.
     *
     * @see <a href="https://ai.google.dev/gemini-api/docs">Gemini API</a>
     */
    private fun createGeminiChatModel(
        modelName: String, apiKey: String, baseUrl: String?, conf: ImmutableConfig
    ): BrowserChatModel {
        val builder = GoogleAiGeminiChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
        if (baseUrl != null) {
            builder.baseUrl(baseUrl)
        }
        val lm = builder
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
