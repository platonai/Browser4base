package ai.platon.pulsar.chrome

import ai.platon.pulsar.api.model.BrowserSettings
import ai.platon.pulsar.common.ResourceLoader
import ai.platon.pulsar.common.config.ImmutableConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Regression tests for the vendored page-world stealth payload `js/stealth.js`.
 *
 * The payload is a curated copy of the browser-side bundle of
 * [puppeteer-extra-plugin-stealth](https://github.com/berstend/puppeteer-extra), registered with
 * `Page.addScriptToEvaluateOnNewDocument` by
 * [ai.platon.pulsar.api.scripting.DualWorldScriptLoader]. The file is a documentation header
 * followed by a sequence of standalone top-level IIFE statements.
 *
 * These tests pin the curation guarantees of issue #11 section 3:
 *  - the payload is non-blank, structurally intact and free of ES module / `require` syntax;
 *  - the evasions that fabricate values a worker scope can contradict stay excluded;
 *  - the two hand-written blocks that shipped in the stale vendored copy stay removed;
 *  - the self-consistent evasions stay present;
 *  - the documentation header keeps recording how to refresh the payload.
 *
 * The header deliberately names the excluded and removed constructs for auditability, therefore
 * every token check below is scoped to the payload region, i.e. everything after the unique
 * `PAYLOAD START` marker line, and asserts that the header is the only place those tokens appear.
 */
@DisplayName("Vendored page-world stealth payload js/stealth.js")
class StealthPayloadTest {

    companion object {
        private const val RESOURCE = "js/stealth.js"

        /**
         * The marker line that closes the curated documentation header. It is unique in the file.
         */
        private const val PAYLOAD_MARKER = "* PAYLOAD START"

        /**
         * Tokens that must never appear in the injected payload: the first two belong to the
         * hand-written blocks of the stale vendored copy, the rest to the excluded evasions.
         */
        private val FORBIDDEN_IN_PAYLOAD = listOf(
            // Removed hand-written blocks
            "prepareStackTrace",
            "cdc_",
            // Excluded: navigator.hardwareConcurrency
            "hardwareConcurrency",
            // Excluded: webgl.vendor
            "Intel Inc",
            "Intel Iris",
            "UNMASKED_VENDOR_WEBGL",
            "getParameter",
            // Excluded: navigator.languages
            "en-US",
            "\"languages\"",
            "navigator.languages"
        )

        /**
         * Fingerprints of the self-consistent evasions that must stay in the payload.
         */
        private val REQUIRED_EVASION_FINGERPRINTS = listOf(
            "window.chrome.app",
            "window.chrome.csi",
            "window.chrome.loadTimes",
            "chrome.runtime",
            "createElement", // iframe.contentWindow
            "canPlayType", // media.codecs
            "Permissions.prototype", // navigator.permissions
            "Native Client", // navigator.plugins
            "navigator.webdriver",
            "window.outerWidth" // window.outerdimensions
        )

        /**
         * Evasions that the file header must document as excluded, with the reason.
         */
        private val DOCUMENTED_EXCLUSIONS = listOf(
            "navigator.hardwareConcurrency",
            "navigator.languages",
            "webgl.vendor"
        )

        /**
         * Tokens that would break the "plain script, injected as a sequence of top-level
         * statements" contract of `Page.addScriptToEvaluateOnNewDocument`.
         */
        private val FORBIDDEN_SYNTAX = listOf("import ", "import{", "export ", "export{", "require(")

        private fun countOccurrences(text: String, token: String): Int = text.split(token).size - 1
    }

    private val config = ImmutableConfig(loadDefaults = true)
    private val settings = BrowserSettings(config)
    private val loader = settings.dualWorldScriptLoader

    /**
     * The file exactly as packaged, read from the classpath without line filtering.
     */
    private val fileText: String by lazy {
        val stream = javaClass.getResourceAsStream("/$RESOURCE")
        assertNotNull(stream, "Classpath resource /$RESOURCE must exist")
        stream!!.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private val fileLines: List<String> by lazy { fileText.lines() }

    /**
     * Index of the unique `PAYLOAD START` marker line that closes the documentation header.
     */
    private val markerIndex: Int by lazy {
        assertEquals(
            1, fileLines.count { it.trimStart().startsWith(PAYLOAD_MARKER) },
            "The '$PAYLOAD_MARKER' marker line must exist and be unique, it delimits the payload"
        )
        fileLines.indexOfFirst { it.trimStart().startsWith(PAYLOAD_MARKER) }
    }

    /**
     * The curated documentation header, i.e. everything up to and including the marker line.
     */
    private val documentationHeader: String by lazy {
        fileLines.subList(0, markerIndex + 1).joinToString("\n")
    }

    /**
     * The generated payload, i.e. everything after the marker line.
     */
    private val payload: String by lazy {
        fileLines.subList(markerIndex + 1, fileLines.size).joinToString("\n")
    }

    @Test
    @DisplayName("test the payload is non-blank and loads through the dual world loader")
    fun testPayloadIsNonBlankAndLoadsThroughDualWorldLoader() {
        assertTrue(payload.isNotBlank(), "The stealth payload must not be blank")
        assertTrue(payload.length > 100_000, "The stealth payload looks truncated: ${payload.length} chars")

        // The exact call DualWorldScriptLoader.loadDefaultResource() makes
        val lines = ResourceLoader.readAllLines(RESOURCE)
        assertTrue(lines.isNotEmpty(), "ResourceLoader must read the packaged $RESOURCE")

        // The real injection path: loader -> Page.addScriptToEvaluateOnNewDocument
        val pageWorldJs = loader.getPageWorldJs()
        assertNotNull(pageWorldJs)
        assertTrue(pageWorldJs.isNotBlank(), "Page world scripts should not be empty")
        assertTrue(
            pageWorldJs.length >= payload.length,
            "Page world scripts must embed the whole payload"
        )
        assertTrue(
            pageWorldJs.contains("chrome.runtime"),
            "Page world scripts must embed the packaged payload"
        )

        println("Stealth payload: ${payload.length} chars, page world: ${pageWorldJs.length} chars")
    }

    @Test
    @DisplayName("test the payload drops the hand-written blocks and the excluded evasions")
    fun testPayloadDropsHandWrittenBlocksAndExcludedEvasions() {
        FORBIDDEN_IN_PAYLOAD.forEach { token ->
            assertEquals(
                0, countOccurrences(payload, token),
                "The payload must not contain '$token' (see issue #11 section 3)"
            )
            assertEquals(
                countOccurrences(fileText, token), countOccurrences(documentationHeader, token),
                "'$token' may only be documented in the header, never in the injected payload"
            )
        }
    }

    @Test
    @DisplayName("test the payload keeps the self-consistent evasions")
    fun testPayloadKeepsSelfConsistentEvasions() {
        REQUIRED_EVASION_FINGERPRINTS.forEach { fingerprint ->
            assertTrue(
                payload.contains(fingerprint),
                "The payload must keep the evasion fingerprint '$fingerprint'"
            )
        }

        // One top-level statement per page-world evasion, each starting with "(" and ending with
        // ");". The count may grow with a refresh, it must never shrink below the curated ten.
        val payloadLines = payload.lines()
        val statements = payloadLines.count { it.startsWith("(") }
        val terminators = payloadLines.count { it.trimEnd().endsWith(");") }
        assertTrue(statements >= 10, "Expected at least 10 top-level statements, found $statements")
        assertEquals(statements, terminators, "Every top-level statement must be terminated with ');'")
    }

    @Test
    @DisplayName("test the payload is structurally intact and free of module syntax")
    fun testPayloadIsStructurallyIntactAndFreeOfModuleSyntax() {
        val problem = checkDelimiterBalance(payload)
        assertNull(problem, "The payload is corrupt: $problem")

        assertTrue(
            payload.trimStart().startsWith("/*!"),
            "The payload must keep the extract-stealth-evasions provenance header"
        )
        FORBIDDEN_SYNTAX.forEach { token ->
            assertFalse(
                payload.contains(token),
                "The payload must stay a plain script and must not contain '$token'"
            )
        }
    }

    @Test
    @DisplayName("test the header records the upstream version, the refresh command and the exclusions")
    fun testHeaderRecordsUpstreamVersionRefreshCommandAndExclusions() {
        assertTrue(
            documentationHeader.startsWith("/*!"),
            "The documentation header must be the first block comment of the file"
        )
        assertTrue(
            documentationHeader.contains("puppeteer-extra-plugin-stealth@"),
            "The header must record the upstream package and its exact version"
        )
        assertTrue(
            documentationHeader.contains("extract-stealth-evasions@"),
            "The header must record the generator and its exact version"
        )
        assertTrue(
            documentationHeader.contains("npx --yes extract-stealth-evasions@") &&
                documentationHeader.contains("-m false"),
            "The header must record the exact, copy-pasteable generation command"
        )
        DOCUMENTED_EXCLUSIONS.forEach { evasion ->
            assertTrue(
                documentationHeader.contains(evasion),
                "The header must document the excluded evasion '$evasion'"
            )
        }
        assertTrue(
            Regex("""Generated: \d{4}-\d{2}-\d{2}""").containsMatchIn(documentationHeader),
            "The header must record the generation date"
        )
        assertTrue(
            documentationHeader.contains("issue #11"),
            "The header must point back to the issue that motivated the curation"
        )
    }

    /**
     * Checks that every string literal, comment and delimiter of a plain JavaScript script is
     * closed and balanced. This is a JVM side "parseable-ish" integrity check: it catches a
     * truncated or hand-mangled payload without needing a JavaScript engine on the test classpath.
     * Full syntax validation is done out of band with `node --check`, as documented in the header.
     *
     * @return `null` when the script is balanced, otherwise a human readable description
     */
    private fun checkDelimiterBalance(script: String): String? {
        val open = ArrayDeque<Char>()
        // 'c' = code, 'l' = line comment, 'b' = block comment, anything else = the active quote
        var state = 'c'
        var i = 0
        while (i < script.length) {
            val ch = script[i]
            val next = if (i + 1 < script.length) script[i + 1] else '\u0000'

            if (state == 'c') {
                if (ch == '/' && next == '/') {
                    state = 'l'; i += 2; continue
                }
                if (ch == '/' && next == '*') {
                    state = 'b'; i += 2; continue
                }
                if (ch == '\'' || ch == '"' || ch == '`') {
                    state = ch; i += 1; continue
                }
                if (ch == '(' || ch == '{' || ch == '[') {
                    open.addLast(ch); i += 1; continue
                }
                if (ch == ')' || ch == '}' || ch == ']') {
                    val last = open.removeLastOrNull() ?: return "unbalanced '$ch' at offset $i"
                    val expected = when (last) {
                        '(' -> ')'
                        '{' -> '}'
                        else -> ']'
                    }
                    if (ch != expected) return "mismatched '$last' and '$ch' at offset $i"
                    i += 1; continue
                }
                i += 1
                continue
            }

            if (state == 'l') {
                if (ch == '\n') state = 'c'
                i += 1
                continue
            }

            if (state == 'b') {
                if (ch == '*' && next == '/') {
                    state = 'c'; i += 2; continue
                }
                i += 1
                continue
            }

            // Inside a string literal
            if (ch == '\\') {
                i += 2; continue
            }
            if (ch == state) {
                state = 'c'; i += 1; continue
            }
            if (ch == '\n' && state != '`') return "unterminated string literal at offset $i"
            i += 1
        }

        if (state == 'l' || state == 'b') return "script ends inside a comment"
        if (state != 'c') return "script ends inside a string literal"
        if (open.isNotEmpty()) return "unclosed delimiters: ${open.joinToString("")}"
        return null
    }
}
