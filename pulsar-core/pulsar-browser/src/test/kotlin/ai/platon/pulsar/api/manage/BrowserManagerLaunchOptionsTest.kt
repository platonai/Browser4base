package ai.platon.pulsar.api.manage

import ai.platon.pulsar.api.Browser
import ai.platon.pulsar.api.BrowserId
import ai.platon.pulsar.api.ChromeOptions
import ai.platon.pulsar.api.LauncherOptions
import ai.platon.pulsar.api.model.BrowserSettings
import ai.platon.pulsar.common.browser.BrowserType
import ai.platon.pulsar.common.config.CapabilityTypes
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.config.MutableConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * A downstream caller must be able to customize the Chrome command line of a browser it launches
 * through the browser manager, and the manager must own the launched browser.
 *
 * Covers the two entry points added for that purpose:
 * 1. [BrowserManager.launch] with an explicit [ChromeOptions] — full control, passed verbatim;
 * 2. [BrowserManager.launchWithExtraOptions] — the standard launch line plus extra options,
 *    following the [ChromeOptions] priority rules.
 * */
@Tag("Unit")
@Tag("Fast")
@DisplayName("Browser manager launches with custom Chrome options")
class BrowserManagerLaunchOptionsTest {

    /**
     * A factory that records every launch instead of starting a browser process.
     * */
    private class RecordingFactory(
        conf: ImmutableConfig = ImmutableConfig(),
        settings: BrowserSettings = BrowserSettings(conf),
    ) : AbstractBrowserFactory(conf, settings) {
        val launched = mutableListOf<Triple<BrowserId, LauncherOptions, ChromeOptions>>()

        override fun connect(browserType: BrowserType, port: Int, settings: BrowserSettings): Browser = mock()

        override fun launch(
            browserId: BrowserId, launcherOptions: LauncherOptions, launchOptions: ChromeOptions
        ): Browser {
            launched += Triple(browserId, launcherOptions, launchOptions)
            return mock<Browser>().also { whenever(it.id).thenReturn(browserId) }
        }
    }

    /**
     * A headless configuration: headless mode makes the standard launch line effectively set
     * `--headless`, which is exactly what the priority rules are about. The user agent stealth
     * mode is off to keep the test hermetic (it would look for an installed Chrome).
     * */
    private fun headlessConf() = MutableConfig().apply {
        this[CapabilityTypes.BROWSER_DISPLAY_MODE] = "HEADLESS"
        this[CapabilityTypes.BROWSER_LAUNCH_USER_AGENT_STEALTH] = "false"
    }

    @Test
    @DisplayName("launch with explicit options passes them verbatim and the manager owns the browser")
    fun launchWithExplicitOptionsPassesThemVerbatim() {
        val factory = RecordingFactory()
        val manager = BasicBrowserManager(factory, factory.conf)
        val browserId = BrowserId.createDefault()
        val launcherOptions = LauncherOptions(factory.settings)
        val launchOptions = ChromeOptions().addArguments("--lang=zh-CN")

        val browser = manager.launch(browserId, launcherOptions, launchOptions)

        val (recordedId, recordedLauncherOptions, recordedLaunchOptions) = factory.launched.single()
        assertEquals(browserId, recordedId)
        assertSame(launcherOptions, recordedLauncherOptions)
        assertSame(launchOptions, recordedLaunchOptions, "the Chrome options must not be replaced")
        assertSame(browser, manager.browsers[browserId], "the manager must own the launched browser")
    }

    @Test
    @DisplayName("launch with extra options keeps the standard launch line and applies the extras")
    fun launchWithExtraOptionsKeepsTheStandardLaunchLine() {
        val conf = headlessConf()
        val factory = RecordingFactory(conf, BrowserSettings(conf))
        val manager = BasicBrowserManager(factory, conf)
        val browserId = BrowserId.createDefault()

        val browser = manager.launchWithExtraOptions(
            browserId, ChromeOptions().addArguments("--lang=zh-CN")
        )

        val args = factory.launched.single().third.toList()
        assertTrue(args.contains("--headless"), "the standard launch line must be preserved: $args")
        assertTrue(args.contains("--lang=zh-CN"), "the extra raw argument must be applied: $args")
        assertSame(browser, manager.browsers[browserId], "the manager must own the launched browser")
    }

    @Test
    @DisplayName("an extra raw argument does not override a key set by the standard launch line")
    fun extraRawArgumentDoesNotOverrideTheStandardLaunchLine() {
        val conf = headlessConf()
        val factory = RecordingFactory(conf, BrowserSettings(conf))
        val manager = BasicBrowserManager(factory, conf)
        val browserId = BrowserId.createDefault()

        manager.launchWithExtraOptions(browserId, ChromeOptions())
        val standardWindowSize = factory.launched.last().third.toList().single { it.startsWith("--window-size=") }

        manager.launchWithExtraOptions(browserId, ChromeOptions().addArguments("--window-size=800,600"))

        val args = factory.launched.last().third.toList()
        assertTrue(args.contains(standardWindowSize), "the standard window size must win: $args")
        assertFalse(args.contains("--window-size=800,600"), "the extra raw argument must be ignored: $args")
    }

    @Test
    @DisplayName("an extra additional argument overrides the standard value of the same key")
    fun extraAdditionalArgumentOverridesTheStandardLaunchLine() {
        val conf = headlessConf()
        val factory = RecordingFactory(conf, BrowserSettings(conf))
        val manager = BasicBrowserManager(factory, conf)
        val browserId = BrowserId.createDefault()

        manager.launchWithExtraOptions(browserId, ChromeOptions())
        val standardWindowSize = factory.launched.last().third.toList().single { it.startsWith("--window-size=") }

        manager.launchWithExtraOptions(browserId, ChromeOptions().addArgument("window-size", "800,600"))

        val args = factory.launched.last().third.toList()
        assertTrue(args.contains("--window-size=800,600"), "the extra additional argument must win: $args")
        assertFalse(args.contains(standardWindowSize), "the standard window size must be replaced: $args")
    }

    @Test
    @DisplayName("the launched browser is closed and forgotten when the manager closes it")
    fun launchedBrowserIsClosedByTheManager() {
        val factory = RecordingFactory()
        val manager = BasicBrowserManager(factory, factory.conf)
        val browserId = BrowserId.createDefault()

        val browser = manager.launchWithExtraOptions(browserId, ChromeOptions())
        manager.closeBrowser(browserId)

        assertNull(manager.browsers[browserId], "a closed browser must not stay in the browser list")
        verify(browser).close()
    }

    @Test
    @DisplayName("an external browser id is rejected, it can only be connected to")
    fun externalBrowserIdIsRejected() {
        val factory = RecordingFactory()
        val manager = BasicBrowserManager(factory, factory.conf)

        assertThrows(IllegalArgumentException::class.java) {
            manager.launchWithExtraOptions(BrowserId.external("unit-test-external"), ChromeOptions())
        }
        assertTrue(factory.launched.isEmpty(), "an external browser id must never reach the factory")
    }
}
