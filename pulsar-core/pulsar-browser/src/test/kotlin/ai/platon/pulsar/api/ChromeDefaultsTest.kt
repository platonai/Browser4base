package ai.platon.pulsar.api

import ai.platon.pulsar.common.config.CapabilityTypes
import ai.platon.pulsar.common.config.MutableConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChromeDefaultsTest {

    @Test
    fun testLaunchConfigLoadsCodeDefaultsWhenNotConfigured() {
        val config = ChromeLaunchConfig.load(MutableConfig())

        assertEquals(ChromeDefaults.WINDOW_POSITION, config.windowPosition)
        assertEquals(ChromeDefaults.PAGE_LOAD_STRATEGY, config.pageLoadStrategy)
        assertEquals(ChromeDefaults.THROW_EXCEPTION_ON_SCRIPT_ERROR, config.throwExceptionOnScriptError)
    }

    @Test
    fun testLaunchConfigLoadsOverridesFromConfigFile() {
        val conf = MutableConfig()
        conf[CapabilityTypes.BROWSER_LAUNCH_WINDOW_POSITION] = "100,200"
        conf[CapabilityTypes.BROWSER_LAUNCH_PAGE_LOAD_STRATEGY] = "eager"
        conf[CapabilityTypes.BROWSER_LAUNCH_THROW_EXCEPTION_ON_SCRIPT_ERROR] = "false"

        val config = ChromeLaunchConfig.load(conf)

        assertEquals("100,200", config.windowPosition)
        assertEquals("eager", config.pageLoadStrategy)
        assertEquals(false, config.throwExceptionOnScriptError)
    }

    @Test
    fun testChromeOptionsDefaultsAreCentralizedInChromeDefaults() {
        val options = ChromeOptions()

        assertEquals(ChromeDefaults.HEADLESS, options.headless)
        assertEquals(ChromeDefaults.REMOTE_DEBUGGING_PORT, options.remoteDebuggingPort)
        assertEquals(ChromeDefaults.DISABLE_BLINK_FEATURES, options.disableBlinkFeatures)
        assertEquals(ChromeDefaults.REMOTE_ALLOW_ORIGINS, options.remoteAllowOrigins)
        assertEquals(ChromeDefaults.NO_SANDBOX, options.noSandbox)
        assertEquals(ChromeDefaults.PROXY_SERVER, options.proxyServer)
    }

    @Test
    fun testLaunchLogicDefaultsAreCentralized() {
        assertTrue(ChromeDefaults.LAUNCH_RETRY_COUNT > 0)
        assertTrue(ChromeDefaults.LAUNCH_RETRY_INTERVAL_MS > 0)
        assertEquals(5, ChromeDefaults.LAUNCH_RETRY_COUNT)
        assertEquals(3000L, ChromeDefaults.LAUNCH_RETRY_INTERVAL_MS)
        assertEquals(
            listOf("--remote-debugging-port=0", "--remote-allow-origins=*", "about:blank"),
            ChromeDefaults.SYSTEM_DEFAULT_BROWSER_ARGS
        )
    }
}
