package ai.platon.pulsar.api.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * Tests for issue #11 section 1: the `HeadlessChrome/<version>` token must be replaced at
 * launch, because that is the only mechanism that reaches every JavaScript scope of a session
 * (page, iframes, dedicated/shared/service workers) while leaving `Sec-CH-UA*` intact.
 */
class ReducedUserAgentTest {

    private val windowsPrefix = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"

    @Test
    @DisplayName("reduce strips the Headless token from a native headless user agent")
    fun testReduceStripsHeadlessToken() {
        val headless = "$windowsPrefix HeadlessChrome/153.0.0.0 Safari/537.36"

        val reduced = ReducedUserAgent.reduce(headless)

        assertFalse(ReducedUserAgent.isHeadless(reduced), "the reduced user agent must not advertise headless")
        assertTrue(reduced.contains("Chrome/153.0.0.0"), "got: $reduced")
        assertEquals("$windowsPrefix Chrome/153.0.0.0 Safari/537.36", reduced)
    }

    @Test
    @DisplayName("reduce leaves an already-headful user agent untouched")
    fun testReduceLeavesHeadfulUserAgentUntouched() {
        val headful = "$windowsPrefix Chrome/153.0.0.0 Safari/537.36"

        assertEquals(headful, ReducedUserAgent.reduce(headful))
    }

    @Test
    @DisplayName("isHeadless detects the token in any casing")
    fun testIsHeadlessIsCaseInsensitive() {
        assertTrue(ReducedUserAgent.isHeadless("HeadlessChrome/153.0.0.0"))
        assertTrue(ReducedUserAgent.isHeadless("headlesschrome/153.0.0.0"))
        assertFalse(ReducedUserAgent.isHeadless("Chrome/153.0.0.0"))
        assertFalse(ReducedUserAgent.isHeadless(null))
    }

    @Test
    @DisplayName("parseMajorVersion reads the major version from chrome --version output")
    fun testParseMajorVersion() {
        assertEquals(153, ReducedUserAgent.parseMajorVersion("Google Chrome 153.0.8010.52"))
        assertEquals(153, ReducedUserAgent.parseMajorVersion("HeadlessChrome/153.0.0.0"))
        assertEquals(152, ReducedUserAgent.parseMajorVersion("Microsoft Edge 152.0.4234.48"))
        assertNull(ReducedUserAgent.parseMajorVersion("no version here"))
    }

    @Test
    @DisplayName("build produces a reduced desktop user agent for the given major version")
    fun testBuildProducesReducedUserAgent() {
        val ua = ReducedUserAgent.build(153, ReducedUserAgent.Platform.WINDOWS)

        assertEquals("$windowsPrefix Chrome/153.0.0.0 Safari/537.36", ua)
        assertFalse(ReducedUserAgent.isHeadless(ua))
    }

    @Test
    @DisplayName("build uses platform-appropriate prefixes")
    fun testBuildUsesPlatformPrefix() {
        assertTrue(ReducedUserAgent.build(153, ReducedUserAgent.Platform.MAC).contains("Macintosh"))
        assertTrue(ReducedUserAgent.build(153, ReducedUserAgent.Platform.LINUX).contains("X11"))
        assertTrue(ReducedUserAgent.build(153, ReducedUserAgent.Platform.WINDOWS).contains("Windows NT"))
    }

    @Test
    @DisplayName("the major version is derived from Chrome's version-named install directory")
    fun testMajorVersionFromInstallLayout() {
        val applicationDir = Files.createTempDirectory("chrome-application")
        try {
            // Chrome keeps the launcher stub next to a version-named directory holding the real binary.
            val binary = applicationDir.resolve("chrome.exe")
            Files.createFile(binary)
            Files.createDirectories(applicationDir.resolve("151.0.7000.10"))
            Files.createDirectories(applicationDir.resolve("153.0.8010.52"))
            Files.createDirectories(applicationDir.resolve("not-a-version"))

            assertEquals(153, ReducedUserAgent.majorVersionFromInstallLayout(binary))
        } finally {
            applicationDir.toFile().deleteRecursively()
        }
    }

    @Test
    @DisplayName("no version directory means no derived version")
    fun testMajorVersionFromInstallLayoutWithoutVersionDirs() {
        val dir = Files.createTempDirectory("chrome-application-empty")
        try {
            val binary = dir.resolve("chrome.exe")
            Files.createFile(binary)

            assertNull(ReducedUserAgent.majorVersionFromInstallLayout(binary))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    @DisplayName("buildOrNull prefers a configured user agent and reduces it")
    fun testBuildOrNullPrefersConfiguredUserAgent() {
        val configured = "$windowsPrefix HeadlessChrome/153.0.0.0 Safari/537.36"

        val resolved = ReducedUserAgent.buildOrNull(configured, binary = null)

        assertEquals("$windowsPrefix Chrome/153.0.0.0 Safari/537.36", resolved)
    }

    @Test
    @DisplayName("buildOrNull returns null when nothing is configured and no version can be derived")
    fun testBuildOrNullReturnsNullWithoutVersion() {
        val dir = Files.createTempDirectory("chrome-no-version")
        try {
            val binary = dir.resolve("chrome.exe")
            Files.createFile(binary)

            assertNull(ReducedUserAgent.buildOrNull(configuredUserAgent = "  ", binary = binary))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    @DisplayName("buildOrNull ignores a blank configured user agent")
    fun testBuildOrNullIgnoresBlankConfiguredUserAgent() {
        // binary = null keeps this hermetic: no installed Chrome is consulted.
        assertNull(ReducedUserAgent.buildOrNull(configuredUserAgent = "   ", binary = null))
    }
}
