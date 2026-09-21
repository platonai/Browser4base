package ai.platon.pulsar.chrome.network

import ai.platon.cdt.kt.protocol.types.network.Cookie
import ai.platon.cdt.kt.protocol.types.network.CookiePriority
import ai.platon.cdt.kt.protocol.types.network.CookieSameSite
import ai.platon.cdt.kt.protocol.types.network.CookieSourceScheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Regression tests for issue #10.
 *
 * Over an extension-relayed CDP session the `Network.getCookies` result deserializes to generic
 * maps instead of typed `Cookie` objects, which used to throw
 * `ClassCastException: LinkedHashMap cannot be cast to Cookie` and broke the whole cookie
 * surface (`getCookies()`, `saveStorageState()`). These tests pin the relay shape as well as
 * the typed shape, and assert that both normalize to the same field set.
 */
class CookiePayloadNormalizerTest {

    /** The relay shape: a JSON result whose cookie entries are LinkedHashMaps, not Cookie instances. */
    private fun relayedCookie(value: String = "abc123") = linkedMapOf<String, Any?>(
        "name" to "session-id",
        "value" to value,
        "domain" to ".example.com",
        "path" to "/",
        "expires" to 1790000000.5,
        "size" to 42,
        "httpOnly" to true,
        "secure" to true,
        "session" to false,
        "sameSite" to "Lax",
        "priority" to "Medium",
        "sameParty" to false,
        "sourceScheme" to "Secure",
        "sourcePort" to 443,
    )

    /** The typed shape produced by a directly-connected browser. */
    private fun typedCookie(value: String = "abc123") = Cookie(
        name = "session-id",
        value = value,
        domain = ".example.com",
        path = "/",
        expires = 1790000000.5,
        size = 42,
        httpOnly = true,
        secure = true,
        session = false,
        sameSite = CookieSameSite.LAX,
        priority = CookiePriority.MEDIUM,
        sameParty = false,
        sourceScheme = CookieSourceScheme.SECURE,
        sourcePort = 443,
    )

    @Test
    @DisplayName("relay shape: cookie elements arriving as generic maps are normalized")
    fun testRelayShapeGenericMapsAreNormalized() {
        val cookies = CookiePayloadNormalizer.normalize(mapOf("cookies" to listOf(relayedCookie())))

        assertEquals(1, cookies.size, "the relayed cookie must survive normalization")
        val cookie = cookies.single()
        assertEquals("session-id", cookie["name"])
        assertEquals("abc123", cookie["value"])
        assertEquals(".example.com", cookie["domain"])
        assertEquals("/", cookie["path"])
        assertEquals("Lax", cookie["sameSite"])
        // Values are stringified so downstream parsing (toStorageStateCookie) works unchanged.
        assertEquals("true", cookie["httpOnly"])
        assertEquals("true", cookie["secure"])
        assertEquals("false", cookie["session"])
        // Jackson renders the double in its own textual form; what matters is that it parses
        // back to the exact same instant, and that both transports agree (see the test below).
        assertEquals(1790000000.5, cookie["expires"]?.toDoubleOrNull(), "expires must round-trip losslessly")
    }

    @Test
    @DisplayName("typed shape: Cookie elements produce the same field set")
    fun testTypedShapeProducesSameFieldSet() {
        val cookies = CookiePayloadNormalizer.normalize(mapOf("cookies" to listOf(typedCookie())))

        assertEquals(1, cookies.size)
        val flat = cookies.single()
        assertEquals("session-id", flat["name"])
        assertEquals("abc123", flat["value"])
        assertEquals(".example.com", flat["domain"])
        assertEquals("/", flat["path"])
        assertEquals("Lax", flat["sameSite"])
        assertEquals("true", flat["httpOnly"])
        assertEquals("true", flat["secure"])
    }

    @Test
    @DisplayName("relay and typed shapes normalize to identical cookie maps")
    fun testRelayAndTypedShapesAgree() {
        val relayed = CookiePayloadNormalizer.normalize(mapOf("cookies" to listOf(relayedCookie()))).single()
        val typed = CookiePayloadNormalizer.normalize(mapOf("cookies" to listOf(typedCookie()))).single()

        assertEquals(typed, relayed, "both transports must produce the same cookie shape")
    }

    @Test
    @DisplayName("a bare list payload is accepted as well as the wrapped command result")
    fun testBareListPayloadIsAccepted() {
        val cookies = CookiePayloadNormalizer.normalize(listOf(linkedMapOf("name" to "a", "value" to "b")))

        assertEquals(1, cookies.size)
        assertEquals("a", cookies.single()["name"])
    }

    @Test
    @DisplayName("nested structures such as partitionKey are dropped, not fatal")
    fun testNestedStructuresAreDropped() {
        val payload = mapOf(
            "cookies" to listOf(
                linkedMapOf(
                    "name" to "partitioned",
                    "value" to "v",
                    "partitionKey" to linkedMapOf(
                        "topLevelSite" to "https://example.com",
                        "hasCrossSiteAncestor" to false,
                    ),
                )
            )
        )

        val cookie = CookiePayloadNormalizer.normalize(payload).single()

        assertEquals("partitioned", cookie["name"])
        assertEquals("v", cookie["value"])
        assertTrue("partitionKey" !in cookie, "nested structures must not leak into the flat cookie shape")
    }

    @Test
    @DisplayName("null, empty and malformed payloads yield an empty list instead of throwing")
    fun testDegeneratePayloadsYieldEmptyList() {
        assertTrue(CookiePayloadNormalizer.normalize(null).isEmpty())
        assertTrue(CookiePayloadNormalizer.normalize(emptyMap<String, Any>()).isEmpty())
        assertTrue(CookiePayloadNormalizer.normalize(mapOf("cookies" to emptyList<Any>())).isEmpty())
        assertTrue(CookiePayloadNormalizer.normalize("not a payload").isEmpty())
        assertTrue(CookiePayloadNormalizer.normalize(mapOf("cookies" to listOf("not a cookie"))).isEmpty())
    }

    @Test
    @DisplayName("multiple cookies keep their order and are all normalized")
    fun testMultipleCookiesAreAllNormalized() {
        val payload = mapOf(
            "cookies" to listOf(
                relayedCookie("first"),
                typedCookie("second"),
            )
        )

        val cookies = CookiePayloadNormalizer.normalize(payload)

        assertEquals(2, cookies.size, "a mixed relay/typed payload must not drop entries")
        assertEquals("first", cookies[0]["value"])
        assertEquals("second", cookies[1]["value"])
    }

    @Test
    @DisplayName("an element without any scalar cookie field is skipped")
    fun testElementsWithoutScalarFieldsAreSkipped() {
        assertNull(CookiePayloadNormalizer.toFlatCookie(null))
        assertNull(CookiePayloadNormalizer.toFlatCookie(linkedMapOf("partitionKey" to linkedMapOf("a" to "b"))))
    }
}
