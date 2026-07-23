package ai.platon.pulsar.external

import ai.platon.pulsar.common.config.CapabilityTypes.LLM_PROVIDER_CONFIG_PATH
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.serialize.json.pulsarObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Paths

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
 */
object ProviderConfigLoader {
    private const val DEFAULT_RESOURCE_PATH = "/ai/platon/pulsar/external/providers.json"

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

    /**
     * Load the provider registry, preferring an external override when configured.
     *
     * 1. If [LLM_PROVIDER_CONFIG_PATH] is set and points to an existing file, that
     *    file is parsed as the registry.
     * 2. Otherwise the built-in classpath resource is used.
     *
     * @param conf The immutable configuration to consult for the override path.
     * @return The parsed [Registry].
     * @throws IllegalStateException if the classpath resource is missing (packaging
     *         error) or the external file cannot be parsed.
     */
    fun load(conf: ImmutableConfig): Registry {
        // 1. External override via llm.provider.config.path
        val overridePath = conf[LLM_PROVIDER_CONFIG_PATH]
        if (!overridePath.isNullOrBlank()) {
            val path = Paths.get(overridePath)
            if (Files.exists(path)) {
                val json = Files.readString(path)
                return parseJson(json)
            }
        }

        // 2. Built-in classpath resource
        return loadDefault()
    }

    /**
     * Load the built-in classpath resource, ignoring any external override.
     *
     * Used by [ChatModelFactory.SUPPORTED_API_KEY_NAMES] and other no-config
     * code paths where an [ImmutableConfig] is not available.
     */
    fun loadDefault(): Registry {
        val stream = ProviderConfigLoader::class.java.getResourceAsStream(DEFAULT_RESOURCE_PATH)
            ?: throw IllegalStateException(
                "Default providers.json not found on classpath: $DEFAULT_RESOURCE_PATH"
            )
        val json = stream.reader().readText()
        return parseJson(json)
    }

    // ---------------------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------------------

    private fun parseJson(json: String): Registry {
        return pulsarObjectMapper().readValue(json)
    }
}
