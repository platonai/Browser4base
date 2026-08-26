package ai.platon.pulsar.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeOptionsTest {

    @Test
    fun testParseArgumentsSplitsOnWhitespace() {
        val parsed = ChromeOptions.parseArguments("--start-maximized --disable-features=Translate")

        assertEquals(listOf("--start-maximized", "--disable-features=Translate"), parsed)
    }

    @Test
    fun testParseArgumentsHandlesQuotedArgument() {
        val parsed = ChromeOptions.parseArguments("--proxy-server=\"http=foopy:80;ftp=foopy2\" --no-sandbox")

        assertEquals(listOf("--proxy-server=http=foopy:80;ftp=foopy2", "--no-sandbox"), parsed)
    }

    @Test
    fun testParseArgumentsKeepsCommasAndSemicolons() {
        val parsed = ChromeOptions.parseArguments("--disable-features=Translate,AutofillServerCommunication")

        assertEquals(listOf("--disable-features=Translate,AutofillServerCommunication"), parsed)
    }

    @Test
    fun testParseArgumentsBlankReturnsEmpty() {
        assertTrue(ChromeOptions.parseArguments("").isEmpty())
        assertTrue(ChromeOptions.parseArguments("   ").isEmpty())
    }

    @Test
    fun testParseArgumentsToleratesUnbalancedQuote() {
        val parsed = ChromeOptions.parseArguments("--user-agent=\"Mozilla/5.0 Windows NT 10.0")

        assertEquals(listOf("--user-agent=Mozilla/5.0 Windows NT 10.0"), parsed)
    }

    @Test
    fun testAddArgumentsParsesConfigString() {
        val options = ChromeOptions().addArguments("--disable-features=Translate --proxy-server=127.0.0.1:8080")

        assertTrue(options.toList().contains("--disable-features=Translate"))
        assertTrue(options.toList().contains("--proxy-server=127.0.0.1:8080"))
    }

    @Test
    fun testAddArgumentsVarargAppendsVerbatim() {
        val options = ChromeOptions().addArguments("--start-maximized", "--foo=false")

        // raw arguments are appended verbatim, even a value that looks like false
        assertTrue(options.toList().contains("--start-maximized"))
        assertTrue(options.toList().contains("--foo=false"))
    }

    @Test
    fun testToIncludeTypedAndRawArguments() {
        val options = ChromeOptions().apply {
            noSandbox = true
            addArgument("disable-features", "Translate")
        }.addArguments("--start-maximized")

        val args = options.toList()

        assertTrue(args.contains("--no-sandbox"))
        assertTrue(args.contains("--disable-features=Translate"))
        assertTrue(args.contains("--start-maximized"))
    }

    @Test
    fun testProgrammaticArgumentsTakePrecedenceOverRawArguments() {
        val options = ChromeOptions().apply {
            headless = true
            addArgument("disable-features", "Translate")
        }.addArguments("--headless=false --disable-features=Autofill")

        val args = options.toList()

        // the raw arguments with the same keys are ignored
        assertTrue(args.contains("--headless"))
        assertFalse(args.contains("--headless=false"))
        assertEquals(1, args.count { it == "--disable-features=Translate" })
        assertFalse(args.contains("--disable-features=Autofill"))
    }

    @Test
    fun testRawArgumentsReplaceTrivialPlaceholders() {
        val options = ChromeOptions().addArguments("--remote-debugging-port=9222")

        val args = options.toList()

        // the trivial default placeholder --remote-debugging-port=0 is replaced
        assertTrue(args.contains("--remote-debugging-port=9222"))
        assertFalse(args.contains("--remote-debugging-port=0"))
        assertEquals(1, args.count { it.startsWith("--remote-debugging-port") })
    }

    @Test
    fun testRawArgumentsFillUnsetSwitches() {
        val options = ChromeOptions().addArguments("--headless=true")

        val args = options.toList()

        // headless is false by default (not emitted), so the raw argument takes effect
        assertTrue(args.contains("--headless=true"))
        assertFalse(args.contains("--headless=false"))
    }

    @Test
    fun testLastRawArgumentWithSameKeyWins() {
        val options = ChromeOptions().addArguments("--disable-features=A --disable-features=B")

        val args = options.toList()

        assertEquals(1, args.count { it.startsWith("--disable-features=") })
        assertTrue(args.contains("--disable-features=B"))
    }
}
