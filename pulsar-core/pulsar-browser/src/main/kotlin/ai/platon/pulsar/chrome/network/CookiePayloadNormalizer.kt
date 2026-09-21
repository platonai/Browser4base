package ai.platon.pulsar.chrome.network

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * Normalizes a raw `Network.getCookies` / `Network.getAllCookies` CDP result into the flat
 * `Map<String, String>` cookie shape exposed by the public driver API.
 *
 * The typed CDP model is not available on every transport. When the CDP traffic is relayed —
 * for example through the browser extension in `attach --extension` — the very same response
 * arrives as generic JSON maps, so casting the elements to
 * [ai.platon.cdt.kt.protocol.types.network.Cookie] throws `ClassCastException`. Reading the raw
 * payload and normalizing it here keeps `getCookies()` / `saveStorageState()` working
 * regardless of the transport.
 *
 * Because normalization goes through JSON, it accepts any element representation
 * (typed cookie, `Map`, `JsonNode`) and always produces the same field set.
 */
object CookiePayloadNormalizer {
    private val mapper = jacksonObjectMapper()
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)

    /**
     * Extracts the cookie entries from a raw CDP command result.
     *
     * Accepts both the full command result (a map carrying a `"cookies"` list) and a bare list
     * of cookie entries, so it also works for transports that unwrap the payload themselves.
     *
     * @param payload the raw result of a cookie CDP command
     * @return the flat cookie maps; empty when the payload carries no cookies
     */
    fun normalize(payload: Any?): List<Map<String, String>> {
        val elements = when (payload) {
            null -> emptyList<Any?>()
            is Map<*, *> -> payload["cookies"] as? List<*> ?: emptyList<Any?>()
            is List<*> -> payload
            else -> emptyList<Any?>()
        }

        return elements.mapNotNull { toFlatCookie(it) }
    }

    /**
     * Converts a single cookie entry into a flat `Map<String, String>`.
     *
     * @param element one cookie, in any representation
     * @return the flat cookie map, or `null` when the element carries no usable cookie field
     */
    fun toFlatCookie(element: Any?): Map<String, String>? {
        if (element == null) return null

        // Preferred path: let Jackson coerce every scalar into its textual form. This is
        // byte-for-byte what the typed read produced before, so the wire shape of the cookie
        // surface — and therefore saveStorageState() output — is unchanged for direct sessions.
        runCatching {
            mapper.readValue<Map<String, String>>(mapper.writeValueAsString(element))
        }.getOrNull()?.let { return it.takeIf { cookie -> cookie.isNotEmpty() } }

        // Fallback path: payloads carrying nested structures (e.g. `partitionKey`) cannot be
        // mapped to Map<String, String>. Keep the scalar fields and drop the nested ones
        // instead of failing the whole read.
        val fields = runCatching {
            mapper.readValue<Map<String, Any?>>(mapper.writeValueAsString(element))
        }.getOrNull() ?: return null

        return fields.entries
            .mapNotNull { (key, value) -> stringifyScalar(value)?.let { key to it } }
            .toMap()
            .takeIf { it.isNotEmpty() }
    }

    /**
     * Renders a scalar cookie field as the string Jackson would have produced, so relayed and
     * typed payloads agree; returns `null` for values that are not scalars.
     */
    private fun stringifyScalar(value: Any?): String? = when (value) {
        null -> null
        is String -> value
        // Serialize through Jackson rather than calling toString(): it renders numbers in
        // plain decimal form and booleans as true/false, matching the typed path.
        is Number, is Boolean -> runCatching { mapper.writeValueAsString(value) }.getOrNull()
        else -> null
    }
}
