package ai.platon.pulsar.api.model

import ai.platon.pulsar.api.ChromeDefaults
import ai.platon.pulsar.api.DelayPreset
import ai.platon.pulsar.api.InteractSettings
import ai.platon.pulsar.common.browser.InteractLevel
import ai.platon.pulsar.common.config.CapabilityTypes
import ai.platon.pulsar.common.config.MutableConfig
import ai.platon.pulsar.common.serialize.json.pulsarObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.DisplayName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BrowserSettingsTests {

    @Test
    fun testInteractSettings() {
        val settings = InteractSettings()
        settings.overrideSystemProperties()

        val json = System.getProperty(CapabilityTypes.BROWSER_INTERACT_SETTINGS)
        assertNotNull(json)

        val settings2: InteractSettings = pulsarObjectMapper().readValue(json)
        assertEquals(settings.toString(), settings2.toString())
    }

    @Test
    fun testDelayPolicy() {
        val settings = InteractSettings()
        val delayPolicy = settings.generateRestrictedDelayPolicy()

        assertNotNull(delayPolicy[""])
        assertNotNull(delayPolicy["default"])
        delayPolicy.values.forEach { assertTrue(it.first >= 50, "range: $it") }
        delayPolicy.values.forEach { assertTrue(it.last <= 2000, "range: $it") }
    }

    @Test
    fun testDelayPreset() {
        val settings = InteractSettings()
        val defaultPolicy = settings.generateRestrictedDelayPolicy().toMap()
        val fastestPolicy = settings.applyDelayPreset(DelayPreset.FASTEST).generateRestrictedDelayPolicy()

        assertEquals(10..10, fastestPolicy["gap"])
        assertEquals(10..10, fastestPolicy["type"])
        assertEquals(fastestPolicy["default"], fastestPolicy[""])

        val fastPolicy = settings.applyDelayPreset(DelayPreset.FAST).generateRestrictedDelayPolicy()
        assertTrue((fastPolicy["type"]?.last ?: Int.MAX_VALUE) < (defaultPolicy["type"]?.last ?: Int.MIN_VALUE))
        assertEquals(fastPolicy["default"], fastPolicy[""])

        val stealthPolicy = settings.applyDelayPreset(DelayPreset.STEALTH).generateRestrictedDelayPolicy()
        assertTrue((stealthPolicy["gap"]?.first ?: Int.MIN_VALUE) > (defaultPolicy["gap"]?.first ?: Int.MAX_VALUE))
        assertEquals(stealthPolicy["default"], stealthPolicy[""])

        settings.applyDelayPreset(DelayPreset.DEFAULT)
        assertEquals(defaultPolicy["type"], settings.delayPolicy["type"])
        assertEquals(settings.delayPolicy["default"], settings.delayPolicy[""])
    }

    @Test
    fun testFastestDelayPolicy() {
        val policy = InteractSettings.FASTEST_DELAY_POLICY

        assertEquals(10..10, policy["gap"])
        assertEquals(10..10, policy["type"])
        assertEquals(policy["default"], policy[""])
        policy.values.forEach { assertEquals(10..10, it) }
    }

    @Test
    fun testCreateLevelDelayPresetMapping() {
        val fastest = InteractSettings.create(InteractLevel.FASTEST).delayPolicy
        val fast = InteractSettings.create(InteractLevel.FASTER).delayPolicy
        val normal = InteractSettings.create(InteractLevel.DEFAULT).delayPolicy
        val stealth = InteractSettings.create(InteractLevel.BEST_DATA).delayPolicy

        assertEquals(10..10, fastest["gap"])
        assertEquals(10..10, fastest["type"])
        assertEquals(fastest["default"], fastest[""])
        assertTrue((fast["type"]?.last ?: Int.MAX_VALUE) < (normal["type"]?.last ?: Int.MIN_VALUE))
        assertTrue((normal["type"]?.last ?: Int.MAX_VALUE) < (stealth["type"]?.last ?: Int.MIN_VALUE))
        assertEquals(fast["default"], fast[""])
        assertEquals(normal["default"], normal[""])
        assertEquals(stealth["default"], stealth[""])
    }

    @Test
    fun testTimeoutPolicy() {
        val settings = InteractSettings()
        val timeoutPolicy = settings.generateRestrictedTimeoutPolicy()

        assertNotNull(timeoutPolicy[""])
        assertNotNull(timeoutPolicy["default"])
        timeoutPolicy.values.forEach { assertTrue(it >= settings.minTimeout, "timeout: $it") }
        timeoutPolicy.values.forEach { assertTrue(it <= settings.maxTimeout, "timeout: $it") }
    }

    @Test
    fun testInteractSettingsJson() {
        val settings = InteractSettings()
        val json = settings.toJson()
        assertNotNull(json)

        val settings2: InteractSettings = pulsarObjectMapper().readValue(json)
        assertNotNull(settings2)
        assertEquals(settings.toString(), settings2.toString())
    }

    @Test
    fun testOverrideConfiguration() {
        val settings = InteractSettings()
        val conf = MutableConfig()
        settings.overrideConfiguration(conf)

        val json = conf.get(CapabilityTypes.BROWSER_INTERACT_SETTINGS)
        assertNotNull(json)

        val settings2: InteractSettings = pulsarObjectMapper().readValue(json)
        assertEquals(settings.toString(), settings2.toString())
    }

    @Test
    fun testChromeArgumentsReadFromConfig() {
        val conf = MutableConfig()
        conf[CapabilityTypes.BROWSER_LAUNCH_CHROME_ARGS] =
            "--disable-features=Translate --proxy-server=\"http=foopy:80;ftp=foopy2\""

        val settings = BrowserSettings(conf)
        assertEquals(
            listOf("--disable-features=Translate", "--proxy-server=http=foopy:80;ftp=foopy2"),
            settings.chromeArguments
        )
    }

    @Test
    fun testChromeArgumentsEmptyWhenNotConfigured() {
        val settings = BrowserSettings(MutableConfig())
        assertTrue(settings.chromeArguments.isEmpty())
    }

    @Test
    fun testCreateChromeOptionsUsesLaunchConfigFromConfigFile() {
        val conf = MutableConfig()
        conf[CapabilityTypes.BROWSER_LAUNCH_WINDOW_POSITION] = "100,200"
        conf[CapabilityTypes.BROWSER_LAUNCH_PAGE_LOAD_STRATEGY] = "eager"
        conf[CapabilityTypes.BROWSER_LAUNCH_THROW_EXCEPTION_ON_SCRIPT_ERROR] = "false"
        // Keep this test hermetic: do not go looking for an installed Chrome to derive a UA from.
        conf[CapabilityTypes.BROWSER_LAUNCH_USER_AGENT_STEALTH] = "false"

        val settings = BrowserSettings(conf)
        val args = settings.createChromeOptions(emptyMap()).toList()

        assertTrue(args.contains("--window-position=100,200"))
    }

    @Test
    fun testCreateChromeOptionsUsesCodeDefaultsWhenNotConfigured() {
        val conf = MutableConfig()
        conf[CapabilityTypes.BROWSER_LAUNCH_USER_AGENT_STEALTH] = "false"
        val settings = BrowserSettings(conf)
        val args = settings.createChromeOptions(emptyMap()).toList()

        assertTrue(args.contains("--window-position=0,0"))
    }

    @Test
    @DisplayName("Selenium capabilities are not emitted on the Chrome command line")
    fun testSeleniumCapabilitiesAreNotChromeArguments() {
        // --pageLoadStrategy and --throwExceptionOnScriptError are Selenium capabilities, not
        // Chrome switches: on a plain Chrome process they are inert noise visible in
        // chrome://version. See issue #11 section 6.
        val conf = MutableConfig()
        conf[CapabilityTypes.BROWSER_LAUNCH_USER_AGENT_STEALTH] = "false"
        val settings = BrowserSettings(conf)

        val args = settings.createChromeOptions(emptyMap()).toList()

        assertTrue(
            args.none { it.startsWith("--pageLoadStrategy") },
            "pageLoadStrategy must not be passed to Chrome, got: $args"
        )
        assertTrue(
            args.none { it.startsWith("--throwExceptionOnScriptError") },
            "throwExceptionOnScriptError must not be passed to Chrome, got: $args"
        )
        // The values are still part of the settings model, they are just not CLI arguments.
        assertEquals(ChromeDefaults.PAGE_LOAD_STRATEGY, settings.launchConfig.pageLoadStrategy)
    }

    @Test
    @DisplayName("gpu / scrollbar / audio flags are off by default so Chrome decides")
    fun testStealthHostileFlagsAreNotForcedByDefault() {
        val conf = MutableConfig()
        conf[CapabilityTypes.BROWSER_LAUNCH_USER_AGENT_STEALTH] = "false"
        val settings = BrowserSettings(conf)

        val args = settings.createChromeOptions(emptyMap()).toList()

        assertTrue("--disable-gpu" !in args, "--disable-gpu must not be forced, got: $args")
        assertTrue("--hide-scrollbars" !in args, "--hide-scrollbars must not be forced, got: $args")
        assertTrue("--mute-audio" !in args, "--mute-audio must not be forced, got: $args")
    }

    @Test
    @DisplayName("gpu / scrollbar / audio flags can be turned on from configuration")
    fun testStealthHostileFlagsAreConfigurable() {
        val conf = MutableConfig()
        conf[CapabilityTypes.BROWSER_LAUNCH_USER_AGENT_STEALTH] = "false"
        conf[CapabilityTypes.BROWSER_LAUNCH_DISABLE_GPU] = "true"
        conf[CapabilityTypes.BROWSER_LAUNCH_HIDE_SCROLLBARS] = "true"
        conf[CapabilityTypes.BROWSER_LAUNCH_MUTE_AUDIO] = "true"
        val settings = BrowserSettings(conf)

        val args = settings.createChromeOptions(emptyMap()).toList()

        assertTrue("--disable-gpu" in args, "got: $args")
        assertTrue("--hide-scrollbars" in args, "got: $args")
        assertTrue("--mute-audio" in args, "got: $args")
    }

    @Test
    @DisplayName("a configured user agent is passed to Chrome with the headless token reduced")
    fun testConfiguredUserAgentIsReducedAndPassed() {
        val conf = MutableConfig()
        conf[CapabilityTypes.BROWSER_LAUNCH_USER_AGENT] =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) HeadlessChrome/153.0.0.0 Safari/537.36"
        val settings = BrowserSettings(conf)

        val args = settings.createChromeOptions(emptyMap()).toList()

        val userAgentArg = args.firstOrNull { it.startsWith("--user-agent=") }
        assertNotNull(userAgentArg, "a configured user agent must reach the command line, got: $args")
        assertTrue("HeadlessChrome" !in userAgentArg, "the headless token must be dropped: $userAgentArg")
        assertTrue(userAgentArg.contains("Chrome/153.0.0.0"), "got: $userAgentArg")
    }

    @Test
    @DisplayName("no user agent is forced when stealth is disabled and none is configured")
    fun testNoUserAgentWhenStealthDisabled() {
        val conf = MutableConfig()
        conf[CapabilityTypes.BROWSER_LAUNCH_USER_AGENT_STEALTH] = "false"
        val settings = BrowserSettings(conf)

        val args = settings.createChromeOptions(emptyMap()).toList()

        assertTrue(
            args.none { it.startsWith("--user-agent") },
            "no user agent must be forced, got: $args"
        )
    }
}
