package ai.platon.pulsar.external

import ai.platon.pulsar.common.config.CapabilityTypes.LLM_PROVIDER_CONFIG_PATH
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.common.serialize.json.pulsarObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes

/**
 * Loads the provider registry from a JSON configuration file.
 *
 * The default provider list ships as a classpath resource
 * (`/ai/platon/pulsar/external/providers.json`).  Users can override it by
 * setting [LLM_PROVIDER_CONFIG_PATH] to point to an external JSON file — this
 * is the recommended way to update default models without recompiling.
 *
 * The JSON schema matches [ProviderConfig]'s fields plus two alias maps:
 * ```json
 * {
 *   "providers": [ { "apiKeyName": "...", ... } ],
 *   "aliases": { "KIMI_API_KEY": "moonshot", ... },
 *   "canonicalAliases": { "claude": "anthropic", ... }
 * }
 * ```
 *
 * Both sources are parsed once and cached: the registry is resolved on every
 * LLM call, so re-reading and re-parsing the file each time was pure overhead.
 * An external file is re-read when its path, size, or modification time
 * changes, which keeps "edit the JSON, restart nothing" working.
 */
object ProviderConfigLoader {
    private val logger = getLogger(ProviderConfigLoader::class)

    private const val DEFAULT_RESOURCE_PATH = "/ai/platon/pulsar/external/providers.json"

    /**
     * Identifies a parsed external file.  Any change to a component means the
     * cached registry no longer describes the file on disk.
     */
    private data class ExternalCacheKey(val path: String, val size: Long, val lastModifiedMillis: Long)

    /**
     * The parsed content of a providers.json file.
     *
     * @property providers       Ordered list of built-in provider configs.
     * @property aliases         Maps alternate API key names to canonical provider names
     *                           (e.g. `"KIMI_API_KEY"` → `"moonshot"`).
     * @property canonicalAliases Maps user-facing aliases to canonical provider names
     *                           (e.g. `"claude"` → `"anthropic"`).
     */
    data class Registry(
        val providers: List<ProviderConfig> = emptyList(),
        val aliases: Map<String, String> = emptyMap(),
        val canonicalAliases: Map<String, String> = emptyMap(),
    )

    @Volatile
    private var cachedDefault: Registry? = null

    @Volatile
    private var cachedExternalKey: ExternalCacheKey? = null

    @Volatile
    private var cachedExternal: Registry? = null

    /**
     * Load the provider registry, preferring an external override when configured.
     *
     * 1. If [LLM_PROVIDER_CONFIG_PATH] is set and points to an existing file, that
     *    file is parsed as the registry.
     * 2. Otherwise the built-in classpath resource is used.
     *
     * A configured path that does not exist is **reported**: falling back to the
     * built-in list silently made a typo look like "my override had no effect".
     *
     * @param conf The immutable configuration to consult for the override path.
     * @return The parsed [Registry].
     * @throws IllegalStateException if the classpath resource is missing (packaging
     *         error) or the external file cannot be parsed.
     */
    fun load(conf: ImmutableConfig): Registry {
        val overridePath = conf[LLM_PROVIDER_CONFIG_PATH]
        if (overridePath.isNullOrBlank()) {
            return loadDefault()
        }

        val path = Paths.get(overridePath).toAbsolutePath()
        if (!Files.exists(path)) {
            logger.warn(
                "{} is set to '{}', which does not exist; using the built-in provider list instead",
                LLM_PROVIDER_CONFIG_PATH, path
            )
            return loadDefault()
        }

        val key = cacheKeyOrNull(path)
        if (key != null && key == cachedExternalKey) {
            cachedExternal?.let { return it }
        }

        val registry = parseJson(Files.readString(path))
        if (key != null) {
            cachedExternalKey = key
            cachedExternal = registry
        }
        return registry
    }

    /**
     * Load the built-in classpath resource, ignoring any external override.
     *
     * Used by [ChatModelFactory.SUPPORTED_API_KEY_NAMES] and other no-config
     * code paths where an [ImmutableConfig] is not available.
     */
    fun loadDefault(): Registry {
        cachedDefault?.let { return it }

        val json = ProviderConfigLoader::class.java.getResourceAsStream(DEFAULT_RESOURCE_PATH)
            ?.use { it.reader().readText() }
            ?: throw IllegalStateException(
                "Default providers.json not found on classpath: $DEFAULT_RESOURCE_PATH"
            )

        return parseJson(json).also { cachedDefault = it }
    }

    /**
     * Drop every cached registry so the next [load] reads its source again.
     *
     * Called by `ChatModelFactory.resetProviders()`, which is the documented way
     * to pick up a changed [LLM_PROVIDER_CONFIG_PATH] at runtime.
     */
    fun resetCache() {
        cachedDefault = null
        cachedExternalKey = null
        cachedExternal = null
    }

    // ---------------------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------------------

    /**
     * The cache key of [path], or `null` when its attributes cannot be read (the
     * file disappeared, or the file system does not report them) — in which case
     * the caller parses without caching.
     */
    private fun cacheKeyOrNull(path: Path): ExternalCacheKey? = runCatching {
        val attrs = Files.readAttributes(path, BasicFileAttributes::class.java)
        ExternalCacheKey(path.toString(), attrs.size(), attrs.lastModifiedTime().toMillis())
    }.getOrNull()

    private fun parseJson(json: String): Registry {
        return pulsarObjectMapper().readValue(json)
    }
}
