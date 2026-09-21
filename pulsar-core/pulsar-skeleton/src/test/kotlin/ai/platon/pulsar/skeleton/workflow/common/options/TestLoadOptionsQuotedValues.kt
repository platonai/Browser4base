package ai.platon.pulsar.skeleton.workflow.common.options

import ai.platon.pulsar.common.config.VolatileConfig
import ai.platon.pulsar.common.urls.Hyperlink
import ai.platon.pulsar.common.urls.URLUtils
import ai.platon.pulsar.skeleton.common.options.LoadOptions
import ai.platon.pulsar.skeleton.common.options.PulsarOptions
import ai.platon.pulsar.skeleton.common.urls.CombinedUrlNormalizer
import ai.platon.pulsar.skeleton.common.urls.NormURL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Regression tests for issue #8:
 * `URLUtils.splitUrlArgs truncates on '#' inside a LoadOptions value, silently dropping the rest
 * of the options`.
 *
 * The `#` character is a normal character of an option value, e.g. `-requireNotBlank
 * '#productTitle'`, and must never be treated as the start of a url fragment. A fragment is
 * removed from the url token only.
 *
 * The tests also cover the silent degradation: a value that can not be parsed must not drop the
 * options that follow it and must be reported by [PulsarOptions.hasParseError].
 * */
class TestLoadOptionsQuotedValues {

    private val conf = VolatileConfig.UNSAFE

    private val url = "https://www.amazon.com/dp/B0XXXXXXX?th=1"

    /**
     * The exact configured url of the issue report.
     * */
    private val configuredUrl = "$url -refresh -parse -requireNotBlank '#productTitle' -nMaxRetry 3"

    // ---------------------------------------------------------------------------
    // issue #8: a value containing '#' must survive
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("issue #8: splitUrlArgs keeps the whole argument list, '#' is not a fragment")
    fun testSplitUrlArgsKeepsHashValueAndTrailingOptions() {
        val (url0, args) = URLUtils.splitUrlArgs(configuredUrl)

        assertEquals(url, url0)
        assertEquals("-refresh -parse -requireNotBlank '#productTitle' -nMaxRetry 3", args)
    }

    @Test
    @DisplayName("issue #8: the configured url of the report parses into the right fields")
    fun testIssue8ConfiguredUrlParses() {
        val (_, args) = URLUtils.splitUrlArgs(configuredUrl)
        val options = LoadOptions.parse(args, conf)

        assertTrue(options.refresh, "the options after the '#' value must still be applied")
        assertTrue(options.parse, "the options after the '#' value must still be applied")
        assertEquals("#productTitle", options.requireNotBlank, "the quotes must be removed")
        assertEquals(3, options.nMaxRetry)
        assertFalse(options.hasParseError, "a valid argument vector must not be reported as an error")
    }

    @Test
    @DisplayName("issue #8: NormURL.parse keeps the '#' value and the trailing options")
    fun testNormUrlParseKeepsHashValue() {
        val normURL = NormURL.parse(configuredUrl, conf)

        assertEquals(url, normURL.urlString)
        assertEquals("#productTitle", normURL.options.requireNotBlank)
        assertEquals(3, normURL.options.nMaxRetry)
    }

    @Test
    @DisplayName("issue #8: the url normalizer keeps the '#' value and the trailing options")
    fun testUrlNormalizerKeepsHashValue() {
        val options = LoadOptions.parse("", conf)
        val link = Hyperlink(configuredUrl, "", href = url)
        val normURL = CombinedUrlNormalizer().normalize(link, options, false)

        assertEquals(url, normURL.urlString)
        assertEquals("#productTitle", normURL.options.requireNotBlank)
        assertEquals(3, normURL.options.nMaxRetry)
        assertFalse(normURL.isNil)
    }

    @Test
    @DisplayName("issue #8: the option after an '#' value is applied, not dropped")
    fun testOptionAfterHashValueIsApplied() {
        val args = "-requireNotBlank '#productTitle' -nMaxRetry 7"
        val options = LoadOptions.parse(args, conf)

        assertEquals("#productTitle", options.requireNotBlank)
        assertEquals(7, options.nMaxRetry, "-nMaxRetry after the '#' value must be applied")
    }

    // ---------------------------------------------------------------------------
    // quoted values, both quote characters
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("a single quoted value is unquoted")
    fun testSingleQuotedValueIsUnquoted() {
        val options = LoadOptions.parse("-requireNotBlank '#productTitle'", conf)
        assertEquals("#productTitle", options.requireNotBlank)
    }

    @Test
    @DisplayName("a double quoted value is unquoted")
    fun testDoubleQuotedValueIsUnquoted() {
        val options = LoadOptions.parse("-requireNotBlank \"#productTitle\"", conf)
        assertEquals("#productTitle", options.requireNotBlank)
    }

    @Test
    @DisplayName("an unquoted value is kept as is")
    fun testUnquotedValueIsKept() {
        val options = LoadOptions.parse("-requireNotBlank #productTitle", conf)
        assertEquals("#productTitle", options.requireNotBlank)
    }

    @Test
    @DisplayName("a single quoted value containing a space is kept together")
    fun testSingleQuotedOutLinkWithSpace() {
        val options = LoadOptions.parse("-outLink '#main a' -nMaxRetry 7", conf)

        assertEquals("#main a", options.outLinkSelector)
        assertEquals(7, options.nMaxRetry)
    }

    @Test
    @DisplayName("a double quoted value containing a space is kept together")
    fun testDoubleQuotedOutLinkWithSpace() {
        val options = LoadOptions.parse("-outLink \"#main a\" -nMaxRetry 7", conf)

        assertEquals("#main a", options.outLinkSelector)
        assertEquals(7, options.nMaxRetry)
    }

    @Test
    @DisplayName("a quoted value containing spaces is kept together")
    fun testQuotedValueWithSpaces() {
        val options = LoadOptions.parse("-label 'best sellers' -nMaxRetry 7", conf)

        assertEquals("best sellers", options.label)
        assertEquals(7, options.nMaxRetry)
    }

    @Test
    @DisplayName("a comma separated selector list inside quotes is one value")
    fun testCommaSeparatedSelectorListIsOneValue() {
        val args = "-requireNotBlank '#productTitle, #title' -nMaxRetry 7"
        val options = LoadOptions.parse(args, conf)

        assertEquals("#productTitle, #title", options.requireNotBlank)
        assertEquals(7, options.nMaxRetry)
    }

    @Test
    @DisplayName("split keeps a single quoted value together with its quotes")
    fun testSplitKeepsSingleQuotedValueTogether() {
        val argv = PulsarOptions.split("-outLink '#main a' -nMaxRetry 7")

        assertEquals(listOf("-outLink", "'#main a'", "-nMaxRetry", "7"), argv.toList())
    }

    @Test
    @DisplayName("normalize replaces only the commas outside quoted values")
    fun testNormalizeKeepsCommaInsideQuotes() {
        assertEquals(
            "-requireNotBlank '#a, #b' -nMaxRetry 7",
            PulsarOptions.normalize("-requireNotBlank '#a, #b' -nMaxRetry 7")
        )
        // the old comma separated style is still converted
        assertEquals("-expires 1d -incognito", PulsarOptions.normalize("-expires 1d,-incognito"))
    }

    @Test
    @DisplayName("an apostrophe inside a word is not a quote")
    fun testApostropheInsideWordIsNotAQuote() {
        val options = LoadOptions.parse("-label don't -nMaxRetry 7", conf)

        assertEquals("don't", options.label)
        assertEquals(7, options.nMaxRetry)
    }

    @Test
    @DisplayName("quoted values survive a toString round trip")
    fun testQuotedValuesRoundTrip() {
        val options = LoadOptions.parse("-requireNotBlank '#productTitle' -outLink '#main a' -nMaxRetry 7", conf)
        val reparsed = LoadOptions.parse(options.toString(), conf)

        assertEquals("#productTitle", reparsed.requireNotBlank)
        assertEquals("#main a", reparsed.outLinkSelector)
        assertEquals(7, reparsed.nMaxRetry)
        assertEquals(options, reparsed, "the normalized options must parse back into an equal object")
    }

    // ---------------------------------------------------------------------------
    // url with a real fragment followed by args
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("a fragment is removed from the url token only, the args are untouched")
    fun testFragmentIsRemovedFromUrlTokenOnly() {
        val configured = "https://www.amazon.com/dp/B0XXXXXXX#reviews -requireNotBlank '#productTitle' -nMaxRetry 7"
        val (url0, args) = URLUtils.splitUrlArgs(configured)

        assertEquals("https://www.amazon.com/dp/B0XXXXXXX#reviews", url0)
        assertEquals("-requireNotBlank '#productTitle' -nMaxRetry 7", args)
        assertEquals("https://www.amazon.com/dp/B0XXXXXXX", URLUtils.normalizeOrNull(url0))

        val options = LoadOptions.parse(args, conf)
        assertEquals("#productTitle", options.requireNotBlank)
        assertEquals(7, options.nMaxRetry)
    }

    @Test
    @DisplayName("a url without args keeps being normalized with its fragment removed")
    fun testUrlWithoutArgsFragmentRemoved() {
        val (url0, args) = URLUtils.splitUrlArgs("https://www.amazon.com/dp/B0XXXXXXX#reviews")

        assertEquals("https://www.amazon.com/dp/B0XXXXXXX#reviews", url0)
        assertEquals("", args)
        assertEquals("https://www.amazon.com/dp/B0XXXXXXX", URLUtils.normalizeOrNull(url0, ignoreQuery = true))
    }

    // ---------------------------------------------------------------------------
    // malformed values must not drop the remaining options
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("a malformed option does not drop the options after it")
    fun testMalformedOptionDoesNotDropFollowingOptions() {
        val options = LoadOptions.parse("-parse -refresh -requireNotBlank -nMaxRetry 7", conf)

        assertTrue(options.hasParseError, "the malformed argument vector must be reported")
        assertEquals("", options.requireNotBlank, "the malformed option must not swallow -nMaxRetry")
        assertEquals(7, options.nMaxRetry, "-nMaxRetry after the malformed option must be applied")
        assertTrue(options.parse)
        assertTrue(options.refresh)
    }

    @Test
    @DisplayName("a trailing malformed option does not drop the options before it")
    fun testTrailingMalformedOptionKeepsPreviousOptions() {
        val options = LoadOptions.parse("-parse -refresh -requireNotBlank", conf)

        assertTrue(options.hasParseError, "the malformed argument vector must be reported")
        assertEquals("", options.requireNotBlank)
        assertTrue(options.parse)
        assertTrue(options.refresh)
    }

    @Test
    @DisplayName("a well formed argument vector is not reported as an error")
    fun testWellFormedArgsHaveNoParseError() {
        val options = LoadOptions.parse("-parse -refresh -requireNotBlank '#productTitle' -nMaxRetry 7", conf)

        assertFalse(options.hasParseError)
        assertEquals("#productTitle", options.requireNotBlank)
        assertEquals(7, options.nMaxRetry)
    }
}
